package com.dycrd.muragram;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.VideoView;
import android.widget.MediaController;
import android.os.Handler;

public class MediaActivity extends Activity {
    private static final String TAG = "MediaActivity";
    private VideoView videoView;
    private View audioControls;
    private Button btnPlayPause;
    private SeekBar seekBar;
    private ProgressBar progress;
    private MediaPlayer mediaPlayer;
    private String type;
    
    private final Handler handler = new Handler();
    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                if (seekBar != null) seekBar.setProgress(mediaPlayer.getCurrentPosition());
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            LegacyHelper.applyLocale(this);
            boolean isDark = getSharedPreferences(NetworkUtils.PREFS_NAME, 0).getBoolean(NetworkUtils.KEY_DARK_THEME, false);
            setTheme(isDark ? R.style.Theme_Muragram_Dark : R.style.Theme_Muragram);
            
            setContentView(R.layout.activity_media);

            videoView = (VideoView) findViewById(R.id.videoView);
            audioControls = findViewById(R.id.audioControls);
            btnPlayPause = (Button) findViewById(R.id.btnPlayPause);
            seekBar = (SeekBar) findViewById(R.id.audioSeekBar);
            progress = (ProgressBar) findViewById(R.id.mediaProgress);

            String url = getIntent().getStringExtra("url");
            type = getIntent().getStringExtra("type");

            if (url == null) { finish(); return; }

            Uri uri = Uri.parse(url);

            if (type != null && type.startsWith("video/")) {
                if (videoView != null) {
                    videoView.setVisibility(View.VISIBLE);
                    if (audioControls != null) audioControls.setVisibility(View.GONE);
                    MediaController mc = new MediaController(this);
                    mc.setAnchorView(videoView);
                    videoView.setMediaController(mc);
                    videoView.setVideoURI(uri);
                    videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override public void onPrepared(MediaPlayer mp) {
                            if (isFinishing()) return;
                            if (progress != null) progress.setVisibility(View.GONE);
                            videoView.start();
                        }
                    });
                    if (progress != null) progress.setVisibility(View.VISIBLE);
                }
            } else {
                if (videoView != null) videoView.setVisibility(View.GONE);
                if (audioControls != null) audioControls.setVisibility(View.VISIBLE);
                playAudio(uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
        }
    }

    private void playAudio(Uri uri) {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    if (isFinishing()) return;
                    if (progress != null) progress.setVisibility(View.GONE);
                    mediaPlayer.start();
                    if (btnPlayPause != null) btnPlayPause.setText(R.string.pause);
                    if (seekBar != null) {
                        seekBar.setMax(mediaPlayer.getDuration());
                        handler.post(updateSeekBar);
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    if (btnPlayPause != null) btnPlayPause.setText(R.string.play);
                    if (seekBar != null) seekBar.setProgress(0);
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (progress != null) progress.setVisibility(View.GONE);
                    return false;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            if (progress != null) progress.setVisibility(View.GONE);
            Log.e(TAG, "Error playing audio", e);
        }

        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (mediaPlayer == null) return;
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        btnPlayPause.setText(R.string.play);
                    } else {
                        mediaPlayer.start();
                        btnPlayPause.setText(R.string.pause);
                        handler.post(updateSeekBar);
                    }
                }
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (btnPlayPause != null) btnPlayPause.setText(R.string.play);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
