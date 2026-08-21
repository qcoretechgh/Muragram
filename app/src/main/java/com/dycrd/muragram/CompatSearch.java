package com.dycrd.muragram;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;
import android.annotation.TargetApi;

public class CompatSearch {
    @TargetApi(11)
    public static void addSearch(final MainActivity activity, Menu menu, int id) {
        MenuItem searchItem = menu.add(0, id, 0, activity.getString(R.string.search_hint));
        searchItem.setIcon(android.R.drawable.ic_menu_search);
        
        // SHOW_AS_ACTION_ALWAYS = 2, SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW = 8
        int flags = 2;
        if (android.os.Build.VERSION.SDK_INT >= 14) flags |= 8;
        searchItem.setShowAsAction(flags);
        
        SearchView sv = new SearchView(activity);
        sv.setQueryHint(activity.getString(R.string.search_hint));
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                activity.loadChats(query);
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
                // Do nothing here to prevent overlapping the chat list while typing.
                return true;
            }
        });
        searchItem.setActionView(sv);
    }
}
