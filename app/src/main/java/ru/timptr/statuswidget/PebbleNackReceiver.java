package ru.timptr.statuswidget;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;

public class PebbleNackReceiver extends PebbleKit.PebbleNackReceiver {
    public PebbleNackReceiver() {
        super(PebbleCompanion.PEBBLE_APP_UUID);
    }

    @Override
    public void receiveNack(Context context, int transactionId) {
        PebbleCompanion.recordNack(context, transactionId);
    }
}
