package com.example.taskmanager.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.taskmanager.R;
import com.example.taskmanager.database.AppDatabase;
import com.example.taskmanager.models.Task;

public class TaskReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_TASK_ID = "extra_task_id";
    public static final String CHANNEL_ID = "task_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);
        if (taskId == -1) return;

        Task task = AppDatabase.getInstance(context).taskDao().getTaskById((int) taskId);
        if (task == null) return;

        String title = "Upcoming meeting";
        String content = task.title;
        if (task.date != null) {
            content += " on " + task.date;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    "android.permission.POST_NOTIFICATIONS"
            ) != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, skip notification
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // create a small bell icon/vector
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context)
                .notify((int) taskId, builder.build());
    }
}
