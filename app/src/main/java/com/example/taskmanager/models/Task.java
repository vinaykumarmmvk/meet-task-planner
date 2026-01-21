package com.example.taskmanager.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "created_at")
    public long createdAt;

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

    @androidx.room.ColumnInfo(name = "attachment_uris")
    public String attachmentUris; // URIs separated by ';'

    @ColumnInfo(name = "attachment_names")
    public String attachmentNames;

    @ColumnInfo(name = "status")
    public String status;   // "Not started", "In Progress", "Completed"

    // Optional constants to avoid typos:
    public static final String STATUS_NOT_STARTED = "Not started";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED   = "Completed";
}
