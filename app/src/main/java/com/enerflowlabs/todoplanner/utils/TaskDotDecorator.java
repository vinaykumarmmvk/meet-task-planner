// TaskDotDecorator.java
package com.enerflowlabs.todoplanner.utils;

import android.graphics.Color;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;

import java.util.HashSet;

public class TaskDotDecorator implements DayViewDecorator {

    private final HashSet<CalendarDay> datesWithTasks;

    public TaskDotDecorator(HashSet<CalendarDay> datesWithTasks) {
        this.datesWithTasks = datesWithTasks;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return datesWithTasks.contains(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        view.addSpan(new DotSpan(6, Color.RED)); // 6 = dot size
    }

}
