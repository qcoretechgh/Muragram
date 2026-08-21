package com.dycrd.muragram;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class NotificationService extends Service {
    private static final String TAG = "NotificationService";
    private Handler handler = new Handler();
    private String token;
    private NotificationManager nm;
    private long lastCheckTime = 0;

    private Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                MuragramLogger.log(TAG, "Polling new messages...");
                checkNewMessages();
            } catch (Exception e) {
                MuragramLogger.log(TAG, "Error in poll: " + e.getMessage());
            }
            
            SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, MODE_PRIVATE);
            int delaySeconds = (prefs != null) ? prefs.getInt(NetworkUtils.KEY_NOTIFY_DELAY, 15) : 15;
            if (delaySeconds < 5) delaySeconds = 5;
            
            handler.postDelayed(this, delaySeconds * 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NetworkUtils.updateServer(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, MODE_PRIVATE);
        token = (prefs != null) ? prefs.getString(NetworkUtils.KEY_TOKEN, null) : null;
        
        lastCheckTime = (prefs != null) ? prefs.getLong("last_check_time", 0) : 0;
        
        if (token != null) {
            handler.postDelayed(pollRunnable, 2000);
            MuragramLogger.log(TAG, "Service started polling.");
        } else {
            MuragramLogger.log(TAG, "No token, stopping service.");
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void checkNewMessages() {
        if (token == null) return;

        new NetworkUtils.ApiTask("chats", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override
            public void onResult(JSONObject result) {
                if (result == null || !result.optBoolean("ok")) return;
                try {
                    JSONArray data = result.getJSONArray("data");
                    SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, MODE_PRIVATE);
                    if (prefs == null) return;
                    
                    boolean useWhitelist = prefs.getBoolean(NetworkUtils.KEY_WHITELIST_ENABLED, false);
                    String whitelistRaw = prefs.getString(NetworkUtils.KEY_WHITELIST, "").toLowerCase();
                    
                    List<String> cleanWhitelist = new ArrayList<String>();
                    if (useWhitelist && whitelistRaw.length() != 0) {
                        String[] parts = whitelistRaw.split(",");
                        for (int i = 0; i < parts.length; i++) {
                            String trimmed = parts[i].trim();
                            if (trimmed.length() != 0) {
                                if (trimmed.startsWith("@")) trimmed = trimmed.substring(1);
                                cleanWhitelist.add(trimmed);
                            }
                        }
                    }

                    long maxDate = lastCheckTime;
                    
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject chat = data.getJSONObject(i);
                        int unread = chat.optInt("unread_count", 0);
                        long lastDate = chat.optLong("last_date", 0);
                        
                        if (unread > 0 && lastDate > lastCheckTime) {
                            String name = chat.optString("name", "").toLowerCase();
                            String username = chat.optString("username", "").toLowerCase();
                            
                            boolean allowed = !useWhitelist;
                            if (useWhitelist) {
                                for (int j = 0; j < cleanWhitelist.size(); j++) {
                                    String pattern = cleanWhitelist.get(j);
                                    if ((username.length() != 0 && username.contains(pattern)) || 
                                        (name.length() != 0 && name.contains(pattern))) {
                                        allowed = true; break;
                                    }
                                }
                            }

                            if (allowed) {
                                showNotification(chat.getLong("id"), chat.optString("name", "Muragram"), chat.optString("last_message", getString(R.string.new_message_label)));
                            }
                        }
                        if (lastDate > maxDate) maxDate = lastDate;
                    }
                    
                    if (maxDate > lastCheckTime) {
                        lastCheckTime = maxDate;
                        prefs.edit().putLong("last_check_time", lastCheckTime).commit();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Process error", e);
                }
            }
        }).start();
    }

    private void showNotification(long chatId, String title, String text) {
        SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, MODE_PRIVATE);
        if (prefs == null) return;
        boolean sound = prefs.getBoolean(NetworkUtils.KEY_NOTIFY_SOUND, true);
        boolean vibrate = prefs.getBoolean(NetworkUtils.KEY_NOTIFY_VIBRATE, true);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("chat_name", title);
        intent.putExtra("from_notification", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, (int)chatId, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        int defaults = 0;
        if (sound) defaults |= Notification.DEFAULT_SOUND;
        if (vibrate) defaults |= Notification.DEFAULT_VIBRATE;

        String channelId = "messages";
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationHelper.createChannel(nm, channelId, this);
        }

        Notification n;
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            n = NotificationHelper.buildNotification(this, pi, channelId, title, text, defaults);
        } else {
            n = new Notification(android.R.drawable.stat_notify_chat, text, System.currentTimeMillis());
            try {
                java.lang.reflect.Method m = n.getClass().getMethod("setLatestEventInfo", 
                    android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                m.invoke(n, this, title, text, pi);
            } catch (Exception e) {}
            n.flags |= Notification.FLAG_AUTO_CANCEL;
            n.defaults = defaults;
        }
        nm.notify((int)chatId, n);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        super.onDestroy();
    }
}
