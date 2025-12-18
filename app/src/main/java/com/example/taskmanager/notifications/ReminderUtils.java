package com.example.taskmanager.notifications;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;

import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.taskmanager.R;
import com.example.taskmanager.MainActivity;
import com.example.taskmanager.ViewTaskActivity;
import com.example.taskmanager.models.Task;
import com.example.taskmanager.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderUtils {

    public static final String CHANNEL_ID_TASK_REMINDER = "task_reminder_channel";

    // ---------- Public helpers ----------

    /**
     * Schedules reminders for a task:
     *
     * - All-day / Enter-duration:
     *      * previous day 20:00  (if still in the future)
     *      * same day   09:00    (if still in the future)
     *      * if it has an explicit start time -> also at exact start time
     *
     * - Clock-in tasks: nothing here (they get immediate notifications instead).
     */
    public static void scheduleReminderForTask(Context context, Task task) {
        if (task == null) return;

        // Clock-in task → handled with immediate notifications, no AlarmManager
        if (!task.isAllDay && task.fromDate == null && task.toDate == null) {
            return;
        }

        if (task.startTimestamp <= 0) return;

        if (!canPostNotifications(context)) {
            Toast.makeText(context,
                    "Notification permission not granted – no reminder scheduled",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        ensureNotificationChannel(context);

        long now = System.currentTimeMillis();
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(task.startTimestamp);

// We treat 00:00:00 as "no explicit time"
        boolean hasExplicitTime =
                !(startCal.get(Calendar.HOUR_OF_DAY) == 0
                        && startCal.get(Calendar.MINUTE) == 0
                        && startCal.get(Calendar.SECOND) == 0);

        List<Long> triggers = new ArrayList<>();
        boolean isAllDay = task.isAllDay;

        if (hasExplicitTime && !isAllDay) {
            // ---------- Enter Duration WITH start time ----------
            long startMs = task.startTimestamp;

            // 15 minutes before start
            long fifteenBefore = startMs - 15 * 60 * 1000L;
            if (fifteenBefore > now) {
                triggers.add(fifteenBefore);
            }

            // At exact start time
            if (startMs > now) {
                triggers.add(startMs);
            }

        } else {
            // ---------- All-day or date-only (no explicit time) ----------
            // Same-day 09:00
            Calendar sameDay9 = (Calendar) startCal.clone();
            sameDay9.set(Calendar.HOUR_OF_DAY, 9);
            sameDay9.set(Calendar.MINUTE, 0);
            sameDay9.set(Calendar.SECOND, 0);
            sameDay9.set(Calendar.MILLISECOND, 0);
            long sameDay9Ms = sameDay9.getTimeInMillis();
            if (sameDay9Ms > now) {
                triggers.add(sameDay9Ms);
            }

            // Previous day 20:00
            Calendar prevDay20 = (Calendar) startCal.clone();
            prevDay20.add(Calendar.DAY_OF_YEAR, -1);
            prevDay20.set(Calendar.HOUR_OF_DAY, 20);
            prevDay20.set(Calendar.MINUTE, 0);
            prevDay20.set(Calendar.SECOND, 0);
            prevDay20.set(Calendar.MILLISECOND, 0);
            long prevDay20Ms = prevDay20.getTimeInMillis();
            if (prevDay20Ms > now) {
                triggers.add(prevDay20Ms);
            }
        }

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SimpleDateFormat debugFormat =
                new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

        for (long trigger : triggers) {
            Intent intent = new Intent(context, TaskReminderReceiver.class);
            intent.putExtra("task_id", task.id);

            int requestCode = buildRequestCode(task.id, trigger);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ : exact alarms require SCHEDULE_EXACT_ALARM permission.
                    // If we don't have it, fall back to inexact alarm to avoid crash.
                    if (alarmManager.canScheduleExactAlarms()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP, trigger, pi);
                        } else {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
                        }
                    } else {
                        // No exact-alarm privilege -> use inexact alarm
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, trigger, pi);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
                }
            }


            // 🔍 Debug info so you see exactly what was scheduled
            Toast.makeText(context,
                    "Reminder set for: " + debugFormat.format(new Date(trigger)),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Immediate notification when a clock-in task is started.
     */
    public static void notifyClockinStarted(Context context, Task task) {
        if (task == null || !canPostNotifications(context)) return;
        ensureNotificationChannel(context);

        String timeStr = formatDateTime(task.startTimestamp);
        String text = "Clock-in started: \"" + task.title + "\" at " + timeStr;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, CHANNEL_ID_TASK_REMINDER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Clock-in started")
                .setContentText(text)
                .setContentIntent(getOpenAppPendingIntent(context))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context)
                .notify(buildRequestCode(task.id, System.currentTimeMillis()),
                        builder.build());
    }

    /**
     * Immediate notification when a clock-in task is stopped.
     */
    public static void notifyClockinStopped(Context context, Task task) {
        if (task == null || !canPostNotifications(context)) return;
        ensureNotificationChannel(context);

        String dur = DateUtils.formatDuration(task.durationMillis);
        String text = "Clock-in stopped: \"" + task.title + "\" (" + dur + ")";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, CHANNEL_ID_TASK_REMINDER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Clock-in finished")
                .setContentText(text)
                .setContentIntent(getOpenTaskPendingIntent(context, task.id))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context)
                .notify(buildRequestCode(task.id, System.currentTimeMillis()),
                        builder.build());
    }

    // ---------- Internal helpers ----------

    /**
     * PendingIntent that just opens the app (MainActivity).
     * Used for "Clock-in started" notification click.
     */
    public static PendingIntent getOpenAppPendingIntent(Context context) {
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                1000,
                openApp,
                buildPendingIntentFlags()
        );
    }

    /**
     * PendingIntent that opens the ViewTaskActivity for the given task id.
     * Uses TaskStackBuilder so the back button returns to MainActivity.
     */
    public static PendingIntent getOpenTaskPendingIntent(Context context, int taskId) {
        Intent view = new Intent(context, ViewTaskActivity.class);
        view.putExtra("task_id", taskId);

        // ✅ Make intent unique per task (prevents stale PendingIntent extras)
        view.setAction("com.example.taskmanager.ACTION_VIEW_TASK");
        view.setData(Uri.parse("taskmanager://task/" + taskId));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(view);

        int requestCode = 200000 + Math.abs(taskId);
        return stackBuilder.getPendingIntent(
                requestCode,
                buildPendingIntentFlags()
        );
    }

    private static int buildPendingIntentFlags() {
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }



    private static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (manager.getNotificationChannel(CHANNEL_ID_TASK_REMINDER) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_TASK_REMINDER,
                    "Task reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Reminders for your tasks");
            manager.createNotificationChannel(channel);
        }
    }

    private static int buildRequestCode(int taskId, long triggerTimeMillis) {
        long value = taskId * 31L + (triggerTimeMillis / 60000L);
        return (int) (value & 0x7fffffff);  // always positive
    }

    private static String formatDateTime(long millis) {
        if (millis <= 0) return "";
        SimpleDateFormat fmt =
                new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        return fmt.format(new Date(millis));
    }
}
