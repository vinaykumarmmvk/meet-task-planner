package com.example.taskmanager.notifications;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.taskmanager.models.Task;

import java.util.Calendar;

public class ReminderUtils {

    private static final String CHANNEL_ID = TaskReminderReceiver.CHANNEL_ID;

    public static void scheduleReminderForTask(Context context, Task task) {
        if (task == null) return;

        long triggerAt = computeTriggerTime(task);
        if (triggerAt <= System.currentTimeMillis()) {
            // In the past, do nothing
            return;
        }

        ensureChannel(context);

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                (int) task.id, // one reminder per task id
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private static long computeTriggerTime(Task task) {
        // 10 minutes before start
        long offset = 10 * 60 * 1000L;

        long start = task.startTimestamp;

        // For all-day, assume notification at 09:00 that day
        if (task.isAllDay && start > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(start);
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            start = cal.getTimeInMillis();
        }

        return start - offset;
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;

            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Task reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Reminders before your meetings/tasks");
                nm.createNotificationChannel(channel);
            }
        }
    }
}
