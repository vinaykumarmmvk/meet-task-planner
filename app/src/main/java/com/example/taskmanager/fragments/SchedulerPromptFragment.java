package com.example.taskmanager.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.database.TaskDao;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.notifications.ReminderUtils;
import com.example.taskmanager.utils.AiBackendApi;
import com.example.taskmanager.utils.DateUtils;
import com.example.taskmanager.utils.DialogUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SchedulerPromptFragment extends Fragment {

    private TextInputEditText editPrompt;
    private MaterialButton btnMic;
    private MaterialButton btnSend;
    private ProgressBar progress;
    private TextView tvDisabled;

    private ActivityResultLauncher<Intent> speechLauncher;

    private boolean promptEnabled = true;
    private String disabledMessage = null;

    public SchedulerPromptFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scheduler_prompt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editPrompt = view.findViewById(R.id.edit_prompt);
        btnMic = view.findViewById(R.id.btn_mic);
        btnSend = view.findViewById(R.id.btn_send);
        progress = view.findViewById(R.id.progress);
        tvDisabled = view.findViewById(R.id.tv_disabled);

        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) {
                        return;
                    }
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        editPrompt.setText(matches.get(0));
                        editPrompt.setSelection(editPrompt.getText() != null ? editPrompt.getText().length() : 0);
                    }
                }
        );

        btnMic.setOnClickListener(v -> startSpeechToText());
        btnSend.setOnClickListener(v -> runPrompt());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPromptStatus();
    }

    private void refreshPromptStatus() {
        setBusy(true);
        AiBackendApi.fetchStatus(new AiBackendApi.StatusCallback() {
            @Override
            public void onResult(boolean enabled, @Nullable String message) {
                promptEnabled = enabled;
                disabledMessage = message;
                applyEnabledState();
                setBusy(false);
            }

            @Override
            public void onError(@NonNull String error) {
                // If status check fails, we keep prompt enabled but show a small toast.
                promptEnabled = true;
                disabledMessage = null;
                applyEnabledState();
                setBusy(false);
                Toast.makeText(getContext(), getString(R.string.ai_status_failed, error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startSpeechToText() {
        if (!promptEnabled) {
            Toast.makeText(getContext(), getDisabledText(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speak_now));

        try {
            speechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.speech_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void runPrompt() {
        if (!promptEnabled) {
            Toast.makeText(getContext(), getDisabledText(), Toast.LENGTH_SHORT).show();
            return;
        }

        String promptText = editPrompt.getText() != null ? editPrompt.getText().toString().trim() : "";
        if (TextUtils.isEmpty(promptText)) {
            Toast.makeText(getContext(), getString(R.string.prompt_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        AiBackendApi.parsePrompt(promptText, new AiBackendApi.ParseCallback() {
            @Override
            public void onResult(@NonNull AiBackendApi.ParseResponse response) {
                if (!response.enabled) {
                    // Global €5 cap reached (or backend disabled prompts)
                    promptEnabled = false;
                    disabledMessage = response.message;
                    applyEnabledState();
                    setBusy(false);
                    Toast.makeText(getContext(), getDisabledText(), Toast.LENGTH_LONG).show();
                    return;
                }

                if (response.result == null) {
                    setBusy(false);
                    Toast.makeText(getContext(), getString(R.string.prompt_parse_failed), Toast.LENGTH_SHORT).show();
                    return;
                }

                handleParsedResult(response.result);
                setBusy(false);
            }

            @Override
            public void onError(@NonNull String error) {
                setBusy(false);
                Toast.makeText(getContext(), getString(R.string.prompt_request_failed, error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleParsedResult(@NonNull AiBackendApi.ParsedResult r) {
        String intent = safeLower(r.intent);
        if (!"create".equals(intent) && !"update".equals(intent)) {
            Toast.makeText(getContext(), getString(R.string.prompt_invalid_intent), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(r.title)) {
            Toast.makeText(getContext(), getString(R.string.prompt_missing_title), Toast.LENGTH_SHORT).show();
            return;
        }

        if (r.startMillis == null) {
            Toast.makeText(getContext(), getString(R.string.prompt_missing_time), Toast.LENGTH_SHORT).show();
            return;
        }

        long start = r.startMillis;
        long end;
        if (r.endMillis != null) {
            end = r.endMillis;
        } else if (r.durationMinutes != null) {
            end = start + (r.durationMinutes * 60_000L);
        } else {
            Toast.makeText(getContext(), getString(R.string.prompt_missing_time), Toast.LENGTH_SHORT).show();
            return;
        }

        if (end <= start) {
            Toast.makeText(getContext(), getString(R.string.prompt_invalid_time_range), Toast.LENGTH_SHORT).show();
            return;
        }

        if ("create".equals(intent)) {
            createTaskFromPrompt(r, start, end);
        } else {
            updateTaskFromPrompt(r, start, end);
        }
    }

    private void createTaskFromPrompt(@NonNull AiBackendApi.ParsedResult r, long start, long end) {
        if (getContext() == null) return;

        Task task = new Task();
        task.title = r.title;
        task.description = r.details;
        task.isAllDay = false;
        task.isOngoing = false;
        task.startTimestamp = start;
        task.stopTimestamp = end;
        task.durationMillis = end - start;
        task.createdAt = System.currentTimeMillis();
        task.status = Task.STATUS_NOT_STARTED;

        // Store dates used by Calendar/Tasks UI
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        task.fromDate = dateFmt.format(new Date(start));
        task.toDate = dateFmt.format(new Date(end));
        task.date = task.fromDate;

        SimpleDateFormat dtFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        task.dateTime = dtFmt.format(new Date(task.createdAt));

        TaskDao dao = AppDatabase.getInstance(getContext()).taskDao();
        long id = dao.insertAndReturnId(task);
        task.id = (int) id;

        ReminderUtils.scheduleReminderForTask(requireContext(), task);

        String durationStr = DateUtils.formatDuration(task.durationMillis);
        DialogUtils.showSuccessDialog(getContext(),
                "Title: " + task.title +
                        "\nDescription: " + (task.description == null ? "" : task.description) +
                        "\nDuration: " + durationStr +
                        "\n\nAbove task is added successfully!");

        editPrompt.setText("");
    }

    private void updateTaskFromPrompt(@NonNull AiBackendApi.ParsedResult r, long start, long end) {
        if (getContext() == null) return;

        TaskDao dao = AppDatabase.getInstance(getContext()).taskDao();
        List<Task> matches = dao.searchByTitleNewestFirst(r.title);
        if (matches == null || matches.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.prompt_update_not_found, r.title), Toast.LENGTH_LONG).show();
            return;
        }

        Task task = matches.get(0);
        if (task.isOngoing) {
            Toast.makeText(getContext(), getString(R.string.prompt_update_ongoing_not_allowed), Toast.LENGTH_LONG).show();
            return;
        }

        // Cancel old alarms before changing the schedule
        ReminderUtils.cancelRemindersForTask(getContext(), task);

        task.title = r.title; // keep same
        if (!TextUtils.isEmpty(r.details)) {
            task.description = r.details;
        }

        task.isAllDay = false;
        task.isOngoing = false;
        task.startTimestamp = start;
        task.stopTimestamp = end;
        task.durationMillis = end - start;
        task.status = Task.STATUS_NOT_STARTED;

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        task.fromDate = dateFmt.format(new Date(start));
        task.toDate = dateFmt.format(new Date(end));
        task.date = task.fromDate;

        dao.update(task);
        ReminderUtils.scheduleReminderForTask(requireContext(), task);

        String durationStr = DateUtils.formatDuration(task.durationMillis);
        DialogUtils.showSuccessDialog(getContext(),
                "Updated: " + task.title +
                        "\nNew duration: " + durationStr +
                        "\n\nTask updated successfully!");

        editPrompt.setText("");
    }

    private void applyEnabledState() {
        boolean enabled = promptEnabled;
        if (editPrompt != null) editPrompt.setEnabled(enabled);
        if (btnMic != null) btnMic.setEnabled(enabled);
        if (btnSend != null) btnSend.setEnabled(enabled);

        if (tvDisabled != null) {
            if (enabled) {
                tvDisabled.setVisibility(View.GONE);
            } else {
                tvDisabled.setVisibility(View.VISIBLE);
                tvDisabled.setText(getDisabledText());
            }
        }
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        // While busy, prevent double-tap.
        if (btnSend != null) btnSend.setEnabled(!busy && promptEnabled);
        if (btnMic != null) btnMic.setEnabled(!busy && promptEnabled);
        if (editPrompt != null) editPrompt.setEnabled(!busy && promptEnabled);
    }

    private String getDisabledText() {
        if (!TextUtils.isEmpty(disabledMessage)) return disabledMessage;
        return getString(R.string.ai_disabled_message);
    }

    private static String safeLower(@Nullable String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }
}
