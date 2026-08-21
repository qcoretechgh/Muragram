package com.dycrd.muragram;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.annotation.TargetApi;

public class CompatDownload {
    @TargetApi(9)
    public static void start(Context ctx, String url, String name) {
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle(name);
            if (android.os.Build.VERSION.SDK_INT >= 11) {
                // VISIBILITY_VISIBLE_NOTIFY_COMPLETED = 1
                r.setNotificationVisibility(1);
            }
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(r);
        } catch (Throwable e) {}
    }
}
