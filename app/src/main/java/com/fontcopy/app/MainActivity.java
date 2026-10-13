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
import android.view.inputmethod.InputMethodManager;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

public class MainActivity extends Activity {
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

    // kbhist: last copied styles, shown in the keyboard history tab
    void saveHist(String t) {
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE);
        String cur = sp.getString("hist", "");
        ArrayList<String> l = new ArrayList<>();
        if (cur != null && !cur.isEmpty()) {
            for (String x : cur.split("\u0001")) {
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
