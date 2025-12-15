package com.example.taskmanager.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.notifications.ReminderUtils;
import com.example.taskmanager.utils.DateUtils;

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

        // 🔹 Detect ongoing clock-in
        boolean isClockInOngoing =
                !task.isAllDay
                        && task.fromDate == null
                        && task.toDate == null
                        && task.isOngoing;

        if (isClockInOngoing) {
            // 🔒 Ongoing clock-in: hide edit & delete
            holder.imgEdit.setVisibility(View.GONE);
            holder.imgDelete.setVisibility(View.GONE);
            holder.badgeInProgress.setVisibility(View.VISIBLE);

            // Optional: show “(In progress…)”
            holder.duration.setText("In progress …");   //durationDisplay
        } else {
            // ✅ finished / normal task: allow edit & delete
            holder.imgEdit.setVisibility(View.VISIBLE);
            holder.imgDelete.setVisibility(View.VISIBLE);
            holder.badgeInProgress.setVisibility(View.GONE);
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
        EditText editTo = dialogView.findViewById(R.id.edit_to);
        TextView labelAllDay = dialogView.findViewById(R.id.text_all_day_label);
        TextView labelDuration = dialogView.findViewById(R.id.text_duration_label);
        TextView textInfo = dialogView.findViewById(R.id.text_info);

        // Prefill title & description
        editTitle.setText(task.title);
        editDescription.setText(task.description);

        // Show appropriate time controls
        if (task.isAllDay) {
            labelAllDay.setVisibility(View.VISIBLE);
            editDate.setVisibility(View.VISIBLE);
            editDate.setText(task.date != null ? task.date : "");
            textInfo.setText("Change the date for this all-day task (dd.MM.yyyy).");
        } else if (task.fromDate != null && task.toDate != null) {
            labelDuration.setVisibility(View.VISIBLE);
            editFrom.setVisibility(View.VISIBLE);
            editTo.setVisibility(View.VISIBLE);
            editFrom.setText(task.fromDate);
            editTo.setText(task.toDate);
            textInfo.setText("Change FROM and TO dates for this duration task (dd.MM.yyyy).");
        } else {
            // Clock-in task
            textInfo.setText("Clock-in task: you can edit title and description. Time comes from the clock-in/stop.");
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Edit task")
                .setView(dialogView)
                .setPositiveButton("Save", null) // override in onShow
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

                // Time update for All-day
                if (task.isAllDay) {
                    String newDate = editDate.getText().toString().trim();
                    if (newDate.isEmpty()) {
                        editDate.setError("Date required");
                        return;
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    try {
                        Date d = sdf.parse(newDate);
                        if (d == null) throw new ParseException("null", 0);

                        task.date = newDate;
                        task.startTimestamp = d.getTime();
                        task.durationMillis = 24L * 60L * 60L * 1000L; // 1 day
                        task.stopTimestamp = task.startTimestamp + task.durationMillis;
                    } catch (ParseException e) {
                        editDate.setError("Use format dd.MM.yyyy");
                        return;
                    }

                    // Time update for Duration
                } else if (task.fromDate != null && task.toDate != null) {
                    String fromStr = editFrom.getText().toString().trim();
                    String toStr = editTo.getText().toString().trim();

                    if (fromStr.isEmpty()) {
                        editFrom.setError("Required");
                        return;
                    }
                    if (toStr.isEmpty()) {
                        editTo.setError("Required");
                        return;
                    }

                    if (!DateUtils.isFromBeforeTo(fromStr, toStr)) {
                        editTo.setError("TO must be after or same as FROM");
                        Toast.makeText(context,
                                "FROM must be before or equal to TO",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    try {
                        Date fromDate = sdf.parse(fromStr);
                        if (fromDate == null) throw new ParseException("null", 0);

                        int days = DateUtils.calculateDaysInclusive(fromStr, toStr);
                        long duration = days * 24L * 60L * 60L * 1000L;

                        task.fromDate = fromStr;
                        task.toDate = toStr;
                        task.startTimestamp = fromDate.getTime();
                        task.durationMillis = duration;
                        task.stopTimestamp = task.startTimestamp + duration;
                    } catch (ParseException e) {
                        editFrom.setError("Use format dd.MM.yyyy");
                        return;
                    }
                }

                // Optional: update "last changed" dateTime
                SimpleDateFormat sdfFull =
                        new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
                task.dateTime = sdfFull.format(new Date());

                // Persist changes
                AppDatabase.getInstance(context).taskDao().update(task);
                ReminderUtils.scheduleReminderForTask(context, task);

                // Update list + UI
                taskList.set(position, task);
                notifyItemChanged(position);

                Toast.makeText(context, "Task updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, description, duration, badgeInProgress;
        ImageView imgDelete, imgEdit;

        TaskViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.text_title);
            description = view.findViewById(R.id.text_description);
            duration = view.findViewById(R.id.text_duration);
            imgDelete = view.findViewById(R.id.img_delete);
            imgEdit = view.findViewById(R.id.img_edit);
            badgeInProgress = view.findViewById(R.id.badge_in_progress);
        }
    }
}
