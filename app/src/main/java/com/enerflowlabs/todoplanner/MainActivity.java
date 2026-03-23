package com.enerflowlabs.todoplanner;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.enerflowlabs.todoplanner.adapters.LanguageAdapter;
import com.enerflowlabs.todoplanner.utils.LocaleHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends BaseActivity {

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
            View custom = getLayoutInflater().inflate(R.layout.tab_icon_text_pill, null);

            ImageView icon = custom.findViewById(R.id.tab_icon);
            TextView label = custom.findViewById(R.id.tab_label);

            if (position == 0) {
                icon.setImageResource(R.drawable.ic_tab_timer);
                label.setText(getString(R.string.tab_scheduler));
            } else if (position == 1) {
                icon.setImageResource(R.drawable.ic_tab_calendar);
                label.setText(getString(R.string.tab_calendar));
            } else {
                icon.setImageResource(R.drawable.ic_tab_tasks);
                label.setText(getString(R.string.tab_todo));
            }

            tab.setCustomView(custom);
        }).attach();

        // Apply initial selected state
        updateTabUi(tabLayout, tabLayout.getSelectedTabPosition());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateTabUi(tabLayout, tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                updateTabUi(tabLayout, tabLayout.getSelectedTabPosition());
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });


        int openTab = getIntent().getIntExtra("open_tab", -1);
        if (openTab >= 0 && openTab < 3) {
            viewPager.setCurrentItem(openTab, false);
        }

    }

    private void updateTabUi(TabLayout tabLayout, int selectedPos) {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab t = tabLayout.getTabAt(i);
            if (t == null || t.getCustomView() == null) continue;

            View container = t.getCustomView().findViewById(R.id.tab_container);
            ImageView icon = t.getCustomView().findViewById(R.id.tab_icon);
            TextView label = t.getCustomView().findViewById(R.id.tab_label);

            boolean selected = (i == selectedPos);

            // Light-blue oval background on selected
            if (selected) container.setBackgroundResource(R.drawable.bg_tab_selected_oval);
            else container.setBackground(null);

            // Bold label on selected
            label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            // Optional: color change
            label.setTextColor(selected ? 0xFF000000 : 0xFF7A7A7A);

            // Optional: icon tint change (if you want)
            // icon.setColorFilter(selected ? 0xFF000000 : 0xFF7A7A7A);
            icon.clearColorFilter(); // keep original icon if you already tint via tab_icon_tint
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        int id = item.getItemId();
        /*if (id == R.id.action_settings) {
            // open Settings screen or dialog
            return true;
        }*/ if (id == R.id.action_about) {

            LayoutInflater inflater = LayoutInflater.from(this);
            View dialogView = inflater.inflate(R.layout.dialog_about_app, null);
            TextView versionText = dialogView.findViewById(R.id.versionNumber);

            try {
                String versionName = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;

                versionText.setText("v" + versionName);

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            AlertDialog aboutAppDialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

// Transparent background so our rounded card shows properly
            if (aboutAppDialog.getWindow() != null) {
                aboutAppDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            // Close btn
            Button btnClose = dialogView.findViewById(R.id.btnDialogClose);
            btnClose.setOnClickListener(v -> aboutAppDialog.dismiss());

            aboutAppDialog.show();
            return true;
        } else if (id == R.id.action_developer) {

            LayoutInflater inflater = LayoutInflater.from(this);
            View dialogView = inflater.inflate(R.layout.dialog_about_developer, null);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

// Transparent background so our rounded card shows properly
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

// Close btn
            Button btnClose = dialogView.findViewById(R.id.btnDialogClose);
            btnClose.setOnClickListener(v -> dialog.dismiss());

// Contact actions
            LinearLayout emailChip = dialogView.findViewById(R.id.btnEmail);
            LinearLayout linkedinChip = dialogView.findViewById(R.id.btnLinkedIn);

// open email app
            emailChip.setOnClickListener(v -> {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:mmvinaykumar.mm@gmail.com"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Screen Timer feedback");
                startActivity(Intent.createChooser(emailIntent, "Send email"));
            });

// open LinkedIn profile
            linkedinChip.setOnClickListener(v -> {
                Intent browserIntent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.linkedin.com/in/vinaykumar-mysuru-manjunath-33b522ba/")
                );
                startActivity(browserIntent);
            });

            dialog.show();

            return true;
        }else if (item.getItemId() == R.id.action_language) {
            showLanguageDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    private void showLanguageDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_language_picker, null);

        EditText search = view.findViewById(R.id.edit_search_language);
        RecyclerView rv = view.findViewById(R.id.rv_languages);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<LanguageAdapter.LangItem> languages = new ArrayList<>();
        languages.add(new LanguageAdapter.LangItem("en", "English"));
        languages.add(new LanguageAdapter.LangItem("de", "German"));
        languages.add(new LanguageAdapter.LangItem("fr", "French"));

        languages.add(new LanguageAdapter.LangItem("es", "Spanish"));
        languages.add(new LanguageAdapter.LangItem("it", "Italian"));
        languages.add(new LanguageAdapter.LangItem("pt-BR", "Portuguese (Brazil)"));

        languages.add(new LanguageAdapter.LangItem("tr", "Turkish"));
        languages.add(new LanguageAdapter.LangItem("pl", "Polish"));
        languages.add(new LanguageAdapter.LangItem("ro", "Romanian"));
        languages.add(new LanguageAdapter.LangItem("uk", "Ukrainian"));
        languages.add(new LanguageAdapter.LangItem("cs", "Czech"));
        languages.add(new LanguageAdapter.LangItem("hu", "Hungarian"));
        languages.add(new LanguageAdapter.LangItem("el", "Greek"));

        languages.add(new LanguageAdapter.LangItem("hi", "Hindi"));
        languages.add(new LanguageAdapter.LangItem("kn", "Kannada"));
        languages.add(new LanguageAdapter.LangItem("ta", "Tamil"));
        languages.add(new LanguageAdapter.LangItem("te", "Telugu"));
        languages.add(new LanguageAdapter.LangItem("ml", "Malayalam"));
        languages.add(new LanguageAdapter.LangItem("mr", "Marathi"));
        languages.add(new LanguageAdapter.LangItem("bn", "Bengali"));
        languages.add(new LanguageAdapter.LangItem("gu", "Gujarati"));
        languages.add(new LanguageAdapter.LangItem("pa", "Punjabi"));

        // add more whenever you want

        String current = LocaleHelper.getSavedLanguage(this);

        LanguageAdapter adapter = new LanguageAdapter(languages, current);
        rv.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_language))
                .setView(view)
                .setPositiveButton(getString(R.string.confirm), null) // override later
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String code = adapter.getSelectedCode();
                if (code == null || code.trim().isEmpty()) return;

                LocaleHelper.saveLanguage(this, code);

                // Restart MainActivity cleanly so tabs/fragments reload strings
                recreate();

                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
                dialog.dismiss();
            });
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
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