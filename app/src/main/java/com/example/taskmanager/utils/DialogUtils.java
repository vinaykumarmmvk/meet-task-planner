package com.example.taskmanager.utils;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.widget.EditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DialogUtils {

    public static void showDatePicker(Context context, EditText targetEditText) {
        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(context,
                (view, year, month, day) -> {
                    calendar.set(year, month, day);
                    String formatted = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            .format(calendar.getTime());
                    targetEditText.setText(formatted);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        datePicker.show();
    }

    public static void showTimePicker(Context context, EditText targetEditText) {
        final Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePicker = new TimePickerDialog(
                context,
                (view, hourOfDay, minute1) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute1);
                    String formatted = new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(calendar.getTime());
                    targetEditText.setText(formatted);
                },
                hour,
                minute,
                true   // 24-hour format
        );

        timePicker.show();
    }

    public static void showSuccessDialog(Context context, String message) {
        new AlertDialog.Builder(context)
                .setTitle("Task Saved")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
