package ru.timptr.statuswidget;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;

final class PebbleCompanion {
    static final UUID PEBBLE_APP_UUID = UUID.fromString("62391359-3e79-487e-b011-c5583372b08f");

    private static final int KEY_STATUS_LINE = 0;
    private static final int KEY_UPDATED_AT = 1;
    private static final int KEY_STATUS_COUNT = 2;
    private static final int KEY_REQUEST = 3;
    private static long lastSentAt;
    private static String lastSentLine = "";

    private PebbleCompanion() {
    }

    static void sendStatus(Context context, StatusRepository.StatusResult result) {
        if (context == null || result == null || !result.hasData()) {
            return;
        }

        Context appContext = context.getApplicationContext();
        try {
            if (!PebbleKit.isWatchConnected(appContext)) {
                return;
            }
        } catch (RuntimeException exception) {
            return;
        }

        String statusLine = buildStatusLine(result);
        long now = System.currentTimeMillis();
        if (statusLine.equals(lastSentLine) && now - lastSentAt < 15_000L) {
            return;
        }

        PebbleDictionary dict = new PebbleDictionary();
        dict.addString(KEY_STATUS_LINE, statusLine);
        dict.addInt32(KEY_UPDATED_AT, (int) (result.fetchedAt / 1000L));
        dict.addInt32(KEY_STATUS_COUNT, result.items.size());

        try {
            PebbleKit.sendDataToPebble(appContext, PEBBLE_APP_UUID, dict);
            lastSentLine = statusLine;
            lastSentAt = now;
        } catch (RuntimeException ignored) {
            // PebbleKit 4.x can throw on newer Android builds when Pebble is absent or restricted.
        }
    }

    private static String buildStatusLine(StatusRepository.StatusResult result) {
        StringBuilder builder = new StringBuilder(result.items.size());
        for (StatusItem item : result.items) {
            builder.append(item.pebbleSymbol());
        }
        return builder.toString();
    }

    static boolean isRefreshRequest(PebbleDictionary data) {
        if (data == null) {
            return false;
        }
        String request = data.getString(KEY_REQUEST);
        return "refresh".equals(request);
    }
}
