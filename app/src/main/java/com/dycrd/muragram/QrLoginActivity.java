package com.dycrd.muragram;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import org.json.JSONObject;

public class QrLoginActivity extends Activity {
    private static final String TAG = "QrLoginActivity";
    private ImageView qrCodeImage;
    private ProgressBar progressBar;
    private Button backButton;
    private String qrToken;
    private final Handler qrHandler = new Handler();
    private boolean isPollingQr = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            LegacyHelper.applyLocale(this);
            NetworkUtils.updateServer(this);
            SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, MODE_PRIVATE);
            boolean isDark = prefs != null && prefs.getBoolean(NetworkUtils.KEY_DARK_THEME, false);
            setTheme(isDark ? R.style.Theme_Muragram_Dark : R.style.Theme_Muragram);

            setContentView(R.layout.activity_qr_login);

            qrCodeImage = (ImageView) findViewById(R.id.qrCodeImage);
            progressBar = (ProgressBar) findViewById(R.id.qrProgressBar);
            backButton = (Button) findViewById(R.id.backButton);

            if (backButton != null) {
                backButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { finish(); }
                });
            }

            startQrLogin();
            MuragramLogger.log(TAG, "QR Login started");
        } catch (Exception e) {
            MuragramLogger.log(TAG, "onCreate failed: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopQrPolling();
    }

    private void startQrLogin() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        new NetworkUtils.ApiTask("auth/qr/start", "POST", null, null, new NetworkUtils.ApiCallback() {
            @Override
            public void onResult(JSONObject result) {
                if (isFinishing()) return;
                try {
                    if (result != null && result.getBoolean("ok")) {
                        JSONObject data = result.getJSONObject("data");
                        qrToken = data.getString("qr_token");
                        String qrUrl = data.getString("qr_url");
                        
                        String chartUrl = "http://api.qrserver.com/v1/create-qr-code/?data=" + java.net.URLEncoder.encode(qrUrl, "UTF-8") + "&size=500x500";
                        
                        new NetworkUtils.ImageTask(null, new NetworkUtils.ImageCallback() {
                            @Override
                            public void onBitmap(final Bitmap bitmap) {
                                qrHandler.post(new Runnable() {
                                    @Override public void run() {
                                        if (isFinishing()) return;
                                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                                        if (bitmap != null && qrCodeImage != null) {
                                            qrCodeImage.setImageBitmap(bitmap);
                                            startQrPolling();
                                        } else {
                                            Toast.makeText(QrLoginActivity.this, R.string.failed_load_image, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            }
                        }).execute(new Object[]{chartUrl});
                    } else {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(QrLoginActivity.this, R.string.login_error, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    private void startQrPolling() {
        isPollingQr = true;
        qrHandler.postDelayed(qrPollingRunnable, 3000);
    }

    private void stopQrPolling() {
        isPollingQr = false;
        qrHandler.removeCallbacks(qrPollingRunnable);
    }

    private final Runnable qrPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPollingQr || isFinishing()) return;
            
            new NetworkUtils.ApiTask("auth/qr/check/" + qrToken, "GET", null, null, new NetworkUtils.ApiCallback() {
                @Override
                public void onResult(final JSONObject result) {
                    qrHandler.post(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || !isPollingQr) return;
                            try {
                                if (result != null && result.getBoolean("ok")) {
                                    JSONObject data = result.getJSONObject("data");
                                    String status = data.getString("status");
                                    
                                    if ("ok".equals(status)) {
                                        String token = data.getString("token");
                                        SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, MODE_PRIVATE);
                                        if (prefs != null) {
                                            prefs.edit().putString(NetworkUtils.KEY_TOKEN, token).commit();
                                        }
                                        stopQrPolling();
                                        
                                        startService(new Intent(QrLoginActivity.this, NotificationService.class));
                                        startActivity(new Intent(QrLoginActivity.this, MainActivity.class));
                                        finish();
                                    } else if ("waiting".equals(status)) {
                                        qrHandler.postDelayed(qrPollingRunnable, 3000);
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    });
                }
            }).start();
        }
    };
}
