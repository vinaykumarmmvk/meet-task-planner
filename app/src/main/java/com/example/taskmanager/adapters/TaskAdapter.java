package com.example.taskmanager.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.ViewTaskActivity;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.utils.DateUtils;
import com.example.taskmanager.utils.DialogUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final List<Task> taskList;

    // Used by TasksFragment: new TaskAdapter(taskList)
    public TaskAdapter(List<Task> tasks) {
        this.taskList = new ArrayList<>(tasks); // mutable copy
    }

    public void updateList(List<Task> updated) {
        taskList.clear();
        taskList.addAll(updated);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_display, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);
        Context context = holder.itemView.getContext();

        holder.title.setText(task.title);
        holder.description.setText(task.description == null ? "" : task.description);

        // Build duration / time text
        String durationDisplay;
        if (task.isAllDay) {
            durationDisplay = context.getString(R.string.all_day_date, task.date != null ? task.date : "");
        } else if (task.fromDate != null && task.toDate != null) {
            String base = context.getString(R.string.from_to_date, task.fromDate, task.toDate);

            String durStr = DateUtils.formatDuration(task.durationMillis);
            durationDisplay = base + " (" + durStr + ")";
        } else {
            // Clock-in task
            String durStr = DateUtils.formatDuration(task.durationMillis);
            if (task.date != null) {
                durationDisplay = task.date + " - " + durStr;
            } else {
                durationDisplay = durStr;
            }
        }
        holder.duration.setText(durationDisplay);

        String statusText;

        // --- Setup spinner adapter once ---
        if (holder.statusSpinner.getAdapter() == null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    holder.itemView.getContext(),
                    R.array.status_labels,
                    R.layout.spinner_item_black
            );
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black);
            holder.statusSpinner.setAdapter(adapter);
        }

// Determine current status

        boolean isClockInType =
                !task.isAllDay
                        && task.fromDate == null
                        && task.toDate == null;

        boolean isClockInOngoing = isClockInType && task.isOngoing;

        if (isClockInOngoing) {
            statusText = Task.STATUS_IN_PROGRESS;

            // Hide edit/delete, show in-progress text
            holder.imgEdit.setVisibility(View.GONE);
            holder.imgDelete.setVisibility(View.GONE);
            holder.duration.setText(R.string.in_progress_dots);

            // Disable click + disable spinner
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            holder.statusSpinner.setEnabled(false);

        } else {
            statusText = (task.status != null && !task.status.trim().isEmpty())
                    ? task.status
                    : Task.STATUS_NOT_STARTED;

            holder.imgEdit.setVisibility(View.VISIBLE);
            holder.imgDelete.setVisibility(View.VISIBLE);

            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v -> {
                Context ctx = v.getContext();
                Intent intent = new Intent(ctx, ViewTaskActivity.class);
                intent.putExtra("task_id", task.id);
                ctx.startActivity(intent);
            });

            holder.statusSpinner.setEnabled(!isClockInType);

        }

// Set spinner selection WITHOUT triggering listener
        holder.statusSpinner.setOnItemSelectedListener(null);
        holder.statusSpinner.setSelection(statusToIndex(statusText), false);

// Apply background badge grey color + black text color
        applyTaskCardBackground(holder, statusText);

