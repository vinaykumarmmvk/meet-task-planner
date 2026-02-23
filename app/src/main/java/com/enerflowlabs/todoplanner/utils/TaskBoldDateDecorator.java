package com.enerflowlabs.todoplanner.utils;

import android.graphics.Typeface;
import android.text.style.StyleSpan;

import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.CalendarDay;

import java.util.HashSet;

public class TaskBoldDateDecorator implements DayViewDecorator {

    private final HashSet<CalendarDay> dates;

    public TaskBoldDateDecorator(HashSet<CalendarDay> datesWithTasks) {
        this.dates = datesWithTasks;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return dates.contains(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        view.addSpan(new StyleSpan(Typeface.BOLD));
    }
}
