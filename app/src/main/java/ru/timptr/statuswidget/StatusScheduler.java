package ru.timptr.statuswidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public final class StatusScheduler {
    private static final long UPDATE_INTERVAL_MS = 60_000L;
    private static final int REQUEST_UPDATE = 42;

    private StatusScheduler() {
    }

    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = createUpdateIntent(context);
        long triggerAt = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(createUpdateIntent(context));
        }
    }

    private static PendingIntent createUpdateIntent(Context context) {
        Intent intent = new Intent(context, StatusWidgetProvider.class)
                .setAction(StatusWidgetProvider.ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, getWidgetIds(context));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_UPDATE, intent, flags);
    }

    private static int[] getWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] regularIds = manager.getAppWidgetIds(new ComponentName(context, StatusWidgetProvider.class));
        int[] compactIds = manager.getAppWidgetIds(new ComponentName(context, StatusWidgetCompactProvider.class));
        int[] allIds = new int[regularIds.length + compactIds.length];
        System.arraycopy(regularIds, 0, allIds, 0, regularIds.length);
        System.arraycopy(compactIds, 0, allIds, regularIds.length, compactIds.length);
        return allIds;
    }
}
