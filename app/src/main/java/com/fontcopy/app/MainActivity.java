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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String REMOTE_URL = "https://raw.githubusercontent.com/oneshoot5010/font-stickers/main/styles.json";
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
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
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
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE);
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

    class Bridge {
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
