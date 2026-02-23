package com.enerflowlabs.todoplanner.fragments;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enerflowlabs.todoplanner.R;
import com.enerflowlabs.todoplanner.ViewTaskActivity;
import com.enerflowlabs.todoplanner.adapters.CalendarTaskAdapter;
import com.enerflowlabs.todoplanner.database.AppDatabase;
import com.enerflowlabs.todoplanner.models.Task;
import com.enerflowlabs.todoplanner.utils.TaskDotDecorator;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.format.TitleFormatter;

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
    private TextView textGotoDate;
    private ImageView imgGotoDate;

    public CalendarFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyCalendarMonthLocaleFix();
        refreshCalendarDecorators();

        // Also refresh the list for the currently selected day (Bug fixes #1/#2)
        if (calendarView != null) {
            CalendarDay selected = calendarView.getSelectedDate();
            if (selected == null) selected = CalendarDay.today();
            calendarView.setSelectedDate(selected);
            showTasksForDay(selected);
            updateGotoDateText(selected);
        }
    }

    private void applyCalendarMonthLocaleFix() {
        if (calendarView == null) return;

        final java.util.Locale locale = java.util.Locale.getDefault();

        calendarView.setTitleFormatter(new TitleFormatter() {
            @Override
            public CharSequence format(CalendarDay day) {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("MMMM yyyy", locale);
                return sdf.format(day.getDate());
            }
        });

        // Force header redraw immediately
        CalendarDay current = calendarView.getCurrentDate();
        if (current != null) {
            calendarView.setCurrentDate(current);
        }
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
        applyCalendarMonthLocaleFix();

        textHeader = view.findViewById(R.id.text_calendar_tasks_header);
        textEmpty = view.findViewById(R.id.text_calendar_empty);
        recycler = view.findViewById(R.id.recycler_calendar_tasks);
        textGotoDate = view.findViewById(R.id.text_goto_date);
        imgGotoDate = view.findViewById(R.id.img_goto_date);

        adapter = new CalendarTaskAdapter(new CalendarTaskAdapter.OnTaskActionListener() {
            @Override
            public void onOpen(Task task) {
                // Feature #4: open View page, but ensure back returns to Calendar tab
                Intent intent = new Intent(getContext(), ViewTaskActivity.class);
                intent.putExtra("task_id", task.id);
                intent.putExtra("return_tab", 1);        // 0=Enter, 1=Calendar, 2=Tasks
                intent.putExtra("from_calendar", true);
                startActivity(intent);
            }

            @Override
            public void onEdit(Task task) {
                Intent intent = new Intent(getContext(), ViewTaskActivity.class);
                intent.putExtra("task_id", task.id);
                intent.putExtra("return_tab", 1);
                intent.putExtra("from_calendar", true);
                intent.putExtra("open_edit", true); // open directly in edit mode
                startActivity(intent);
            }

            @Override
            public void onDelete(Task task) {
                if (getContext() == null) return;
                AppDatabase.getInstance(getContext()).taskDao().delete(task);
                Toast.makeText(getContext(), getString(R.string.task_deleted), Toast.LENGTH_SHORT).show();
                // Refresh dots + list
                refreshCalendarDecorators();
                CalendarDay selected = calendarView.getSelectedDate();
                if (selected == null) selected = CalendarDay.today();
                showTasksForDay(selected);
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        // Initial dots
        refreshCalendarDecorators();

        // Feature #5: remove popup on date click; just show list below
        calendarView.setOnDateChangedListener((widget, date, selected) -> showTasksForDay(date));

        CalendarDay today = CalendarDay.today();
        calendarView.setSelectedDate(today);
        showTasksForDay(today);
        updateGotoDateText(today);

        // Requirement #15: Go to date
        View.OnClickListener goToClick = v -> openDatePicker();
        imgGotoDate.setOnClickListener(goToClick);
        textGotoDate.setOnClickListener(goToClick);

    }

    private void openDatePicker() {
        if (getContext() == null) return;

        Calendar cal = Calendar.getInstance();
        CalendarDay selected = calendarView.getSelectedDate();
        if (selected != null) {
            cal.set(Calendar.YEAR, selected.getYear());
            cal.set(Calendar.MONTH, selected.getMonth());
            cal.set(Calendar.DAY_OF_MONTH, selected.getDay());
        }

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dlg = new DatePickerDialog(getContext(), (view, y, m, d) -> {
            CalendarDay cd = CalendarDay.from(y, m, d);
            calendarView.setCurrentDate(cd, true);
            calendarView.setSelectedDate(cd);
            updateGotoDateText(cd);
            showTasksForDay(cd);
        }, year, month, day);

        dlg.show();
    }

    private void updateGotoDateText(CalendarDay day) {
        if (textGotoDate == null || day == null) return;
        String selectedDate = String.format(Locale.getDefault(), "%02d.%02d.%d",
                day.getDay(), day.getMonth() + 1, day.getYear());
        textGotoDate.setText(selectedDate);
    }

    private void showTasksForDay(CalendarDay date) {
        String selectedDate = String.format(Locale.getDefault(), "%02d.%02d.%d",
                date.getDay(), date.getMonth() + 1, date.getYear());

        textHeader.setText(getString(R.string.tasks_on, selectedDate));

        List<Task> tasks = AppDatabase.getInstance(getContext())
                .taskDao()
                .getTasksForDate(selectedDate);

        if (tasks == null || tasks.isEmpty()) {
            textEmpty.setVisibility(View.VISIBLE);
            textEmpty.setText(getString(R.string.no_tasks_on, selectedDate));
            adapter.setItems(null);
        } else {
            textEmpty.setVisibility(View.GONE);
            adapter.setItems(tasks);
        }
    }

}
