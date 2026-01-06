package com.example.taskmanager.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.taskmanager.models.Task;

import java.util.List;

@Dao
public interface TaskDao {

    @Insert
    void insert(Task task);

    @Query("SELECT * FROM tasks ORDER BY startTimestamp DESC")
    List<Task> getAllTasks();

    @Query("SELECT * FROM tasks WHERE date = :selectedDate OR fromDate = :selectedDate")
    List<Task> getTasksForDate(String selectedDate);

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%'")
    List<Task> searchByTitle(String query);

    // Used by Prompt-Update commands (pick the most recently created match)
    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' ORDER BY created_at DESC")
    List<Task> searchByTitleNewestFirst(String query);

    @Insert
    long insertAndReturnId(Task task);

    @Query("SELECT * FROM tasks WHERE isOngoing = 1")
    List<Task> getOngoingTasks();

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task getTaskById(int id);

    @Query("SELECT * FROM tasks ORDER BY created_at ASC")
    List<Task> getAllOrderByCreatedAsc();

    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    List<Task> getAllOrderByCreatedDesc();

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

}
