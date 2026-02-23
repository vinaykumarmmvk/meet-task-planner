package com.enerflowlabs.todoplanner;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.enerflowlabs.todoplanner.fragments.CalendarFragment;
import com.enerflowlabs.todoplanner.fragments.SchedulerManualFragment;
import com.enerflowlabs.todoplanner.fragments.TasksFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new SchedulerManualFragment();
            case 1: return new CalendarFragment();
            case 2: return new TasksFragment();
            default: return new SchedulerManualFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
