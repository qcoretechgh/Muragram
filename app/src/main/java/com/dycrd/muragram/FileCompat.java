package com.dycrd.muragram;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

public class FileCompat {
    public static String getDisplayName(Context ctx, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = ctx.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    // _display_name constant from OpenableColumns is "_display_name"
                    int index = cursor.getColumnIndex("_display_name");
                    if (index >= 0) return cursor.getString(index);
                }
            } catch (Throwable e) {
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        return null;
    }
}
