package com.planify.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            PlanStore store = new PlanStore(context);
            String title = intent == null ? "Planify" : intent.getStringExtra("title");
            String message = intent == null ? "" : intent.getStringExtra("message");
            String custom = intent == null ? "" : intent.getStringExtra("sound");
            int request = intent == null ? (int)(System.currentTimeMillis() % 100000) : intent.getIntExtra("request", (int)(System.currentTimeMillis() % 100000));
            Uri sound = null;
            if (store.soundEnabled()) {
                if (custom != null && !custom.isEmpty()) {
                    try { sound = Uri.parse(custom); } catch (Exception ignored) {}
                }
                if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "planify_alarm_" + (sound == null ? "silent" : Integer.toHexString(sound.toString().hashCode())) + "_" + (store.vibrationEnabled() ? "v" : "n");
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(channelId, "Avisos de Planify", NotificationManager.IMPORTANCE_HIGH);
                channel.enableVibration(store.vibrationEnabled());
                if (sound != null) {
                    AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
                    channel.setSound(sound, attrs);
                } else channel.setSound(null, null);
                nm.createNotificationChannel(channel);
            }

            Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, request, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, channelId) : new Notification.Builder(context);
            b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title == null || title.isEmpty() ? "Planify" : title)
                    .setContentText(message == null || message.isEmpty() ? "Aviso programado" : message)
                    .setAutoCancel(true)
                    .setContentIntent(content)
                    .setPriority(Notification.PRIORITY_HIGH);
            if (Build.VERSION.SDK_INT < 26) {
                if (sound != null) b.setSound(sound);
                if (store.vibrationEnabled()) b.setVibrate(new long[]{0, 400, 250, 400});
            }
            if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                nm.notify(request, b.build());
            }
        } catch (Throwable ignored) {
            // A reminder must never be allowed to crash the app process.
        }
    }
}
