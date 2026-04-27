package ru.timptr.statuswidget;

import android.content.Context;
import android.content.SharedPreferences;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;

final class PebbleCompanion {
    static final UUID PEBBLE_APP_UUID = UUID.fromString("62391359-3e79-487e-b011-c5583372b08f");
    static final String ACTION_STATE_CHANGED = "ru.timptr.statuswidget.PEBBLE_STATE_CHANGED";

    private static final int KEY_STATUS_LINE = 0;
    private static final int KEY_UPDATED_AT = 1;
    private static final int KEY_STATUS_COUNT = 2;
    private static final int KEY_REQUEST = 3;
    private static final int KEY_LABELS = 4;
    private static final int MIN_SEND_INTERVAL_MS = 15_000;
    private static final String PREFS = "pebble_state";
    private static final String KEY_LAST_LINE = "last_line";
    private static final String KEY_LAST_ATTEMPT_AT = "last_attempt_at";
    private static final String KEY_LAST_ACK_AT = "last_ack_at";
    private static final String KEY_LAST_REQUEST_AT = "last_request_at";
    private static final String KEY_LAST_TRANSACTION_ID = "last_transaction_id";
    private static final String KEY_LAST_MESSAGE = "last_message";
    private static final String KEY_NEXT_TRANSACTION_ID = "next_transaction_id";
    private static boolean liveHandlersRegistered;

    private PebbleCompanion() {
    }

    static SendResult sendStatus(Context context, StatusRepository.StatusResult result) {
        return sendStatus(context, result, false);
    }

    static SendResult forceSendStatus(Context context, StatusRepository.StatusResult result) {
        return sendStatus(context, result, true);
    }

