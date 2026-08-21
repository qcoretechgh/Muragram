package com.dycrd.muragram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private ListView categoryList;
    private View settingsScroll;
    private View secGeneral, secAppearance, secNotifications, secNetwork, secAccount;
    
    private RadioGroup serverGroup;
    private CheckBox darkThemeCheck, soundCheckbox, vibrateCheckbox, whitelistEnabledCheckbox, showDateCheckbox;
    private EditText textSizeInput, whitelistInput, searchDelayInput, notifyDelayInput;
    private Button saveButton, infoButton, logoutButton, appInfoButton, editProfileButton, languageButton;
    
    private String token;
    private boolean isTablet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            LegacyHelper.applyLocale(this);
            final SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
            boolean isDark = (prefs != null) && prefs.getBoolean(NetworkUtils.KEY_DARK_THEME, false);
            setTheme(isDark ? R.style.Theme_Muragram_Dark : R.style.Theme_Muragram);
            
            setContentView(R.layout.activity_settings);
            isTablet = getResources().getBoolean(R.bool.isTablet);
            token = (prefs != null) ? prefs.getString(NetworkUtils.KEY_TOKEN, null) : null;

            LegacyHelper.setupActionBar(this);

            categoryList = (ListView) findViewById(R.id.categoryList);
            settingsScroll = findViewById(R.id.settingsScroll);
            
            secGeneral = findViewById(R.id.sectionGeneral);
            secAppearance = findViewById(R.id.sectionAppearance);
            secNotifications = findViewById(R.id.sectionNotifications);
            secNetwork = findViewById(R.id.sectionNetwork);
            secAccount = findViewById(R.id.sectionAccount);

            serverGroup = (RadioGroup) findViewById(R.id.serverGroup);
            darkThemeCheck = (CheckBox) findViewById(R.id.darkThemeCheck);
            textSizeInput = (EditText) findViewById(R.id.textSizeInput);
            soundCheckbox = (CheckBox) findViewById(R.id.soundCheck);
            vibrateCheckbox = (CheckBox) findViewById(R.id.vibrateCheck);
            whitelistEnabledCheckbox = (CheckBox) findViewById(R.id.whitelistCheck);
            showDateCheckbox = (CheckBox) findViewById(R.id.showDateCheck);
            whitelistInput = (EditText) findViewById(R.id.whitelistInput);
            searchDelayInput = (EditText) findViewById(R.id.searchDelayInput);
            notifyDelayInput = (EditText) findViewById(R.id.pollInput);
            
            saveButton = (Button) findViewById(R.id.saveButton);
            infoButton = (Button) findViewById(R.id.infoButton);
            logoutButton = (Button) findViewById(R.id.logoutButton);
            appInfoButton = (Button) findViewById(R.id.appInfoButton);
            editProfileButton = (Button) findViewById(R.id.editProfileButton);
            languageButton = (Button) findViewById(R.id.languageButton);

            final String[] categories = new String[]{
                getString(R.string.system_label),
                getString(R.string.appearance), 
                getString(R.string.notifications), 
                getString(R.string.server_label), 
                getString(R.string.account)
            };
            
            if (categoryList != null) {
                categoryList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, categories));

                categoryList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                        showSection(pos);
                        if (!isTablet && categoryList != null && settingsScroll != null) {
                            categoryList.setVisibility(View.GONE);
                            settingsScroll.setVisibility(View.VISIBLE);
                            LegacyHelper.setTitle(SettingsActivity.this, categories[pos]);
                        }
                    }
                });
            }

            if (isTablet) {
                if (settingsScroll != null) settingsScroll.setVisibility(View.VISIBLE);
                showSection(0);
            } else {
                if (categoryList != null) categoryList.setVisibility(View.VISIBLE);
                if (settingsScroll != null) settingsScroll.setVisibility(View.GONE);
            }

            // Load values
            if (prefs != null) {
                if (darkThemeCheck != null) darkThemeCheck.setChecked(prefs.getBoolean(NetworkUtils.KEY_DARK_THEME, false));
                
                if (serverGroup != null) {
                    String currentServer = prefs.getString(NetworkUtils.KEY_SERVER, NetworkUtils.SERVER_MAIN);
                    if (NetworkUtils.SERVER_ALT.equals(currentServer)) {
                        RadioButton rb = (RadioButton) findViewById(R.id.serverAlt);
                        if (rb != null) rb.setChecked(true);
                    } else {
                        RadioButton rb = (RadioButton) findViewById(R.id.serverMain);
                        if (rb != null) rb.setChecked(true);
                    }
                }

                if (textSizeInput != null) textSizeInput.setText(String.valueOf(prefs.getInt(NetworkUtils.KEY_TEXT_SIZE, 16)));
                if (soundCheckbox != null) soundCheckbox.setChecked(prefs.getBoolean(NetworkUtils.KEY_NOTIFY_SOUND, true));
                if (vibrateCheckbox != null) vibrateCheckbox.setChecked(prefs.getBoolean(NetworkUtils.KEY_NOTIFY_VIBRATE, true));
                if (whitelistEnabledCheckbox != null) whitelistEnabledCheckbox.setChecked(prefs.getBoolean(NetworkUtils.KEY_WHITELIST_ENABLED, false));
                if (showDateCheckbox != null) showDateCheckbox.setChecked(prefs.getBoolean(NetworkUtils.KEY_SHOW_DATE, true));
                if (whitelistInput != null) {
                    whitelistInput.setText(prefs.getString(NetworkUtils.KEY_WHITELIST, ""));
                }
                if (searchDelayInput != null) searchDelayInput.setText(String.valueOf(prefs.getInt(NetworkUtils.KEY_SEARCH_DELAY, 500)));
                if (notifyDelayInput != null) notifyDelayInput.setText(String.valueOf(prefs.getInt(NetworkUtils.KEY_NOTIFY_DELAY, 15)));
            }

            if (saveButton != null && prefs != null) {
                saveButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        SharedPreferences.Editor ed = prefs.edit();
                        
                        if (darkThemeCheck != null) {
                            boolean wasDark = prefs.getBoolean(NetworkUtils.KEY_DARK_THEME, false);
                            boolean isDarkNow = darkThemeCheck.isChecked();
                            ed.putBoolean(NetworkUtils.KEY_DARK_THEME, isDarkNow);
                            if (wasDark != isDarkNow) {
                                ed.commit();
                                finish(); startActivity(getIntent());
                                return;
                            }
                        }

                        if (serverGroup != null) {
                            RadioButton rbAlt = (RadioButton) findViewById(R.id.serverAlt);
                            String newServer = (rbAlt != null && rbAlt.isChecked()) ? NetworkUtils.SERVER_ALT : NetworkUtils.SERVER_MAIN;
                            ed.putString(NetworkUtils.KEY_SERVER, newServer);
                            NetworkUtils.BASE_URL = newServer;
                        }

                        try { 
                            if (textSizeInput != null) ed.putInt(NetworkUtils.KEY_TEXT_SIZE, Integer.parseInt(textSizeInput.getText().toString())); 
                        } catch (Exception e) {}
                        
                        if (soundCheckbox != null) ed.putBoolean(NetworkUtils.KEY_NOTIFY_SOUND, soundCheckbox.isChecked());
                        if (vibrateCheckbox != null) ed.putBoolean(NetworkUtils.KEY_NOTIFY_VIBRATE, vibrateCheckbox.isChecked());
                        if (whitelistEnabledCheckbox != null) ed.putBoolean(NetworkUtils.KEY_WHITELIST_ENABLED, whitelistEnabledCheckbox.isChecked());
                        if (whitelistInput != null) ed.putString(NetworkUtils.KEY_WHITELIST, whitelistInput.getText().toString());
                        if (showDateCheckbox != null) ed.putBoolean(NetworkUtils.KEY_SHOW_DATE, showDateCheckbox.isChecked());
                        
                        try { 
                            if (searchDelayInput != null) ed.putInt(NetworkUtils.KEY_SEARCH_DELAY, Integer.parseInt(searchDelayInput.getText().toString())); 
                        } catch (Exception e) {}
                        
                        try {
                            if (notifyDelayInput != null) {
                                int n = Integer.parseInt(notifyDelayInput.getText().toString());
                                ed.putInt(NetworkUtils.KEY_NOTIFY_DELAY, n < 5 ? 5 : n);
                            }
                        } catch (Exception e) {}
                        
                        ed.commit();
                        Toast.makeText(SettingsActivity.this, R.string.save, Toast.LENGTH_SHORT).show();
                        if (!isTablet) {
                            doBack();
                        }
                    }
                });
            }

            if (infoButton != null) {
                infoButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showUserInfo(); }
                });
            }

            if (logoutButton != null) {
                logoutButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { logout(); }
                });
            }

            if (appInfoButton != null) {
                appInfoButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new AlertDialog.Builder(SettingsActivity.this).setTitle(R.string.about)
                            .setMessage(R.string.app_info).setPositiveButton(R.string.ok, null).show();
                    }
                });
            }

            if (editProfileButton != null) {
                editProfileButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this, EditProfileActivity.class));
                    }
                });
            }

            if (languageButton != null) {
                languageButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        final String[] langs = {"English", "Русский", "Українська", "中文"};
                        final String[] codes = {"en", "ru", "uk", "zh"};
                        new AlertDialog.Builder(SettingsActivity.this).setTitle(R.string.language)
                            .setItems(langs, new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int which) { setLocale(codes[which]); }
                            }).show();
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "onCreate failed", e);
        }
    }

    private void setLocale(String lang) {
        java.util.Locale locale = new java.util.Locale(lang);
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
        SharedPreferences p = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
        if (p != null) p.edit().putString("language", lang).commit();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showSection(int index) {
        if (secGeneral != null) secGeneral.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (secAppearance != null) secAppearance.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (secNotifications != null) secNotifications.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (secNetwork != null) secNetwork.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        if (secAccount != null) secAccount.setVisibility(index == 4 ? View.VISIBLE : View.GONE);
    }

    private boolean doBack() {
        if (!isTablet && categoryList != null && categoryList.getVisibility() == View.GONE) {
            categoryList.setVisibility(View.VISIBLE);
            if (settingsScroll != null) settingsScroll.setVisibility(View.GONE);
            LegacyHelper.setTitle(this, getString(R.string.settings));
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (doBack()) return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 16908332) { // home
            if (!doBack()) finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        new NetworkUtils.ApiTask("auth/logout", "POST", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(JSONObject res) {
                if (isFinishing()) return;
                stopService(new Intent(SettingsActivity.this, NotificationService.class));
                SharedPreferences p = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
                if (p != null) p.edit().remove(NetworkUtils.KEY_TOKEN).commit();
                Intent i = new Intent(SettingsActivity.this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i); finish();
            }
        }).start();
    }

    private void showUserInfo() {
        new NetworkUtils.ApiTask("me", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(JSONObject res) {
                if (isFinishing()) return;
                try {
                    if (res != null && res.getBoolean("ok")) {
                        JSONObject d = res.getJSONObject("data");
                        String s = getString(R.string.username) + ": @" + d.optString("username", getString(R.string.none)) + "\n" + getString(R.string.id_label) + ": " + d.getLong("id");
                        new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle(d.optString("first_name", ""))
                            .setMessage(s).setPositiveButton(R.string.ok, null).show();
                    }
                } catch (Exception e) {}
            }
        }).start();
    }
}
