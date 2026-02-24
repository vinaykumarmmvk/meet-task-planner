package com.enerflowlabs.todoplanner.utils;

import com.prolificinteractive.materialcalendarview.CalendarDay;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    public static boolean isFromBeforeTo(String from, String to) {
        try {
            Date fromDate = dateFormat.parse(from);
            Date toDate = dateFormat.parse(to);
            return fromDate != null && toDate != null && fromDate.before(toDate);
        } catch (ParseException e) {
            return false;
        }
    }

    public static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("dd.MM.yyyy hh:mm a", Locale.getDefault())
                .format(new Date(timestamp));
    }

    public static CalendarDay toCalendarDay(String ddMMyyyy) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            java.util.Date date = sdf.parse(ddMMyyyy);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return CalendarDay.from(calendar);
        } catch (Exception e) {
            return null;
        }
    }

    public static int calculateDaysInclusive(String from, String to) {
        try {
            Date fromDate = dateFormat.parse(from);
            Date toDate = dateFormat.parse(to);
            long diff = toDate.getTime() - fromDate.getTime();
            return (int) (diff / (1000 * 60 * 60 * 24)) + 1;
        } catch (ParseException e) {
            return 1;
        }
    }

    public static String formatDuration(long millis) {
        long minutes = millis / (1000 * 60);
        long hours = minutes / 60;
        long days = hours / 24;
        minutes %= 60;
        hours %= 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + (hours > 0 ? " " + hours + " hr" : "");
        } else {
            return hours + " hr " + minutes + " min";
        }
    }

    // daysSelected: Mon..Sun as [0..6]
    public static String joinSelectedWeekdays(boolean[] daysSelected) {
        if (daysSelected == null || daysSelected.length != 7) return "";
        String[] labels = new String[]{"MON","TUE","WED","THU","FRI","SAT","SUN"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (daysSelected[i]) {
                if (sb.length() > 0) sb.append(",");
                sb.append(labels[i]);
            }
        }
        return sb.toString();
    }
}
