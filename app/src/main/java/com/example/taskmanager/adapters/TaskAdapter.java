package com.example.taskmanager.adapters;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.utils.DateUtils;

import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> taskList;

    public TaskAdapter(List<Task> tasks) {
        this.taskList = tasks;
    }

    public void updateList(List<Task> updated) {
        this.taskList = updated;
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
    public int getItemCount() {
        return taskList.size();
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);

        holder.imgDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        AppDatabase.getInstance(v.getContext()).taskDao().delete(taskList.get(position));
                        taskList.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, taskList.size());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        holder.title.setText(task.title);
        holder.description.setText(task.description);

        String durationDisplay;
        if (task.isAllDay) {
            durationDisplay = "1 day - " + task.date;
        } else if (task.fromDate != null && task.toDate != null) {
            durationDisplay = "Duration: " + task.fromDate + " to " + task.toDate;
        } else if (task.isOngoing) {
            durationDisplay = "In Progress";
        } else {
            durationDisplay = DateUtils.formatDuration(task.durationMillis);

            if (task.date != null && !task.date.isEmpty()) {
                durationDisplay = task.date + " - " + durationDisplay;
            }
        }


        holder.duration.setText(durationDisplay);
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, description, duration;
        ImageView imgDelete;

        TaskViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.text_title);
            description = view.findViewById(R.id.text_description);
            duration = view.findViewById(R.id.text_duration);
            imgDelete = view.findViewById(R.id.img_delete);
        }
    }
}
