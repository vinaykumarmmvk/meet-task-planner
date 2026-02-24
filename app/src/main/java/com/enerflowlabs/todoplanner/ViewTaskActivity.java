package com.enerflowlabs.todoplanner;

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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.enerflowlabs.todoplanner.database.AppDatabase;
import com.enerflowlabs.todoplanner.fragments.FrequencyBottomSheetDialog;
import com.enerflowlabs.todoplanner.models.Task;
import com.enerflowlabs.todoplanner.utils.DateUtils;
import com.enerflowlabs.todoplanner.utils.AttachmentUtils;
import com.enerflowlabs.todoplanner.utils.DialogUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewTaskActivity extends BaseActivity {

    private static final int REQ_PICK_IMAGE = 3001;
    private static final int REQ_PICK_FILE = 3002;
    private static final int REQ_TAKE_PHOTO = 3003;
    private static final int REQ_CAMERA_PERMISSION = 3004;
    private static final int ATTACH_TYPE_IMAGE = 1;
    private static final int ATTACH_TYPE_FILE = 2;

    private EditText editTitle, editDescription, editCreatedAt, textType;
    private LinearLayout layoutAllDay, layoutDuration, layoutClockin, layoutFrequency;
    private EditText editAllDayDate;
    private EditText editFromDate, editFromTime, editToDate, editToTime, editFrequency;
    private TextView textClockinDate, textClockinDuration;
    private Spinner spinnerStatus;

    private TextView textHeader;

    private TextView imgAddAttachment;
    private LinearLayout layoutAttachmentList;
    private ImageButton btnBack;
    private FloatingActionButton fabEdit, fabSave, fabDelete;

    private Task task;
    private boolean isEditMode = false;

    private final List<Uri> attachmentUris = new ArrayList<>();
    private final List<String> attachmentNames = new ArrayList<>();

    private Uri pendingCameraUri = null;

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

        // If opened from Calendar edit icon, open edit mode directly
        if (getIntent().getBooleanExtra("open_edit", false)) {
            setModeEdit();
        }

        setupPickerTouchHandlers();

        editFrequency.setOnClickListener(v -> {
            if (!isEditMode) return;

            boolean isRepeatMaster = "REPEAT".equals(task.taskType) && task.repeatParentId == null;
            if (!isRepeatMaster) return;

            boolean everyDay = !"CUSTOM_DAYS".equals(task.repeatRule);
            boolean[] daysSelected = parseRepeatDaysToBoolArray(task.repeatDays);

            FrequencyBottomSheetDialog sheet =
                    FrequencyBottomSheetDialog.newInstance(everyDay, daysSelected);

            sheet.setCallback((isEveryDay, selectedDays) -> {
                task.repeatRule = isEveryDay ? "EVERY_DAY" : "CUSTOM_DAYS";
                task.repeatDays = isEveryDay ? "" : boolArrayToRepeatDaysCsv(selectedDays);

                // Update UI
                if (isEveryDay) {
                    editFrequency.setText("Every day");
                } else {
                    String csv = task.repeatDays;
                    editFrequency.setText(csv == null || csv.isEmpty()
                            ? "Custom days"
                            : "Custom days: " + csv);
                }
            });

            sheet.show(getSupportFragmentManager(), "freq_sheet");
        });

        btnBack.setOnClickListener(v -> {
            if (isEditMode) {
                // Back from edit -> discard changes and show view mode
                loadTaskIntoUi();
                setModeView();
            } else {
                // Back from view -> close activity (back to tabs)
                goBackToTabIfNeeded();
            }
        });

        fabEdit.setOnClickListener(v -> setModeEdit());

        fabSave.setOnClickListener(v -> {
            if (saveChanges()) {
                task = AppDatabase.getInstance(this).taskDao().getTaskById(task.id);
                loadTaskIntoUi();
                setModeView();
                Toast.makeText(this, getString(R.string.task_updated), Toast.LENGTH_SHORT).show();

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

    private String boolArrayToRepeatDaysCsv(boolean[] days) {
        if (days == null || days.length < 7) return "";
        StringBuilder sb = new StringBuilder();
        if (days[0]) sb.append("MON,");
        if (days[1]) sb.append("TUE,");
        if (days[2]) sb.append("WED,");
        if (days[3]) sb.append("THU,");
        if (days[4]) sb.append("FRI,");
        if (days[5]) sb.append("SAT,");
        if (days[6]) sb.append("SUN,");
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private boolean[] parseRepeatDaysToBoolArray(String csv) {
        // Index: 0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri, 5=Sat, 6=Sun
        boolean[] arr = new boolean[7];
        if (csv == null) return arr;

        String s = csv.trim();
        if (s.isEmpty()) return arr;

        String[] parts = s.split(",");
        for (String p : parts) {
            String day = p.trim().toUpperCase();
            switch (day) {
                case "MON": arr[0] = true; break;
                case "TUE": arr[1] = true; break;
                case "WED": arr[2] = true; break;
                case "THU": arr[3] = true; break;
                case "FRI": arr[4] = true; break;
                case "SAT": arr[5] = true; break;
                case "SUN": arr[6] = true; break;
            }
        }
        return arr;
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
        editCreatedAt = findViewById(R.id.edit_created_at);
        layoutAllDay = findViewById(R.id.layout_all_day);
        layoutDuration = findViewById(R.id.layout_duration);
        layoutClockin = findViewById(R.id.layout_clockin);
        editAllDayDate = findViewById(R.id.edit_all_day_date);
        editFromDate = findViewById(R.id.edit_from_date);
        editFromTime = findViewById(R.id.edit_from_time);
        editToDate = findViewById(R.id.edit_to_date);
        editToTime = findViewById(R.id.edit_to_time);
        layoutFrequency = findViewById(R.id.layout_frequency);
        editFrequency = findViewById(R.id.edit_frequency);
        textClockinDate = findViewById(R.id.text_clockin_date);
        textClockinDuration = findViewById(R.id.text_clockin_duration);
        spinnerStatus = findViewById(R.id.spinner_status);
        textHeader = findViewById(R.id.text_header);
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
        editCreatedAt.setText(formatCreatedAt(task.createdAt));

        String type;
        if (task.taskType != null) {
            switch (task.taskType) {
                case "REPEAT":
                case "REPEAT_OCCURRENCE":
                    type = "Repeat";
                    break;
                case "ALL_DAY":
                    type = "All-day";
                    break;
                case "DURATION":
                    type = "Duration";
                    break;
                case "CLOCK_IN":
                default:
                    type = "Clock-in";
                    break;
            }
        } else {
            // Backward compatibility for older tasks
            if (task.isAllDay) type = "All-day";
            else if (task.fromDate != null && task.toDate != null) type = "Duration";
            else type = "Clock-in";
        }
        textType.setText(type);

        // ---------- Status ----------
        String status = task.status;
        //boolean isClockInType = !task.isAllDay && task.fromDate == null && task.toDate == null;

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this,
                        R.array.status_labels,
                        R.layout.spinner_item_black
                );

        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black);
        spinnerStatus.setAdapter(adapter);
        spinnerStatus.setSelection(com.enerflowlabs.todoplanner.utils.StatusUi.codeToIndex(status), false);

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

        boolean isRepeatMaster = "REPEAT".equals(task.taskType) && task.repeatParentId == null;

        if (layoutFrequency != null) {
            layoutFrequency.setVisibility(isRepeatMaster ? View.VISIBLE : View.GONE);
        }

        if (isRepeatMaster && editFrequency != null) {
            // Show a readable value
            // Example mapping:
            // task.repeatRule = "EVERY_DAY" or "CUSTOM_DAYS"
            // task.repeatDays = "MON,TUE,WED" (or whatever you store)
            String freqText;

            if ("CUSTOM_DAYS".equals(task.repeatRule) && task.repeatDays != null && !task.repeatDays.trim().isEmpty()) {
                freqText = "Custom days: " + task.repeatDays;
            } else {
                freqText = "Every day";
            }

            editFrequency.setText(freqText);
        }
        // Attachments: build list from URIs and generate display names
        attachmentUris.clear();
        attachmentNames.clear();

        /* 1️⃣ Read attachment NAMES saved by Scheduler */
        List<String> savedNames = new ArrayList<>();
        if (task.attachmentNames != null && !task.attachmentNames.trim().isEmpty()) {
            String[] nameParts = task.attachmentNames.split(";");
            for (String n : nameParts) {
                if (!n.trim().isEmpty()) {
                    savedNames.add(n.trim());
                }
            }
        }

        /* 2️⃣ Read attachment URIs */
        if (task.attachmentUris != null && !task.attachmentUris.trim().isEmpty()) {
            String[] uriParts = task.attachmentUris.split(";");

            for (int i = 0; i < uriParts.length; i++) {
                String p = uriParts[i].trim();
                if (p.isEmpty()) continue;

                Uri uri = Uri.parse(p);
                attachmentUris.add(uri);

                // ✅ USE NAME FROM SCHEDULER (same index)
                if (i < savedNames.size()) {
                    attachmentNames.add(savedNames.get(i));
                } else {
                    // fallback for old tasks
                    attachmentNames.add("attachment");
                }
            }
        }


        renderAttachments();
    }

    private String formatCreatedAt(long ts) {
        if (ts <= 0) return "";
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(ts));
    }

    private void applyModeUi(boolean editMode) {
        // Badge + subtle background distinction between View and Edit modes
        if (textHeader != null) {
            if (editMode) {
                textHeader.setText("Edit Task Details");
            } else {
                textHeader.setText("View Task Details");
            }
        }

        int fieldBg = editMode ? R.drawable.bg_field_edit : R.drawable.bg_field_view;
        int spinnerBg = editMode ? R.drawable.spinner_bg_white : R.drawable.spinner_bg_grey;

        // Common fields
        applyFieldBg(editTitle, fieldBg);
        applyFieldBg(editDescription, fieldBg);
        applyFieldBg(editCreatedAt, R.drawable.bg_field_view);
        applyFieldBg(textType, R.drawable.bg_field_view);

        if (textType.getText().toString().equals("Clock-in"))
            spinnerStatus.setBackgroundResource(R.drawable.bg_status_spinner_grey);
        else
            spinnerStatus.setBackgroundResource(spinnerBg);

        // All-day / Duration fields
        applyFieldBg(editAllDayDate, fieldBg);
        applyFieldBg(editFromDate, fieldBg);
        applyFieldBg(editFromTime, fieldBg);
        applyFieldBg(editToDate, fieldBg);
        applyFieldBg(editToTime, fieldBg);

    }

    private void applyFieldBg(EditText et, int bgRes) {
        if (et == null) return;
        et.setBackgroundResource(bgRes);
        et.setPadding(et.getPaddingLeft(), et.getPaddingTop(), et.getPaddingRight(), et.getPaddingBottom());
    }

    void setModeView() {
        isEditMode = false;
        applyModeUi(false);
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

        if (editFrequency != null) {
            editFrequency.setEnabled(false);
            editFrequency.setAlpha(0.6f);
        }

        renderAttachments(); // delete icons disabled in view mode
    }

    private void setModeEdit() {
        isEditMode = true;
        applyModeUi(true);
        fabEdit.setVisibility(View.GONE);
        fabSave.setVisibility(View.VISIBLE);

        editCreatedAt.setEnabled(false);
        editCreatedAt.setFocusable(false);
        editCreatedAt.setClickable(false);
        textType.setEnabled(false);
        textType.setFocusable(false);
        textType.setClickable(false);

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

        boolean isRepeatMaster = "REPEAT".equals(task.taskType) && task.repeatParentId == null;

        if (layoutFrequency != null) {
            layoutFrequency.setVisibility(isRepeatMaster ? View.VISIBLE : View.GONE);
        }

        if (editFrequency != null) {
            if (isRepeatMaster) {
                editFrequency.setEnabled(true);
                editFrequency.setAlpha(1f);
            } else {
                editFrequency.setEnabled(false);
                editFrequency.setAlpha(0.6f);
            }
        }
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
            task.status = com.enerflowlabs.todoplanner.utils.StatusUi.indexToCode(pos);
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

        // Attachments => join URIs + join Names
        if (attachmentUris.isEmpty()) {
            task.attachmentUris = null;
            task.attachmentNames = null;
        } else {
            // URIs
            StringBuilder sbUris = new StringBuilder();
            for (int i = 0; i < attachmentUris.size(); i++) {
                if (i > 0) sbUris.append(";");
                sbUris.append(attachmentUris.get(i).toString());
            }
            task.attachmentUris = sbUris.toString();

            // Names (must match same index order)
            StringBuilder sbNames = new StringBuilder();
            for (int i = 0; i < attachmentNames.size(); i++) {
                if (i > 0) sbNames.append(";");
                sbNames.append(attachmentNames.get(i));
            }
            task.attachmentNames = sbNames.toString();
        }


        SimpleDateFormat sdfFull = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        task.dateTime = sdfFull.format(new Date());

        AppDatabase.getInstance(this).taskDao().update(task);

        boolean isRepeatMaster = "REPEAT".equals(task.taskType) && task.repeatParentId == null;
        if (isRepeatMaster) {
            rebuildRepeatOccurrences(task);   // delete old occurrences + create new ones based on custom days
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            loadTaskIntoUi();
            setModeView();
        } else {
            goBackToTabIfNeeded();
        }
    }

    private void rebuildRepeatOccurrences(Task master) {

        // 1) Remove old occurrences
        AppDatabase db = AppDatabase.getInstance(this);
        db.taskDao().deleteOccurrencesForMaster(master.id);

        // 2) Determine selected weekdays
        // If your master.repeatRule / master.repeatDays are updated from Frequency callback, use them:
        boolean everyDay = !"CUSTOM_DAYS".equals(master.repeatRule);

        boolean[] daysSelected;
        if (everyDay) {
            daysSelected = new boolean[]{true,true,true,true,true,true,true}; // Mon..Sun
        } else {
            daysSelected = parseRepeatDaysToBoolArray(master.repeatDays); // Mon..Sun
        }

        // 3) Loop dates from master.fromDate -> master.toDate
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat dtFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

        Date startDate, endDate;
        try {
            startDate = sdf.parse(master.fromDate);
            endDate = sdf.parse(master.toDate);
            if (startDate == null || endDate == null) return;
        } catch (Exception e) {
            return;
        }

        Calendar c = Calendar.getInstance();
        c.setTime(startDate);

        Calendar end = Calendar.getInstance();
        end.setTime(endDate);

        String startTimeStr = editFromTime.getText().toString().trim();
        String endTimeStr   = editToTime.getText().toString().trim();

        while (!c.after(end)) {

            int dayIndexMon0 = calendarDayToMon0Index(c.get(Calendar.DAY_OF_WEEK));
            if (daysSelected[dayIndexMon0]) {

                String dayStr = sdf.format(c.getTime());

                try {
                    Date startDt = dtFormat.parse(dayStr + " " + startTimeStr);
                    Date endDt   = dtFormat.parse(dayStr + " " + endTimeStr);
                    if (startDt == null || endDt == null) {
                        c.add(Calendar.DAY_OF_MONTH, 1);
                        continue;
                    }

                    Task occ = new Task();
                    occ.title = master.title;
                    occ.description = master.description;
                    occ.status = master.status;

                    occ.isAllDay = false;
                    occ.taskType = "REPEAT_OCCURRENCE";
                    occ.repeatParentId = master.id;          // ✅ critical: keep this non-null
                    occ.repeatRule = null;
                    occ.repeatDays = null;

                    occ.fromDate = dayStr;                   // ✅ used for calendar queries + dots
                    occ.toDate = dayStr;
                    occ.date = null;

                    occ.startTimestamp = startDt.getTime();
                    occ.stopTimestamp  = endDt.getTime();
                    occ.durationMillis = occ.stopTimestamp - occ.startTimestamp;

                    occ.createdAt = System.currentTimeMillis();
                    occ.attachmentUris = master.attachmentUris; // if you want attachments copied

                    db.taskDao().insert(occ);

                } catch (Exception ignored) {}
            }

            c.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    // Calendar.DAY_OF_WEEK: Sun=1..Sat=7 -> convert to Mon=0..Sun=6
    private int calendarDayToMon0Index(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY: return 0;
            case Calendar.TUESDAY: return 1;
            case Calendar.WEDNESDAY: return 2;
            case Calendar.THURSDAY: return 3;
            case Calendar.FRIDAY: return 4;
            case Calendar.SATURDAY: return 5;
            case Calendar.SUNDAY: return 6;
        }
        return 0;
    }
    private void goBackToTabIfNeeded() {
        int returnTab = getIntent().getIntExtra("return_tab", -1);
        if (returnTab >= 0) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("open_tab", returnTab);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    // ---------------- Delete ----------------

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_task))
                .setMessage(getString(R.string.delete_task_confirm))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    AppDatabase.getInstance(this).taskDao().delete(task);
                    Toast.makeText(this, getString(R.string.task_deleted), Toast.LENGTH_SHORT).show();
                    finish(); // back to tabs / list
                })
                .setNegativeButton(getString(R.string.cancel), null)
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
                    : getStableAttachmentDisplayName(uri);

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
            tv.setOnClickListener(v -> AttachmentUtils.viewOnly(ctx, uri, displayName));
            row.addView(tv);

            // Download icon (always available)
            ImageView imgDownload = new ImageView(ctx);
            LinearLayout.LayoutParams dlParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            imgDownload.setLayoutParams(dlParams);
            imgDownload.setImageResource(android.R.drawable.stat_sys_download);
            imgDownload.setColorFilter(0xFF444444);

            imgDownload.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            imgDownload.setOnClickListener(v -> AttachmentUtils.downloadAndOpen(ctx, uri, displayName));
            row.addView(imgDownload);

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

    /**
     * Returns a STABLE display name for an attachment.
     *
     * IMPORTANT:
     * - Do NOT generate a timestamp-based name here.
     * - View/Edit screens can be opened multiple times; timestamp-based names will change on every reload.
     *
     * Strategy:
     * 1) For content:// URIs -> use OpenableColumns.DISPLAY_NAME (original filename)
     * 2) For file:// or FileProvider URIs -> use lastPathSegment / file name
     * 3) Fallback -> "attachment"
     */
    private String getStableAttachmentDisplayName(Uri uri) {
        if (uri == null) return "attachment";

        // 1) content:// -> query the display name
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            Cursor c = null;
            try {
                c = getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME},
                        null, null, null);
                if (c != null && c.moveToFirst()) {
                    String name = c.getString(0);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
        }

        // 2) file:// or FileProvider -> try lastPathSegment
        String last = uri.getLastPathSegment();
        if (last != null && !last.trim().isEmpty()) {
            // Some providers may return a path-like segment; keep only the file name part.
            int slash = last.lastIndexOf('/');
            if (slash >= 0 && slash < last.length() - 1) {
                last = last.substring(slash + 1);
            }
            return last.trim();
        }

        return "attachment";
    }

    private String generateAttachmentDisplayName(Uri uri, int attachType) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
        String ts = sdf.format(new Date());

        if (attachType == ATTACH_TYPE_IMAGE) {
            return "img_" + ts + ".jpg";
        } else {
            String ext = "";
            String originalName = null;

            try {
                if (uri != null && "content".equals(uri.getScheme())) {
                    Cursor c = getContentResolver().query(uri,
                            new String[]{OpenableColumns.DISPLAY_NAME},
                            null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) originalName = c.getString(0);
                        c.close();
                    }
                }
            } catch (Exception ignored) {}

            if (originalName == null && uri != null) {
                originalName = uri.getLastPathSegment();
            }

            if (originalName != null) {
                int dot = originalName.lastIndexOf('.');
                if (dot >= 0 && dot < originalName.length() - 1) {
                    ext = originalName.substring(dot);
                }
            }

            return "doc_" + ts + ext;
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
            int type = (requestCode == REQ_PICK_IMAGE || requestCode == REQ_TAKE_PHOTO)
                    ? ATTACH_TYPE_IMAGE
                    : ATTACH_TYPE_FILE;

            attachmentUris.add(uri);
            attachmentNames.add(generateAttachmentDisplayName(uri, type));
            renderAttachments();
        }

    }
}
