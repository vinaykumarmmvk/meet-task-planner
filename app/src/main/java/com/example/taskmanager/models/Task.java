package com.example.taskmanager.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String description;

    public boolean isAllDay;

    public String date;       // For All Day
    public String dateTime;
    public String fromDate;   // For Duration
    public String toDate;

    public long startTimestamp;
    public long stopTimestamp;
    public long durationMillis;

    public boolean isOngoing; // true = started but not yet stopped
}
