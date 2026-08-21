package com.dycrd.muragram;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONObject;

public class LoginActivity extends BaseActivity {
    private EditText phoneInput;
    private EditText codeInput;
    private View codeInputContainer;
    private Button loginButton;
    private Button qrButton, serverButton;
    private String phoneCodeHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            NetworkUtils.updateServer(this);
            setContentView(R.layout.activity_login);

            phoneInput = (EditText) findViewById(R.id.phoneInput);
            codeInput = (EditText) findViewById(R.id.codeInput);
            codeInputContainer = findViewById(R.id.codeInputContainer);
            loginButton = (Button) findViewById(R.id.loginButton);
            qrButton = (Button) findViewById(R.id.qrButton);
            serverButton = (Button) findViewById(R.id.serverButton);

            if (loginButton != null) {
                loginButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (phoneCodeHash == null) {
                            sendCode();
                        } else {
                            verifyCode();
                        }
                    }
                });
            }

            if (qrButton != null) {
                qrButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(LoginActivity.this, QrLoginActivity.class));
                    }
                });
            }

            if (serverButton != null) {
                serverButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showServerDialog();
                    }
                });
            }
        } catch (Exception e) {}
    }

    private void showServerDialog() {
        final String[] servers = new String[]{getString(R.string.server_main), getString(R.string.server_alt)};
        final String[] urls = new String[]{NetworkUtils.SERVER_MAIN, NetworkUtils.SERVER_ALT};
        
        SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
        if (prefs == null) return;
        
        String current = prefs.getString(NetworkUtils.KEY_SERVER, NetworkUtils.SERVER_MAIN);
        int selected = current.equals(NetworkUtils.SERVER_ALT) ? 1 : 0;

        new AlertDialog.Builder(this)
            .setTitle(R.string.select_server)
            .setSingleChoiceItems(servers, selected, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences p = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
                    if (p != null) p.edit().putString(NetworkUtils.KEY_SERVER, urls[which]).commit();
                    NetworkUtils.updateServer(LoginActivity.this);
                    Toast.makeText(LoginActivity.this, R.string.server_changed, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void sendCode() {
        if (phoneInput == null) return;
        final String phone = phoneInput.getText().toString();
        if (phone.length() == 0) return;

        try {
            JSONObject params = new JSONObject();
            params.put("phone", phone);

            new NetworkUtils.ApiTask("auth/phone/send_code", "POST", params, null, new NetworkUtils.ApiCallback() {
                @Override
                public void onResult(final JSONObject result) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            try {
                                if (result != null && result.getBoolean("ok")) {
                                    JSONObject data = result.getJSONObject("data");
                                    phoneCodeHash = data.getString("phone_code_hash");
                                    if (codeInputContainer != null) {
                                        codeInputContainer.setVisibility(View.VISIBLE);
                                    }
                                    if (loginButton != null) loginButton.setText(R.string.verify_code);
                                    Toast.makeText(LoginActivity.this, R.string.code_sent, Toast.LENGTH_SHORT).show();
                                } else {
                                    String err = (result != null) ? getString(R.string.server_error) : getString(R.string.network_error);
                                    Toast.makeText(LoginActivity.this, err, Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(LoginActivity.this, getString(R.string.error_with_details, getString(R.string.response_error), e.getMessage()), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }).start();
        } catch (Exception e) {}
    }

    private void verifyCode() {
        if (phoneInput == null || codeInput == null) return;
        final String phone = phoneInput.getText().toString();
        final String code = codeInput.getText().toString();
        if (code.length() == 0) return;

        try {
            JSONObject params = new JSONObject();
            params.put("phone", phone);
            params.put("code", code);
            params.put("phone_code_hash", phoneCodeHash);

            new NetworkUtils.ApiTask("auth/phone/verify", "POST", params, null, new NetworkUtils.ApiCallback() {
                @Override
                public void onResult(final JSONObject result) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            try {
                                if (result != null && result.getBoolean("ok")) {
                                    JSONObject data = result.getJSONObject("data");
                                    String token = data.getString("token");
                                    
                                    SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
                                    if (prefs != null) {
                                        prefs.edit().putString(NetworkUtils.KEY_TOKEN, token).commit();
                                    }
                                    
                                    startService(new Intent(LoginActivity.this, NotificationService.class));
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                } else {
                                    Toast.makeText(LoginActivity.this, R.string.invalid_code, Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(LoginActivity.this, R.string.login_error, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }).start();
        } catch (Exception e) {}
    }
}
