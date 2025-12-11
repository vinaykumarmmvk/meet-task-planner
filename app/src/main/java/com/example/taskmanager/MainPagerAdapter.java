package com.example.taskmanager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.taskmanager.fragments.CalendarFragment;
import com.example.taskmanager.fragments.EnterDurationFragment;
import com.example.taskmanager.fragments.TasksFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new EnterDurationFragment();
            case 1: return new CalendarFragment();
            case 2: return new TasksFragment();
            default: return new EnterDurationFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
