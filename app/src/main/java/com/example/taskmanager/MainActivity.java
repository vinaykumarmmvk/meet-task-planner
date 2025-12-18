package com.example.taskmanager;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.taskmanager.fragments.CalendarFragment;
import com.example.taskmanager.fragments.EnterDurationFragment;
import com.example.taskmanager.fragments.TasksFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;


public class MainActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MainPagerAdapter pagerAdapter;
    private static final int REQ_NOTIF_PERMISSION = 1001;

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    "android.permission.POST_NOTIFICATIONS"
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        REQ_NOTIF_PERMISSION
                );
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_main);

        ensureNotificationPermission();
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            View custom = getLayoutInflater().inflate(R.layout.tab_icon_pill, null);
            ImageView icon = custom.findViewById(R.id.tab_icon);

            if (position == 0) icon.setImageResource(R.drawable.ic_tab_timer);
            else if (position == 1) icon.setImageResource(R.drawable.ic_tab_calendar);
            else icon.setImageResource(R.drawable.ic_tab_tasks);

            tab.setCustomView(custom);
        }).attach();

        // initial highlight
        updateSelectedTabPill(tabLayout, tabLayout.getSelectedTabPosition());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                updateSelectedTabPill(tabLayout, tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {
                updateSelectedTabPill(tabLayout, tabLayout.getSelectedTabPosition());
            }
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });


        int openTab = getIntent().getIntExtra("open_tab", -1);
        if (openTab >= 0 && openTab < 3) {
            viewPager.setCurrentItem(openTab, false);
        }

    }

    private void updateSelectedTabPill(TabLayout tabLayout, int selectedPos) {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab t = tabLayout.getTabAt(i);
            if (t == null || t.getCustomView() == null) continue;

            View container = t.getCustomView().findViewById(R.id.tab_container);
            if (container == null) continue;

            if (i == selectedPos) {
                container.setBackgroundResource(R.drawable.bg_tab_selected_oval);
            } else {
                container.setBackground(null);
            }
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int openTab = intent.getIntExtra("open_tab", -1);
        if (openTab >= 0 && openTab < 3 && viewPager != null) {
            viewPager.setCurrentItem(openTab, false);
        }
    }

}