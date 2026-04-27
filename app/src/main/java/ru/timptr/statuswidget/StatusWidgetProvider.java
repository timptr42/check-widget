package ru.timptr.statuswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StatusWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "ru.timptr.statuswidget.REFRESH";
    private static final int COMPACT_VISIBLE_ROWS = 4;
    private static final int DEFAULT_VISIBLE_ROWS = 8;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        StatusScheduler.schedule(context);
        updateAllWidgets(context, StatusRepository.getCached(context));
        refreshInBackground(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            StatusScheduler.schedule(context);
            refreshInBackground(context);
        }
    }

    @Override
    public void onEnabled(Context context) {
        StatusScheduler.schedule(context);
    }

    @Override
    public void onDisabled(Context context) {
        StatusScheduler.cancel(context);
    }

    static void refreshInBackground(Context context) {
        Thread thread = new Thread(() -> {
            StatusRepository.StatusResult result;
            try {
                result = StatusRepository.refresh(context.getApplicationContext());
            } catch (Exception e) {
                StatusRepository.StatusResult cached = StatusRepository.getCached(context);
                result = new StatusRepository.StatusResult(cached.items, cached.fetchedAt, true,
                        context.getString(R.string.refresh_failed));
            }
            updateAllWidgets(context, result);
            PebbleCompanion.sendStatus(context, result);
            StatusScheduler.schedule(context);
        }, "status-widget-refresh");
        thread.start();
    }

    static void updateAllWidgets(Context context, StatusRepository.StatusResult result) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateProvider(context, manager, StatusWidgetProvider.class, R.layout.widget_status, result, false);
        updateProvider(context, manager, StatusWidgetCompactProvider.class, R.layout.widget_status_compact, result, true);
    }

    private static void updateProvider(Context context, AppWidgetManager manager, Class<?> provider,
                                       int layout, StatusRepository.StatusResult result, boolean compact) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, provider, id, layout, result, compact));
        }
    }

    private static RemoteViews buildViews(Context context, Class<?> provider, int widgetId, int layout,
                                          StatusRepository.StatusResult result, boolean compact) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context));
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, provider, widgetId));
        views.setTextViewText(R.id.widget_title, compact
                ? context.getString(R.string.app_name_short)
                : context.getString(R.string.app_name));
        views.setTextViewText(R.id.widget_updated_at, subtitle(context, result));
        views.removeAllViews(R.id.widget_rows);

        if (!result.hasData()) {
            addEmptyRow(context, views, result.message == null ? context.getString(R.string.no_cached_data) : result.message);
            return views;
        }

        int limit = visibleRowLimit(context, widgetId, compact);
        int usedRows = 0;
        int hiddenRows = 0;
        for (SourceGroup group : groupBySource(result.items)) {
            if (usedRows >= limit) {
                hiddenRows += group.items.size();
                continue;
            }

            int rowsForGroup = Math.min(group.items.size(), limit - usedRows);
            views.addView(R.id.widget_rows, cardViews(context, group, rowsForGroup, compact));
            usedRows += rowsForGroup;
            hiddenRows += group.items.size() - rowsForGroup;
        }
        if (hiddenRows > 0 && usedRows < limit) {
            addEmptyRow(context, views, context.getString(R.string.more_statuses, hiddenRows));
        }
        return views;
    }

    private static RemoteViews cardViews(Context context, SourceGroup group, int rowCount, boolean compact) {
        RemoteViews card = new RemoteViews(context.getPackageName(), R.layout.widget_status_card);
        card.setTextViewText(R.id.card_source, group.source);
        card.removeAllViews(R.id.card_rows);
        for (int i = 0; i < rowCount; i++) {
            card.addView(R.id.card_rows, rowViews(context, group.items.get(i), compact));
        }
        return card;
    }

    private static RemoteViews rowViews(Context context, StatusItem item, boolean compact) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_status_row);
        row.setTextViewText(R.id.row_dot, "●");
        row.setTextColor(R.id.row_dot, statusColor(item.status));
        row.setTextViewText(R.id.row_title, item.indicatorTitle());
        row.setTextViewText(R.id.row_time, compact
                ? StatusRepository.formatIso(context, item.updatedAt)
                : context.getString(R.string.row_status_time, item.status, StatusRepository.formatIso(context, item.updatedAt)));
        return row;
    }

    private static void addEmptyRow(Context context, RemoteViews views, String text) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_status_row);
        row.setViewVisibility(R.id.row_dot, View.GONE);
        row.setTextViewText(R.id.row_title, text);
        row.setTextViewText(R.id.row_time, "");
        views.addView(R.id.widget_rows, row);
    }

    private static int visibleRowLimit(Context context, int widgetId, boolean compact) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        if (minHeight <= 0) {
            return compact ? COMPACT_VISIBLE_ROWS : DEFAULT_VISIBLE_ROWS;
        }

        int reservedHeight = compact ? 34 : 58;
        int rowHeight = 21;
        return Math.max(1, (minHeight - reservedHeight) / rowHeight);
    }

    private static List<SourceGroup> groupBySource(List<StatusItem> items) {
        Map<String, SourceGroup> groups = new LinkedHashMap<>();
        for (StatusItem item : items) {
            String source = item.sourceTitle();
            SourceGroup group = groups.get(source);
            if (group == null) {
                group = new SourceGroup(source);
                groups.put(source, group);
            }
            group.items.add(item);
        }
        return new ArrayList<>(groups.values());
    }

    private static String subtitle(Context context, StatusRepository.StatusResult result) {
        if (result.fetchedAt <= 0L) {
            return context.getString(R.string.not_loaded_yet);
        }
        String prefix = result.fromCache ? context.getString(R.string.cached_at) : context.getString(R.string.loaded_at);
        return prefix + " " + StatusRepository.formatEpoch(context, result.fetchedAt);
    }

    private static int statusColor(String status) {
        if ("green".equalsIgnoreCase(status)) {
            return Color.rgb(35, 167, 85);
        }
        if ("yellow".equalsIgnoreCase(status)) {
            return Color.rgb(245, 174, 31);
        }
        if ("red".equalsIgnoreCase(status)) {
            return Color.rgb(220, 53, 69);
        }
        return Color.rgb(108, 117, 125);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent, pendingFlags());
    }

    private static PendingIntent refreshIntent(Context context, Class<?> provider, int widgetId) {
        Intent intent = new Intent(context, provider);
        intent.setAction(ACTION_REFRESH);
        intent.setData(Uri.parse("status-widget://refresh/" + provider.getSimpleName() + "/" + widgetId));
        return PendingIntent.getBroadcast(context, widgetId, intent, pendingFlags());
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private static final class SourceGroup {
        final String source;
        final List<StatusItem> items = new ArrayList<>();

        SourceGroup(String source) {
            this.source = source;
        }
    }
}
