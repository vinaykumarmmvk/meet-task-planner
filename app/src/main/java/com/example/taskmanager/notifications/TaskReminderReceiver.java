package com.example.taskmanager.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TaskReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int taskId = intent.getIntExtra("task_id", -1);
        if (taskId == -1) return;

        Task task = AppDatabase.getInstance(context)
                .taskDao()
                .getTaskById(taskId);
        if (task == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // No permission – silently ignore
            return;
        }

        ReminderUtils.ensureNotificationChannel(context);

        String message;
        if (task.isAllDay) {
            message = "All-day task on " + (task.date != null ? task.date : "");
        } else if (task.fromDate != null && task.toDate != null) {
            SimpleDateFormat fmt =
                    new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            String start = fmt.format(new Date(task.startTimestamp));
            String end = fmt.format(new Date(task.stopTimestamp));
            String duration = DateUtils.formatDuration(task.durationMillis);
            message = "From " + start + " to " + end + " (" + duration + ")";
        } else {
            // Clock-in should normally not arrive here, but just in case:
            String duration = DateUtils.formatDuration(task.durationMillis);
            message = "Clock-in task (" + duration + ")";
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, ReminderUtils.CHANNEL_ID_TASK_REMINDER)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Task reminder: " + task.title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(taskId, builder.build());
    }
}
