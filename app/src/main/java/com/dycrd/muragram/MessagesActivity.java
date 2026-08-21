package com.dycrd.muragram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessagesActivity extends BaseActivity {
    private static final String TAG = "MessagesActivity";
    private ListView messageList;
    private MessageAdapter messageAdapter;
    private EditText messageInput;
    private long chatId;
    private String chatName;
    private boolean isTablet;
    
    private android.media.MediaRecorder recorder;
    private String voicePath;
    private boolean isRecording = false;
    private View normalInputLayout, recordingLayout;
    private TextView recordingTimer;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    
    private View replyPreview;
    private TextView replyText;
    private long replyToId = -1;

    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            if (!isRecording || isFinishing()) return;
            long millis = System.currentTimeMillis() - startTime;
            int secondsTotal = (int) (millis / 1000);
            int minutes = secondsTotal / 60;
            int seconds = secondsTotal % 60;
            recordingTimer.setText(getString(R.string.recording_format, getString(R.string.recording_label), minutes, seconds));
            uiHandler.postDelayed(this, 500);
        }
    };

    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private long oldestId = 0;
    private int textSize = 16;
    private boolean showDate = true;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            NetworkUtils.updateServer(this);
            chatId = getIntent().getLongExtra("chat_id", -1);
            chatName = getIntent().getStringExtra("chat_name");
            if (chatId == -1) { finish(); return; }

            setContentView(R.layout.activity_messages);
            LegacyHelper.setupActionBar(this);
            LegacyHelper.setTitle(this, chatName != null ? chatName : getString(R.string.chat_label));
            isTablet = getResources().getBoolean(R.bool.isTablet);

            SharedPreferences prefs = getSharedPreferences(NetworkUtils.PREFS_NAME, 0);
            textSize = prefs.getInt(NetworkUtils.KEY_TEXT_SIZE, 16);
            showDate = prefs.getBoolean(NetworkUtils.KEY_SHOW_DATE, true);

            messageList = findViewById(R.id.messageList);
            messageInput = findViewById(R.id.messageInput);
            View sendButton = findViewById(R.id.sendButton);
            View attachButton = findViewById(R.id.attachButton);
            View micButton = findViewById(R.id.micButton);
            
            if (micButton instanceof Button) {
                ((Button)micButton).setText(R.string.mic_symbol);
            } else if (micButton instanceof TextView) {
                ((TextView)micButton).setText(R.string.mic_symbol);
            }
            
            if (attachButton instanceof Button) {
                ((Button)attachButton).setText(R.string.attach_symbol);
            }
            
            normalInputLayout = findViewById(R.id.normalInputLayout);
            recordingLayout = findViewById(R.id.recordingLayout);
            recordingTimer = findViewById(R.id.recordingTimer);
            View stopRecordingBtn = findViewById(R.id.stopRecordingButton);
            View cancelRecordingBtn = findViewById(R.id.cancelRecordingButton);

            replyPreview = findViewById(R.id.replyPreview);
            replyText = findViewById(R.id.replyText);
            View closeReply = findViewById(R.id.closeReply);

            if (closeReply != null) {
                closeReply.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { cancelReply(); }
                });
            }

            handleIntent(getIntent());

            if (sendButton != null) {
                sendButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { sendMessage(); }
                });
            }

            if (attachButton != null) {
                attachButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("*/*");
                            startActivityForResult(i, 200);
                        } catch (Exception ignored) {}
                    }
                });
            }

            if (micButton != null) {
                micButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { startRecording(); }
                });
            }
            if (stopRecordingBtn != null) {
                stopRecordingBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { stopRecording(); }
                });
            }
            if (cancelRecordingBtn != null) {
                cancelRecordingBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { cancelRecording(); }
                });
            }

            if (messageList != null) {
                messageList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                        Object item = p.getItemAtPosition(pos);
                        if (item instanceof Message) showMessageOptions((Message) item);
                        return true;
                    }
                });

                messageList.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
                    @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                        if (first == 0 && total > 0 && hasMoreMessages && !isLoadingMore) {
                            loadMoreMessages();
                        }
                    }
                });
            }

            loadInitialMessages();
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
        }
    }

    private void showMessageOptions(final Message msg) {
        if (msg == null || isFinishing()) return;
        try {
            final List<String> opt = new ArrayList<String>();
            opt.add(getString(R.string.reply)); opt.add(getString(R.string.copy_text));
            if (msg.isOut) opt.add(getString(R.string.delete));
            if (msg.hasMedia) opt.add(getString(R.string.download_media));

            final String[] items = opt.toArray(new String[0]);
            new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (isFinishing()) return;
                    String action = items[which];
                    if (getString(R.string.reply).equals(action)) startReply(msg);
                    else if (getString(R.string.copy_text).equals(action)) {
                        android.text.ClipboardManager cm = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setText(msg.text != null ? msg.text : "");
                    } else if (getString(R.string.delete).equals(action)) deleteMessage(msg);
                    else if (getString(R.string.download_media).equals(action)) downloadMedia(msg);
                }
            }).show();
        } catch (Exception ignored) {}
    }

    private void startReply(Message msg) {
        replyToId = msg.id;
        if (replyPreview != null) {
            replyPreview.setVisibility(View.VISIBLE);
            if (replyText != null) {
                String label = msg.sender + ": " + (msg.text != null && !msg.text.isEmpty() ? msg.text : getString(R.string.media_label));
                replyText.setText(label);
            }
        }
    }

    private void cancelReply() {
        replyToId = -1;
        if (replyPreview != null) replyPreview.setVisibility(View.GONE);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String shareUri = intent.getStringExtra("share_uri");
        if (shareUri != null) {
            uploadFile(Uri.parse(shareUri));
        }
    }

    private void deleteMessage(final Message msg) {
        new NetworkUtils.ApiTask("chats/" + chatId + "/messages/" + msg.id, "DELETE", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() { if (!isFinishing()) loadInitialMessages(); }
                });
            }
        }).start();
    }

    private void loadMoreMessages() {
        if (oldestId == 0) return;
        isLoadingMore = true;
        String path = "chats/" + chatId + "/messages?limit=30&offset_id=" + oldestId;
        new NetworkUtils.ApiTask(path, "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        isLoadingMore = false;
                        if (isFinishing() || r == null || !r.optBoolean("ok")) return;
                        try {
                            JSONArray data = r.getJSONArray("data");
                            if (data.length() == 0) { hasMoreMessages = false; return; }
                            
                            List<Message> oldOnes = new ArrayList<Message>();
                            for (int i = 0; i < data.length(); i++) {
                                Message m = parseMessage(data.getJSONObject(i));
                                if (m != null) {
                                    oldOnes.add(m);
                                    if (m.id < oldestId || oldestId == 0) oldestId = m.id;
                                }
                            }
                            
                            if (messageAdapter != null) {
                                messageAdapter.setNotifyOnChange(false);
                                for (int i = oldOnes.size() - 1; i >= 0; i--) {
                                    messageAdapter.insert(oldOnes.get(i), 0);
                                }
                                messageAdapter.notifyDataSetChanged();
                                if (messageList != null) messageList.setSelection(oldOnes.size());
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void loadInitialMessages() {
        if (isFinishing()) return;
        new NetworkUtils.ApiTask("chats/" + chatId + "/messages?limit=30", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        try {
                            if (r != null && r.optBoolean("ok")) {
                                JSONArray data = r.getJSONArray("data");
                                List<Message> list = new ArrayList<Message>();
                                for (int i = 0; i < data.length(); i++) {
                                    Message m = parseMessage(data.getJSONObject(i));
                                    if (m != null) {
                                        list.add(m);
                                        if (m.id < oldestId || oldestId == 0) oldestId = m.id;
                                    }
                                }
                                setupAdapter(list);
                            } else {
                                Toast.makeText(MessagesActivity.this, R.string.load_failed, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void setupAdapter(List<Message> list) {
        if (messageAdapter == null) {
            messageAdapter = new MessageAdapter(this, list, token, chatId, isDark, isTablet, textSize, showDate);
            if (messageList != null) messageList.setAdapter(messageAdapter);
        } else {
            messageAdapter.setNotifyOnChange(false);
            messageAdapter.clear();
            for (int i = 0; i < list.size(); i++) messageAdapter.add(list.get(i));
            messageAdapter.notifyDataSetChanged();
        }
        if (messageList != null && !list.isEmpty()) messageList.setSelection(list.size() - 1);
    }

    private Message parseMessage(JSONObject o) {
        try {
            return new Message(
                o.getLong("id"), o.getString("sender"), o.optString("text", ""),
                o.getLong("date"), o.optBoolean("has_media", false),
                o.optString("media_type", ""), o.optBoolean("is_out", false), 
                o.optString("media_name", "")
            );
        } catch (Exception e) { return null; }
    }

    private void sendMessage() {
        if (messageInput == null) return;
        final String text = messageInput.getText().toString();
        if (text.isEmpty()) return;
        try {
            JSONObject p = new JSONObject(); p.put("text", text);
            if (replyToId != -1) p.put("reply_to_id", replyToId);
            new NetworkUtils.ApiTask("chats/" + chatId + "/messages", "POST", p, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(final JSONObject r) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || r == null) return;
                            if (r.optBoolean("ok")) { 
                                if (messageInput != null) messageInput.setText(""); 
                                cancelReply();
                                loadInitialMessages(); 
                            }
                        }
                    });
                }
            }).start();
        } catch (Exception ignored) {}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null && requestCode == 200) uploadFile(data.getData());
    }

    private void uploadFile(Uri uri) {
        if (uri == null) return;
        try {
            String mime = getContentResolver().getType(uri);
            String name = FileCompat.getDisplayName(this, uri);
            if (name == null) name = uri.getLastPathSegment();
            InputStream is = getContentResolver().openInputStream(uri);
            NetworkUtils.ApiTask task = new NetworkUtils.ApiTask("chats/" + chatId + "/messages/media", "POST", null, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(JSONObject res) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() { if (!isFinishing()) loadInitialMessages(); }
                    });
                }
            });
            task.setFileData(is, name, mime);
            task.start();
        } catch (Exception ignored) {}
    }

    private void startRecording() {
        try {
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) return;
            voicePath = cacheDir.getAbsolutePath() + "/voice.mp3";
            recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(voicePath);
            recorder.prepare(); recorder.start();
            isRecording = true; startTime = System.currentTimeMillis();
            if (normalInputLayout != null) normalInputLayout.setVisibility(View.GONE);
            if (recordingLayout != null) recordingLayout.setVisibility(View.VISIBLE);
            uiHandler.post(timerRunnable);
        } catch (Exception e) { Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show(); }
    }

    private void stopRecording() {
        if (!isRecording) return;
        try {
            isRecording = false; recorder.stop(); recorder.release(); recorder = null;
            if (normalInputLayout != null) normalInputLayout.setVisibility(View.VISIBLE);
            if (recordingLayout != null) recordingLayout.setVisibility(View.GONE);
            uploadVoice();
        } catch (Exception ignored) {}
    }

    private void cancelRecording() {
        if (!isRecording) return;
        try {
            isRecording = false; recorder.stop(); recorder.release(); recorder = null;
            if (normalInputLayout != null) normalInputLayout.setVisibility(View.VISIBLE);
            if (recordingLayout != null) recordingLayout.setVisibility(View.GONE);
            new File(voicePath).delete();
        } catch (Exception ignored) {}
    }

    private void uploadVoice() {
        try {
            File file = new File(voicePath);
            if (!file.exists()) return;
            InputStream is = new FileInputStream(file);
            NetworkUtils.ApiTask task = new NetworkUtils.ApiTask("chats/" + chatId + "/messages/media", "POST", null, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(JSONObject res) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() { if (!isFinishing()) loadInitialMessages(); }
                    });
                }
            });
            task.setFileData(is, "voice.mp3", "audio/mpeg");
            task.start();
        } catch (Exception ignored) {}
    }

    private void downloadMedia(Message msg) {
        String url = NetworkUtils.BASE_URL + "/chats/" + chatId + "/messages/" + msg.id + "/media?token=" + token;
        String name = (msg.mediaName != null && !msg.mediaName.isEmpty()) ? msg.mediaName : getString(R.string.file_prefix) + msg.id;
        LegacyHelper.startDownload(this, url, name);
    }

    @Override protected void onPause() {
        super.onPause();
        if (isRecording) stopRecording();
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 16908332) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    public static class Message {
        long id; String sender, text, mediaType, mediaName; long date; boolean hasMedia, isOut;
        public Message(long id, String s, String t, long d, boolean hm, String mt, boolean io, String mn) {
            this.id = id; this.sender = s; this.text = t; this.date = d; this.hasMedia = hm; this.mediaType = mt; this.isOut = io; this.mediaName = mn;
        }
    }

    public static class MessageAdapter extends ArrayAdapter<Message> {
        private final String token; private final long chatId; private final boolean isTablet, showDate;
        private final int textSize; private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);

        public MessageAdapter(Context ctx, List<Message> list, String t, long cid, boolean d, boolean tab, int ts, boolean sd) {
            super(ctx, 0, list); this.token = t; this.chatId = cid; this.isTablet = tab; this.textSize = ts; this.showDate = sd;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) cv = LayoutInflater.from(getContext()).inflate(R.layout.item_message, parent, false);
            final Message msg = getItem(pos);
            if (msg == null) return cv;

            TextView sender = cv.findViewById(R.id.messageSender);
            TextView text = cv.findViewById(R.id.messageText);
            TextView date = cv.findViewById(R.id.messageDate);
            View root = cv.findViewById(R.id.messageRoot);
            View bubble = cv.findViewById(R.id.messageBubble);
            
            if (sender != null) sender.setText(msg.sender != null ? msg.sender : ""); 
            if (text != null) { 
                text.setText(msg.text != null ? msg.text : ""); 
                text.setTextSize(textSize); 
            }
            if (showDate && date != null) {
                date.setText(sdf.format(new Date(msg.date * 1000)));
            }
            
            if (bubble != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bubble.getLayoutParams();
                if (lp != null) {
                    // 1. Remove bubbles completely
                    bubble.setBackgroundColor(0x00000000);
                    
                    // 2. Alignment logic
                    if (isTablet) {
                        lp.gravity = 0x03; // STRICT LEFT on tablets
                    } else {
                        lp.gravity = msg.isOut ? 0x05 : 0x03; // RIGHT (out) or LEFT (in) on phones
                    }
                    bubble.setLayoutParams(lp);
                }
                if (root != null) {
                    if (isTablet) {
                        // All messages strictly left-aligned on tablets
                        root.setPadding(8, 4, 80, 4); 
                    } else {
                        // Dynamic alignment on phones
                        root.setPadding(msg.isOut ? 80 : 8, 4, msg.isOut ? 8 : 80, 4);
                    }
                }
            }

            ImageView img = cv.findViewById(R.id.messageImage);
            View file = cv.findViewById(R.id.messageFileLayout);
            if (msg.hasMedia && msg.mediaType != null && msg.mediaType.startsWith("image/")) {
                if (img != null) {
                    img.setVisibility(View.VISIBLE);
                    String url = NetworkUtils.BASE_URL + "/chats/" + chatId + "/messages/" + msg.id + "/photo?token=" + token;
                    if (!url.equals(img.getTag())) {
                        img.setTag(url);
                        android.graphics.Bitmap cached = NetworkUtils.getMemoryCache(url);
                        if (cached != null) {
                            img.setImageBitmap(cached);
                        } else {
                            img.setImageResource(android.R.drawable.ic_menu_gallery);
                            new NetworkUtils.ImageTask(img, null).start(url);
                        }
                    }
                    img.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            try {
                                Intent i = new Intent(getContext(), PhotoViewerActivity.class);
                                i.putExtra("url", (String)v.getTag()); getContext().startActivity(i);
                            } catch (Exception ignored) {}
                        }
                    });
                }
                if (file != null) file.setVisibility(View.GONE);
            } else if (msg.hasMedia) {
                if (img != null) img.setVisibility(View.GONE);
                if (file != null) {
                    file.setVisibility(View.VISIBLE);
                    TextView fileNameTv = cv.findViewById(R.id.messageFileName);
                    if (fileNameTv != null) fileNameTv.setText(msg.mediaName != null ? msg.mediaName : getContext().getString(R.string.file_label));
                    
                    View playBtn = cv.findViewById(R.id.messagePlayButton);
                    if (playBtn != null) {
                        if (msg.id == -1) {
                            playBtn.setVisibility(View.GONE);
                        } else if (msg.mediaType != null && (msg.mediaType.contains("audio") || msg.mediaType.contains("video") || msg.mediaType.contains("mpeg") || msg.mediaType.contains("voice") || msg.mediaType.contains("amr"))) {
                            playBtn.setVisibility(View.VISIBLE);
                            if (playBtn instanceof Button) {
                                Button b = (Button) playBtn;
                                b.setText(getContext().getString(R.string.play_symbol) + getContext().getString(R.string.play));
                                TypedValue tval = new TypedValue();
                                getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tval, true);
                                b.setTextColor(tval.data);
                            }
                            playBtn.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    try {
                                        String url = NetworkUtils.BASE_URL + "/chats/" + chatId + "/messages/" + msg.id + "/media?token=" + token;
                                        Intent i = new Intent(getContext(), MediaActivity.class);
                                        i.putExtra("url", url); i.putExtra("type", msg.mediaType);
                                        getContext().startActivity(i);
                                    } catch (Exception ignored) {}
                                }
                            });
                        } else {
                            playBtn.setVisibility(View.GONE);
                        }
                    }

                    file.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (getContext() instanceof MessagesActivity) ((MessagesActivity)getContext()).downloadMedia(msg);
                            else downloadMediaTablet(msg);
                        }
                    });
                }
            } else { 
                if (img != null) img.setVisibility(View.GONE); 
                if (file != null) file.setVisibility(View.GONE); 
            }

            return cv;
        }

        public void downloadMediaTablet(Message msg) {
            String url = NetworkUtils.BASE_URL + "/chats/" + chatId + "/messages/" + msg.id + "/media?token=" + token;
            String name = (msg.mediaName != null && !msg.mediaName.isEmpty()) ? msg.mediaName : getContext().getString(R.string.file_prefix) + msg.id;
            LegacyHelper.startDownload(getContext(), url, name);
        }
    }
}
