package com.enerflowlabs.todoplanner.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.enerflowlabs.todoplanner.models.Task;

import java.util.List;

@Dao
public interface TaskDao {

    @Insert
    void insert(Task task);

    @Query("SELECT * FROM tasks ORDER BY startTimestamp DESC")
    List<Task> getAllTasks();

    // Todo tab: hide repeat occurrences; show only master tasks
    @Query("SELECT * FROM tasks " +
            "WHERE repeat_parent_id IS NULL " +
            "AND (task_type IS NULL OR task_type != 'REPEAT_OCCURRENCE') " +
            "ORDER BY created_at DESC")
    List<Task> getTodoTasks();

    @Query("SELECT * FROM tasks WHERE " +
            " (isAllDay = 1 AND startTimestamp BETWEEN :dayStart AND :dayEnd) " +
            " OR " +
            " (isAllDay = 0 AND startTimestamp <= :dayEnd AND " +
            "   (CASE WHEN stopTimestamp = 0 THEN :dayEnd ELSE stopTimestamp END) >= :dayStart)")
    List<Task> getTasksForDate(long dayStart, long dayEnd);

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

    @Query("DELETE FROM tasks WHERE repeat_parent_id = :masterId")
    void deleteOccurrencesForMaster(int masterId);

    @Query("SELECT * FROM tasks WHERE repeat_parent_id = :masterId ORDER BY startTimestamp ASC")
    List<Task> getOccurrencesForMaster(int masterId);

    @Delete
    void delete(Task task);

}
