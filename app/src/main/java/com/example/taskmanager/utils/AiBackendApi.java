package com.example.taskmanager.utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backend wrapper for AI prompt parsing.
 *
 * IMPORTANT: The Android app must NOT call OpenAI directly.
 * It calls your backend, which calls OpenAI and enforces the global €5 cap.
 */
public class AiBackendApi {

    /**
     * TODO: Replace with your backend base URL.
     * Example: "https://api.yourdomain.com"
     */
    public static String BASE_URL = "https://YOUR_BACKEND_DOMAIN";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface StatusCallback {
        void onResult(boolean enabled, @Nullable String message);
        void onError(@NonNull String error);
    }

    public interface ParseCallback {
        void onResult(@NonNull ParseResponse response);
        void onError(@NonNull String error);
    }

    public static class ParseResponse {
        public boolean enabled = true;
        public String message;
        public ParsedResult result;
    }

    public static class ParsedResult {
        public String intent; // create / update
        public String type;   // meeting / task
        public String title;
        public String details;

        public Long startMillis;
        public Long endMillis;
        public Long durationMinutes;

        // optional debug/echo fields
        public String rawStart;
        public String rawEnd;
    }

    /** GET /ai/status -> { enabled: boolean, message?: string } */
    public static void fetchStatus(@NonNull StatusCallback cb) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject json = httpJson("GET", BASE_URL + "/ai/status", null);
                boolean enabled = json.optBoolean("enabled", true);
                String message = json.optString("message", null);
                MAIN.post(() -> cb.onResult(enabled, message));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(e.getMessage() != null ? e.getMessage() : "Status check failed"));
            }
        });
    }

    /** POST /ai/parse -> { enabled?: boolean, message?: string, result?: {...} } */
    public static void parsePrompt(@NonNull String prompt, @NonNull ParseCallback cb) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("prompt", prompt);
                body.put("timezone", TimeZone.getDefault().getID());
                body.put("locale", Locale.getDefault().toLanguageTag());

                JSONObject json = httpJson("POST", BASE_URL + "/ai/parse", body);
                ParseResponse resp = new ParseResponse();

                // Allow backend to return enabled/message at root
                if (json.has("enabled")) {
                    resp.enabled = json.optBoolean("enabled", true);
                }
                resp.message = json.optString("message", null);

                // Result can be nested under "result" OR be the root itself.
                JSONObject resultObj = json.optJSONObject("result");
                if (resultObj == null && json.has("intent")) {
                    resultObj = json;
                }

                if (resultObj != null) {
                    resp.result = parseResult(resultObj);
                }

                MAIN.post(() -> cb.onResult(resp));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(e.getMessage() != null ? e.getMessage() : "Prompt failed"));
            }
        });
    }

    // -------------------- Internal helpers --------------------

    private static ParsedResult parseResult(@NonNull JSONObject obj) {
        ParsedResult r = new ParsedResult();
        r.intent = optStringAny(obj, "intent");
        r.type = optStringAny(obj, "type");
        r.title = optStringAny(obj, "title");
        r.details = optStringAny(obj, "details", "description");

        Object startVal = optAny(obj, "start_time", "startTime", "start", "start_datetime");
        Object endVal = optAny(obj, "end_time", "endTime", "end", "end_datetime");

        r.rawStart = startVal != null ? String.valueOf(startVal) : null;
        r.rawEnd = endVal != null ? String.valueOf(endVal) : null;

        r.startMillis = parseTimeToMillis(startVal);
        r.endMillis = parseTimeToMillis(endVal);

        // duration can be minutes or an object like {minutes: 60}
        Long dur = optLongAny(obj, "duration_minutes", "durationMinutes", "duration_mins");
        if (dur == null) {
            JSONObject durObj = obj.optJSONObject("duration");
            if (durObj != null) {
                Long mins = optLongAny(durObj, "minutes", "mins");
                Long hours = optLongAny(durObj, "hours");
                if (hours != null) mins = (mins == null ? 0 : mins) + (hours * 60L);
                dur = mins;
            }
        }
        r.durationMinutes = dur;

        // If end missing but duration exists, compute end.
        if (r.startMillis != null && r.endMillis == null && r.durationMinutes != null) {
            r.endMillis = r.startMillis + (r.durationMinutes * 60_000L);
        }

        return r;
    }

    private static JSONObject httpJson(@NonNull String method,
                                      @NonNull String urlStr,
                                      @Nullable JSONObject body) throws IOException, JSONException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream os = conn.getOutputStream();
                os.write(out);
                os.flush();
                os.close();
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(is);
            if (text == null || text.trim().isEmpty()) {
                // Make a minimal json so caller can handle.
                JSONObject empty = new JSONObject();
                empty.put("enabled", code != 402 && code != 403);
                empty.put("message", "HTTP " + code);
                return empty;
            }

            // Sometimes backend might return arrays; wrap them.
            String t = text.trim();
            if (t.startsWith("[")) {
                JSONObject wrap = new JSONObject();
                wrap.put("array", new JSONArray(t));
                return wrap;
            }
            JSONObject json = new JSONObject(t);

            // If backend uses 402/403 for cap reached, normalize it here.
            if (code == 402 || code == 403) {
                json.put("enabled", false);
            }
            return json;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(@Nullable InputStream is) throws IOException {
        if (is == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }

    private static Object optAny(JSONObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                return obj.opt(k);
            }
        }
        return null;
    }

    private static String optStringAny(JSONObject obj, String... keys) {
        for (String k : keys) {
            String v = obj.optString(k, null);
            if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v.trim())) {
                return v;
            }
        }
        return null;
    }

    private static Long optLongAny(JSONObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                Object v = obj.opt(k);
                if (v instanceof Number) return ((Number) v).longValue();
                if (v instanceof String) {
                    try {
                        return Long.parseLong(((String) v).trim());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static Long parseTimeToMillis(@Nullable Object val) {
        if (val == null) return null;

        if (val instanceof Number) {
            return ((Number) val).longValue();
        }

        String s = String.valueOf(val).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;

        // Accept ISO-8601: 2026-01-03T10:00:00+01:00
        try {
            OffsetDateTime odt = OffsetDateTime.parse(s);
            return odt.toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }

        // Accept ISO-8601 with zone: 2026-01-03T10:00:00+01:00[Europe/Berlin]
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(s);
            return zdt.toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }

        // As a fallback, accept "yyyy-MM-dd HH:mm" (backend can send this if easier)
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return ZonedDateTime.of(
                    java.time.LocalDateTime.parse(s, f),
                    java.time.ZoneId.systemDefault()
            ).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }

        return null;
    }
}
