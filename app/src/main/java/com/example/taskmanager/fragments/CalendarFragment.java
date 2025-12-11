package com.example.taskmanager.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.taskmanager.utils.TaskDotDecorator;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    public CalendarFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCalendarDecorators(); // 👈 create this method
    }

    private void refreshCalendarDecorators() {
        if (getView() == null) return;

        MaterialCalendarView calendarView = getView().findViewById(R.id.calendar_view);

        calendarView.removeDecorators(); // 🧼 Clear previous dots

        List<Task> allTasks = AppDatabase.getInstance(getContext()).taskDao().getAllTasks();
        HashSet<CalendarDay> taskDates = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        for (Task task : allTasks) {
            String d = (task.date != null && !task.date.isEmpty()) ? task.date : task.fromDate;
            if (d == null || d.trim().isEmpty()) continue;

            try {
                Date date = sdf.parse(d);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                taskDates.add(CalendarDay.from(cal));
            } catch (ParseException ignored) {
            }
        }

        calendarView.addDecorator(new TaskDotDecorator(taskDates));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialCalendarView calendarView = view.findViewById(R.id.calendar_view);

        TextView textTaskTitles = view.findViewById(R.id.text_task_titles);

// Collect all task dates into CalendarDay objects
        List<Task> allTasks = AppDatabase.getInstance(getContext())
                .taskDao().getAllTasks();

        HashSet<CalendarDay> taskDates = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.US);

        //SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        for (Task task : allTasks) {
            String d = (task.date != null && !task.date.isEmpty()) ? task.date : task.fromDate;

            if (d == null || d.trim().isEmpty()) continue; // ✅ skip invalid entries

            try {
                Date date = sdf.parse(d);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                taskDates.add(CalendarDay.from(cal));
            } catch (ParseException ignored) {
            }
        }

// Add the red dot decorator
        calendarView.addDecorator(new TaskDotDecorator(taskDates));

        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            String selectedDate = String.format(Locale.getDefault(), "%02d.%02d.%d",
                    date.getDay(), date.getMonth() + 1, date.getYear());

            List<Task> tasks = AppDatabase.getInstance(getContext())
                    .taskDao()
                    .getTasksForDate(selectedDate);

            if (tasks.isEmpty()) {
                textTaskTitles.setText("No tasks on " + selectedDate);
                Toast.makeText(getContext(), "No tasks on " + selectedDate, Toast.LENGTH_SHORT).show();
            } else {
                // Update text view below calendar
                StringBuilder sb = new StringBuilder("Tasks on " + selectedDate + ":\n\n");
                StringBuilder popup = new StringBuilder("Tasks on " + selectedDate + ":\n\n");

                for (Task task : tasks) {
                    sb.append("• ").append(task.title).append("\n");
                    popup.append("• ").append(task.title).append("\n")
                            .append("  ").append(task.description).append("\n")
                            .append("  Duration: ")
                            .append(DateUtils.formatDuration(task.durationMillis)).append("\n\n");
                }

                textTaskTitles.setText(sb.toString());
                DialogUtils.showSuccessDialog(getContext(), popup.toString());
            }
        });

    }
}
