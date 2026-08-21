package com.dycrd.muragram;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    private ListView chatList, searchList;
    private ChatAdapter chatAdapter, searchAdapter;
    private boolean isTablet;
    
    private FrameLayout tabletContainer;
    private View searchContainer;
    private View tabletMessagesView;
    private ListView tabletMessageList;
    private MessagesActivity.MessageAdapter tabletMessageAdapter;
    private EditText tabletMessageInput;
    private long selectedChatId = -1;
    private long lastLoadedId = -1;
    private long replyToId = -1;
    private View tabletReplyPreview;
    private TextView tabletReplyText;
    
    private android.media.MediaRecorder recorder;
    private String voicePath;
    private boolean isRecording = false;
    private Uri pendingShareUri;

    private View tabletNormalInput, tabletRecordingLayout;
    private TextView tabletRecordingTimer;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private StringBuilder debugInputBuffer = new StringBuilder();
    private long lastKeyTime = 0;
    
    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            if (!isRecording || isFinishing()) return;
            long millis = System.currentTimeMillis() - startTime;
            int secondsTotal = (int) (millis / 1000);
            int minutes = secondsTotal / 60;
            int seconds = secondsTotal % 60;
            if (tabletRecordingTimer != null) {
                tabletRecordingTimer.setText(getString(R.string.recording_format, getString(R.string.recording_label), minutes, seconds));
            }
            uiHandler.postDelayed(this, 500);
        }
    };

    private static final int MENU_SEARCH = 1001;
    private static final int MENU_SETTINGS = 1002;
    private int searchVersion = 0;
    private final List<Chat> masterResults = Collections.synchronizedList(new ArrayList<Chat>());
    private final Set<Long> foundIds = Collections.synchronizedSet(new HashSet<Long>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            NetworkUtils.updateServer(this);
            if (token == null) {
                startActivity(new Intent(this, LoginActivity.class));
                finish(); return;
            }

            startService(new Intent(this, NotificationService.class));

            setContentView(R.layout.activity_main);
            LegacyHelper.setupActionBar(this);
            isTablet = getResources().getBoolean(R.bool.isTablet);

            chatList = findViewById(R.id.chatList);
            searchContainer = findViewById(R.id.searchContainer);
            searchList = findViewById(R.id.searchList);
            
            if (isTablet) {
                tabletContainer = findViewById(R.id.tabletMessagesContainer);
            }

            if (searchList != null) {
                searchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                        Chat chat = (Chat) p.getItemAtPosition(pos);
                        if (chat != null && chat.id != -1) {
                            if (isTablet) openChatInTablet(chat);
                            else {
                                Intent i = new Intent(MainActivity.this, MessagesActivity.class);
                                i.putExtra("chat_id", chat.id);
                                i.putExtra("chat_name", chat.name);
                                startActivity(i);
                            }
                        }
                    }
                });
            }

            if (chatList != null) {
                chatList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                        Chat chat = (Chat) p.getItemAtPosition(pos);
                        if (chat == null) return;
                        if (chat.id == -1 && getString(R.string.load_failed_retry).equals(chat.name)) {
                            loadChats(""); return;
                        }
                        if (chat.id == -1) return;
                        if (isTablet) {
                            openChatInTablet(chat);
                            if (pendingShareUri != null) {
                                offerToSendImageTablet(pendingShareUri);
                                pendingShareUri = null;
                            }
                        } else {
                            Intent i = new Intent(MainActivity.this, MessagesActivity.class);
                            i.putExtra("chat_id", chat.id); 
                            i.putExtra("chat_name", chat.name);
                            if (pendingShareUri != null) {
                                i.putExtra("share_uri", pendingShareUri.toString());
                                pendingShareUri = null;
                            }
                            startActivity(i);
                        }
                    }
                });
            }

            if (!handleIntent(getIntent())) {
                loadChats("");
            }
            if (getIntent().getBooleanExtra("show_debug", false)) {
                showDebugMenu();
            }
            
            // Hidden debug menu entry for tablets (long click on app logo text)
            if (isTablet) {
                View appLogo = findViewById(R.id.appLogoText);
                if (appLogo != null) {
                    appLogo.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override public boolean onLongClick(View v) {
                            showDebugMenu();
                            return true;
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        if (intent.getBooleanExtra("show_debug", false)) {
            showDebugMenu();
        }
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        try {
            String action = intent.getAction();
            if (Intent.ACTION_SEARCH.equals(action)) {
                String query = intent.getStringExtra(android.app.SearchManager.QUERY);
                loadChats(query);
                return true;
            } else if (Intent.ACTION_VIEW.equals(action)) {
                String data = intent.getDataString();
                if (data != null && data.contains("chats/")) {
                    String part = data.substring(data.lastIndexOf("/") + 1);
                    try {
                        long chatId = Long.parseLong(part);
                        if (isTablet) {
                            openChatInTablet(new Chat(chatId, getString(R.string.chat_label), "", 0, null));
                        } else {
                            Intent i = new Intent(this, MessagesActivity.class);
                            i.putExtra("chat_id", chatId);
                            startActivity(i);
                        }
                    } catch (Exception e) {
                        loadChats(part);
                    }
                }
                return true;
            }

            String type = intent.getType();
            if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
                Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) {
                    if (isTablet && selectedChatId != -1) offerToSendImageTablet(stream);
                    else {
                        pendingShareUri = stream;
                        Toast.makeText(this, R.string.select_chat_to_share, Toast.LENGTH_SHORT).show();
                    }
                }
            }
            long targetId = intent.getLongExtra("chat_id", -1);
            if (targetId != -1) {
                String name = intent.getStringExtra("chat_name");
                if (isTablet) {
                    openChatInTablet(new Chat(targetId, name != null ? name : getString(R.string.chat_label), "", 0, null));
                } else {
                    Intent i = new Intent(this, MessagesActivity.class);
                    i.putExtra("chat_id", targetId);
                    i.putExtra("chat_name", name);
                    startActivity(i);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "handleIntent failed", e);
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu == null) return false;
        LegacyHelper.addSearchToMenu(this, menu, MENU_SEARCH);
        menu.add(0, MENU_SETTINGS, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null) return false;
        int id = item.getItemId();
        if (id == MENU_SEARCH) {
            onSearchRequested();
            return true;
        }
        if (id == MENU_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void loadChats(final String query) {
        final int currentVersion = ++searchVersion;
        if (query == null || query.trim().isEmpty()) {
            if (searchContainer != null) searchContainer.setVisibility(View.GONE);
            if (chatList != null) chatList.setVisibility(View.VISIBLE);
            fetchInitialList(currentVersion);
            return;
        }

        if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
        if (chatList != null) chatList.setVisibility(View.GONE);
        final String cleanQuery = query.startsWith("@") ? query.substring(1) : query;
        final String encoded = encode(cleanQuery);
        
        synchronized (masterResults) { masterResults.clear(); foundIds.clear(); }
        
        // Show loading state in SEARCH list
        updateAdapter(Collections.singletonList(new Chat(-1, getString(R.string.search) + getString(R.string.search_dots), "", 0, null)), true);

        // Local search "at any cost"
        boolean localFound = false;
        if (chatAdapter != null) {
            for (int i = 0; i < chatAdapter.getCount(); i++) {
                Chat c = chatAdapter.getItem(i);
                if (c != null && c.id != -1 && c.name != null && c.name.toLowerCase().contains(cleanQuery.toLowerCase())) {
                    if (!foundIds.contains(c.id)) {
                        foundIds.add(c.id);
                        masterResults.add(c);
                        localFound = true;
                    }
                }
            }
        }
        if (localFound) updateAdapter(new ArrayList<Chat>(masterResults), true);

        new NetworkUtils.ApiTask("chats?q=" + encoded, "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (!isFinishing() && currentVersion == searchVersion) {
                            if (r == null || !r.optBoolean("ok")) {
                                if (masterResults.isEmpty()) {
                                    // Try ALT server if main failed
                                    NetworkUtils.ApiTask retryTask = new NetworkUtils.ApiTask("chats?q=" + encoded, "GET", null, token, new NetworkUtils.ApiCallback() {
                                        @Override public void onResult(final JSONObject r2) {
                                            uiHandler.post(new Runnable() {
                                                @Override public void run() {
                                                    if (!isFinishing() && currentVersion == searchVersion) {
                                                        if (r2 == null || !r2.optBoolean("ok")) {
                                                            if (masterResults.isEmpty()) updateAdapter(Collections.singletonList(new Chat(-1, getString(R.string.response_error), "", 0, null)), true);
                                                        } else { processSearchBatch(r2, false); }
                                                    }
                                                }
                                            });
                                        }
                                    });
                                    retryTask.setBaseUrl(NetworkUtils.SERVER_ALT);
                                    retryTask.start();
                                }
                            } else {
                                processSearchBatch(r, false);
                            }
                        }
                    }
                });
            }
        }).start();

        new NetworkUtils.ApiTask("users/" + cleanQuery + "/info", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || currentVersion != searchVersion || r == null || !r.optBoolean("ok")) return;
                        try {
                            JSONObject d = r.getJSONObject("data");
                            Chat c = new Chat(d.getLong("id"), d.optString("title", d.optString("username")), "", 0, d.optString("photo_path"));
                            c.username = d.optString("username"); c.isGlobal = true;
                            synchronized (masterResults) {
                                if (!foundIds.contains(c.id)) { 
                                    foundIds.add(c.id); 
                                    masterResults.add(c); 
                                    updateAdapter(new ArrayList<Chat>(masterResults), true); 
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void processSearchBatch(JSONObject r, boolean isGlobal) {
        if (r == null || !r.optBoolean("ok") || isFinishing()) return;
        try {
            JSONArray d = r.getJSONArray("data");
            synchronized (masterResults) {
                for (int i = 0; i < d.length(); i++) {
                    Chat c = parseChat(d.getJSONObject(i), isGlobal);
                    if (c != null && !foundIds.contains(c.id)) { foundIds.add(c.id); masterResults.add(c); }
                }
                updateAdapter(new ArrayList<Chat>(masterResults), true);
            }
        } catch (Exception ignored) {}
    }

    private void offerToSendImageTablet(final Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(this).setTitle(R.string.share_confirm_title).setMessage(R.string.share_confirm_msg)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { uploadFileTablet(uri); }
            }).setNegativeButton(R.string.cancel, null).show();
    }

    private void fetchInitialList(final int version) {
        new NetworkUtils.ApiTask("chats", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || version != searchVersion) return;
                        try {
                            if (r != null && r.optBoolean("ok")) {
                                JSONArray d = r.getJSONArray("data");
                                List<Chat> list = new ArrayList<Chat>();
                                for (int i = 0; i < d.length(); i++) list.add(parseChat(d.getJSONObject(i), false));
                                saveChatsToCache(r.toString());
                                updateAdapter(list, false);
                            } else {
                                String err = (r != null) ? r.optString("error", getString(R.string.unknown_error)) : getString(R.string.network_error);
                                Log.e(TAG, "Fetch chats failed: " + err);
                                updateAdapter(Collections.singletonList(new Chat(-1, getString(R.string.load_failed_retry), "", 0, null)), false);
                            }
                        } catch (Exception e) {
                            updateAdapter(Collections.singletonList(new Chat(-1, getString(R.string.load_failed_retry), "", 0, null)), false);
                        }
                    }
                });
            }
        }).start();
    }

    private void openChatInTablet(final Chat chat) {
        if (chat == null || chat.id == -1 || isFinishing()) return;
        selectedChatId = chat.id;
        
        try {
            View placeholder = findViewById(R.id.tabletPlaceholder);
            if (placeholder != null) placeholder.setVisibility(View.GONE);
            
            TextView chatNameTv = findViewById(R.id.tabletChatName);
            if (chatNameTv != null) chatNameTv.setText(chat.name);

            if (tabletMessagesView == null && tabletContainer != null) {
                tabletMessagesView = LayoutInflater.from(this).inflate(R.layout.activity_messages, tabletContainer, false);
                tabletContainer.removeAllViews(); 
                tabletContainer.addView(tabletMessagesView);
                
                if (tabletMessagesView != null) {
                    tabletMessageList = tabletMessagesView.findViewById(R.id.messageList);
                    tabletMessageInput = tabletMessagesView.findViewById(R.id.messageInput);
                    tabletReplyPreview = tabletMessagesView.findViewById(R.id.replyPreview);
                    tabletReplyText = tabletMessagesView.findViewById(R.id.replyText);
                    
                    View closeReply = tabletMessagesView.findViewById(R.id.closeReply);
                    if (closeReply != null) {
                        closeReply.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { cancelReplyTablet(); }
                        });
                    }

                    View sendBtn = tabletMessagesView.findViewById(R.id.sendButton);
                    View attachBtn = tabletMessagesView.findViewById(R.id.attachButton);
                    View micBtn = tabletMessagesView.findViewById(R.id.micButton);
                    
                    if (micBtn instanceof Button) {
                        ((Button)micBtn).setText(R.string.mic_symbol);
                    } else if (micBtn instanceof TextView) {
                        ((TextView)micBtn).setText(R.string.mic_symbol);
                    }
                    
                    if (sendBtn != null) {
                        sendBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { sendMessageTablet(); }
                        });
                    }
                    if (attachBtn != null) {
                        attachBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                try {
                                    Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("*/*");
                                    startActivityForResult(i, 200);
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                    tabletNormalInput = tabletMessagesView.findViewById(R.id.normalInputLayout);
                    tabletRecordingLayout = tabletMessagesView.findViewById(R.id.recordingLayout);
                    tabletRecordingTimer = tabletMessagesView.findViewById(R.id.recordingTimer);
                    View stopRecBtn = tabletMessagesView.findViewById(R.id.stopRecordingButton);
                    View cancelRecBtn = tabletMessagesView.findViewById(R.id.cancelRecordingButton);

                    if (micBtn != null) {
                        micBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { startRecordingTablet(); }
                        });
                    }
                    if (stopRecBtn != null) {
                        stopRecBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { stopRecordingTablet(); }
                        });
                    }
                    if (cancelRecBtn != null) {
                        cancelRecBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { cancelRecordingTablet(); }
                        });
                    }
                    
                    if (tabletMessageList != null) {
                        tabletMessageList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                                Object item = p.getItemAtPosition(pos);
                                if (item instanceof MessagesActivity.Message) {
                                    showMessageOptionsTablet((MessagesActivity.Message) item);
                                }
                                return true;
                            }
                        });
                    }
                }
            }
            
            if (tabletMessageList != null) tabletMessageList.setAdapter(null);
            cancelReplyTablet();
            tabletMessageAdapter = null;
            loadMessagesTablet();
        } catch (Exception e) {
            Log.e(TAG, "openChatInTablet failed", e);
        }
    }

    private void showMessageOptionsTablet(final MessagesActivity.Message msg) {
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
                    if (getString(R.string.reply).equals(action)) startReplyTablet(msg);
                    else if (getString(R.string.copy_text).equals(action)) {
                        android.text.ClipboardManager cm = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setText(msg.text);
                    } else if (getString(R.string.delete).equals(action)) deleteMessageTablet(msg);
                    else if (getString(R.string.download_media).equals(action)) {
                        if (tabletMessageAdapter != null) tabletMessageAdapter.downloadMediaTablet(msg);
                    }
                }
            }).show();
        } catch (Exception ignored) {}
    }

    private void startReplyTablet(MessagesActivity.Message m) {
        if (m == null) return;
        replyToId = m.id;
        if (tabletReplyPreview != null) {
            tabletReplyPreview.setVisibility(View.VISIBLE);
            if (tabletReplyText != null) {
                String label = m.sender + ": " + (m.text != null && !m.text.isEmpty() ? m.text : getString(R.string.media_label));
                tabletReplyText.setText(label);
            }
        }
    }

    private void cancelReplyTablet() {
        replyToId = -1;
        if (tabletReplyPreview != null) tabletReplyPreview.setVisibility(View.GONE);
    }

    private void deleteMessageTablet(final MessagesActivity.Message msg) {
        if (msg == null) return;
        new NetworkUtils.ApiTask("chats/" + selectedChatId + "/messages/" + msg.id, "DELETE", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() { if (!isFinishing()) loadMessagesTablet(); }
                });
            }
        }).start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null && requestCode == 200) uploadFileTablet(data.getData());
    }

    private void uploadFileTablet(Uri uri) {
        if (uri == null) return;
        try {
            String mime = getContentResolver().getType(uri);
            String name = FileCompat.getDisplayName(this, uri);
            if (name == null) name = uri.getLastPathSegment();
            InputStream is = getContentResolver().openInputStream(uri);
            NetworkUtils.ApiTask t = new NetworkUtils.ApiTask("chats/" + selectedChatId + "/messages/media", "POST", null, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(JSONObject res) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() { if (!isFinishing()) loadMessagesTablet(); }
                    });
                }
            });
            t.setFileData(is, name, mime);
            t.start();
        } catch (Exception ignored) {}
    }

    private void loadMessagesTablet() {
        if (selectedChatId == -1 || tabletMessageList == null || isFinishing()) return;
        final long currentChatId = selectedChatId;
        lastLoadedId = currentChatId;

        new NetworkUtils.ApiTask("chats/" + selectedChatId + "/messages", "GET", null, token, new NetworkUtils.ApiCallback() {
            @Override public void onResult(final JSONObject r) {
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || r == null || currentChatId != lastLoadedId) return;
                        try {
                            if (r.optBoolean("ok")) {
                                JSONArray d = r.getJSONArray("data");
                                List<MessagesActivity.Message> list = new ArrayList<MessagesActivity.Message>();
                                for (int i = 0; i < d.length(); i++) {
                                    JSONObject o = d.getJSONObject(i);
                                    list.add(new MessagesActivity.Message(
                                        o.getLong("id"), o.getString("sender"), o.optString("text", ""),
                                        o.getLong("date"), o.optBoolean("has_media", false),
                                        o.optString("media_type", ""), o.optBoolean("is_out", false), 
                                        o.optString("media_name", "")
                                    ));
                                }
                                tabletMessageAdapter = new MessagesActivity.MessageAdapter(MainActivity.this, list, token, currentChatId, isDark, true, 16, true);
                                if (tabletMessageList != null) {
                                    tabletMessageList.setAdapter(tabletMessageAdapter);
                                    if (!list.isEmpty()) tabletMessageList.setSelection(list.size() - 1);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void sendMessageTablet() {
        if (tabletMessageInput == null || isFinishing()) return;
        final String text = tabletMessageInput.getText().toString();
        if (text.isEmpty()) return;
        try {
            JSONObject p = new JSONObject(); p.put("text", text);
            if (replyToId != -1) p.put("reply_to_id", replyToId);
            new NetworkUtils.ApiTask("chats/" + selectedChatId + "/messages", "POST", p, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(final JSONObject r) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || r == null) return;
                            if (r.optBoolean("ok")) { 
                                if (tabletMessageInput != null) tabletMessageInput.setText(""); 
                                cancelReplyTablet(); 
                                loadMessagesTablet(); 
                            }
                        }
                    });
                }
            }).start();
        } catch (Exception ignored) {}
    }

    private void startRecordingTablet() {
        try {
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) return;
            voicePath = cacheDir.getAbsolutePath() + "/voice.mp3";
            recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            // Reverted to MPEG_4 / AAC which is often used for higher quality "mp3-like" voice messages
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(voicePath);
            recorder.prepare(); recorder.start();
            isRecording = true; startTime = System.currentTimeMillis();
            if (tabletNormalInput != null) tabletNormalInput.setVisibility(View.GONE);
            if (tabletRecordingLayout != null) tabletRecordingLayout.setVisibility(View.VISIBLE);
            uiHandler.post(timerRunnable);
        } catch (Exception e) { Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show(); }
    }

    private void stopRecordingTablet() {
        if (!isRecording) return;
        try {
            isRecording = false; recorder.stop(); recorder.release(); recorder = null;
            if (tabletNormalInput != null) tabletNormalInput.setVisibility(View.VISIBLE);
            if (tabletRecordingLayout != null) tabletRecordingLayout.setVisibility(View.GONE);
            uploadVoiceTablet();
        } catch (Exception ignored) {}
    }

    private void cancelRecordingTablet() {
        if (!isRecording) return;
        try {
            isRecording = false; recorder.stop(); recorder.release(); recorder = null;
            if (tabletNormalInput != null) tabletNormalInput.setVisibility(View.VISIBLE);
            if (tabletRecordingLayout != null) tabletRecordingLayout.setVisibility(View.GONE);
            new File(voicePath).delete();
        } catch (Exception ignored) {}
    }

    private void uploadVoiceTablet() {
        try {
            File f = new File(voicePath);
            if (!f.exists()) return;
            FileInputStream fis = new FileInputStream(f);
            NetworkUtils.ApiTask task = new NetworkUtils.ApiTask("chats/" + selectedChatId + "/messages/media", "POST", null, token, new NetworkUtils.ApiCallback() {
                @Override public void onResult(JSONObject res) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() { if (!isFinishing()) loadMessagesTablet(); }
                    });
                }
            });
            task.setFileData(fis, "voice.mp3", "audio/mpeg");
            task.start();
        } catch (Exception ignored) {}
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private Chat parseChat(JSONObject o, boolean isGlobal) {
        try {
            Chat c = new Chat(o.getLong("id"), o.getString("name"), o.optString("last_message", ""), o.optInt("unread_count", 0), o.optString("photo_path"));
            c.username = o.optString("username", ""); c.isGlobal = isGlobal;
            return c;
        } catch (Exception e) { return null; }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode >= android.view.KeyEvent.KEYCODE_0 && keyCode <= android.view.KeyEvent.KEYCODE_9) {
                long now = System.currentTimeMillis();
                if (now - lastKeyTime > 2000) debugInputBuffer.setLength(0);
                lastKeyTime = now;
                debugInputBuffer.append(keyCode - android.view.KeyEvent.KEYCODE_0);
                checkDebugCode();
            } else if (keyCode == android.view.KeyEvent.KEYCODE_STAR || keyCode == android.view.KeyEvent.KEYCODE_POUND) {
                long now = System.currentTimeMillis();
                if (now - lastKeyTime > 2000) debugInputBuffer.setLength(0);
                lastKeyTime = now;
                debugInputBuffer.append(keyCode == android.view.KeyEvent.KEYCODE_STAR ? "*" : "#");
                checkDebugCode();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    private void checkDebugCode() {
        String code = debugInputBuffer.toString();
        if (code.equals("*#*#68724726#*#*")) {
            debugInputBuffer.setLength(0);
            showDebugMenu();
        }
    }

    private void showDebugMenu() {
        if (isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.debug_menu_title);
        String[] items = {
            getString(R.string.debug_wipe_cache),
            getString(R.string.debug_show_logs),
            getString(R.string.debug_server_status),
            getString(R.string.debug_clear_logs),
            getString(R.string.debug_custom_server),
            getString(R.string.debug_wipe_restart),
            getString(R.string.debug_show_token)
        };
        b.setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                if (w == 0) {
                    synchronized (NetworkUtils.imageCache) { NetworkUtils.imageCache.clear(); }
                    Toast.makeText(MainActivity.this, R.string.debug_cache_wiped, Toast.LENGTH_SHORT).show();
                } else if (w == 1) {
                    showLogsDialog();
                } else if (w == 2) {
                    Toast.makeText(MainActivity.this, getString(R.string.server_label) + ": " + NetworkUtils.BASE_URL, Toast.LENGTH_LONG).show();
                } else if (w == 3) {
                    MuragramLogger.clear();
                    Toast.makeText(MainActivity.this, R.string.debug_clear_logs, Toast.LENGTH_SHORT).show();
                } else if (w == 4) {
                    showCustomServerDialog();
                } else if (w == 5) {
                    synchronized (NetworkUtils.imageCache) { NetworkUtils.imageCache.clear(); }
                    getSharedPreferences(NetworkUtils.PREFS_NAME, 0).edit().clear().commit();
                    finish();
                    startActivity(new Intent(MainActivity.this, MainActivity.class));
                } else if (w == 6) {
                    showTokenDialog();
                }
            }
        });
        b.show();
    }

    private void showTokenDialog() {
        TextView tv = new TextView(this);
        tv.setPadding(20, 20, 20, 20);
        tv.setText(token != null ? token : getString(R.string.none));
        tv.setTextSize(12);
        new AlertDialog.Builder(this)
            .setTitle(R.string.debug_token_title)
            .setView(tv)
            .setPositiveButton(R.string.copy, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    android.text.ClipboardManager cm = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && token != null) {
                        cm.setText(token);
                        Toast.makeText(MainActivity.this, R.string.text_copied, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    private void showCustomServerDialog() {
        final EditText input = new EditText(this);
        input.setText(NetworkUtils.BASE_URL);
        new AlertDialog.Builder(this)
            .setTitle(R.string.debug_custom_server_title)
            .setView(input)
            .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        NetworkUtils.BASE_URL = newUrl;
                        getSharedPreferences(NetworkUtils.PREFS_NAME, 0).edit().putString(NetworkUtils.KEY_SERVER, newUrl).commit();
                        MuragramLogger.log("Debug", "Server changed to: " + newUrl);
                        Toast.makeText(MainActivity.this, R.string.server_changed, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showLogsDialog() {
        TextView tv = new TextView(this);
        tv.setTextSize(10);
        tv.setPadding(10, 10, 10, 10);
        tv.setText(MuragramLogger.getLogs());
        
        // Add scrollability
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.debug_logs_title)
            .setView(sv)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.debug_refresh, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { showLogsDialog(); }
            })
            .show();
    }

    private void updateAdapter(List<Chat> list, boolean isSearchUpdate) {
        if (list == null) list = new ArrayList<Chat>();
        
        if (isSearchUpdate) {
            // Update SEARCH list
            searchAdapter = new ChatAdapter(this, list, token);
            if (searchList != null) searchList.setAdapter(searchAdapter);
        } else {
            // Update MAIN list
            if (chatAdapter == null) {
                chatAdapter = new ChatAdapter(this, new ArrayList<Chat>(list), token);
                if (chatList != null) chatList.setAdapter(chatAdapter);
            } else {
                chatAdapter.setNotifyOnChange(false);
                chatAdapter.clear();
                for (int i = 0; i < list.size(); i++) {
                    Chat c = list.get(i);
                    if (c != null) chatAdapter.add(c);
                }
                chatAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (isRecording) stopRecordingTablet();
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }

    private void saveChatsToCache(String json) {
        try {
            getSharedPreferences(NetworkUtils.PREFS_NAME, 0).edit().putString("cached_chats_json", json).commit();
        } catch (Exception ignored) {}
    }

    public static class Chat {
        long id; String name, lastMessage, photoPath, username; int unreadCount; boolean isGlobal;
        public Chat(long id, String name, String lm, int u, String p) {
            this.id = id; this.name = name; this.lastMessage = lm; this.unreadCount = u; this.photoPath = p;
        }
    }

    private static class ChatAdapter extends ArrayAdapter<Chat> {
        private final String token;
        public ChatAdapter(Context ctx, List<Chat> list, String t) { super(ctx, 0, list); this.token = t; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public int getItemViewType(int pos) { return 0; }
        @Override public boolean areAllItemsEnabled() { return true; }
        @Override public boolean isEnabled(int pos) { return true; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) cv = LayoutInflater.from(getContext()).inflate(R.layout.item_chat, parent, false);
            Chat chat = getItem(pos);
            if (chat == null) return cv;

            TextView name = cv.findViewById(R.id.chatName);
            TextView msg = cv.findViewById(R.id.lastMessage);
            ImageView icon = cv.findViewById(R.id.chatIcon);

            if (name != null) name.setText(chat.name);
            if (msg != null) msg.setText(chat.lastMessage);

            if (icon != null) {
                if (chat.id == -1) {
                    icon.setVisibility(View.GONE);
                } else {
                    icon.setVisibility(View.VISIBLE);
                    if (chat.photoPath != null && !chat.photoPath.isEmpty()) {
                        String photoUrl = NetworkUtils.BASE_URL + chat.photoPath + "?token=" + token;
                        if (!photoUrl.equals(icon.getTag())) {
                            icon.setTag(photoUrl);
                            android.graphics.Bitmap cached = NetworkUtils.getMemoryCache(photoUrl);
                            if (cached != null) {
                                icon.setImageBitmap(cached);
                            } else {
                                icon.setImageResource(android.R.drawable.sym_def_app_icon);
                                new NetworkUtils.ImageTask(icon, null).start(photoUrl);
                            }
                        }
                    } else {
                        icon.setTag(null);
                        icon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                }
            }
            
            if (chat.id == -1 && msg != null) {
                msg.setText("");
            }

            return cv;
        }
    }
}
