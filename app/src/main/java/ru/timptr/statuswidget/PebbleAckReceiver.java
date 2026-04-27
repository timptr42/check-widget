package ru.timptr.statuswidget;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;

public class PebbleAckReceiver extends PebbleKit.PebbleAckReceiver {
    public PebbleAckReceiver() {
        super(PebbleCompanion.PEBBLE_APP_UUID);
    }

    @Override
    public void receiveAck(Context context, int transactionId) {
        PebbleCompanion.recordAck(context, transactionId);
    }
}
