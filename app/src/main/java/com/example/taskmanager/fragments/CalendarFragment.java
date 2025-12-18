package com.example.taskmanager.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.ViewTaskActivity;
import com.example.taskmanager.adapters.CalendarTaskAdapter;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.utils.TaskDotDecorator;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private MaterialCalendarView calendarView;
    private TextView textHeader;
    private TextView textEmpty;
    private RecyclerView recycler;
    private CalendarTaskAdapter adapter;

    public CalendarFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCalendarDecorators();
    }

    private void refreshCalendarDecorators() {
        if (getView() == null) return;

        MaterialCalendarView cv = getView().findViewById(R.id.calendar_view);
        cv.removeDecorators();

        List<Task> allTasks = AppDatabase.getInstance(getContext())
                .taskDao().getAllTasks();

        HashSet<CalendarDay> taskDates = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.US);

        for (Task task : allTasks) {
            String d = (task.date != null && !task.date.isEmpty()) ? task.date : task.fromDate;
            if (d == null || d.isEmpty()) continue;

            try {
                java.util.Date date = sdf.parse(d);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                taskDates.add(CalendarDay.from(cal));
            } catch (ParseException ignored) { }
        }

        cv.addDecorator(new TaskDotDecorator(taskDates));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        calendarView = view.findViewById(R.id.calendar_view);
        textHeader = view.findViewById(R.id.text_calendar_tasks_header);
        textEmpty = view.findViewById(R.id.text_calendar_empty);
        recycler = view.findViewById(R.id.recycler_calendar_tasks);

        adapter = new CalendarTaskAdapter(task -> {
            // Feature #4: open View page, but ensure back returns to Calendar tab
            Intent intent = new Intent(getContext(), ViewTaskActivity.class);
            intent.putExtra("task_id", task.id);
            intent.putExtra("return_tab", 1);        // 0=Enter, 1=Calendar, 2=Tasks
            intent.putExtra("from_calendar", true);
            startActivity(intent);
        });

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        // Initial dots
        refreshCalendarDecorators();

        // Feature #5: remove popup on date click; just show list below
        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            String selectedDate = String.format(Locale.getDefault(), "%02d.%02d.%d",
                    date.getDay(), date.getMonth() + 1, date.getYear());

            textHeader.setText("Tasks on " + selectedDate);

            List<Task> tasks = AppDatabase.getInstance(getContext())
                    .taskDao()
                    .getTasksForDate(selectedDate);

            if (tasks == null || tasks.isEmpty()) {
                textEmpty.setVisibility(View.VISIBLE);
                textEmpty.setText("No tasks on " + selectedDate);
                adapter.setItems(null);
            } else {
                textEmpty.setVisibility(View.GONE);
                adapter.setItems(tasks);
            }
        });
    }
}
