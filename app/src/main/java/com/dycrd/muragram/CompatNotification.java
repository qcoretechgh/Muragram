package com.dycrd.muragram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.annotation.TargetApi;

public class CompatNotification {
    @TargetApi(26)
    public static void createChannel(NotificationManager nm, String id, Context ctx) {
        String name = ctx.getString(R.string.notifications_channel_messages);
        NotificationChannel channel = new NotificationChannel(id, name, 3);
        nm.createNotificationChannel(channel);
    }

    @TargetApi(11)
    public static Notification build(Context ctx, PendingIntent pi, String channelId, String title, String text, int defaults) {
        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, channelId);
        } else {
            b = new Notification.Builder(ctx);
        }

        b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setDefaults(defaults);

        if (android.os.Build.VERSION.SDK_INT >= 16) {
            return b.build();
        } else {
            return b.getNotification();
        }
    }
}
