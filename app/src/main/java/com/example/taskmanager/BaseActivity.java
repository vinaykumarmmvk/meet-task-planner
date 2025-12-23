package com.example.taskmanager;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.example.taskmanager.utils.LocaleHelper;

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }
}
