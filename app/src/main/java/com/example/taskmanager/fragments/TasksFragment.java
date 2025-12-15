package com.example.taskmanager.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.example.taskmanager.adapters.TaskAdapter;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;

import java.util.List;

public class TasksFragment extends Fragment {

    private TaskAdapter adapter;
    private RecyclerView recyclerView;
    private SearchView searchView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.recycler_tasks);
        searchView = view.findViewById(R.id.search_tasks);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 🔹 Load initial list from DB
        List<Task> tasks = AppDatabase.getInstance(getContext())
                .taskDao()
                .getAllTasks();

        adapter = new TaskAdapter(tasks);
        recyclerView.setAdapter(adapter);

        // 🔹 Search always uses adapter.updateList(...)
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                List<Task> filtered = AppDatabase.getInstance(getContext())
                        .taskDao()
                        .searchByTitle(newText);
                adapter.updateList(filtered);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadTasks();   // every time you come to this tab, reload from DB
    }

    private void reloadTasks() {
        List<Task> latest = AppDatabase.getInstance(getContext())
                .taskDao()
                .getAllTasks();
        adapter.updateList(latest);   // 🔑 refresh the adapter's internal list
    }
}

