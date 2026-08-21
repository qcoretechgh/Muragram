package com.dycrd.muragram;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import org.json.JSONArray;
import org.json.JSONObject;

public class SearchProvider extends ContentProvider {
    public static final String AUTHORITY = "com.dycrd.muragram.SearchProvider";
    
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (uri == null) return null;
        String query = uri.getLastPathSegment();
        String searchTerm;
        
        String path = uri.getPath();
        if (path != null && path.contains(SearchManager.SUGGEST_URI_PATH_QUERY)) {
            searchTerm = (selectionArgs != null && selectionArgs.length > 0) ? selectionArgs[0] : query;
            if (SearchManager.SUGGEST_URI_PATH_QUERY.equals(searchTerm)) searchTerm = "";
        } else if (selectionArgs != null && selectionArgs.length > 0) {
            searchTerm = selectionArgs[0];
        } else {
            searchTerm = query;
        }
        
        return getSuggestions(searchTerm);
    }

    private Cursor getSuggestions(String query) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_SHORTCUT_ID
        });

        if (query == null || query.trim().isEmpty() || getContext() == null) return cursor;

        // 1. Immediate local cache results
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
            String cached = prefs.getString("cached_chats_json", null);
            if (cached != null) {
                JSONObject r = new JSONObject(cached);
                JSONArray d = r.getJSONArray("data");
                for (int i = 0; i < d.length(); i++) {
                    JSONObject chat = d.getJSONObject(i);
                    String name = chat.getString("name").toLowerCase();
                    String username = chat.optString("username", "").toLowerCase();
                    if (name.contains(query.toLowerCase()) || username.contains(query.toLowerCase())) {
                        long id = chat.getLong("id");
                        String displayTitle = chat.getString("name");
                        if (!username.isEmpty()) displayTitle += " (@" + chat.getString("username") + ")";
                        cursor.addRow(new Object[] {
                            id, displayTitle, chat.optString("last_message", ""),
                            "content://com.dycrd.muragram.SearchProvider/chats/" + id,
                            android.R.drawable.sym_contact_card, android.content.Intent.ACTION_VIEW, "chat_" + id
                        });
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Immediate "Search in App" entry if few results
        if (cursor.getCount() < 3) {
            cursor.addRow(new Object[] {
                0,
                getContext().getString(R.string.error_with_details, getContext().getString(R.string.search), query),
                getContext().getString(R.string.app_name),
                query,
                android.R.drawable.ic_menu_search,
                android.content.Intent.ACTION_SEARCH,
                "search_query"
            });
        }

        // 3. Fetch live results from API (Nickname / Chat names)
        try {
            NetworkUtils.updateServer(getContext());
            SharedPreferences prefs = getContext().getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
            String token = prefs.getString(NetworkUtils.KEY_TOKEN, null);
            if (token == null) return cursor;

            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            JSONObject res = NetworkUtils.ApiTask.executeSync("chats?q=" + encoded, "GET", token);
            
            if (res == null || !res.optBoolean("ok")) {
                // Try ALT server if main failed
                res = NetworkUtils.ApiTask.executeSync("chats?q=" + encoded, "GET", token, NetworkUtils.SERVER_ALT);
            }

            if (res != null && res.optBoolean("ok")) {
                JSONArray data = res.getJSONArray("data");
                for (int i = 0; i < data.length() && i < 15; i++) {
                    JSONObject chat = data.getJSONObject(i);
                    long id = chat.getLong("id");
                    String name = chat.getString("name");
                    String last = chat.optString("last_message", "");
                    String username = chat.optString("username", "");
                    
                    String displayTitle = name;
                    if (username != null && !username.isEmpty()) {
                        displayTitle += " (@" + username + ")";
                    }

                    cursor.addRow(new Object[] {
                        id,
                        displayTitle,
                        last,
                        "content://com.dycrd.muragram.SearchProvider/chats/" + id,
                        android.R.drawable.sym_contact_card,
                        android.content.Intent.ACTION_VIEW,
                        "chat_" + id
                    });
                }
            }
        } catch (Exception ignored) {}

        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return SearchManager.SUGGEST_MIME_TYPE;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
