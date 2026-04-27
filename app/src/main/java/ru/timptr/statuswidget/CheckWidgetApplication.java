package ru.timptr.statuswidget;

import android.app.Application;

public class CheckWidgetApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PebbleCompanion.registerRuntimeReceivers(this);
    }
}