    static PebbleState getState(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean connected;
        boolean appMessages;
        String connectionError = null;
        try {
            connected = PebbleKit.isWatchConnected(appContext);
            appMessages = PebbleKit.areAppMessagesSupported(appContext);
        } catch (RuntimeException exception) {
            connected = false;
            appMessages = false;
            connectionError = safeMessage(exception);
        }

        return new PebbleState(
                connected,
                appMessages,
                prefs.getString(KEY_LAST_LINE, ""),
                prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L),
                prefs.getLong(KEY_LAST_ACK_AT, 0L),
                prefs.getLong(KEY_LAST_REQUEST_AT, 0L),
                prefs.getInt(KEY_LAST_TRANSACTION_ID, -1),
                prefs.getString(KEY_LAST_MESSAGE, connectionError)
        );
    }

    static String statusText(Context context) {
        PebbleState state = getState(context);
        StringBuilder builder = new StringBuilder();
        builder.append("Pebble: ");
        if (!state.connected) {
            builder.append("не подтверждены PebbleKit");
        } else if (!state.appMessagesSupported) {
            builder.append("подключены, AppMessage недоступен");
        } else {
            builder.append("готовы");
        }

        if (state.lastAttemptAt > 0L) {
            builder.append("\nОтправка: ")
                    .append(StatusRepository.formatEpoch(context, state.lastAttemptAt));
            if (state.lastTransactionId >= 0) {
                builder.append(" #").append(state.lastTransactionId);
            }
        }
        if (state.lastAckAt > 0L) {
            builder.append("\nACK: ")
                    .append(StatusRepository.formatEpoch(context, state.lastAckAt));
        }
        if (state.lastRequestAt > 0L) {
            builder.append("\nЗапрос часов: ")
                    .append(StatusRepository.formatEpoch(context, state.lastRequestAt));
        }
        if (state.lastLine != null && !state.lastLine.isEmpty()) {
            builder.append("\nПоследнее: ").append(formatMarkers(state.lastLine));
        }
        if (state.lastMessage != null && !state.lastMessage.isEmpty()) {
            builder.append("\n").append(state.lastMessage);
        }
        return builder.toString();
    }

    static void startWatchface(Context context) {
        try {
            PebbleKit.startAppOnPebble(context.getApplicationContext(), PEBBLE_APP_UUID);
            saveMessage(context, "Команда запуска watchface отправлена");
        } catch (RuntimeException exception) {
            saveMessage(context, "Ошибка запуска Pebble: " + safeMessage(exception));
        }
    }

    static void registerRuntimeReceivers(Context context) {
        if (liveHandlersRegistered || context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        try {
            PebbleKit.registerReceivedDataHandler(appContext, new PebbleDataReceiver());
            PebbleKit.registerReceivedAckHandler(appContext, new PebbleAckReceiver());
            PebbleKit.registerReceivedNackHandler(appContext, new PebbleNackReceiver());
            PebbleKit.registerPebbleConnectedReceiver(appContext, new PebbleConnectionReceiver());
            PebbleKit.registerPebbleDisconnectedReceiver(appContext, new PebbleConnectionReceiver());
            liveHandlersRegistered = true;
            saveMessage(appContext, "Pebble runtime receivers активны");
        } catch (RuntimeException exception) {
            saveMessage(appContext, "Ошибка runtime receivers: " + safeMessage(exception));
        }
    }

    static void markManualAttempt(Context context) {
        saveMessage(context, "Ручная отправка Pebble запрошена");
    }

    static void recordRequest(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_REQUEST_AT, System.currentTimeMillis())
                .putString(KEY_LAST_MESSAGE, "Часы запросили обновление")
                .apply();
        notifyStateChanged(context);
    }

    static void recordAck(Context context, int transactionId) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_ACK_AT, System.currentTimeMillis())
                .putInt(KEY_LAST_TRANSACTION_ID, transactionId)
                .putString(KEY_LAST_MESSAGE, "ACK от Pebble #" + transactionId)
                .apply();
        notifyStateChanged(context);
    }

    static void recordNack(Context context, int transactionId) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_TRANSACTION_ID, transactionId)
                .putString(KEY_LAST_MESSAGE, "NACK от Pebble #" + transactionId)
                .apply();
        notifyStateChanged(context);
    }

    static void recordConnectionChange(Context context, boolean connected) {
        saveMessage(context, connected ? "Pebble подключены" : "Pebble отключены");
        if (connected) {
            startWatchface(context);
            forceSendStatus(context, StatusRepository.getCached(context));
        }
    }

    static boolean isRefreshRequest(PebbleDictionary data) {
        if (data == null) {
            return false;
        }
        String request = data.getString(KEY_REQUEST);
        return "refresh".equals(request) || "hello".equals(request);
    }

    private static SendResult sendStatus(Context context, StatusRepository.StatusResult result, boolean force) {
        if (context == null) {
            return SendResult.error("Нет context");
        }
        if (result == null || (!result.hasData() && result.fetchedAt <= 0L)) {
            saveMessage(context, "Нет данных для отправки на Pebble");
            return SendResult.error("Нет данных");
        }

        Context appContext = context.getApplicationContext();
        PebbleState state = getState(appContext);

        String statusLine = buildStatusLine(result);
        long now = System.currentTimeMillis();
        if (!force && statusLine.equals(state.lastLine) && now - state.lastAttemptAt < MIN_SEND_INTERVAL_MS) {
            return SendResult.skipped("Без изменений");
        }

        int transactionId = nextTransactionId(appContext);
        PebbleDictionary dict = new PebbleDictionary();
        dict.addString(KEY_STATUS_LINE, statusLine);
        dict.addInt32(KEY_UPDATED_AT, (int) (result.fetchedAt / 1000L));
        dict.addInt32(KEY_STATUS_COUNT, result.items.size());
        dict.addString(KEY_LABELS, buildLabelLine(result));

        try {
            PebbleKit.sendDataToPebbleWithTransactionId(appContext, PEBBLE_APP_UUID, dict, transactionId);
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_LINE, statusLine)
                    .putLong(KEY_LAST_ATTEMPT_AT, now)
                    .putInt(KEY_LAST_TRANSACTION_ID, transactionId)
                    .putString(KEY_LAST_MESSAGE, state.connected
                            ? "Отправлено на Pebble #" + transactionId
                            : "Отправлено без подтверждения PebbleKit #" + transactionId)
                    .apply();
            notifyStateChanged(appContext);
            return SendResult.sent(transactionId);
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            saveMessage(appContext, "Ошибка отправки Pebble: " + message);
            return SendResult.error(message);
        }
    }

    private static int nextTransactionId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int next = (prefs.getInt(KEY_NEXT_TRANSACTION_ID, 0) + 1) & 0xFF;
        prefs.edit().putInt(KEY_NEXT_TRANSACTION_ID, next).apply();
        return next;
    }

    private static String buildStatusLine(StatusRepository.StatusResult result) {
        StringBuilder builder = new StringBuilder(result.items.size());
        for (StatusItem item : result.items) {
            builder.append(item.pebbleSymbol());
        }
        return builder.toString();
    }

    private static String buildLabelLine(StatusRepository.StatusResult result) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < result.items.size(); i++) {
            if (i > 0) {
                builder.append('|');
            }
            StatusItem item = result.items.get(i);
            builder.append(cleanLabelPart(item.sourceTitle()))
                    .append(" - ")
                    .append(cleanLabelPart(item.indicatorTitle()));
        }
        return builder.toString();
    }

    private static String cleanLabelPart(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '/').trim();
    }

    private static String formatMarkers(String line) {
        StringBuilder builder = new StringBuilder(line.length() * 3);
        for (int i = 0; i < line.length(); i++) {
            builder.append('[').append(line.charAt(i)).append(']');
        }
        return builder.toString();
    }

    private static void saveMessage(Context context, String message) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_MESSAGE, message)
                .apply();
        notifyStateChanged(context);
    }

    private static void notifyStateChanged(Context context) {
        context.getApplicationContext().sendBroadcast(new android.content.Intent(ACTION_STATE_CHANGED));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    static final class PebbleState {
        final boolean connected;
        final boolean appMessagesSupported;
        final String lastLine;
        final long lastAttemptAt;
        final long lastAckAt;
        final long lastRequestAt;
        final int lastTransactionId;
        final String lastMessage;

        PebbleState(boolean connected, boolean appMessagesSupported, String lastLine,
                    long lastAttemptAt, long lastAckAt, long lastRequestAt,
                    int lastTransactionId, String lastMessage) {
            this.connected = connected;
            this.appMessagesSupported = appMessagesSupported;
            this.lastLine = lastLine;
            this.lastAttemptAt = lastAttemptAt;
            this.lastAckAt = lastAckAt;
            this.lastRequestAt = lastRequestAt;
            this.lastTransactionId = lastTransactionId;
            this.lastMessage = lastMessage;
        }
    }

    static final class SendResult {
        final boolean sent;
        final boolean skipped;
        final int transactionId;
        final String message;

        private SendResult(boolean sent, boolean skipped, int transactionId, String message) {
            this.sent = sent;
            this.skipped = skipped;
            this.transactionId = transactionId;
            this.message = message;
        }

        static SendResult sent(int transactionId) {
            return new SendResult(true, false, transactionId, "sent");
        }

        static SendResult skipped(String message) {
            return new SendResult(false, true, -1, message);
        }

        static SendResult error(String message) {
            return new SendResult(false, false, -1, message);
        }
    }
}
