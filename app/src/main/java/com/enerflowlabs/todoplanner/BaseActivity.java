package com.enerflowlabs.todoplanner;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.enerflowlabs.todoplanner.utils.LocaleHelper;

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }
}
