package com.fontcopy.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FontKeyboardService extends InputMethodService {
    static final String[] STYLES = {"Normal", "Bold Script", "Double Struck", "Fraktur", "Bold Serif", "Mono", "Small Caps", "Circled", "Wide", "Tatweel", "Tashkeel", "Tashkeel Mix", "Tatweel+Tashkeel", "Frames"};
    static final String[] FRAMES = {"꧁༒ {t} ༒꧂", "❥ {t} ❥", "★彡 {t} 彡★", "『 {t} 』", "【 {t} 】", "﴾ {t} ﴿", "•°¯`•• {t} ••´¯°•", "ـﮩ٨ـﮩﮩ٨ـ {t} ـﮩ٨ـﮩﮩ٨ـ", "๛ {t} ๛", "✿ {t} ✿", "☆•°{t}°•☆", "◥꧁ {t} ꧂◤", "♛ {t} ♛", "⚔ {t} ⚔", "─═≡ {t} ≡═─", "◄♥ {t} ♥►"};
    static final String[] MARKS = {"\u064E", "\u064F", "\u0650"};
    static final String NONJOIN = "اأإآدذرزوؤءةى";
    static final int BG = 0xFF0E0F2E;
    static final int KEY = 0xFF1C1F52;
    static final int SPECIAL = 0xFF2A2E7A;
    static final int ACCENT = 0xFF6A62F2;
    static final int CHIP = 0xFF14173F;

    int style = 0;
    int page = 0; // 0 EN, 1 AR, 2 SYM, 3 FRAMES
    int prevPage = 0;
    int shift = 0; // 0 off, 1 once, 2 caps
    int markIdx = 0;
    float density = 2f;
    LinearLayout root, keysBox, styleRow;
    final Handler handler = new Handler(Looper.getMainLooper());

    int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    @Override
    public View onCreateInputView() {
        density = getResources().getDisplayMetrics().density;
        style = getSharedPreferences("kb", MODE_PRIVATE).getInt("style", 0);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(3), dp(3), dp(3), dp(6));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(styleRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildStyleRow();
        buildKeys();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        shift = 0;
        if (keysBox != null) buildKeys();
    }

    void buildStyleRow() {
        styleRow.removeAllViews();
        for (int i = 0; i < STYLES.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(STYLES[i]);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setTextColor(i == style ? Color.WHITE : 0xFFB4B7E6);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(16));
            g.setColor(i == style ? ACCENT : CHIP);
            t.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> setStyle(idx));
            styleRow.addView(t);
        }
    }

    void setStyle(int i) {
        style = i;
        getSharedPreferences("kb", MODE_PRIVATE).edit().putInt("style", i).apply();
        if (i >= 9 && i <= 12) page = 1;
        else if (i == 13) page = 3;
        else if (page == 1 || page == 3) page = 0;
        markIdx = 0;
        buildStyleRow();
        buildKeys();
    }

    LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return r;
    }

    TextView key(String label, float weight, int color, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        t.setLayoutParams(lp);
        if (action != null) {
            t.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                action.run();
            });
        }
        return t;
    }

    View spacer(float w) {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w));
        return s;
    }

    void addLetters(LinearLayout r, String letters) {
        for (int i = 0; i < letters.length(); i++) {
            final String k = String.valueOf(letters.charAt(i));
            String label = (page == 0 && shift > 0) ? k.toUpperCase() : k;
            r.addView(key(label, 1f, KEY, () -> onKey(k)));
        }
    }

    TextView backKey(float w) {
        final Runnable rep = new Runnable() {
            @Override
            public void run() {
                doBack();
                handler.postDelayed(this, 55);
            }
        };
        TextView b = key("⌫", w, SPECIAL, null);
        b.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                doBack();
                handler.postDelayed(rep, 400);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(rep);
            }
            return true;
        });
        return b;
    }

    void buildKeys() {
        keysBox.removeAllViews();
        if (page == 0) {
            LinearLayout r1 = newRow();
            addLetters(r1, "qwertyuiop");
            keysBox.addView(r1);
            LinearLayout r2 = newRow();
            r2.addView(spacer(0.5f));
            addLetters(r2, "asdfghjkl");
            r2.addView(spacer(0.5f));
            keysBox.addView(r2);
            LinearLayout r3 = newRow();
            r3.addView(key(shift == 2 ? "⇪" : "⇧", 1.5f, shift > 0 ? ACCENT : SPECIAL, () -> {
                shift = (shift + 1) % 3;
                buildKeys();
            }));
            addLetters(r3, "zxcvbnm");
            r3.addView(backKey(1.5f));
            keysBox.addView(r3);
        } else if (page == 1) {
            LinearLayout r1 = newRow();
            addLetters(r1, "ضصثقفغعهخحجد");
            keysBox.addView(r1);
            LinearLayout r2 = newRow();
            r2.addView(spacer(0.5f));
            addLetters(r2, "شسيبلاتنمكط");
            r2.addView(spacer(0.5f));
            keysBox.addView(r2);
            LinearLayout r3 = newRow();
            addLetters(r3, "ئءؤرىةوزظذ");
            r3.addView(backKey(1.5f));
            keysBox.addView(r3);
        } else if (page == 3) {
            for (int row = 0; row < 3; row++) {
                LinearLayout fr = newRow();
                for (int c = 0; c < 6; c++) {
                    final int idx = row * 6 + c;
                    if (idx < FRAMES.length) {
                        TextView f = key(FRAMES[idx].replace("{t}", "…"), 1f, KEY, () -> wrapAll(FRAMES[idx]));
                        f.setTextSize(9);
                        f.setMaxLines(2);
                        fr.addView(f);
                    } else {
                        fr.addView(spacer(1f));
                    }
                }
                keysBox.addView(fr);
            }
        } else {
            LinearLayout r1 = newRow();
            addLetters(r1, "1234567890");
            keysBox.addView(r1);
            LinearLayout r2 = newRow();
            addLetters(r2, "@#$%&*-+()");
            keysBox.addView(r2);
            LinearLayout r3 = newRow();
            addLetters(r3, "!?'\":;/_~");
            r3.addView(backKey(1.5f));
            keysBox.addView(r3);
            LinearLayout r4 = newRow();
            addLetters(r4, "★♥❥༒꧁꧂亗ッ✿☠");
            keysBox.addView(r4);
        }

        LinearLayout b = newRow();
        b.addView(key(page == 2 ? (prevPage == 1 ? "ع" : "ABC") : "?123", 1.3f, SPECIAL, () -> {
            if (page == 2) {
                page = prevPage;
            } else {
                prevPage = page;
                page = 2;
            }
            buildKeys();
        }));
        b.addView(key(page == 1 ? "EN" : "ع", 1.1f, SPECIAL, () -> {
            page = (page == 1) ? 0 : 1;
            buildKeys();
        }));
        b.addView(key(page == 1 ? "،" : ",", 1f, KEY, () -> onKey(page == 1 ? "،" : ",")));
        TextView sp = key(STYLES[style], 4f, KEY, () -> typeRaw(style == 8 && page == 0 ? "\u3000" : " "));
        sp.setTextSize(13);
        b.addView(sp);
        b.addView(key(".", 1f, KEY, () -> onKey(".")));
        b.addView(key("⏎", 1.4f, ACCENT, this::doEnter));
        keysBox.addView(b);
    }

    static boolean joinsNext(char p) {
        if (p == 0x0640 || p == 0x0626) return true;
        return p >= 0x0628 && p <= 0x064A && NONJOIN.indexOf(p) < 0;
    }

    void onKey(String k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String out = k;
        if (page == 0) {
            String c = shift > 0 ? k.toUpperCase() : k;
            out = Fonts.conv(style, c);
            if (shift == 1) {
                shift = 0;
                buildKeys();
            }
        } else if (page == 1 && !k.equals("ء")) {
            boolean tat = (style == 9 || style == 12);
            boolean tash = (style >= 10 && style <= 12);
            if (tat) {
                char p = lastLetter(ic);
                if (p != 0 && joinsNext(p)) out = "\u0640" + k;
            }
            if (tash) {
                if (style == 10) out = out + MARKS[0];
                else out = out + MARKS[markIdx++ % 3];
            }
        }
        ic.commitText(out, 1);
    }

    static char lastLetter(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(2, 0);
        if (b == null || b.length() == 0) return 0;
        char p = b.charAt(b.length() - 1);
        if (p >= 0x064B && p <= 0x0652 && b.length() >= 2) p = b.charAt(b.length() - 2);
        return p;
    }

    void wrapAll(String frame) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        String txt = (et != null && et.text != null) ? et.text.toString().trim() : "";
        if (txt.isEmpty()) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText(frame.replace("{t}", txt), 1);
    }

    void typeRaw(String s) {
        markIdx = 0;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    void doBack() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        CharSequence b = ic.getTextBeforeCursor(2, 0);
        int n = 1;
        if (b != null && b.length() == 2 && Character.isHighSurrogate(b.charAt(0)) && Character.isLowSurrogate(b.charAt(1))) n = 2;
        ic.deleteSurroundingText(n, 0);
    }

    void doEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = ei != null ? (ei.imeOptions & EditorInfo.IME_MASK_ACTION) : EditorInfo.IME_ACTION_NONE;
        boolean noAction = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (!noAction) {
            ic.performEditorAction(action);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }
}
