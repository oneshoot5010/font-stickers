package com.fontcopy.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String REMOTE_URL = "https://raw.githubusercontent.com/oneshoot5010/font-stickers/main/styles.json";
    static final int REQ_FILE = 7101;
    ValueCallback<Uri[]> fileCb;
    WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0B0B1E"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B0B1E"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B0B1E"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // lets <input type="file"> (gallery picker for the background photo) open
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb.onReceiveValue(null);
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh keyboard setup status when the user comes back from system settings
        if (web != null) web.evaluateJavascript("if(window.onAndroidResume)onAndroidResume()", null);
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("!!(window.onAndroidBack&&onAndroidBack())", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    // kbhist: last copied styles, shown in the keyboard history tab
    void saveHist(String t) {
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        String cur = sp.getString("hist", "");
        ArrayList<String> l = new ArrayList<>();
        if (cur != null && !cur.isEmpty()) {
            for (String x : cur.split("\\u0001")) {
                if (!x.isEmpty() && !x.equals(t)) l.add(x);
            }
        }
        l.add(0, t);
        while (l.size() > 12) l.remove(l.size() - 1);
        sp.edit().putString("hist", TextUtils.join("\u0001", l)).apply();
    }

    static volatile boolean voiceBusy = false;

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (web != null) web.evaluateJavascript("if(window.onVoice)onVoice(-3)", null);
    }

    static final String UPD_URL = "https://github.com/oneshoot5010/font-stickers/releases/latest/download/latest.json";
    static volatile boolean updBusy;

    void jsCall(String js) {
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    // hands the downloaded apk to the system installer (asks once for "install unknown apps" permission)
    void installApk() {
        try {
            File f = new File(new File(getCacheDir(), "update"), "update.apk");
            if (!f.exists()) {
                jsCall("if(window.onUpdProg)onUpdProg(-2)");
                return;
            }
            if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                jsCall("if(window.onUpdProg)onUpdProg(-4)");
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                return;
            }
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            jsCall("if(window.onUpdProg)onUpdProg(101)");
        } catch (Exception e) {
            jsCall("if(window.onUpdProg)onUpdProg(-2)");
        }
    }

    class Bridge {
        @JavascriptInterface
        public int appVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public void updateCheck() {
            new Thread(() -> {
                String res = "";
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(UPD_URL).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setInstanceFollowRedirects(true);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    try (java.io.InputStream in = c.getInputStream()) {
                        byte[] b = new byte[4096];
                        int r;
                        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
                    }
                    res = bo.toString("UTF-8");
                } catch (Exception e) {
                    res = "";
                }
                jsCall("if(window.onUpdate)onUpdate(" + org.json.JSONObject.quote(res) + ")");
            }).start();
        }

        @JavascriptInterface
        public void updateDownload(String url) {
            if (url == null || !url.startsWith("https://github.com/")) return;
            if (updBusy) return;
            updBusy = true;
            new Thread(() -> {
                boolean ok = false;
                try {
                    File dir = new File(getCacheDir(), "update");
                    dir.mkdirs();
                    File f = new File(dir, "update.apk");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    long total = c.getContentLengthLong(), done = 0;
                    int last = -1;
                    try (java.io.InputStream in = c.getInputStream(); java.io.FileOutputStream fo = new java.io.FileOutputStream(f)) {
                        byte[] b = new byte[65536];
                        int r;
                        while ((r = in.read(b)) > 0) {
                            fo.write(b, 0, r);
                            done += r;
                            if (total > 0) {
                                int pct = (int) (done * 100 / total);
                                if (pct != last && pct < 100) {
                                    last = pct;
                                    jsCall("if(window.onUpdProg)onUpdProg(" + pct + ")");
                                }
                            }
                        }
                    }
                    ok = f.length() > 100000 && (total <= 0 || f.length() == total);
                } catch (Exception e) {
                    ok = false;
                }
                updBusy = false;
                if (ok) {
                    runOnUiThread(() -> installApk());
                } else {
                    jsCall("if(window.onUpdProg)onUpdProg(-2)");
                    runOnUiThread(() -> openBrowser(url));
                }
            }).start();
        }

        @JavascriptInterface
        public void updateInstall() {
            runOnUiThread(() -> installApk());
        }

        @JavascriptInterface
        public void keyboardSettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        }

        @JavascriptInterface
        public void keyboardPicker() {
            runOnUiThread(() -> {
                InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (m != null) m.showInputMethodPicker();
            });
        }

        // true if our keyboard is enabled in system settings
        @JavascriptInterface
        public boolean isKeyboardEnabled() {
            InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (m == null) return false;
            for (InputMethodInfo i : m.getEnabledInputMethodList()) {
                if (getPackageName().equals(i.getPackageName())) return true;
            }
            return false;
        }

        // true if our keyboard is the currently selected one
        @JavascriptInterface
        public boolean isKeyboardSelected() {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            return id != null && id.startsWith(getPackageName() + "/");
        }

        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "مشاركة"));
            });
        }

        // open an installed app by package name; fall back to a web link if it isn't installed
        @JavascriptInterface
        public void openApp(String pkg, String fallback) {
            runOnUiThread(() -> {
                try {
                    Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (li != null) {
                        startActivity(li);
                        return;
                    }
                } catch (Exception ignored) {
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // share a PNG (base64, no data: prefix) through the system share sheet
        @JavascriptInterface
        public void shareImage(String b64) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) {
                    long now = System.currentTimeMillis();
                    for (File o : old) {
                        if (now - o.lastModified() > 3600000L) o.delete();
                    }
                }
                File f = new File(dir, "style_" + System.currentTimeMillis() + ".png");
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(bytes);
                fo.close();
                final Uri u = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.setClipData(ClipData.newRawUri("image", u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent ch = Intent.createChooser(i, "مشاركة الصورة");
                    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(ch);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر مشاركة الصورة", Toast.LENGTH_SHORT).show());
            }
        }

        // download styles.json in the background and hand the text to the page (empty string on failure)
        @JavascriptInterface
        public void fetchRemote() {
            new Thread(() -> {
                String res = "";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    c.setUseCaches(false);
                    if (c.getResponseCode() == 200) {
                        InputStream in = c.getInputStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        int total = 0;
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > 300000) throw new IOException("too big");
                            bo.write(buf, 0, n);
                        }
                        in.close();
                        res = bo.toString("UTF-8");
                    }
                    c.disconnect();
                } catch (Exception e) {
                    res = "";
                }
                final String out = res;
                runOnUiThread(() -> web.evaluateJavascript("if(window.onRemote)onRemote(" + JSONObject.quote(out) + ")", null));
            }).start();
        }

        // crash report text (empty if none)
        @JavascriptInterface
        public String crashLog() {
            try {
                File f = new File(getFilesDir(), FontApp.CRASH_FILE);
                if (!f.exists()) return "";
                byte[] b = new byte[(int) Math.min(f.length(), 40000L)];
                FileInputStream in = new FileInputStream(f);
                new DataInputStream(in).readFully(b);
                in.close();
                return new String(b, "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void clearCrash() {
            new File(getFilesDir(), FontApp.CRASH_FILE).delete();
        }

        // forget learned words: remove the stored list and bump the generation so the keyboard drops its copy
        @JavascriptInterface
        public void clearLearn() {
            SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
            sp.edit().remove("learn").putInt("learnGen", sp.getInt("learnGen", 0) + 1).apply();
        }

        // keyboard color prefs (only these two keys can be written)
        @JavascriptInterface
        public int getKbPref(String k) {
            return getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt(k, 0);
        }

        @JavascriptInterface
        public void setKbPref(String k, int v) {
            if (!java.util.Arrays.asList("iconC", "smallC", "shape", "daily", "theme", "wall", "keyvis", "cBG", "cKEY", "cACC", "cTXT", "cTXT2").contains(k)) return;
            getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit().putInt(k, v).apply();
            if ("daily".equals(k)) {
                if (v == 1) DailyStyle.schedule(MainActivity.this);
                else DailyStyle.cancel(MainActivity.this);
            }
        }

        // asks for the notification permission (Android 13+) when the daily style is turned on
        @JavascriptInterface
        public void askNotif() {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77));
            }
        }

        // 0 = not installed, 1 = installed, 2 = downloading
        @JavascriptInterface
        public int voiceState() {
            if (voiceBusy) return 2;
            return VoiceInput.installed(MainActivity.this) ? 1 : 0;
        }

        @JavascriptInterface
        public boolean voiceMic() {
            return ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void mediaRequest() {
            runOnUiThread(() -> ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.os.Build.VERSION.SDK_INT >= 33
                            ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE}, 43));
        }

        @JavascriptInterface
        public boolean mediaGranted() {
            String p = android.os.Build.VERSION.SDK_INT >= 33
                    ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
            return ContextCompat.checkSelfPermission(MainActivity.this, p) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void voiceMicRequest() {
            runOnUiThread(() -> ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 42));
        }

        @JavascriptInterface
        public void voiceDownload() {
            if (voiceBusy) return;
            voiceBusy = true;
            new Thread(() -> {
                boolean ok;
                try {
                    VoiceInput.download(MainActivity.this, pct -> runOnUiThread(
                            () -> web.evaluateJavascript("if(window.onVoice)onVoice(" + pct + ")", null)));
                    ok = true;
                } catch (Exception e) {
                    VoiceInput.delete(MainActivity.this);
                    ok = false;
                }
                voiceBusy = false;
                final int code = ok ? 100 : -2;
                runOnUiThread(() -> web.evaluateJavascript("if(window.onVoice)onVoice(" + code + ")", null));
            }).start();
        }

        @JavascriptInterface
        public void voiceDelete() {
            VoiceInput.delete(MainActivity.this);
        }

        @JavascriptInterface
        public void toast(String m) {
            Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void open(String url) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void copy(String t) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("style", t));
            saveHist(t);
            Toast.makeText(MainActivity.this, "Copied ✓", Toast.LENGTH_SHORT).show();
        }
    }
}
