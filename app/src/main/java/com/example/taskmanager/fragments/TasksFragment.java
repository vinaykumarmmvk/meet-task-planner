package com.example.taskmanager.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.adapters.TaskAdapter;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TasksFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private SearchView searchView;
    private Spinner spinnerSort;
    private ImageView btnExport;

    private final List<Task> allTasks = new ArrayList<>();
    private String currentQuery = "";
    private int currentSortMode = SORT_LATEST;

    private static final int SORT_LATEST = 0;
    private static final int SORT_OLDEST = 1;
    private static final int SORT_DURATION = 2;
    private static final int SORT_CREATED_ASC = 3;
    private static final int SORT_CREATED_DESC = 4;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_tasks);
        searchView = view.findViewById(R.id.search_tasks);
        spinnerSort = view.findViewById(R.id.spinner_sort);
        btnExport = view.findViewById(R.id.img_export);

        // Always show full search bar with hint
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);
        searchView.clearFocus(); // so keyboard doesn’t pop up immediately

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter(new ArrayList<Task>());
        recyclerView.setAdapter(adapter);

        btnExport.setOnClickListener(v -> exportTasksToCsv());

        // Set up sort spinner
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.task_sort_options,
                android.R.layout.simple_spinner_item
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sortAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view,
                                       int position,
                                       long id) {
                currentSortMode = position; // 0, 1, 2
                applyFilterAndSort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Search (in-memory, on current list)
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText == null ? "" : newText;
                applyFilterAndSort();
                return true;
            }
        });

        // Initial load
        reloadTasks();
    }

    private void exportTasksToCsv() {
        Context ctx = getContext();
        if (ctx == null) return;

        List<Task> tasks = AppDatabase.getInstance(ctx).taskDao().getAllTasks();
        // or getAll(), use whatever you currently use

        StringBuilder sb = new StringBuilder();
        // 1) Header: add Status, rename DurationMinutes -> Duration
        sb.append("Title,Description,Type,Status,Date,From,To,Duration\n");

        SimpleDateFormat dateOnly = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat timeHms = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dateTimeFull = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        SimpleDateFormat timeHm = new SimpleDateFormat("HH:mm", Locale.getDefault());

        long now = System.currentTimeMillis();

        for (Task task : tasks) {
            // ---- Type detection ----
            boolean isClockIn = !task.isAllDay
                    && task.fromDate == null
                    && task.toDate == null;

            String type;
            if (task.isAllDay) {
                type = "All-day";
            } else if (!isClockIn && task.fromDate != null && task.toDate != null) {
                type = "Duration";
            } else {
                type = "Clock-in";
            }

            // ---- Status ----
            String status = task.status;
            if (isClockIn) {
                // for clock-in, status comes from ongoing/completed
                if (task.isOngoing) {
                    status = Task.STATUS_IN_PROGRESS;
                } else {
                    if (status == null || status.trim().isEmpty()) {
                        status = Task.STATUS_COMPLETED;
                    }
                }
            }
            if (status == null || status.trim().isEmpty()) {
                status = Task.STATUS_NOT_STARTED;
            }

            // ---- Date / From / To columns ----
            String dateCol = "";
            String fromCol = "";
            String toCol = "";

            if (isClockIn) {
                if (task.startTimestamp > 0) {
                    Date start = new Date(task.startTimestamp);

                    if (task.stopTimestamp > 0) {
                        Date stop = new Date(task.stopTimestamp);

                        String startDateStr = dateOnly.format(start);
                        String stopDateStr = dateOnly.format(stop);

                        if (startDateStr.equals(stopDateStr)) {
                            // same day → Date = dd.MM.yyyy; From/To = HH:mm:ss
                            dateCol = startDateStr;
                            fromCol = timeHms.format(start);
                            toCol = timeHms.format(stop);
                        } else {
                            // different days → Date empty; From/To = dd-MM-yyyy HH:mm:ss
                            dateCol = dateOnly.format(start) + " - " + dateOnly.format(stop);
                            fromCol = dateTimeFull.format(start);
                            toCol = dateTimeFull.format(stop);
                        }
                    } else {
                        // ongoing clock-in: show start date + time
                        dateCol = dateOnly.format(start);
                        fromCol = timeHms.format(start);
                        toCol = ""; // not yet stopped
                    }
                }
            } else {
                // Non clock-in tasks (All-day or Duration)
                if (task.isAllDay) {
                    // same as earlier: Date = date, From/To empty
                    dateCol = task.date != null ? task.date : "";
                    fromCol = "";
                    toCol = "";
                } else if (task.fromDate != null && task.toDate != null) {
                    // Duration tasks: show from/to date + times
                    if (task.fromDate.equals(task.toDate)) {
                        dateCol = task.fromDate;
                    } else {
                        dateCol = task.fromDate + " - " + task.toDate;
                    }

                    if (task.startTimestamp > 0) {
                        fromCol = dateTimeFull.format(new Date(task.startTimestamp));
                    }
                    if (task.stopTimestamp > 0) {
                        toCol = dateTimeFull.format(new Date(task.stopTimestamp));
                    }
                }
            }

            // ---- Duration (human readable) ----
            String durationCol = "";
            long durationMillis = task.durationMillis;

            if (durationMillis <= 0 && isClockIn && task.isOngoing && task.startTimestamp > 0) {
                // For ongoing clock-in, show duration till now
                durationMillis = now - task.startTimestamp;
            }

            if (durationMillis > 0) {
                durationCol = formatDurationHuman(durationMillis);
            }

            // ---- CSV row ----
            sb.append(csvEscape(task.title))
                    .append(',')
                    .append(csvEscape(task.description))
                    .append(',')
                    .append(csvEscape(type))
                    .append(',')
                    .append(csvEscape(status))
                    .append(',')
                    .append(csvEscape(dateCol))
                    .append(',')
                    .append(csvEscape(fromCol))
                    .append(',')
                    .append(csvEscape(toCol))
                    .append(',')
                    .append(csvEscape(durationCol))
                    .append('\n');
        }

        // ---- write file (keep your existing writing code, just use sb.toString()) ----
        try {
            SimpleDateFormat fileFmt = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
            String ts = fileFmt.format(new Date());
            String fileName = "tasks_export_" + ts + ".csv";

            // 1) Write to app cache (temp export folder)
            File exportDir = new File(ctx.getCacheDir(), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File file = new File(exportDir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();

            // 2) Share immediately via FileProvider
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx,
                    ctx.getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ctx.startActivity(Intent.createChooser(shareIntent, "Share tasks CSV"));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ctx, "Failed to export CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    private String csvEscape(String value) {
        if (value == null) return "\"\"";
        String v = value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private String formatDurationHuman(long millis) {
        if (millis <= 0) return "";

        long totalSeconds = millis / 1000;

        long weeks = totalSeconds / (7L * 24 * 3600);
        totalSeconds %= 7L * 24 * 3600;

        long days = totalSeconds / (24L * 3600);
        totalSeconds %= 24L * 3600;

        long hours = totalSeconds / 3600;
        totalSeconds %= 3600;

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        // If we have weeks or days → show only weeks & days (as you described)
        List<String> parts = new ArrayList<>();

        if (weeks > 0 || days > 0) {
            if (weeks > 0) {
                parts.add(weeks + " week" + (weeks > 1 ? "s" : ""));
            }
            if (days > 0) {
                parts.add(days + " day" + (days > 1 ? "s" : ""));
            }
            if (parts.isEmpty()) {
                parts.add("0 days");
            }
        } else {
            // No weeks/days → show hours/minutes/seconds
            if (hours > 0) {
                parts.add(hours + "h");
            }
            if (minutes > 0) {
                parts.add(minutes + "m");
            }
            if (seconds > 0 || parts.isEmpty()) {
                parts.add(seconds + "s");
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }


    @Override
    public void onResume() {
        super.onResume();
        // Reload from DB every time we come back to this tab
        reloadTasks();
    }

    private void reloadTasks() {
        allTasks.clear();
        List<Task> latestFromDb = AppDatabase
                .getInstance(getContext())
                .taskDao()
                .getAllTasks();
        if (latestFromDb != null) {
            allTasks.addAll(latestFromDb);
        }
        applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        if (adapter == null) return;

        String queryLower = currentQuery == null
                ? ""
                : currentQuery.toLowerCase(Locale.getDefault());

        // 1) Filter by title (in-memory)
        List<Task> filtered = new ArrayList<>();
        for (Task task : allTasks) {
            String title = task.title == null ? "" : task.title;
            if (queryLower.isEmpty()
                    || title.toLowerCase(Locale.getDefault()).contains(queryLower)) {
                filtered.add(task);
            }
        }

        // 2) Sort
        Collections.sort(filtered, new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                switch (currentSortMode) {
                    case SORT_OLDEST:
                        // Oldest first (smallest startTimestamp)
                        return Long.compare(t1.startTimestamp, t2.startTimestamp);

                    case SORT_DURATION:
                        // Longest duration first
                        int byDuration = Long.compare(t2.durationMillis, t1.durationMillis);
                        if (byDuration != 0) return byDuration;
                        // Tie-breaker: latest first
                        return Long.compare(t2.startTimestamp, t1.startTimestamp);

                    case SORT_CREATED_ASC:
                        // Tie-breaker: latest first
                        return Long.compare(t1.createdAt, t2.createdAt);

                    case SORT_CREATED_DESC:
                        // Tie-breaker: latest first
                        return Long.compare(t2.createdAt, t1.createdAt);


                    case SORT_LATEST:
                    default:
                        // Latest first (largest startTimestamp)
                        return Long.compare(t2.startTimestamp, t1.startTimestamp);
                }
            }
        });

        // 3) Push to adapter
        adapter.updateList(filtered);
    }
}
