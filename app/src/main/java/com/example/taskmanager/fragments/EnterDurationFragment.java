package com.example.taskmanager.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.utils.DateUtils;
import com.example.taskmanager.utils.DialogUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EnterDurationFragment extends Fragment {

    private LinearLayout taskContainer;
    private FloatingActionButton fabAddTask;

    public EnterDurationFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_enter_duration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        taskContainer = view.findViewById(R.id.task_container);
        fabAddTask = view.findViewById(R.id.fab_add_task);

        List<Task> ongoingTasks = AppDatabase.getInstance(getContext())
                .taskDao().getOngoingTasks();

        if (ongoingTasks.isEmpty()) {
            // ✅ No ongoing tasks → Add initial empty section
            addTaskSection(null);
        } else {
            // ✅ Restore each ongoing task
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
        RadioButton radioAllDay = taskView.findViewById(R.id.radio_all_day);
        RadioButton radioDuration = taskView.findViewById(R.id.radio_duration);
        RadioGroup radioGroup = taskView.findViewById(R.id.radio_group);

        // Fill previous data
        editTitle.setText(task.title);
        editDescription.setText(task.description);
        textStart.setText("Started on: " + task.dateTime);

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

        btnStop.setOnClickListener(v -> {
            long stopTime = System.currentTimeMillis();
            task.stopTimestamp = stopTime;
            task.durationMillis = stopTime - task.startTimestamp;
            task.isOngoing = false;

            AppDatabase.getInstance(getContext()).taskDao().update(task);

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

    private void addTaskSection(@Nullable String prefillTitle) {
        View taskView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_task_input, taskContainer, false);

        EditText editTitle = taskView.findViewById(R.id.edit_title);
        EditText editDescription = taskView.findViewById(R.id.edit_description);
        RadioGroup radioGroup = taskView.findViewById(R.id.radio_group);
        RadioButton radioClockin = taskView.findViewById(R.id.radio_clockin);
        RadioButton radioAllDay = taskView.findViewById(R.id.radio_all_day);
        RadioButton radioDuration = taskView.findViewById(R.id.radio_duration);
        EditText editDate = taskView.findViewById(R.id.edit_date);
        EditText editFrom = taskView.findViewById(R.id.edit_from);
        EditText editTo = taskView.findViewById(R.id.edit_to);
        LinearLayout layoutDuration = taskView.findViewById(R.id.layout_duration);
        Button btnSubmit = taskView.findViewById(R.id.btn_submit);
        Button btnRemove = taskView.findViewById(R.id.btn_remove);
        Button btnStop = taskView.findViewById(R.id.btn_stop);
        TextView textStart = taskView.findViewById(R.id.text_start_time);

        if (prefillTitle != null) {
            editTitle.setText(prefillTitle);
        }

        // Enable SUBMIT only if title is entered
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
            if (checkedId == R.id.radio_clockin) {
                // Hide all time fields
                editDate.setVisibility(View.GONE);
                layoutDuration.setVisibility(View.GONE);
                btnSubmit.setText("Start");
            } else if (checkedId == R.id.radio_all_day) {
                // Show only date
                editDate.setVisibility(View.VISIBLE);
                layoutDuration.setVisibility(View.GONE);
                btnSubmit.setText("Submit");
            } else if (checkedId == R.id.radio_duration) {
                // Show only from/to layout
                editDate.setVisibility(View.GONE);
                layoutDuration.setVisibility(View.VISIBLE);
                btnSubmit.setText("Submit");
            }
        });


        // Date picker for All Day
        editDate.setOnClickListener(v -> DialogUtils.showDatePicker(getContext(), editDate));

        // Date pickers for From / To
        editFrom.setOnClickListener(v -> DialogUtils.showDatePicker(getContext(), editFrom));
        editTo.setOnClickListener(v -> DialogUtils.showDatePicker(getContext(), editTo));

        // Remove task view
        btnRemove.setOnClickListener(v -> taskContainer.removeView(taskView));

        // START time on SUBMIT
        btnSubmit.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String desc = editDescription.getText().toString().trim();

            boolean isAllDay = radioAllDay.isChecked();
            boolean isDuration = radioDuration.isChecked();
            String dateStr = editDate.getText().toString().trim();
            String fromStr = editFrom.getText().toString().trim();
            String toStr = editTo.getText().toString().trim();

            long currentTimeMillis = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
            String formattedDateTime = sdf.format(new Date(currentTimeMillis));

            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ CASE 1: Manual Timer (no radio selected)
            if (!isAllDay && !isDuration) {
                radioGroup.setVisibility(View.GONE);
                long startTime = System.currentTimeMillis();

                Task task = new Task();
                task.title = title;
                task.description = desc;
                task.startTimestamp = startTime;
                task.isOngoing = true;
                task.dateTime = formattedDateTime;

                SimpleDateFormat sdf4 = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                String formattedDate = sdf4.format(new Date(currentTimeMillis));
                task.date = formattedDate;

                long taskId = AppDatabase.getInstance(getContext()).taskDao().insertAndReturnId(task); // you must create this DAO method
                task.id = (int) taskId;

                // Disable input, show stop button
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

                textStart.setText("Started on: " + formattedDateTime);
                taskView.setTag(R.id.tag_task_id, task.id);
                return;
            }

            // ✅ CASE 2: All Day or Duration selected
            if (isAllDay && dateStr.isEmpty()) {
                Toast.makeText(getContext(), "Select date for all-day task", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isDuration) {
                if (fromStr.isEmpty() || toStr.isEmpty()) {
                    Toast.makeText(getContext(), "Select both FROM and TO dates", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    SimpleDateFormat sdf1 = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    Date fromDate = sdf1.parse(fromStr);
                    Date toDate = sdf1.parse(toStr);

                    if (fromDate.after(toDate)) {
                        Toast.makeText(getContext(), "FROM date must be before or equal to TO date", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (ParseException e) {
                    Toast.makeText(getContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Calculate duration
            long duration;
            if (isAllDay) {
                duration = 24 * 60 * 60 * 1000L; // 1 day
            } else {
                int days = DateUtils.calculateDaysInclusive(fromStr, toStr);
                duration = days * 24 * 60 * 60 * 1000L;
            }

            // Create Task
            Task task = new Task();
            task.title = title;
            task.description = desc;
            task.isAllDay = isAllDay;
            task.fromDate = isDuration ? fromStr : null;
            task.toDate = isDuration ? toStr : null;
            task.startTimestamp = System.currentTimeMillis();
            task.stopTimestamp = task.startTimestamp + duration;
            task.durationMillis = duration;
            task.dateTime = formattedDateTime;

            SimpleDateFormat sdf3 = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
            String currentDateStr = sdf3.format(new Date(task.startTimestamp));
            task.date = isAllDay ? dateStr : (isDuration ? fromStr : currentDateStr);

            //replaced - AppDatabase.getInstance(getContext()).taskDao().insert(task);
            long taskId = AppDatabase.getInstance(getContext()).taskDao().insertAndReturnId(task);
            task.id = (int) taskId;
            taskView.setTag(R.id.tag_task_id, task.id); // ✅ ADD THIS


            String durationStr = DateUtils.formatDuration(duration);
            DialogUtils.showSuccessDialog(getContext(),
                    "Title: " + title +
                            "\nDescription: " + desc +
                            "\nDuration: " + durationStr +
                            "\n\nAbove task is added successfully!");

            taskContainer.removeView(taskView);
        });


        // STOP button clicked
        btnStop.setOnClickListener(v -> {
            int taskId = (int) taskView.getTag(R.id.tag_task_id);
            Task task = AppDatabase.getInstance(getContext()).taskDao().getTaskById(taskId);

            if (task != null) {
                long stopTime = System.currentTimeMillis();
                task.stopTimestamp = stopTime;
                task.durationMillis = stopTime - task.startTimestamp;
                task.isOngoing = false;

                // ✅ Fix: Set date for calendar display
                //SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.US);

                String currentDateStr = sdf.format(new Date(task.startTimestamp));
                task.date = currentDateStr;

                AppDatabase.getInstance(getContext()).taskDao().update(task);

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


    private void filterTasks(String query) {
        for (int i = 0; i < taskContainer.getChildCount(); i++) {
            View taskView = taskContainer.getChildAt(i);
            EditText titleEdit = taskView.findViewById(R.id.edit_title);
            taskView.setVisibility(titleEdit.getText().toString()
                    .toLowerCase().contains(query.toLowerCase()) ? View.VISIBLE : View.GONE);
        }
    }
}
