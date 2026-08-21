package com.dycrd.muragram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.X509Certificate;

public class NetworkUtils {
    public static String BASE_URL = "http://muragram.muror.qzz.io/api/v1";
    public static final String SERVER_MAIN = "http://muragram.muror.qzz.io/api/v1";
    public static final String SERVER_ALT = "http://mura.alwaysdata.net/api/v1";
    
    public static final String PREFS_NAME = "MuragramPrefs";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_SERVER = "server_address";
    public static final String KEY_DARK_THEME = "dark_theme";
    public static final String KEY_SEARCH_DELAY = "search_delay";
    public static final String KEY_TEXT_SIZE = "chat_text_size";
    public static final String KEY_SHOW_DATE = "show_date";
    public static final String KEY_NOTIFY_SOUND = "notify_sound";
    public static final String KEY_NOTIFY_VIBRATE = "notify_vibrate";
    public static final String KEY_NOTIFY_DELAY = "notify_delay";
    public static final String KEY_WHITELIST = "notify_whitelist";
    public static final String KEY_WHITELIST_ENABLED = "notify_whitelist_enabled";

    public interface ApiCallback {
        void onResult(JSONObject result);
    }

    public interface ImageCallback {
        void onBitmap(Bitmap bitmap);
    }

    static final Map<String, Bitmap> imageCache = new LinkedHashMap<String, Bitmap>(20, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) { return size() > 50; }
    };

    public static Bitmap getMemoryCache(String url) {
        if (url == null) return null;
        synchronized (imageCache) { return imageCache.get(url); }
    }

    public static void updateServer(Context context) {
        BASE_URL = context.getSharedPreferences(PREFS_NAME, 0).getString(KEY_SERVER, SERVER_MAIN);
    }

    public static class ImageTask extends AsyncTask<Object, Void, Bitmap> {
        private final ImageCallback callback;
        private String url;
        private final WeakReference<android.widget.ImageView> ivRef;
        private final Context context;

        public ImageTask(android.widget.ImageView iv, ImageCallback callback) {
            this.ivRef = new WeakReference<android.widget.ImageView>(iv);
            this.callback = callback;
            this.context = (iv != null) ? iv.getContext().getApplicationContext() : null;
        }

        @Override
        protected Bitmap doInBackground(Object... urls) {
            this.url = (String) urls[0];
            if (url == null) return null;
            
            synchronized (imageCache) { 
                if (imageCache.containsKey(url)) return imageCache.get(url); 
            }
            
            String fileName = "cache_" + url.hashCode();
            if (context != null) {
                File cacheFile = new File(context.getCacheDir(), fileName);
                if (cacheFile.exists()) {
                    try {
                        FileInputStream fis = new FileInputStream(cacheFile);
                        Bitmap b = BitmapFactory.decodeStream(fis);
                        fis.close();
                        if (b != null) {
                            synchronized (imageCache) { imageCache.put(url, b); }
                            return b;
                        }
                    } catch (Exception ignored) {}
                }
            }

            int redirectCount = 0;
            String currentUrl = url;
            
            while (redirectCount < 5) {
                HttpURLConnection conn = null;
                InputStream is = null;
                try {
                    URL u = new URL(currentUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    
                    if (conn instanceof HttpsURLConnection) {
                        try {
                            java.lang.reflect.Method setHostnameMethod = conn.getClass().getMethod("setHostname", String.class);
                            setHostnameMethod.invoke(conn, u.getHost());
                        } catch (Exception ignored) {}
                        ((HttpsURLConnection)conn).setSSLSocketFactory(getLegacySSLSocketFactory());
                    }

                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Compatible; Muragram Legacy)");
                    conn.setRequestProperty("Accept-Encoding", "gzip");
                    
                    conn.connect();
                    int code = conn.getResponseCode();
                    MuragramLogger.log("MuragramImage", "RES: " + code + " for " + this.url);

                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String location = conn.getHeaderField("Location");
                        if (location != null) {
                            if (location.startsWith("/")) {
                                location = u.getProtocol() + "://" + u.getHost() + location;
                            }
                            currentUrl = location;
                            redirectCount++;
                            conn.disconnect();
                            continue;
                        }
                    }

                    if (code >= 400) return null;
                    
                    String encoding = conn.getContentEncoding();
                    is = conn.getInputStream();
                    if ("gzip".equalsIgnoreCase(encoding)) {
                        is = new GZIPInputStream(is);
                    }
                    is = new BufferedInputStream(is, 8192);
                    
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    
                    byte[] data = readStream(is);
                    if (data == null) return null;
                    
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                    if (bitmap != null) {
                        synchronized (imageCache) { imageCache.put(url, bitmap); }
                        if (context != null) {
                            try {
                                File cacheFile = new File(context.getCacheDir(), fileName);
                                FileOutputStream fos = new FileOutputStream(cacheFile);
                                fos.write(data);
                                fos.close();
                            } catch (Exception ignored) {}
                        }
                    }
                    return bitmap;
                } catch (Exception e) { 
                    MuragramLogger.log("MuragramImage", "Error loading image: " + currentUrl + " - " + e.getMessage());
                    return null; 
                }
                finally {
                    try { if (is != null) is.close(); } catch (Exception e) {}
                    if (conn != null) conn.disconnect();
                }
            }
            return null;
        }

        private byte[] readStream(InputStream is) {
            try {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return buffer.toByteArray();
            } catch (Exception e) { return null; }
        }

        public void start(String url) {
            if (android.os.Build.VERSION.SDK_INT >= 11) {
                CompatExecutor.execute(this, new Object[]{url});
            } else {
                execute(url);
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            android.widget.ImageView iv = ivRef.get();
            if (iv != null && url != null && url.equals(iv.getTag())) {
                if (bitmap != null) iv.setImageBitmap(bitmap);
                else iv.setImageResource(android.R.drawable.btn_star);
            }
            if (callback != null) callback.onBitmap(bitmap);
        }
    }

    public static class ApiTask extends AsyncTask<Object, Void, JSONObject> {
        private final String path, method, token;
        private String fileName, mimeType, customBase;
        private final JSONObject body;
        private final ApiCallback callback;
        private InputStream fileStream;

        public ApiTask(String path, String method, JSONObject body, String token, ApiCallback callback) {
            this.path = path; this.method = method; this.body = body; this.token = token; this.callback = callback;
        }

        public void setBaseUrl(String base) { this.customBase = base; }

        public void setFileData(InputStream stream, String name, String mime) {
            this.fileStream = stream; this.fileName = name; this.mimeType = mime;
        }

        public void start() {
            if (android.os.Build.VERSION.SDK_INT >= 11) {
                CompatExecutor.execute(this, new Object[0]);
            } else {
                execute();
            }
        }

        @Override
        protected JSONObject doInBackground(Object... params) {
            int redirectCount = 0;
            String activeBase = (customBase != null) ? customBase : BASE_URL;
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            StringBuilder urlBuilder = new StringBuilder(activeBase).append("/").append(cleanPath);
            if (token != null) {
                urlBuilder.append(urlBuilder.toString().contains("?") ? "&" : "?").append("token=").append(token);
            }
            urlBuilder.append(urlBuilder.toString().contains("?") ? "&" : "?").append("_ts=").append(System.currentTimeMillis());
            String currentUrl = urlBuilder.toString();
            String currentMethod = method;
            MuragramLogger.log("MuragramApi", "REQ: " + method + " " + path);

            while (redirectCount < 5) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(currentUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    
                    if (conn instanceof HttpsURLConnection) {
                        try {
                            java.lang.reflect.Method setHostnameMethod = conn.getClass().getMethod("setHostname", String.class);
                            setHostnameMethod.invoke(conn, url.getHost());
                        } catch (Exception ignored) {}
                        ((HttpsURLConnection)conn).setSSLSocketFactory(getLegacySSLSocketFactory());
                    }

                    conn.setRequestMethod(currentMethod);
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(false); 
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Compatible; Muragram Legacy)");
                    conn.setRequestProperty("Accept-Encoding", "gzip");
                    conn.setRequestProperty("Connection", "close"); 

                    if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
                    
                    if (fileStream != null && redirectCount == 0) {
                        String boundary = "MuragramBoundary" + System.currentTimeMillis();
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                        OutputStream os = conn.getOutputStream();
                        DataOutputStream dos = new DataOutputStream(os);
                        
                        dos.writeBytes("--" + boundary + "\r\n");
                        String safeName = fileName != null ? fileName.replace("\"", "_").replace("/", "_") : "file";
                        dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n");
                        dos.writeBytes("Content-Type: " + (mimeType != null ? mimeType : "application/octet-stream") + "\r\n\r\n");
                        
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = fileStream.read(buf)) != -1) {
                            dos.write(buf, 0, read);
                        }
                        
                        dos.writeBytes("\r\n--" + boundary + "--\r\n");
                        dos.flush(); dos.close();
                    } else if (body != null && redirectCount == 0) {
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json");
                        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
                        wr.write(body.toString());
                        wr.flush(); wr.close();
                    }

                    int code = conn.getResponseCode();
                    MuragramLogger.log("MuragramApi", "RES: " + code + " for " + this.path);
                    
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String location = conn.getHeaderField("Location");
                        if (location != null) {
                            if (location.startsWith("/")) {
                                URL base = new URL(currentUrl);
                                location = base.getProtocol() + "://" + base.getHost() + location;
                            }
                            if (!location.contains("?") && currentUrl.contains("?")) {
                                location += currentUrl.substring(currentUrl.indexOf("?"));
                            }
                            currentUrl = location;
                            if (code == 301 || code == 302 || code == 303) currentMethod = "GET";
                            redirectCount++;
                            conn.disconnect();
                            continue;
                        }
                    }

                    String encoding = conn.getContentEncoding();
                    InputStream s = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
                    if (s == null) return null;
                    
                    if ("gzip".equalsIgnoreCase(encoding)) {
                        s = new GZIPInputStream(s);
                    }
                    
                    BufferedReader r = new BufferedReader(new InputStreamReader(s, "UTF-8"), 8192);
                    StringBuilder res = new StringBuilder(); String l;
                    while ((l = r.readLine()) != null) res.append(l);
                    r.close();
                    return new JSONObject(res.toString());
                } catch (Exception e) { 
                    MuragramLogger.log("MuragramApi", "Request error on " + path + ": " + e.getMessage());
                    return null; 
                }
                finally { 
                    if (conn != null) conn.disconnect();
                    try { if (fileStream != null) fileStream.close(); } catch (Exception e) {}
                }
            }
            return null;
        }

        @Override protected void onPostExecute(JSONObject result) {
            if (callback != null) {
                callback.onResult(result);
            }
        }

        public static JSONObject executeSync(String path, String method, String token) {
            return executeSync(path, method, token, null);
        }

        public static JSONObject executeSync(String path, String method, String token, String customBase) {
            try {
                String base = (customBase != null) ? customBase : BASE_URL;
                String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                String fullUrl = base + "/" + cleanPath;
                if (token != null) {
                    fullUrl += (fullUrl.contains("?") ? "&" : "?") + "token=" + token;
                }
                
                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                if (conn instanceof HttpsURLConnection) {
                    ((HttpsURLConnection)conn).setSSLSocketFactory(getLegacySSLSocketFactory());
                }

                conn.setRequestMethod(method);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Compatible; Muragram Legacy)");
                conn.setRequestProperty("Connection", "close");

                if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);

                int code = conn.getResponseCode();
                if (code >= 400) return null;

                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"), 8192);
                StringBuilder res = new StringBuilder(); String l;
                while ((l = r.readLine()) != null) res.append(l);
                r.close();
                conn.disconnect();
                return new JSONObject(res.toString());
            } catch (Exception e) {
                return null;
            }
        }
    }
    
    private static SSLSocketFactory legacySSLSocketFactory;
    
    private static synchronized SSLSocketFactory getLegacySSLSocketFactory() {
        if (legacySSLSocketFactory != null) return legacySSLSocketFactory;
        try {
            TrustManager[] tm = new TrustManager[]{ new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) { }
                public void checkServerTrusted(X509Certificate[] c, String a) { }
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, tm, new java.security.SecureRandom());
            legacySSLSocketFactory = sc.getSocketFactory();
        } catch (Exception e) {
            Log.e("MuragramSSL", "Failed to create SSLFactory", e);
        }
        return legacySSLSocketFactory;
    }

    static {
        if (android.os.Build.VERSION.SDK_INT < 11) {
            System.setProperty("http.keepAlive", "false");
        }
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(getLegacySSLSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true; }
            });
        } catch (Exception e) {}
    }
}
