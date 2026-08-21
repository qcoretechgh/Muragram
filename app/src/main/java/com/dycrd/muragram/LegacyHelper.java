package com.dycrd.muragram;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import java.util.Locale;

public class LegacyHelper {
    public static void setupActionBar(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= 11) {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                if (Build.VERSION.SDK_INT >= 14) {
                    actionBar.setHomeButtonEnabled(true);
                }
            }
        }
    }

    public static void setTitle(Activity activity, String title) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= 11) {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                return;
            }
        }
        activity.setTitle(title);
    }

    public static void addSearchToMenu(final Activity activity, Menu menu, final int id) {
        if (menu == null || activity == null) return;
        MenuItem item = menu.add(0, id, 0, R.string.search);
        item.setIcon(android.R.drawable.ic_menu_search);
        
        if (Build.VERSION.SDK_INT >= 11) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            final android.widget.SearchView sv = new android.widget.SearchView(activity);
            sv.setQueryHint(activity.getString(R.string.search_hint));
            
            // Link system searchable config
            android.app.SearchManager sm = (android.app.SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
            if (sm != null) {
                sv.setSearchableInfo(sm.getSearchableInfo(activity.getComponentName()));
            }

            sv.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) { 
                    sv.clearFocus();
                    if (activity instanceof MainActivity) ((MainActivity)activity).loadChats(query);
                    return true; 
                }
                @Override public boolean onQueryTextChange(String query) {
                    // Do nothing here to prevent overlapping the chat list while typing.
                    // Results will be shown in the suggestions dropdown via SearchProvider.
                    return true;
                }
            });
            item.setActionView(sv);
        }
    }

    public static void startDownload(Context ctx, String url, String fileName) {
        if (ctx == null || url == null) return;
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription(ctx.getString(R.string.download_started));
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            
            if (Build.VERSION.SDK_INT >= 11) {
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            }

            DownloadManager manager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(ctx, R.string.download_started, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                ctx.startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(ctx, R.string.browser_not_found, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void applyLocale(Context ctx) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
        String lang = prefs.getString("language", null);
        if (lang != null) {
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);
            Resources res = ctx.getResources();
            Configuration config = res.getConfiguration();
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            
            // Force apply to application context as well
            Context appContext = ctx.getApplicationContext();
            if (appContext != null && appContext != ctx) {
                Resources appRes = appContext.getResources();
                Configuration appConfig = appRes.getConfiguration();
                appConfig.locale = locale;
                appRes.updateConfiguration(appConfig, appRes.getDisplayMetrics());
            }
        }
    }
}
