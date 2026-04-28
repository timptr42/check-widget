package ru.timptr.statuswidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class StatusRepository {
    static final String API_URL = "https://check.timptr.ru/api/statuses/current";

    private static final String PREFS = "status_cache";
    private static final String KEY_JSON = "json";
    private static final String KEY_FETCHED_AT = "fetched_at";

    private StatusRepository() {
    }

    static StatusResult refresh(Context context) {
        try {
            String json = fetchJson();
            long fetchedAt = System.currentTimeMillis();
            save(context, json, fetchedAt);
            return parse(json, fetchedAt, false, context.getString(R.string.updated));
        } catch (Exception exception) {
            StatusResult cached = getCached(context);
            return new StatusResult(
                    cached.items,
                    cached.fetchedAt,
                    true,
                    context.getString(R.string.refresh_error_short, safeMessage(exception))
            );
        }
    }

    static StatusResult getCached(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_JSON, null);
        long fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L);
        if (json == null) {
            return new StatusResult(new ArrayList<>(), 0L, true, context.getString(R.string.no_cached_data));
        }

        try {
            return parse(json, fetchedAt, true, context.getString(R.string.cached));
        } catch (JSONException exception) {
            return new StatusResult(new ArrayList<>(), fetchedAt, true, context.getString(R.string.cache_error));
        }
    }

    static String formatEpoch(Context context, long epochMillis) {
        if (epochMillis <= 0L) {
            return context.getString(R.string.never);
        }
        return "[" + formatAge(epochMillis) + "] " + formatAbsolute(epochMillis);
    }

    static String formatMillis(long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        return "[" + formatAge(epochMillis) + "] " + formatAbsolute(epochMillis);
    }

    static String formatIso(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        try {
            return formatMillis(Instant.parse(value).toEpochMilli());
        } catch (DateTimeParseException exception) {
            return value;
        }
    }

    static String formatIso(Context context, String value) {
        String formatted = formatIso(value);
        return formatted.isEmpty() ? context.getString(R.string.never) : formatted;
    }

    private static String formatAbsolute(long epochMillis) {
        return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date(epochMillis));
    }

    private static String formatAge(long epochMillis) {
        long diffSeconds = Math.max(0L, (System.currentTimeMillis() - epochMillis) / 1000L);
        if (diffSeconds < 60L) {
            return diffSeconds + "сек";
        }
        long diffMinutes = diffSeconds / 60L;
        if (diffMinutes < 60L) {
            return diffMinutes + "мин";
        }
        long diffHours = diffMinutes / 60L;
        if (diffHours < 24L) {
            return diffHours + "ч";
        }
        return (diffHours / 24L) + "д";
    }

    private static String fetchJson() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readFully(stream);
        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }
        return body;
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static void save(Context context, String json, long fetchedAt) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, json)
                .putLong(KEY_FETCHED_AT, fetchedAt)
                .apply();
    }

    private static StatusResult parse(String json, long fetchedAt, boolean fromCache, String message)
            throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("items");
        List<StatusItem> statuses = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                statuses.add(new StatusItem(
                        item.optInt("id"),
                        item.optString("source"),
                        item.optString("indicator"),
                        item.optString("status"),
                        item.optString("note"),
                        item.optString("updated_at"),
                        item.optString("received_at")
                ));
            }
        }
        return new StatusResult(statuses, fetchedAt, fromCache, message);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    static final class StatusResult {
        final List<StatusItem> items;
        final long fetchedAt;
        final boolean fromCache;
        final String message;

        StatusResult(List<StatusItem> items, long fetchedAt, boolean fromCache, String message) {
            this.items = items;
            this.fetchedAt = fetchedAt;
            this.fromCache = fromCache;
            this.message = message;
        }

        boolean hasData() {
            return !items.isEmpty();
        }
    }
}
