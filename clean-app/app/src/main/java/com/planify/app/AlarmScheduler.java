package com.planify.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    public static void scheduleItem(Context context, PlanItem item, PlanStore store) {
        if (context == null || item == null || !item.notify) return;
        try {
            LocalDate date = LocalDate.parse(item.date);
            LocalTime time = LocalTime.parse(item.time == null || item.time.isEmpty() ? "09:00" : item.time);
            long when = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (when <= System.currentTimeMillis()) return;
            schedule(context, requestCode(item.id), when, item.title, item.description, store == null ? null : store.getSoundUri(), item.id);
        } catch (Exception ignored) {}
    }

    public static void scheduleTimer(Context context, int request, long when, String title, String message, String soundUri) {
        schedule(context, request, when, title, message, soundUri, -1L);
    }

    private static void schedule(Context context, int request, long when, String title, String message, String soundUri, long itemId) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(context, AlarmReceiver.class)
                    .putExtra("title", title == null || title.isEmpty() ? "Planify" : title)
                    .putExtra("message", message == null ? "" : message)
                    .putExtra("sound", soundUri == null ? "" : soundUri)
                    .putExtra("item_id", itemId)
                    .putExtra("request", request);
            PendingIntent pi = PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException ignored) {
        } catch (Exception ignored) {}
    }

    public static void cancelItem(Context context, long id) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(id), intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (am != null && pi != null) { am.cancel(pi); pi.cancel(); }
        } catch (Exception ignored) {}
    }

    private static int requestCode(long id) {
        long safe = Math.abs(id % 900000L);
        return (int) safe + 1000;
    }
}
