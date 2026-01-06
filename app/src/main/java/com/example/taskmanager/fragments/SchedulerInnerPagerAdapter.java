package com.example.taskmanager.fragments;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Internal ViewPager adapter for Scheduler tab.
 */
public class SchedulerInnerPagerAdapter extends FragmentStateAdapter {

    public SchedulerInnerPagerAdapter(@NonNull Fragment host) {
        super(host);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new SchedulerPromptFragment();
        return new SchedulerManualFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
