package com.example.taskmanager.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.notifications.ReminderUtils;
import com.example.taskmanager.utils.DateUtils;
import com.example.taskmanager.utils.DialogUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manual scheduler form (existing functionality), now hosted under Scheduler -> Manual tab.
 * <p>
 * NOTE: This is essentially the previous EnterDurationFragment implementation.
 */
public class SchedulerManualFragment extends Fragment {

    private LinearLayout taskContainer;
    private FloatingActionButton fabAddTask;
    private static final int REQ_PICK_IMAGE = 2001;
    private static final int REQ_PICK_FILE = 2002;
    private static final int REQ_TAKE_PHOTO = 2003;
    private static final int REQ_CAMERA_PERMISSION = 2004;

    // Types for display naming
    private static final int ATTACH_TYPE_IMAGE = 1;
    private static final int ATTACH_TYPE_FILE = 2;

    private View currentAttachmentTaskView = null;
    private int currentAttachmentTaskIndex = -1;
    private Uri pendingCameraUri = null;
    private boolean isPickingAttachment = false;

    public SchedulerManualFragment() {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        isPickingAttachment = false;

        if (resultCode != Activity.RESULT_OK) {
            isPickingAttachment = false;
            return;
        }

        // If the view reference was lost (e.g., UI refreshed), rebind it using stored index.
        if (currentAttachmentTaskView == null || currentAttachmentTaskView.getParent() == null) {
            if (taskContainer != null && currentAttachmentTaskIndex >= 0
                    && currentAttachmentTaskIndex < taskContainer.getChildCount()) {
                currentAttachmentTaskView = taskContainer.getChildAt(currentAttachmentTaskIndex);
            }
        }

        if (currentAttachmentTaskView == null) {
            return;
        }

        Uri uri = null;
        int attachType = ATTACH_TYPE_FILE; // default

        if (requestCode == REQ_PICK_IMAGE) {
            if (data != null) {
                uri = data.getData();
                attachType = ATTACH_TYPE_IMAGE;
            }
        } else if (requestCode == REQ_PICK_FILE) {
            if (data != null) {
                uri = data.getData();
                attachType = ATTACH_TYPE_FILE;
            }
        } else if (requestCode == REQ_TAKE_PHOTO) {
            uri = pendingCameraUri;
            attachType = ATTACH_TYPE_IMAGE;
        }

        if (uri != null) {
            // Persist permission for SAF URIs (images/files) so we can access later.
            if (data != null && (requestCode == REQ_PICK_IMAGE || requestCode == REQ_PICK_FILE)) {
                try {
                    final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    requireContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (Exception ignored) {
                }
            }

            addAttachmentToTaskView(currentAttachmentTaskView, uri, attachType);
            Toast.makeText(getContext(), getString(R.string.attachment_added), Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("unchecked")
    private void addAttachmentToTaskView(View taskView, Uri uri, int attachType) {
        ArrayList<Uri> uriList = (ArrayList<Uri>) taskView.getTag(R.id.tag_attachment_list);
        if (uriList == null) {
            uriList = new ArrayList<>();
        }
        uriList.add(uri);
        taskView.setTag(R.id.tag_attachment_list, uriList);

        ArrayList<String> nameList = (ArrayList<String>) taskView.getTag(R.id.tag_attachment_names);
        if (nameList == null) {
            nameList = new ArrayList<>();
        }
        String displayName = generateAttachmentDisplayName(uri, attachType);
        nameList.add(displayName);
        taskView.setTag(R.id.tag_attachment_names, nameList);

        renderAttachments(taskView);
    }

    @SuppressWarnings("unchecked")
    private String joinAttachmentNamesFromView(View taskView) {
        ArrayList<String> names = (ArrayList<String>) taskView.getTag(R.id.tag_attachment_names);
        if (names == null || names.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private String generateAttachmentDisplayName(Uri uri, int attachType) {
        // Timestamp: YYMMDD_HHMMSS
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
        String ts = sdf.format(new Date());

        if (attachType == ATTACH_TYPE_IMAGE) {
            // Photo/image → always .jpg as requested
            return "img_" + ts + ".jpg";
        } else {
            // File/document → doc_YYMMDD_HHMMSS.extension (extension from original name if possible)
            String ext = "";

            // Try to get original display name
            String originalName = null;
            try {
                if (uri != null && "content".equals(uri.getScheme())) {
                    Cursor c = requireContext().getContentResolver()
                            .query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                                    null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            originalName = c.getString(0);
                        }
                        c.close();
                    }
                }
            } catch (Exception ignored) {
            }

            if (originalName == null && uri != null) {
                String last = uri.getLastPathSegment();
                if (last != null) originalName = last;
            }

            if (originalName != null) {
                int dot = originalName.lastIndexOf('.');
                if (dot >= 0 && dot < originalName.length() - 1) {
                    ext = originalName.substring(dot); // includes the dot
                }
            }

            return "doc_" + ts + ext;
        }
    }

    @SuppressWarnings("unchecked")
    private void renderAttachments(View taskView) {
        Context ctx = getContext();
        if (ctx == null) return;

        LinearLayout container = taskView.findViewById(R.id.layout_attachment_list);
        if (container == null) return;

        container.removeAllViews();

        ArrayList<Uri> uriList = (ArrayList<Uri>) taskView.getTag(R.id.tag_attachment_list);
        ArrayList<String> nameList = (ArrayList<String>) taskView.getTag(R.id.tag_attachment_names);

        if (uriList == null || uriList.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);

        float density = ctx.getResources().getDisplayMetrics().density;
        int marginTopPx = (int) (4 * density);
        int paddingPx = (int) (4 * density);

        for (int i = 0; i < uriList.size(); i++) {
            Uri uri = uriList.get(i);
            String displayName = (nameList != null && i < nameList.size())
                    ? nameList.get(i)
                    : "Attachment";

            // Row: [filename          (x)]
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                rowParams.topMargin = marginTopPx;
            }
            row.setLayoutParams(rowParams);

            TextView tv = new TextView(ctx);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            tv.setLayoutParams(tvParams);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setText(displayName);
            row.addView(tv);

            ImageView imgDelete = new ImageView(ctx);
            LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            imgDelete.setLayoutParams(delParams);
            imgDelete.setImageResource(R.drawable.ic_delete);
            imgDelete.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

            int index = i;
            imgDelete.setOnClickListener(v -> {
                ArrayList<Uri> currentUris =
                        (ArrayList<Uri>) taskView.getTag(R.id.tag_attachment_list);
                ArrayList<String> currentNames =
                        (ArrayList<String>) taskView.getTag(R.id.tag_attachment_names);

                if (currentUris != null && index < currentUris.size()) {
                    currentUris.remove(index);
                }
                if (currentNames != null && index < currentNames.size()) {
                    currentNames.remove(index);
                }

                taskView.setTag(R.id.tag_attachment_list, currentUris);
                taskView.setTag(R.id.tag_attachment_names, currentNames);
                renderAttachments(taskView);
            });

            row.addView(imgDelete);
            container.addView(row);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Reuse the existing layout (manual scheduler)
        return inflater.inflate(R.layout.fragment_enter_duration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        taskContainer = view.findViewById(R.id.task_container);
        fabAddTask = view.findViewById(R.id.fab_add_task);

        List<Task> ongoingTasks = AppDatabase.getInstance(getContext())
                .taskDao().getOngoingTasks();

        if (ongoingTasks.isEmpty()) {
            // No ongoing tasks → Add initial empty section
            addTaskSection(null);
        } else {
            // Restore each ongoing task
            for (Task task : ongoingTasks) {
                View taskView = buildTaskViewFromOngoing(task);
                taskView.setTag(R.id.tag_task_id, task.id);
                taskContainer.addView(taskView);
            }
        }

        fabAddTask.setOnClickListener(v -> addTaskSection(null));
    }

    private View buildTaskViewFromOngoing(Task task) {
        View taskView = LayoutInflater.from(getContext()).inflate(R.layout.item_task_input, taskContainer, false);

        EditText editTitle = taskView.findViewById(R.id.edit_title);
        TextView textStart = taskView.findViewById(R.id.text_start_time);
        EditText editDescription = taskView.findViewById(R.id.edit_description);
        Button btnStop = taskView.findViewById(R.id.btn_stop);
        Button btnRemove = taskView.findViewById(R.id.btn_remove);
        Button btnSubmit = taskView.findViewById(R.id.btn_submit);
        RadioGroup radioGroup = taskView.findViewById(R.id.radio_group);
        Button btnAttach = taskView.findViewById(R.id.btn_attach);

        // status row + spinner
        LinearLayout layoutStatusRow = taskView.findViewById(R.id.layout_status_row);
        Spinner spinnerStatus = taskView.findViewById(R.id.spinner_status);

        // For ongoing CLOCK-IN, Status must not be shown
        if (layoutStatusRow != null) {
            layoutStatusRow.setVisibility(View.GONE);
        }
        if (spinnerStatus != null) {
            spinnerStatus.setEnabled(false);
        }

        // Fill previous data
        editTitle.setText(task.title);
        editDescription.setText(task.description);
        textStart.setText(getString(R.string.started_on, task.dateTime));

        // Disable inputs
        editTitle.setEnabled(false);
        editDescription.setEnabled(false);
        editTitle.setBackground(null);
        editTitle.setTextColor(Color.BLACK);
        editDescription.setBackground(null);
        editDescription.setTextColor(Color.BLACK);

        radioGroup.setVisibility(View.GONE);
        textStart.setVisibility(View.VISIBLE);

        btnSubmit.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
        btnRemove.setVisibility(View.GONE);

        btnAttach.setOnClickListener(v -> {
            currentAttachmentTaskView = taskView;
            currentAttachmentTaskIndex = taskContainer != null ? taskContainer.indexOfChild(taskView) : -1;

            String[] options = {"Take photo", "Choose photo", "Choose file"};
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.add_attachment))
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                launchCamera();
                                break;
                            case 1:
                                launchImagePicker();
                                break;
                            case 2:
                                launchFilePicker();
                                break;
                        }
                    })
                    .show();
        });

        btnStop.setOnClickListener(v -> {
            long stopTime = System.currentTimeMillis();
            task.stopTimestamp = stopTime;
            task.durationMillis = stopTime - task.startTimestamp;
            task.isOngoing = false;

            // mark completed
            task.status = Task.STATUS_COMPLETED;

            AppDatabase.getInstance(getContext()).taskDao().update(task);
            ReminderUtils.notifyClockinStopped(getContext(), task);

            String durationStr = DateUtils.formatDuration(task.durationMillis);

            DialogUtils.showSuccessDialog(getContext(),
                    "Title: " + task.title +
                            "\nDescription: " + task.description +
                            "\nDuration: " + durationStr +
                            "\n\nAbove task is added successfully!");

            taskContainer.removeView(taskView);
        });

        return taskView;
    }

