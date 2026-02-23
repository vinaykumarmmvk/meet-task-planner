package com.enerflowlabs.todoplanner.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Downloads an attachment to the device and opens it with an appropriate viewer.
 *
 * - Android 10+ (API 29+): saves into public Downloads via MediaStore.
 * - Android 9 and below: saves into app external Downloads (no permission needed) and opens via FileProvider.
 */
public final class AttachmentUtils {

    private AttachmentUtils() {}


    /**
     * Opens an attachment for viewing only (no download/copy).
     * Uses the original Uri and grants read permission to the chosen viewer app.
     */
    public static void viewOnly(Context context, Uri sourceUri, String displayName) {
        if (context == null || sourceUri == null) return;
        String name = sanitizeFileName(displayName);
        String mime = getMimeType(context, sourceUri, name);
        openUri(context, sourceUri, mime);
    }

    public static void downloadAndOpen(Context context, Uri sourceUri, String preferredName) {
        if (context == null || sourceUri == null) return;

        String name = sanitizeFileName(preferredName);
        if (TextUtils.isEmpty(name)) {
            name = "attachment";
        }

        String mime = getMimeType(context, sourceUri, name);

        try {
            Uri outUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                outUri = saveToPublicDownloads(context, sourceUri, name, mime);
            } else {
                outUri = saveToAppDownloadsAndGetUri(context, sourceUri, name);
            }

            if (outUri == null) {
                Toast.makeText(context, "Failed to download file", Toast.LENGTH_SHORT).show();
                return;
            }

            openUri(context, outUri, mime);

        } catch (Exception e) {
            Toast.makeText(context, "Failed to open file", Toast.LENGTH_SHORT).show();
        }
    }

    private static Uri saveToPublicDownloads(Context context, Uri sourceUri, String displayName, String mime) throws Exception {
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, uniqueNameInDownloads(context, displayName));
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MeetTaskPlanner");

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri outUri = resolver.insert(collection, values);
        if (outUri == null) return null;

        try (InputStream in = resolver.openInputStream(sourceUri);
             OutputStream out = resolver.openOutputStream(outUri)) {

            if (in == null || out == null) return null;
            copy(in, out);
        }

        return outUri;
    }

    private static Uri saveToAppDownloadsAndGetUri(Context context, Uri sourceUri, String fileName) throws Exception {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = context.getFilesDir();

        if (!dir.exists()) dir.mkdirs();

        File outFile = uniqueFile(dir, fileName);

        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(outFile)) {

            if (in == null) return null;
            copy(in, out);
        }

        // Use FileProvider so external viewers can read it
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".provider",
                outFile
        );
    }

    private static void openUri(Context context, Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(intent, "Open with");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private static String getMimeType(Context context, Uri uri, String name) {
        String mime = null;
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {}

        if (!TextUtils.isEmpty(mime)) return mime;

        // Fallback: derive from extension
        String ext = getExtension(name);
        if (!TextUtils.isEmpty(ext)) {
            String guess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            if (!TextUtils.isEmpty(guess)) return guess;
        }

        return "*/*";
    }

    private static String getExtension(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return "";
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return null;
        // Remove path separators and illegal characters for safety
        return name.replaceAll("[\\/:*?\"<>|]", "_").trim();
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        int i = 1;
        while (true) {
            File candidate = new File(dir, base + " (" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
            i++;
        }
    }

    private static String uniqueNameInDownloads(Context context, String name) {
        // MediaStore will allow duplicates, but many file managers look nicer with (1), (2)…
        // We'll check existing entries in our app folder in Downloads (best-effort).
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

            String base = name;
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                base = name.substring(0, dot);
                ext = name.substring(dot);
            }

            String candidate = name;
            int i = 1;
            while (existsInDownloads(resolver, collection, candidate)) {
                candidate = base + " (" + i + ")" + ext;
                i++;
                if (i > 50) break;
            }
            return candidate;
        } catch (Exception ignored) {
            return name;
        }
    }

    private static boolean existsInDownloads(ContentResolver resolver, Uri collection, String displayName) {
        Cursor c = null;
        try {
            c = resolver.query(
                    collection,
                    new String[]{MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                    new String[]{displayName},
                    null
            );
            return c != null && c.moveToFirst();
        } catch (Exception ignored) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }
}
