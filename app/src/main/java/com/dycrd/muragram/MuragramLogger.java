package com.dycrd.muragram;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MuragramLogger {
    private static final int MAX_LOGS = 100;
    private static final List<String> logs = new ArrayList<String>();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static synchronized void log(String tag, String message) {
        String entry = sdf.format(new Date()) + " [" + tag + "] " + message;
        logs.add(entry);
        if (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }
        android.util.Log.d(tag, message);
    }

    public static synchronized String getLogs() {
        StringBuilder sb = new StringBuilder();
        for (int i = logs.size() - 1; i >= 0; i--) {
            sb.append(logs.get(i)).append("\n");
        }
        return sb.toString();
    }
    
    public static synchronized void clear() {
        logs.clear();
    }
}