// Listener: update DB when user changes it (skip for ongoing clockin)
        if (!isClockInType && !isClockInOngoing) {

            holder.statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean first = true;

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    String newCode = com.example.taskmanager.utils.StatusUi.indexToCode(pos);

                    String oldCode = (task.status == null || task.status.trim().isEmpty())
                            ? Task.STATUS_NOT_STARTED
                            : task.status;

                    if (newCode.equals(oldCode)) return;

                    task.status = newCode;
                    AppDatabase.getInstance(holder.itemView.getContext()).taskDao().update(task);

// background should use CODE, not label
                    applyTaskCardBackground(holder, newCode);

                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        // ✅ Show pin if attachments exist
        if (task.attachmentUris != null && !task.attachmentUris.trim().isEmpty()) {
            holder.imgAttachmentPin.setVisibility(View.VISIBLE);
        } else {
            holder.imgAttachmentPin.setVisibility(View.GONE);
        }

        holder.imgDelete.setOnClickListener(v ->
                confirmDeleteTask(v.getContext(), holder.getAdapterPosition()));

        holder.imgEdit.setOnClickListener(v ->
                showEditDialog(v.getContext(),
                        taskList.get(holder.getAdapterPosition()),
                        holder.getAdapterPosition()));
    }

    private void applyTaskCardBackground(TaskViewHolder holder, String statusText) {
        if (Task.STATUS_COMPLETED.equals(statusText)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_task_completed);
        } else if (Task.STATUS_IN_PROGRESS.equals(statusText)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_task_in_progress);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_task_not_started);
        }
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    private int statusToIndex(String status) {
        if (Task.STATUS_IN_PROGRESS.equals(status)) return 1;
        if (Task.STATUS_COMPLETED.equals(status)) return 2;
        return 0; // Not started default
    }

    private void confirmDeleteTask(Context context, int position) {
        if (position == RecyclerView.NO_POSITION) return;
        Task task = taskList.get(position);

        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_task_q)
                .setMessage(R.string.delete_task_confirm)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    AppDatabase.getInstance(context).taskDao().delete(task);
                    taskList.remove(position);
                    notifyItemRemoved(position);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEditDialog(Context context, Task task, int position) {
        if (position == RecyclerView.NO_POSITION) return;

        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_edit_task, null, false);

        EditText editTitle = dialogView.findViewById(R.id.edit_title);
        EditText editDescription = dialogView.findViewById(R.id.edit_description);
        EditText editDate = dialogView.findViewById(R.id.edit_date);
        EditText editFrom = dialogView.findViewById(R.id.edit_from);
        EditText editFromTime = dialogView.findViewById(R.id.edit_from_time);
        EditText editTo = dialogView.findViewById(R.id.edit_to);
        EditText editToTime = dialogView.findViewById(R.id.edit_to_time);
        TextView labelAllDay = dialogView.findViewById(R.id.text_all_day_label);
        TextView labelDuration = dialogView.findViewById(R.id.text_duration_label);
        View layoutDuration = dialogView.findViewById(R.id.layout_duration);
        TextView textInfo = dialogView.findViewById(R.id.text_info);

        // Prefill title & description
        editTitle.setText(task.title);
        editDescription.setText(task.description == null ? "" : task.description);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // Decide which section to show
        if (task.isAllDay) {
            // ALL-DAY
            labelAllDay.setVisibility(View.VISIBLE);
            editDate.setVisibility(View.VISIBLE);
            labelDuration.setVisibility(View.GONE);
            layoutDuration.setVisibility(View.GONE);

            editDate.setText(task.date != null ? task.date : "");

            // Date picker for all-day date
            editDate.setOnClickListener(v -> DialogUtils.showDatePicker(context, editDate));

            textInfo.setText(R.string.edit_info_all_day);

        } else if (task.fromDate != null && task.toDate != null) {
            // ENTER DURATION task (from/to with times)
            labelAllDay.setVisibility(View.GONE);
            editDate.setVisibility(View.GONE);
            labelDuration.setVisibility(View.VISIBLE);
            layoutDuration.setVisibility(View.VISIBLE);

            // Prefill dates
            editFrom.setText(task.fromDate != null ? task.fromDate : "");
            editTo.setText(task.toDate != null ? task.toDate : "");

            // Prefill times from timestamps, if available
            if (task.startTimestamp > 0) {
                Date start = new Date(task.startTimestamp);
                String timeStr = timeFormat.format(start);
                editFromTime.setText(timeStr);

                // if fromDate was empty for some reason, recover it from timestamp
                if (editFrom.getText().toString().trim().isEmpty()) {
                    editFrom.setText(dateFormat.format(start));
                }
            }

            if (task.stopTimestamp > 0) {
                Date stop = new Date(task.stopTimestamp);
                String timeStr = timeFormat.format(stop);
                editToTime.setText(timeStr);

                if (editTo.getText().toString().trim().isEmpty()) {
                    editTo.setText(dateFormat.format(stop));
                }
            }

            // Date pickers
            editFrom.setOnClickListener(v -> DialogUtils.showDatePicker(context, editFrom));
            editTo.setOnClickListener(v -> DialogUtils.showDatePicker(context, editTo));

            // Time pickers
            editFromTime.setOnClickListener(v -> DialogUtils.showTimePicker(context, editFromTime));
            editToTime.setOnClickListener(v -> DialogUtils.showTimePicker(context, editToTime));

            textInfo.setText(R.string.edit_info_duration);

        } else {
            // CLOCK-IN task (only title & description)
            labelAllDay.setVisibility(View.GONE);
            editDate.setVisibility(View.GONE);
            labelDuration.setVisibility(View.GONE);
            layoutDuration.setVisibility(View.GONE);

            textInfo.setText(R.string.edit_info_clockin);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.edit_task)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null) // override later
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dlg -> {
            Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String newTitle = editTitle.getText().toString().trim();
                String newDesc = editDescription.getText().toString().trim();

                if (newTitle.isEmpty()) {
                    editTitle.setError(context.getString(R.string.title_required));
                    return;
                }

                task.title = newTitle;
                task.description = newDesc;

                // ALL-DAY save logic
                if (task.isAllDay) {
                    String newDate = editDate.getText().toString().trim();
                    if (newDate.isEmpty()) {
                        editDate.setError(context.getString(R.string.date_required));
                        return;
                    }

                    try {
                        Date d = dateFormat.parse(newDate);
                        if (d == null) throw new ParseException("null", 0);

                        task.date = newDate;
                        task.startTimestamp = d.getTime();
                        task.durationMillis = 24L * 60L * 60L * 1000L; // 1 day
                        task.stopTimestamp = task.startTimestamp + task.durationMillis;
                    } catch (ParseException e) {
                        editDate.setError(context.getString(R.string.invalid_date_format));
                        return;
                    }

                    // ENTER DURATION save logic
                } else if (task.fromDate != null && task.toDate != null) {
                    String fromStr = editFrom.getText().toString().trim();
                    String toStr = editTo.getText().toString().trim();
                    String fromTimeStr = editFromTime.getText().toString().trim();
                    String toTimeStr = editToTime.getText().toString().trim();

                    if (fromStr.isEmpty()) {
                        editFrom.setError(context.getString(R.string.required));
                        return;
                    }
                    if (toStr.isEmpty()) {
                        editFrom.setError(context.getString(R.string.required));
                        return;
                    }

                    // Defaults if user leaves times blank
                    if (fromTimeStr.isEmpty()) {
                        fromTimeStr = "00:00";
                    }
                    if (toTimeStr.isEmpty()) {
                        toTimeStr = "23:59";
                    }

                    SimpleDateFormat dateTimeFormat =
                            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

                    try {
                        Date fromDateTime = dateTimeFormat.parse(fromStr + " " + fromTimeStr);
                        Date toDateTime = dateTimeFormat.parse(toStr + " " + toTimeStr);

                        if (fromDateTime == null || toDateTime == null) {
                            throw new ParseException("Invalid", 0);
                        }

                        if (fromDateTime.after(toDateTime)) {
                            editToTime.setError(context.getString(R.string.from_before_to_short));
                            Toast.makeText(context,
                                    context.getString(R.string.from_before_to_short),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        task.fromDate = fromStr;
                        task.toDate = toStr;
                        task.startTimestamp = fromDateTime.getTime();
                        task.stopTimestamp = toDateTime.getTime();
                        task.durationMillis = task.stopTimestamp - task.startTimestamp;

                    } catch (ParseException e) {
                        Toast.makeText(context,
                                context.getString(R.string.date_time_format_dd_MM_yyyy_HH_mm),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Clock-in tasks: nothing to do for time here – they keep their timestamps
                }

                // Update last-changed dateTime string
                SimpleDateFormat sdfFull =
                        new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
                task.dateTime = sdfFull.format(new Date());

                // Persist and refresh UI
                AppDatabase.getInstance(context).taskDao().update(task);
                taskList.set(position, task);
                notifyItemChanged(position);

                Toast.makeText(context, context.getString(R.string.task_updated), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }


    static class TaskViewHolder extends RecyclerView.ViewHolder {

        TextView title, description, duration;
        Spinner statusSpinner;

        ImageView imgDelete, imgEdit, imgAttachmentPin;

        TaskViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.text_title);
            description = view.findViewById(R.id.text_description);
            duration = view.findViewById(R.id.text_duration);
            imgDelete = view.findViewById(R.id.img_delete);
            imgEdit = view.findViewById(R.id.img_edit);
            imgAttachmentPin = view.findViewById(R.id.img_attachment_pin);
            statusSpinner = itemView.findViewById(R.id.spinner_status_inline);

        }
    }
}
