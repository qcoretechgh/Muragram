package com.dycrd.muragram;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONObject;

public class EditProfileActivity extends Activity {
    private static final String TAG = "EditProfileActivity";
    private EditText firstInput, lastInput, userInput, bioInput;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            LegacyHelper.applyLocale(this);
            SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
            boolean isDark = (prefs != null) && prefs.getBoolean(NetworkUtils.KEY_DARK_THEME, false);
            setTheme(isDark ? R.style.Theme_Muragram_Dark : R.style.Theme_Muragram);
            
            setContentView(R.layout.activity_edit_profile);
            LegacyHelper.setupActionBar(this);
            token = (prefs != null) ? prefs.getString(NetworkUtils.KEY_TOKEN, null) : null;

            firstInput = (EditText) findViewById(R.id.firstNameInput);
            lastInput = (EditText) findViewById(R.id.lastNameInput);
            userInput = (EditText) findViewById(R.id.usernameInput);
            bioInput = (EditText) findViewById(R.id.bioInput);
            Button saveBtn = (Button) findViewById(R.id.saveProfileButton);

            loadProfile();

            if (saveBtn != null) {
                saveBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { saveProfile(); }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
        }
    }

    private void loadProfile() {
        if (token == null) return;
        new NetworkUtils.ApiTask("me", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject res) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (res != null && res.optBoolean("ok")) {
                            try {
                                JSONObject d = res.getJSONObject("data");
                                if (firstInput != null) firstInput.setText(d.optString("first_name", ""));
                                if (lastInput != null) lastInput.setText(d.optString("last_name", ""));
                                if (userInput != null) userInput.setText(d.optString("username", ""));
                                if (bioInput != null) bioInput.setText(d.optString("bio", ""));
                            } catch (Exception e) {}
                        }
                    }
                });
            }
        }).start();
    }

    private void saveProfile() {
        if (token == null) return;
        try {
            JSONObject p = new JSONObject();
            if (firstInput != null) p.put("first_name", firstInput.getText().toString());
            if (lastInput != null) p.put("last_name", lastInput.getText().toString());
            if (userInput != null) p.put("username", userInput.getText().toString());
            if (bioInput != null) p.put("bio", bioInput.getText().toString());

            new NetworkUtils.ApiTask("profile/edit", "POST", p, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(final JSONObject res) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            if (res != null && res.optBoolean("ok")) {
                                Toast.makeText(EditProfileActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(EditProfileActivity.this, R.string.error_updating_profile, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }).start();
        } catch (Exception e) {}
    }
    
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 16908332) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
