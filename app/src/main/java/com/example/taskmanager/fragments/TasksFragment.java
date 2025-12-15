package com.example.taskmanager.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TasksFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private SearchView searchView;
    private Spinner spinnerSort;
    private Button btnExport;

    private final List<Task> allTasks = new ArrayList<>();
    private String currentQuery = "";
    private int currentSortMode = SORT_LATEST;

    private static final int SORT_LATEST = 0;
    private static final int SORT_OLDEST = 1;
    private static final int SORT_DURATION = 2;

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
        Button btnExport = view.findViewById(R.id.button_export_csv);

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
            @Override public boolean onQueryTextSubmit(String query) { return false; }

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
        if (allTasks.isEmpty()) {
            Toast.makeText(getContext(), "No tasks to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        // Header row
        sb.append("Title,Description,Type,Date,From,To,DurationMinutes\n");

        for (Task task : allTasks) {
            String type;
            if (task.isAllDay) {
                type = "All-day";
            } else if (task.fromDate != null && task.toDate != null) {
                type = "Duration";
            } else {
                type = "Clock-in";
            }

            long durationMinutes = task.durationMillis / (60 * 1000);

            String title = task.title == null ? "" : task.title.replace("\"", "\"\"");
            String desc = task.description == null ? "" : task.description.replace("\"", "\"\"");

            sb.append("\"").append(title).append("\",")
                    .append("\"").append(desc).append("\",")
                    .append(type).append(",")
                    .append(task.date == null ? "" : task.date).append(",")
                    .append(task.fromDate == null ? "" : task.fromDate).append(",")
                    .append(task.toDate == null ? "" : task.toDate).append(",")
                    .append(durationMinutes)
                    .append("\n");
        }

        try {
            // 1) Create CSV file in cache dir
            File cacheDir = requireContext().getCacheDir();
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyMMdd_HHmmss", java.util.Locale.getDefault());
            String timestamp = sdf.format(new java.util.Date());
            String fileName = "tasks_export_" + timestamp + ".csv";

            File csvFile = new File(cacheDir, fileName);

            FileWriter writer = new FileWriter(csvFile);
            writer.write(sb.toString());
            writer.flush();
            writer.close();

            // 2) Get URI via FileProvider
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    csvFile
            );

            // 3) Share the file
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/csv");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Meet & Task Planner - Export");
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(sendIntent, "Share CSV via"));

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to export CSV", Toast.LENGTH_SHORT).show();
        }
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
