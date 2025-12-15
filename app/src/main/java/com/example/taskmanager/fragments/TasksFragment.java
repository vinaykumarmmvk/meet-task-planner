package com.example.taskmanager.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.adapters.TaskAdapter;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;

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

        // Always show full search bar with hint
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);
        searchView.clearFocus(); // so keyboard doesn’t pop up immediately

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter(new ArrayList<Task>());
        recyclerView.setAdapter(adapter);

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