    private void launchImagePicker() {
        isPickingAttachment = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    private void launchFilePicker() {
        isPickingAttachment = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    private void launchCamera() {
        Context ctx = getContext();
        if (ctx == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.CAMERA},
                        REQ_CAMERA_PERMISSION
                );
                return;
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
        String ts = sdf.format(new Date());
        File photoFile = new File(ctx.getCacheDir(),
                "img_" + ts + ".jpg");

        pendingCameraUri = androidx.core.content.FileProvider.getUriForFile(
                ctx,
                ctx.getPackageName() + ".fileprovider",
                photoFile
        );

        isPickingAttachment = true;

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQ_TAKE_PHOTO);
    }

    private void addTaskSection(@Nullable String prefillTitle) {
        View taskView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_task_input, taskContainer, false);

        EditText editTitle = taskView.findViewById(R.id.edit_title);
        EditText editDescription = taskView.findViewById(R.id.edit_description);
        RadioGroup radioGroup = taskView.findViewById(R.id.radio_group);
        RadioButton radioAllDay = taskView.findViewById(R.id.radio_all_day);
        RadioButton radioDuration = taskView.findViewById(R.id.radio_duration);
        EditText editDate = taskView.findViewById(R.id.edit_date);
        EditText editFrom = taskView.findViewById(R.id.edit_from);
        EditText editTo = taskView.findViewById(R.id.edit_to);
        EditText editFromTime = taskView.findViewById(R.id.edit_from_time);
        EditText editToTime = taskView.findViewById(R.id.edit_to_time);
        LinearLayout layoutDuration = taskView.findViewById(R.id.layout_duration);
        Button btnSubmit = taskView.findViewById(R.id.btn_submit);
        Button btnRemove = taskView.findViewById(R.id.btn_remove);
        Button btnStop = taskView.findViewById(R.id.btn_stop);
        TextView textStart = taskView.findViewById(R.id.text_start_time);
        Button btnAttach = taskView.findViewById(R.id.btn_attach);

