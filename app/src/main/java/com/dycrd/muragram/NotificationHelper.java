package com.dycrd.muragram;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

public class NotificationHelper {
    
    public static void createChannel(NotificationManager nm, String channelId, Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            CompatNotification.createChannel(nm, channelId, ctx);
        }
    }

    public static Notification buildNotification(Context ctx, PendingIntent pi, String channelId, String title, String text, int defaults) {
        if (Build.VERSION.SDK_INT >= 11) {
            return CompatNotification.build(ctx, pi, channelId, title, text, defaults);
        } else {
            Notification n = new Notification(android.R.drawable.stat_notify_chat, text, System.currentTimeMillis());
            try {
                java.lang.reflect.Method m = n.getClass().getMethod("setLatestEventInfo", 
                    Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                m.invoke(n, ctx, title, text, pi);
            } catch (Exception e) {}
            n.flags |= Notification.FLAG_AUTO_CANCEL;
            n.defaults = defaults;
            return n;
        }
    }
}
