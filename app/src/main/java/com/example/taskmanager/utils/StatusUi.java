package com.example.taskmanager.utils;

import android.content.Context;
import com.example.taskmanager.R;
import com.example.taskmanager.models.Task; // adjust package

public class StatusUi {

    // Stable DB codes
    public static final String[] CODES = new String[] {
            Task.STATUS_NOT_STARTED,
            Task.STATUS_IN_PROGRESS,
            Task.STATUS_COMPLETED
    };

    public static int codeToIndex(String code) {
        if (Task.STATUS_IN_PROGRESS.equals(code)) return 1;
        if (Task.STATUS_COMPLETED.equals(code)) return 2;
        return 0;
    }

    public static String indexToCode(int idx) {
        if (idx < 0 || idx >= CODES.length) return Task.STATUS_NOT_STARTED;
        return CODES[idx];
    }

    public static String codeToLabel(Context ctx, String code) {
        int idx = codeToIndex(code);
        String[] labels = ctx.getResources().getStringArray(R.array.status_labels);
        return labels[idx];
    }
}
