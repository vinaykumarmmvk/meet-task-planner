package com.example.taskmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
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

public class ViewTaskActivity extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 3001;
    private static final int REQ_PICK_FILE = 3002;
    private static final int REQ_TAKE_PHOTO = 3003;
    private static final int REQ_CAMERA_PERMISSION = 3004;

    private EditText editTitle, editDescription;
    private TextView textType;
    private LinearLayout layoutAllDay, layoutDuration, layoutClockin;
    private EditText editAllDayDate;
    private EditText editFromDate, editFromTime, editToDate, editToTime;
    private TextView textClockinDate, textClockinDuration;
    private Spinner spinnerStatus;

    private ImageView imgAddAttachment;
    private LinearLayout layoutAttachmentList;
    private ImageButton btnBack;
    private FloatingActionButton fabEdit, fabSave, fabDelete;

    private Task task;
    private boolean isEditMode = false;

    private final List<Uri> attachmentUris = new ArrayList<>();
    private final List<String> attachmentNames = new ArrayList<>();

    private Uri pendingCameraUri = null;

    private int statusToPosition(String status) {
        if (Task.STATUS_IN_PROGRESS.equals(status)) return 1;
        if (Task.STATUS_COMPLETED.equals(status)) return 2;
        return 0; // Not started / default
    }

    private String positionToStatus(int pos) {
        switch (pos) {
            case 1:
                return Task.STATUS_IN_PROGRESS;
            case 2:
                return Task.STATUS_COMPLETED;
            default:
                return Task.STATUS_NOT_STARTED;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_task);

        int taskId = getIntent().getIntExtra("task_id", -1);
        if (taskId == -1) {
            finish();
            return;
        }

        task = AppDatabase.getInstance(this).taskDao().getTaskById(taskId);
        if (task == null) {
            finish();
            return;
        }

        bindViews();
        loadTaskIntoUi();
        setModeView();

        setupPickerTouchHandlers();

        btnBack.setOnClickListener(v -> {
            if (isEditMode) {
                // Back from edit -> discard changes and show view mode
                loadTaskIntoUi();
                setModeView();
            } else {
                // Back from view -> close activity (back to tabs)
                finish();
            }
        });

        fabEdit.setOnClickListener(v -> setModeEdit());

        fabSave.setOnClickListener(v -> {
            if (saveChanges()) {
                task = AppDatabase.getInstance(this).taskDao().getTaskById(task.id);
                loadTaskIntoUi();
                setModeView();
                Toast.makeText(this, "Task updated", Toast.LENGTH_SHORT).show();
            }
        });

        fabDelete.setOnClickListener(v -> confirmDelete());

        imgAddAttachment.setOnClickListener(v -> {
            if (!isEditMode) return;

            String[] options = {"Take photo", "Choose photo", "Choose file"};
            new AlertDialog.Builder(this)
                    .setTitle("Add attachment")
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPickerTouchHandlers() {
        // Handles date fields
        View.OnTouchListener dateTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && isEditMode) {
                if (v == editAllDayDate && task.isAllDay) {
                    DialogUtils.showDatePicker(this, editAllDayDate);
                    return true;
                }

                if (!task.isAllDay && task.fromDate != null && task.toDate != null) {
                    if (v == editFromDate) {
                        DialogUtils.showDatePicker(this, editFromDate);
                        return true;
                    } else if (v == editToDate) {
                        DialogUtils.showDatePicker(this, editToDate);
                        return true;
                    }
                }
            }
            return false;
        };

        // Handles time fields
        View.OnTouchListener timeTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    && isEditMode
                    && !task.isAllDay
                    && task.fromDate != null
                    && task.toDate != null) {

                if (v == editFromTime) {
                    DialogUtils.showTimePicker(this, editFromTime);
                    return true;
                } else if (v == editToTime) {
                    DialogUtils.showTimePicker(this, editToTime);
                    return true;
                }
            }
            return false;
        };

        // Attach listeners
        editAllDayDate.setOnTouchListener(dateTouchListener);
        editFromDate.setOnTouchListener(dateTouchListener);
        editToDate.setOnTouchListener(dateTouchListener);

        editFromTime.setOnTouchListener(timeTouchListener);
        editToTime.setOnTouchListener(timeTouchListener);
    }

    private void bindViews() {
        editTitle = findViewById(R.id.edit_title);
        editDescription = findViewById(R.id.edit_description);
        textType = findViewById(R.id.text_type);
        layoutAllDay = findViewById(R.id.layout_all_day);
        layoutDuration = findViewById(R.id.layout_duration);
        layoutClockin = findViewById(R.id.layout_clockin);
        editAllDayDate = findViewById(R.id.edit_all_day_date);
        editFromDate = findViewById(R.id.edit_from_date);
        editFromTime = findViewById(R.id.edit_from_time);
        editToDate = findViewById(R.id.edit_to_date);
        editToTime = findViewById(R.id.edit_to_time);
        textClockinDate = findViewById(R.id.text_clockin_date);
        textClockinDuration = findViewById(R.id.text_clockin_duration);
        spinnerStatus = findViewById(R.id.spinner_status);
        imgAddAttachment = findViewById(R.id.img_add_attachment);
        layoutAttachmentList = findViewById(R.id.layout_attachment_list);
        btnBack = findViewById(R.id.btn_back);
        fabEdit = findViewById(R.id.fab_edit);
        fabSave = findViewById(R.id.fab_save);
        fabDelete = findViewById(R.id.fab_delete);
    }

    private void loadTaskIntoUi() {
        editTitle.setText(task.title);
        editDescription.setText(task.description == null ? "" : task.description);

        String type;
        if (task.isAllDay) type = "All-day";
        else if (task.fromDate != null && task.toDate != null) type = "Duration";
        else type = "Clock-in";
        textType.setText(type);

        // ---------- Status ----------
        String status = task.status;
        boolean isClockInType = !task.isAllDay && task.fromDate == null && task.toDate == null;

// For clock-in: derive status from isOngoing if missing
        if (isClockInType) {
            if (task.isOngoing) {
                status = Task.STATUS_IN_PROGRESS;
            } else {
                status = Task.STATUS_COMPLETED;
            }
        }

        if (status == null || status.trim().isEmpty()) {
            status = Task.STATUS_NOT_STARTED;
        }
        spinnerStatus.setSelection(statusToPosition(status));

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        layoutAllDay.setVisibility(View.GONE);
        layoutDuration.setVisibility(View.GONE);
        layoutClockin.setVisibility(View.GONE);

        if (task.isAllDay) {
            layoutAllDay.setVisibility(View.VISIBLE);
            editAllDayDate.setText(task.date != null ? task.date : "");
        } else if (task.fromDate != null && task.toDate != null) {
            layoutDuration.setVisibility(View.VISIBLE);

            editFromDate.setText(task.fromDate != null ? task.fromDate : "");
            editToDate.setText(task.toDate != null ? task.toDate : "");

            if (task.startTimestamp > 0) {
                Date start = new Date(task.startTimestamp);
                editFromTime.setText(timeFormat.format(start));
                if (editFromDate.getText().toString().trim().isEmpty()) {
                    editFromDate.setText(dateFormat.format(start));
                }
            }
            if (task.stopTimestamp > 0) {
                Date stop = new Date(task.stopTimestamp);
                editToTime.setText(timeFormat.format(stop));
                if (editToDate.getText().toString().trim().isEmpty()) {
                    editToDate.setText(dateFormat.format(stop));
                }
            }
        } else {
            layoutClockin.setVisibility(View.VISIBLE);
            if (task.date != null) {
                textClockinDate.setText(task.date);
            } else if (task.startTimestamp > 0) {
                textClockinDate.setText(dateFormat.format(new Date(task.startTimestamp)));
            } else {
                textClockinDate.setText("-");
            }
            String durStr = DateUtils.formatDuration(task.durationMillis);
            textClockinDuration.setText(durStr);
        }

        // Attachments: build list from URIs and generate display names
        attachmentUris.clear();
        attachmentNames.clear();
        if (task.attachmentUris != null && !task.attachmentUris.trim().isEmpty()) {
            String[] parts = task.attachmentUris.split(";");
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    Uri uri = Uri.parse(p.trim());
                    attachmentUris.add(uri);
                    attachmentNames.add(generateAttachmentDisplayName(uri));
                }
            }
        }

        renderAttachments();
    }

    private void setModeView() {
        isEditMode = false;
        fabEdit.setVisibility(View.VISIBLE);
        fabSave.setVisibility(View.GONE);

        // Everything read-only
        setEditable(editTitle, false);
        setEditable(editDescription, false);
        setEditable(editAllDayDate, false);
        setEditable(editFromDate, false);
        setEditable(editFromTime, false);
        setEditable(editToDate, false);
        setEditable(editToTime, false);

        imgAddAttachment.setEnabled(false);
        imgAddAttachment.setAlpha(0.3f);
        spinnerStatus.setEnabled(false);
        spinnerStatus.setAlpha(0.6f);

        renderAttachments(); // delete icons disabled in view mode
    }

    private void setModeEdit() {
        isEditMode = true;
        fabEdit.setVisibility(View.GONE);
        fabSave.setVisibility(View.VISIBLE);

        boolean isClockInType = !task.isAllDay && task.fromDate == null && task.toDate == null;

        if (isClockInType) {
            // Clock-in: never editable
            spinnerStatus.setEnabled(false);
            spinnerStatus.setAlpha(0.6f);
        } else {
            spinnerStatus.setEnabled(true);
            spinnerStatus.setAlpha(1f);
        }

        // Title & description editable
        setEditable(editTitle, true);
        setEditable(editDescription, true);

        // Date/time fields: editable only for relevant type
        if (task.isAllDay) {
            setEditable(editAllDayDate, true);

            setEditable(editFromDate, false);
            setEditable(editFromTime, false);
            setEditable(editToDate, false);
            setEditable(editToTime, false);

        } else if (task.fromDate != null && task.toDate != null) {
            // Duration type
            setEditable(editAllDayDate, false);

            setEditable(editFromDate, true);
            setEditable(editFromTime, true);
            setEditable(editToDate, true);
            setEditable(editToTime, true);
        } else {
            // Clock-in: no date/time editing
            setEditable(editAllDayDate, false);
            setEditable(editFromDate, false);
            setEditable(editFromTime, false);
            setEditable(editToDate, false);
            setEditable(editToTime, false);
        }

        imgAddAttachment.setEnabled(true);
        imgAddAttachment.setAlpha(1f);

        renderAttachments(); // delete icons become active
    }

    private void setEditable(EditText et, boolean editable) {
        et.setEnabled(editable);
        et.setFocusable(editable);
        et.setFocusableInTouchMode(editable);
        et.setAlpha(editable ? 1f : 0.6f);
    }

    private boolean saveChanges() {
        String newTitle = editTitle.getText().toString().trim();
        String newDesc = editDescription.getText().toString().trim();

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        task.title = newTitle;
        task.description = newDesc;

        boolean isClockInType = !task.isAllDay && task.fromDate == null && task.toDate == null;
        if (!isClockInType) {
            int pos = spinnerStatus.getSelectedItemPosition();
            task.status = positionToStatus(pos);
        }
        // For clock-in, status is controlled by start/stop logic only

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

        if (task.isAllDay) {
            String dateStr = editAllDayDate.getText().toString().trim();
            if (dateStr.isEmpty()) {
                Toast.makeText(this, "Date is required", Toast.LENGTH_SHORT).show();
                return false;
            }
            try {
                Date d = dateFormat.parse(dateStr);
                if (d == null) throw new ParseException("null", 0);
                task.date = dateStr;
                task.startTimestamp = d.getTime();
                task.durationMillis = 24L * 60 * 60 * 1000L;
                task.stopTimestamp = task.startTimestamp + task.durationMillis;
            } catch (ParseException e) {
                Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (task.fromDate != null && task.toDate != null) {
            String fromDateStr = editFromDate.getText().toString().trim();
            String toDateStr = editToDate.getText().toString().trim();
            String fromTimeStr = editFromTime.getText().toString().trim();
            String toTimeStr = editToTime.getText().toString().trim();

            if (fromDateStr.isEmpty() || toDateStr.isEmpty()) {
                Toast.makeText(this, "FROM and TO dates are required", Toast.LENGTH_SHORT).show();
                return false;
            }

            if (fromTimeStr.isEmpty()) fromTimeStr = "00:00";
            if (toTimeStr.isEmpty()) toTimeStr = "23:59";

            try {
                Date from = dateTimeFormat.parse(fromDateStr + " " + fromTimeStr);
                Date to = dateTimeFormat.parse(toDateStr + " " + toTimeStr);
                if (from == null || to == null) throw new ParseException("null", 0);

                if (from.after(to)) {
                    Toast.makeText(this, "FROM must be before TO", Toast.LENGTH_SHORT).show();
                    return false;
                }

                task.fromDate = fromDateStr;
                task.toDate = toDateStr;
                task.startTimestamp = from.getTime();
                task.stopTimestamp = to.getTime();
                task.durationMillis = task.stopTimestamp - task.startTimestamp;

            } catch (ParseException e) {
                Toast.makeText(this, "Invalid date/time", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else {
            // Clock-in: only title/description
        }

        // Attachments => join URIs
        if (attachmentUris.isEmpty()) {
            task.attachmentUris = null;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < attachmentUris.size(); i++) {
                if (i > 0) sb.append(";");
                sb.append(attachmentUris.get(i).toString());
            }
            task.attachmentUris = sb.toString();
        }

        SimpleDateFormat sdfFull = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        task.dateTime = sdfFull.format(new Date());

        AppDatabase.getInstance(this).taskDao().update(task);
        return true;
    }

    // ---------------- Delete ----------------

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AppDatabase.getInstance(this).taskDao().delete(task);
                    Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                    finish(); // back to tabs / list
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --------------- Attachments rendering -------------------

    private void renderAttachments() {
        Context ctx = this;
        layoutAttachmentList.removeAllViews();

        if (attachmentUris.isEmpty()) {
            layoutAttachmentList.setVisibility(View.GONE);
            return;
        }

        layoutAttachmentList.setVisibility(View.VISIBLE);

        float density = ctx.getResources().getDisplayMetrics().density;
        int marginTopPx = (int) (4 * density);
        int paddingPx = (int) (4 * density);

        for (int i = 0; i < attachmentUris.size(); i++) {
            Uri uri = attachmentUris.get(i);
            String displayName = (i < attachmentNames.size())
                    ? attachmentNames.get(i)
                    : generateAttachmentDisplayName(uri);

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

            if (isEditMode) {
                imgDelete.setEnabled(true);
                imgDelete.setAlpha(1f);
                int index = i;
                imgDelete.setOnClickListener(v -> {
                    attachmentUris.remove(index);
                    if (index < attachmentNames.size()) {
                        attachmentNames.remove(index);
                    }
                    renderAttachments();
                });
            } else {
                imgDelete.setEnabled(false);
                imgDelete.setAlpha(0.3f);
                imgDelete.setOnClickListener(null);
            }

            row.addView(imgDelete);
            layoutAttachmentList.addView(row);
        }
    }

    /**
     * Generate display name in the pattern:
     * img_YYMMDD_HHMMSS.jpg  for images
     * doc_YYMMDD_HHMMSS.ext for other documents
     */
    private String generateAttachmentDisplayName(Uri uri) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
        String ts = sdf.format(new Date());

        boolean isImage = false;
        try {
            String mime = getContentResolver().getType(uri);
            if (mime != null && mime.startsWith("image/")) {
                isImage = true;
            }
        } catch (Exception ignored) {
        }

        String uriStr = uri != null ? uri.toString().toLowerCase() : "";
        if (!isImage) {
            if (uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg")
                    || uriStr.endsWith(".png") || uriStr.endsWith(".webp")) {
                isImage = true;
            }
        }

        if (isImage) {
            return "img_" + ts + ".jpg";
        }

        // other docs: figure out extension
        String ext = "";

        try {
            if ("content".equals(uri.getScheme())) {
                Cursor c = getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME},
                        null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        String originalName = c.getString(0);
                        int dot = originalName != null ? originalName.lastIndexOf('.') : -1;
                        if (dot >= 0 && dot < originalName.length() - 1) {
                            ext = originalName.substring(dot);
                        }
                    }
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }

        if (ext.isEmpty() && uriStr.contains(".")) {
            int dot = uriStr.lastIndexOf('.');
            if (dot >= 0 && dot < uriStr.length() - 1) {
                ext = uriStr.substring(dot);
            }
        }

        return "doc_" + ts + ext;
    }

    // --------------- Attachment pickers -------------------

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    private void launchCamera() {
        if (!isEditMode) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
                return;
            }
        }

        File photoFile;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
            String ts = sdf.format(new Date());
            photoFile = new File(getCacheDir(), "img_" + ts + ".jpg");
        } catch (Exception e) {
            photoFile = new File(getCacheDir(), "photo_" + System.currentTimeMillis() + ".jpg");
        }

        pendingCameraUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile
        );

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQ_TAKE_PHOTO);
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
                Toast.makeText(this,
                        "Camera permission is required to take photos",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (!isEditMode || resultCode != Activity.RESULT_OK) return;

        Uri uri = null;
        if (requestCode == REQ_PICK_IMAGE || requestCode == REQ_PICK_FILE) {
            if (data != null) uri = data.getData();
        } else if (requestCode == REQ_TAKE_PHOTO) {
            uri = pendingCameraUri;
        }

        if (uri != null) {
            attachmentUris.add(uri);
            attachmentNames.add(generateAttachmentDisplayName(uri));
            renderAttachments();
        }
    }
}
