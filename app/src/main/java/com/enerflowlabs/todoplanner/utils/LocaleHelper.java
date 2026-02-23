package com.enerflowlabs.todoplanner.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {
    private static final String PREF = "app_settings";
    private static final String KEY_LANG = "lang_code";

    public static String getSavedLanguage(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getString(KEY_LANG, "en"); // default English
    }

    public static void saveLanguage(Context ctx, String code) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LANG, code).apply();
    }

    public static Context wrap(Context ctx) {
        String code = getSavedLanguage(ctx);
        Locale locale = new Locale(code);
        Locale.setDefault(locale);

        Configuration config = new Configuration(ctx.getResources().getConfiguration());
        config.setLocale(locale);

        return ctx.createConfigurationContext(config);
    }
}