        Spinner spinnerStatus = taskView.findViewById(R.id.spinner_status);
        LinearLayout layoutStatusRow = taskView.findViewById(R.id.layout_status_row);

        layoutStatusRow.setVisibility(View.GONE);

        if (prefillTitle != null) {
            editTitle.setText(prefillTitle);
        }

        editTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSubmit.setEnabled(!s.toString().trim().isEmpty());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == -1) {
                editDate.setVisibility(View.GONE);
                layoutDuration.setVisibility(View.GONE);
                btnSubmit.setText(getString(R.string.start));
                layoutStatusRow.setVisibility(View.GONE);
            } else if (checkedId == R.id.radio_clockin) {
                editDate.setVisibility(View.GONE);
                layoutDuration.setVisibility(View.GONE);
                btnSubmit.setText(getString(R.string.start));
                layoutStatusRow.setVisibility(View.GONE);
            } else if (checkedId == R.id.radio_all_day) {
                editDate.setVisibility(View.VISIBLE);
                layoutDuration.setVisibility(View.GONE);
                btnSubmit.setText(getString(R.string.submit));
                layoutStatusRow.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_duration) {
                editDate.setVisibility(View.GONE);
                layoutDuration.setVisibility(View.VISIBLE);
                btnSubmit.setText(getString(R.string.submit));
                layoutStatusRow.setVisibility(View.VISIBLE);
            }
        });

        btnAttach.setOnClickListener(v -> {
            currentAttachmentTaskView = taskView;
            currentAttachmentTaskIndex = taskContainer != null ? taskContainer.indexOfChild(taskView) : -1;

            String[] options = {"Take photo", "Choose photo", "Choose file"};
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.add_attachment))
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                launchCamera();
                                break;
                            case 1:
                                launchImagePicker();
                                break;
                            case 2:
                                launchFilePicker();
                                break;
                        }
                    })
                    .show();
        });

        editDate.setOnClickListener(v -> DialogUtils.showDatePicker(getContext(), editDate));
        editFrom.setOnClickListener(v -> DialogUtils.showDatePicker(getContext(), editFrom));
        editTo.setOnClickListener(v -> DialogUtils.showDatePicker(getContext(), editTo));
        editFromTime.setOnClickListener(v -> DialogUtils.showTimePicker(getContext(), editFromTime));
        editToTime.setOnClickListener(v -> DialogUtils.showTimePicker(getContext(), editToTime));

        btnRemove.setOnClickListener(v -> taskContainer.removeView(taskView));

        btnSubmit.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String desc = editDescription.getText().toString().trim();

            boolean isAllDay = radioAllDay.isChecked();
            boolean isDuration = radioDuration.isChecked();
            String dateStr = editDate.getText().toString().trim();
            String fromStr = editFrom.getText().toString().trim();
            String toStr = editTo.getText().toString().trim();
            String fromTimeStr = editFromTime.getText().toString().trim();
            String toTimeStr = editToTime.getText().toString().trim();

            long currentTimeMillis = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
            String formattedDateTime = sdf.format(new Date(currentTimeMillis));

            if (title.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.title_required), Toast.LENGTH_SHORT).show();
                return;
            }

            // CASE 1: Manual Timer (no radio selected)
            if (!isAllDay && !isDuration) {
                radioGroup.setVisibility(View.GONE);
                long startTime = System.currentTimeMillis();

                Task task = new Task();
                task.title = title;
                task.description = desc;
                task.startTimestamp = startTime;
                task.isOngoing = true;
                task.dateTime = formattedDateTime;
                task.createdAt = System.currentTimeMillis();

                SimpleDateFormat sdf4 = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
                String formattedDate = sdf4.format(new Date(currentTimeMillis));
                task.date = formattedDate;

                task.status = Task.STATUS_IN_PROGRESS;

                ArrayList<Uri> attachments =
                        (ArrayList<Uri>) taskView.getTag(R.id.tag_attachment_list);
                if (attachments != null && !attachments.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < attachments.size(); i++) {
                        if (i > 0) sb.append(";");
                        sb.append(attachments.get(i).toString());
                    }
                    task.attachmentUris = sb.toString();
                    task.attachmentNames = joinAttachmentNamesFromView(taskView);
                } else {
                    task.attachmentUris = null;
                }

                long taskId = AppDatabase.getInstance(getContext()).taskDao().insertAndReturnId(task);
                task.id = (int) taskId;

                ReminderUtils.notifyClockinStarted(requireContext(), task);

                editTitle.setEnabled(false);
                editDescription.setEnabled(false);
                editTitle.setBackground(null);
                editTitle.setTextColor(Color.BLACK);
                editDescription.setBackground(null);
                editDescription.setTextColor(Color.BLACK);
                radioAllDay.setEnabled(false);
                radioDuration.setEnabled(false);
                btnSubmit.setVisibility(View.GONE);
                btnRemove.setVisibility(View.GONE);
                btnStop.setVisibility(View.VISIBLE);

                textStart.setVisibility(View.VISIBLE);
                textStart.setText(getString(R.string.started_on, formattedDateTime));
                taskView.setTag(R.id.tag_task_id, task.id);
                return;
            }

            // CASE 2: All Day or Duration selected
            if (isAllDay && dateStr.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.select_date_all_day), Toast.LENGTH_SHORT).show();
                return;
            }

            if (isDuration) {
                if (fromStr.isEmpty() || toStr.isEmpty()) {
                    Toast.makeText(getContext(), getString(R.string.from_before_to), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (fromTimeStr.isEmpty()) {
                    fromTimeStr = "00:00";
                }
                if (toTimeStr.isEmpty()) {
                    toTimeStr = "23:59";
                }
            }

            long eventStartMillis;
            long eventEndMillis;
            long duration;

            if (isAllDay) {
                try {
                    SimpleDateFormat dayFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    Date allDayDate = dayFormat.parse(dateStr);
                    if (allDayDate == null) throw new ParseException("Invalid date", 0);
                    eventStartMillis = allDayDate.getTime();
                } catch (ParseException e) {
                    Toast.makeText(getContext(), getString(R.string.invalid_date_format), Toast.LENGTH_SHORT).show();
                    return;
                }

                duration = 24 * 60 * 60 * 1000L;
                eventEndMillis = eventStartMillis + duration;
            } else {
                try {
                    SimpleDateFormat dateTimeFormat =
                            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

                    Date fromDateTime = dateTimeFormat.parse(fromStr + " " + fromTimeStr);
                    Date toDateTime = dateTimeFormat.parse(toStr + " " + toTimeStr);

                    if (fromDateTime == null || toDateTime == null) {
                        throw new ParseException("Invalid date/time", 0);
                    }

                    if (fromDateTime.after(toDateTime)) {
                        Toast.makeText(getContext(), getString(R.string.from_before_to), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    eventStartMillis = fromDateTime.getTime();
                    eventEndMillis = toDateTime.getTime();
                    duration = eventEndMillis - eventStartMillis;
                } catch (ParseException e) {
                    Toast.makeText(getContext(), getString(R.string.invalid_date_time_format), Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Task task = new Task();
            task.title = title;
            task.description = desc;
            task.isAllDay = isAllDay;
            task.fromDate = isDuration ? fromStr : null;
            task.toDate = isDuration ? toStr : null;
            task.startTimestamp = eventStartMillis;
            task.stopTimestamp = eventEndMillis;
            task.durationMillis = duration;
            task.dateTime = formattedDateTime;
            task.createdAt = System.currentTimeMillis();

            int statusPos = spinnerStatus.getSelectedItemPosition();
            task.status = com.example.taskmanager.utils.StatusUi.indexToCode(statusPos);

            SimpleDateFormat sdf3 = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
            String currentDateStr = sdf3.format(new Date(task.startTimestamp));
            task.date = isAllDay ? dateStr : (isDuration ? fromStr : currentDateStr);

            ArrayList<Uri> attachments =
                    (ArrayList<Uri>) taskView.getTag(R.id.tag_attachment_list);
            if (attachments != null && !attachments.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < attachments.size(); i++) {
                    if (i > 0) sb.append(";");
                    sb.append(attachments.get(i).toString());
                }
                task.attachmentUris = sb.toString();
                task.attachmentNames = joinAttachmentNamesFromView(taskView);
            } else {
                task.attachmentUris = null;
            }

            long taskId = AppDatabase.getInstance(getContext()).taskDao().insertAndReturnId(task);
            task.id = (int) taskId;

            ReminderUtils.scheduleReminderForTask(requireContext(), task);

            String durationStr = DateUtils.formatDuration(duration);
            DialogUtils.showSuccessDialog(getContext(),
                    "Title: " + title +
                            "\nDescription: " + desc +
                            "\nDuration: " + durationStr +
                            "\n\nAbove task is added successfully!");

            taskContainer.removeView(taskView);
        });

        btnStop.setOnClickListener(v -> {
            int taskId = (int) taskView.getTag(R.id.tag_task_id);
            Task task = AppDatabase.getInstance(getContext()).taskDao().getTaskById(taskId);

            if (task != null) {
                long stopTime = System.currentTimeMillis();
                task.stopTimestamp = stopTime;
                task.durationMillis = stopTime - task.startTimestamp;
                task.isOngoing = false;
                task.status = Task.STATUS_COMPLETED;

                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
                String currentDateStr = sdf.format(new Date(task.startTimestamp));
                task.date = currentDateStr;

                AppDatabase.getInstance(getContext()).taskDao().update(task);
                ReminderUtils.notifyClockinStopped(getContext(), task);

                String durationStr = DateUtils.formatDuration(task.durationMillis);
                DialogUtils.showSuccessDialog(getContext(),
                        "Title: " + task.title +
                                "\nDescription: " + task.description +
                                "\nDuration: " + durationStr +
                                "\n\nAbove task is added successfully!");

                taskContainer.removeView(taskView);
            }
        });

        taskContainer.addView(taskView);
    }


    private boolean hasDraftFormOpen() {
        if (taskContainer == null) return false;

        for (int i = 0; i < taskContainer.getChildCount(); i++) {
            View child = taskContainer.getChildAt(i);

            EditText title = child.findViewById(R.id.edit_title);
            Button submit = child.findViewById(R.id.btn_submit);
            RadioGroup rg = child.findViewById(R.id.radio_group);

            // Draft form: user-editable section (title enabled + submit visible + radio group visible)
            if (title != null && title.isEnabled()
                    && submit != null && submit.getVisibility() == View.VISIBLE
                    && rg != null && rg.getVisibility() == View.VISIBLE) {
                return true;
            }
        }
        return false;
    }

    private void refreshOngoingTasksUi() {
        if (getContext() == null || taskContainer == null) return;

        List<Task> ongoingTasks = AppDatabase.getInstance(getContext())
                .taskDao().getOngoingTasks();

        taskContainer.removeAllViews();

        if (ongoingTasks == null || ongoingTasks.isEmpty()) {
            addTaskSection(null);
            return;
        }

        for (Task task : ongoingTasks) {
            View taskView = buildTaskViewFromOngoing(task);
            taskView.setTag(R.id.tag_task_id, task.id);
            taskContainer.addView(taskView);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPickingAttachment || hasDraftFormOpen()) return;  // do not wipe the draft form
        refreshOngoingTasksUi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(getContext(), getString(R.string.camera_permission), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
