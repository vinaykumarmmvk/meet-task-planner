package com.example.taskmanager.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.taskmanager.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Scheduler tab (external tab 1).
 *
 * NOW: hosts internal tabs:
 *  - Prompt (default)
 *  - Manual (existing scheduler form)
 */
public class EnterDurationFragment extends Fragment {

    public EnterDurationFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scheduler_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TabLayout tabLayout = view.findViewById(R.id.tab_scheduler_internal);
        ViewPager2 pager = view.findViewById(R.id.pager_scheduler_internal);

        SchedulerInnerPagerAdapter adapter = new SchedulerInnerPagerAdapter(this);
        pager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, pager, (tab, position) -> {
            if (position == 0) {
                tab.setText(getString(R.string.tab_prompt));
            } else {
                tab.setText(getString(R.string.tab_manual));
            }
        }).attach();

        pager.setCurrentItem(0, false); // Prompt default
    }
}
