package ru.timptr.statuswidget;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

public class PebbleDataReceiver extends PebbleKit.PebbleDataReceiver {
    public PebbleDataReceiver() {
        super(PebbleCompanion.PEBBLE_APP_UUID);
    }

    @Override
    public void receiveData(Context context, int transactionId, PebbleDictionary data) {
        try {
            PebbleKit.sendAckToPebble(context, transactionId);
        } catch (RuntimeException ignored) {
            // Keep receiver safe on devices without a working Pebble service.
        }

        if (!PebbleCompanion.isRefreshRequest(data)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        PebbleCompanion.sendStatus(appContext, StatusRepository.getCached(appContext));
        new Thread(() -> PebbleCompanion.sendStatus(appContext, StatusRepository.refresh(appContext)),
                "pebble-request-refresh").start();
    }
}
