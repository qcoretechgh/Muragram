package com.dycrd.muragram;

import android.app.Activity;
import android.annotation.TargetApi;

public class CompatActionBar {
    @TargetApi(11)
    public static void setup(Activity activity) {
        if (activity.getActionBar() != null) {
            activity.getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @TargetApi(11)
    public static void setTitle(Activity activity, String title) {
        if (activity.getActionBar() != null) {
            activity.getActionBar().setTitle(title);
        }
    }
}
