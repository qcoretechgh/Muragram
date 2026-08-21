package com.dycrd.muragram;

import android.os.AsyncTask;
import android.annotation.TargetApi;

public class CompatExecutor {
    @TargetApi(11)
    public static void execute(AsyncTask task, Object[] params) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }
}
