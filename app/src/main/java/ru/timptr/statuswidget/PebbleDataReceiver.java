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

        PebbleCompanion.recordRequest(context);
        Context appContext = context.getApplicationContext();
        PebbleCompanion.forceSendStatus(appContext, StatusRepository.getCached(appContext));
        new Thread(() -> PebbleCompanion.forceSendStatus(appContext, StatusRepository.refresh(appContext)),
                "pebble-request-refresh").start();
    }

    public static class AckReceiver extends PebbleKit.PebbleAckReceiver {
        public AckReceiver() {
            super(PebbleCompanion.PEBBLE_APP_UUID);
        }

        @Override
        public void receiveAck(Context context, int transactionId) {
            PebbleCompanion.recordAck(context, transactionId);
        }
    }

    public static class NackReceiver extends PebbleKit.PebbleNackReceiver {
        public NackReceiver() {
            super(PebbleCompanion.PEBBLE_APP_UUID);
        }

        @Override
        public void receiveNack(Context context, int transactionId) {
            PebbleCompanion.recordNack(context, transactionId);
        }
    }

    public static class ConnectionReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            PebbleCompanion.recordConnectionChange(
                    context,
                    intent != null && "com.getpebble.action.PEBBLE_CONNECTED".equals(intent.getAction())
            );
            if (intent != null && "com.getpebble.action.PEBBLE_CONNECTED".equals(intent.getAction())) {
                PebbleCompanion.startWatchface(context);
                PebbleCompanion.forceSendStatus(context, StatusRepository.getCached(context));
            }
        }
    }
}
