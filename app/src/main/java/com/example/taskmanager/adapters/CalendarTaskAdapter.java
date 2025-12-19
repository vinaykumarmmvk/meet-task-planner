package com.example.taskmanager.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;

import java.util.ArrayList;
import java.util.List;

public class CalendarTaskAdapter extends RecyclerView.Adapter<CalendarTaskAdapter.VH> {

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    private final List<Task> items = new ArrayList<>();
    private final OnTaskClickListener listener;

    public CalendarTaskAdapter(OnTaskClickListener listener) {
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

// update DB on change
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


        // Match Tasks list background colors
        int bgRes = R.drawable.bg_task_not_started;
        if (Task.STATUS_IN_PROGRESS.equals(status)) {
            bgRes = R.drawable.bg_task_in_progress;
        } else if (Task.STATUS_COMPLETED.equals(status)) {
            bgRes = R.drawable.bg_task_completed;
        }
        h.root.setBackgroundResource(bgRes);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTaskClick(t);
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
        Spinner statusSpinner;

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.root_calendar_item);
            title = itemView.findViewById(R.id.text_title);
            statusSpinner = itemView.findViewById(R.id.spinner_status_calendar);

        }
    }
}
