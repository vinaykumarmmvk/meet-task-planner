package com.example.taskmanager.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarTaskAdapter extends RecyclerView.Adapter<CalendarTaskAdapter.VH> {

    public interface OnTaskActionListener {
        void onOpen(Task task);
        void onEdit(Task task);
        void onDelete(Task task);
    }

    private final List<Task> items = new ArrayList<>();
    private final OnTaskActionListener listener;

    public CalendarTaskAdapter(OnTaskActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Task> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Task t = items.get(position);

        h.title.setText(t.title == null ? "" : t.title);

        // Duration text (Requirement #16) - mimic Tasks tab formatting
        h.duration.setText(buildDurationLine(h.itemView.getContext(), t));

        boolean isClockIn = !t.isAllDay && t.fromDate == null && t.toDate == null;
        boolean isClockInInProgress = isClockIn && t.isOngoing;

        // ---- Spinner adapter (set once) ----
        if (h.statusSpinner.getAdapter() == null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    h.itemView.getContext(),
                    R.array.task_status_options,
                    R.layout.spinner_item_black
            );
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black);
            h.statusSpinner.setAdapter(adapter);
        }

// normalize status
        String status = (t.status == null || t.status.trim().isEmpty())
                ? Task.STATUS_NOT_STARTED
                : t.status;

// set selection without triggering listener
        h.statusSpinner.setOnItemSelectedListener(null);
        h.statusSpinner.setSelection(statusToIndex(status), false);

// background based on status
        applyCardBg(h, status);

        // Disable status change for clock-in in-progress (Requirement note)
        h.statusSpinner.setEnabled(!isClockInInProgress);

        // update DB on change (only if enabled)
        if (!isClockInInProgress) {
            h.statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    String newStatus = parent.getItemAtPosition(pos).toString();

                    String oldStatus = (t.status == null || t.status.trim().isEmpty())
                            ? Task.STATUS_NOT_STARTED
                            : t.status;

                    if (newStatus.equals(oldStatus)) return;

                    t.status = newStatus;

                    AppDatabase.getInstance(h.itemView.getContext())
                            .taskDao()
                            .update(t);

                    applyCardBg(h, newStatus);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }


        // Match Tasks list background colors
        int bgRes = R.drawable.bg_task_not_started;
        if (Task.STATUS_IN_PROGRESS.equals(status)) {
            bgRes = R.drawable.bg_task_in_progress;
        } else if (Task.STATUS_COMPLETED.equals(status)) {
            bgRes = R.drawable.bg_task_completed;
        }
        h.root.setBackgroundResource(bgRes);

        // Edit / Delete (Requirement #17/#18)
        h.imgEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(t);
        });

        h.imgDelete.setOnClickListener(v -> {
            Context ctx = v.getContext();

            new androidx.appcompat.app.AlertDialog.Builder(ctx)
                    .setTitle("Delete task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (listener != null) {
                            listener.onDelete(t);
                        } else {
                            // fallback (optional)
                            AppDatabase.getInstance(ctx).taskDao().delete(t);
                            Toast.makeText(ctx, "Task deleted", Toast.LENGTH_SHORT).show();
                        }
                    })

                    .setNegativeButton("Cancel", null)
                    .show();
        });


        // Disable edit/delete + item open for clock-in in-progress
        h.imgEdit.setEnabled(!isClockInInProgress);
        h.imgDelete.setEnabled(!isClockInInProgress);
        h.imgEdit.setAlpha(isClockInInProgress ? 0.35f : 1f);
        h.imgDelete.setAlpha(isClockInInProgress ? 0.35f : 1f);

        h.itemView.setOnClickListener(v -> {
            if (isClockInInProgress) return;
            if (listener != null) listener.onOpen(t);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int statusToIndex(String status) {
        if (Task.STATUS_IN_PROGRESS.equals(status)) return 1;
        if (Task.STATUS_COMPLETED.equals(status)) return 2;
        return 0;
    }

    private void applyCardBg(VH h, String status) {
        int bgRes = R.drawable.bg_task_not_started;
        if (Task.STATUS_IN_PROGRESS.equals(status)) {
            bgRes = R.drawable.bg_task_in_progress;
        } else if (Task.STATUS_COMPLETED.equals(status)) {
            bgRes = R.drawable.bg_task_completed;
        }
        h.root.setBackgroundResource(bgRes);
    }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout root;
        TextView title;
        TextView duration;
        Spinner statusSpinner;
        ImageView imgEdit;
        ImageView imgDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.root_calendar_item);
            title = itemView.findViewById(R.id.text_title);
            statusSpinner = itemView.findViewById(R.id.spinner_status_calendar);
            duration = itemView.findViewById(R.id.text_duration_calendar);
            imgEdit = itemView.findViewById(R.id.img_edit_calendar);
            imgDelete = itemView.findViewById(R.id.img_delete_calendar);

        }
    }

    private String buildDurationLine(Context ctx, Task task) {
        // Keep it similar to Tasks tab
        boolean isClockIn = !task.isAllDay && task.fromDate == null && task.toDate == null;

        if (task.isAllDay) {
            // Example: "All day" (date shown in header already)
            return "All day";
        }

        if (isClockIn) {
            if (task.isOngoing) {
                String startedOn = (task.dateTime != null && !task.dateTime.trim().isEmpty())
                        ? task.dateTime
                        : (task.date != null ? task.date : "");
                return startedOn.isEmpty() ? "In progress" : ("Started on: " + startedOn);
            }
            // completed clock-in
            if (task.durationMillis > 0) {
                return formatDuration(task.durationMillis);
            }
            return "";
        }

        // Duration task
        if (task.durationMillis > 0) {
            return formatDuration(task.durationMillis);
        }
        return "";
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, secs);
        }
        return String.format(Locale.getDefault(), "%dm %02ds", minutes, secs);
    }
}
