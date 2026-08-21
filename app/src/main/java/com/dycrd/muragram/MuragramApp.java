package com.dycrd.muragram;

import android.app.Application;
import android.content.res.Configuration;

public class MuragramApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Глобальная инициализация локали при старте приложения
        LegacyHelper.applyLocale(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Повторное применение при смене конфигурации (повороты и т.д.)
        LegacyHelper.applyLocale(this);
    }
}
