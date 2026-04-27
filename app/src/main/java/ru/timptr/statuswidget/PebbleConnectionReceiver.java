package ru.timptr.statuswidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PebbleConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        boolean connected = "com.getpebble.action.PEBBLE_CONNECTED".equals(action);
        PebbleCompanion.recordConnectionChange(context, connected);
        if (connected) {
            Context appContext = context.getApplicationContext();
            PebbleCompanion.startWatchface(appContext);
            PebbleCompanion.forceSendStatus(appContext, StatusRepository.getCached(appContext));
        }
    }
}
