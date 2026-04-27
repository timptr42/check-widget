package ru.timptr.statuswidget;

import java.time.Instant;
import java.time.format.DateTimeParseException;

final class StatusItem {
    final int id;
    final String source;
    final String indicator;
    final String status;
    final String note;
    final String updatedAt;
    final String receivedAt;

    StatusItem(int id, String source, String indicator, String status, String note, String updatedAt, String receivedAt) {
        this.id = id;
        this.source = source;
        this.indicator = indicator;
        this.status = status;
        this.note = note;
        this.updatedAt = updatedAt;
        this.receivedAt = receivedAt;
    }

    String title() {
        if (source == null || source.isEmpty()) {
            return indicator == null ? "" : indicator;
        }
        return source + " / " + (indicator == null ? "" : indicator);
    }

    String sourceTitle() {
        return source == null || source.trim().isEmpty() ? "Источник" : source;
    }

    String indicatorTitle() {
        return indicator == null || indicator.trim().isEmpty() ? "Индикатор" : indicator;
    }

    Instant updatedInstant() {
        return parseInstant(updatedAt);
    }

    Instant receivedInstant() {
        return parseInstant(receivedAt);
    }

    int dotDrawable() {
        switch (status == null ? "" : status.toLowerCase()) {
            case "green":
                return R.drawable.status_dot_green;
            case "yellow":
                return R.drawable.status_dot_yellow;
            case "red":
                return R.drawable.status_dot_red;
            default:
                return R.drawable.status_dot_unknown;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
