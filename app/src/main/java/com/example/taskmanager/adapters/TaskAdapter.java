package com.example.taskmanager.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

        holder.title.setText(task.title);
        holder.description.setText(task.description == null ? "" : task.description);

        // Build duration / time text
        String durationDisplay;
        if (task.isAllDay) {
            durationDisplay = "All day: " + (task.date != null ? task.date : "");
        } else if (task.fromDate != null && task.toDate != null) {
            String base = "From " + task.fromDate + " to " + task.toDate;
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

        boolean isClockInOngoing =
                !task.isAllDay
                        && task.fromDate == null
                        && task.toDate == null
                        && task.isOngoing;

        if (isClockInOngoing) {
            statusText = Task.STATUS_IN_PROGRESS;
            // Hide edit/delete, show badge
            holder.imgEdit.setVisibility(View.GONE);
            holder.imgDelete.setVisibility(View.GONE);
            holder.duration.setText("In progress …");

            // 🔒 Disable click + different background
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            if (task.status != null && !task.status.trim().isEmpty()) {
                statusText = task.status;
            } else {
                statusText = Task.STATUS_NOT_STARTED;
            }
            holder.imgEdit.setVisibility(View.VISIBLE);
            holder.imgDelete.setVisibility(View.VISIBLE);

            // ✅ Normal clickable item
            holder.itemView.setClickable(true);

            holder.itemView.setOnClickListener(v -> {
                Context ctx = v.getContext();
                Intent intent = new Intent(ctx, ViewTaskActivity.class);
                intent.putExtra("task_id", task.id);
                ctx.startActivity(intent);
            });
        }

        holder.status.setText(statusText);

        // Background color based on status
        if (Task.STATUS_COMPLETED.equals(statusText)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_task_completed);
            holder.status.setBackgroundResource(R.drawable.bg_badge_completed);
            holder.status.setTextColor(Color.parseColor("#000000"));
        } else if (Task.STATUS_IN_PROGRESS.equals(statusText)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_task_in_progress);
            holder.status.setBackgroundResource(R.drawable.bg_badge_in_progress);
            holder.status.setTextColor(Color.parseColor("#000000"));
        } else { // Not started or anything else
            holder.itemView.setBackgroundResource(R.drawable.bg_task_not_started);
            holder.status.setBackgroundResource(R.drawable.bg_badge_not_started);
            holder.status.setTextColor(Color.parseColor("#FFFFFF"));
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

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    private void confirmDeleteTask(Context context, int position) {
        if (position == RecyclerView.NO_POSITION) return;
        Task task = taskList.get(position);

        new AlertDialog.Builder(context)
                .setTitle("Delete task?")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    AppDatabase.getInstance(context).taskDao().delete(task);
                    taskList.remove(position);
                    notifyItemRemoved(position);
                })
                .setNegativeButton("Cancel", null)
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

            textInfo.setText("Change the date for this all-day task.");

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

            textInfo.setText("Change FROM and TO date/time for this duration task.");

        } else {
            // CLOCK-IN task (only title & description)
            labelAllDay.setVisibility(View.GONE);
            editDate.setVisibility(View.GONE);
            labelDuration.setVisibility(View.GONE);
            layoutDuration.setVisibility(View.GONE);

            textInfo.setText("Clock-in task: you can edit title and description. Time comes from the clock-in/stop.");
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Edit task")
                .setView(dialogView)
                .setPositiveButton("Save", null) // override later
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String newTitle = editTitle.getText().toString().trim();
                String newDesc = editDescription.getText().toString().trim();

                if (newTitle.isEmpty()) {
                    editTitle.setError("Title required");
                    return;
                }

                task.title = newTitle;
                task.description = newDesc;

                // ALL-DAY save logic
                if (task.isAllDay) {
                    String newDate = editDate.getText().toString().trim();
                    if (newDate.isEmpty()) {
                        editDate.setError("Date required");
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
                        editDate.setError("Use format dd.MM.yyyy");
                        return;
                    }

                    // ENTER DURATION save logic
                } else if (task.fromDate != null && task.toDate != null) {
                    String fromStr = editFrom.getText().toString().trim();
                    String toStr = editTo.getText().toString().trim();
                    String fromTimeStr = editFromTime.getText().toString().trim();
                    String toTimeStr = editToTime.getText().toString().trim();

                    if (fromStr.isEmpty()) {
                        editFrom.setError("Required");
                        return;
                    }
                    if (toStr.isEmpty()) {
                        editTo.setError("Required");
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
                            editToTime.setError("TO must be after FROM");
                            Toast.makeText(context,
                                    "FROM date/time must be before TO date/time",
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
                                "Use format dd.MM.yyyy HH:mm",
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

                Toast.makeText(context, "Task updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }


    static class TaskViewHolder extends RecyclerView.ViewHolder {

        TextView title, description, duration, status;
        ImageView imgDelete, imgEdit, imgAttachmentPin;

        TaskViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.text_title);
            description = view.findViewById(R.id.text_description);
            duration = view.findViewById(R.id.text_duration);
            imgDelete = view.findViewById(R.id.img_delete);
            imgEdit = view.findViewById(R.id.img_edit);
            imgAttachmentPin = view.findViewById(R.id.img_attachment_pin);
            status = view.findViewById(R.id.text_status);
        }
    }
}
