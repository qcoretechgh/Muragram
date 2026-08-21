package com.dycrd.muragram;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public abstract class BaseActivity extends Activity {
    protected boolean isDark;
    protected String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Применяем локаль ДО вызова super.onCreate
        LegacyHelper.applyLocale(this);
        SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
        if (prefs != null) {
            isDark = prefs.getBoolean(NetworkUtils.KEY_DARK_THEME, false);
            setTheme(isDark ? R.style.Theme_Muragram_Dark : R.style.Theme_Muragram);
            token = prefs.getString(NetworkUtils.KEY_TOKEN, null);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // Усиление локализации для старых API и специфичных прошивок
        LegacyHelper.applyLocale(newBase);
        super.attachBaseContext(newBase);
    }
}
