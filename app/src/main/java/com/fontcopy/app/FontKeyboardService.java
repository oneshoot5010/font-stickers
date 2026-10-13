package com.fontcopy.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;

public class FontKeyboardService extends InputMethodService {
    // English: index 0..8 map to Fonts.conv, then Frames, History.
    static final String[] EN_STYLES = {"Normal", "Bold Script", "Double Struck", "Fraktur", "Bold Serif", "Mono", "Small Caps", "Circled", "Wide", "Frames", "History"};
    // Arabic: 0 plain, 1 tatweel, 2 tashkeel, 3 tashkeel mix, 4 tatweel+tashkeel, then frames, history.
    static final String[] AR_STYLES = {"عادي", "تطويل", "تشكيل", "تشكيل متنوع", "تطويل + تشكيل", "إطارات", "السجل"};
    static final String[] FRAMES = {"꧁༒ {t} ༒꧂", "❥ {t} ❥", "★彡 {t} 彡★", "『 {t} 』", "【 {t} 】", "﴾ {t} ﴿", "•°¯`•• {t} ••´¯°•", "ـﮩ٨ـﮩﮩ٨ـ {t} ـﮩ٨ـﮩﮩ٨ـ", "๛ {t} ๛", "✿ {t} ✿", "☆•°{t}°•☆", "◥꧁ {t} ꧂◤", "♛ {t} ♛", "⚔ {t} ⚔", "─═≡ {t} ≡═─", "◄♥ {t} ♥►"};
    static final String[] MARKS = {"\u064E", "\u064F", "\u0650"};
    static final String[] HARAKAT = {"\u064E", "\u064F", "\u0650", "\u0651", "\u0652", "\u064B", "\u064C", "\u064D"};
    static final String[] EMOJI = {"😀", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎",
            "🤩", "😢", "😭", "😡", "🥺", "😴", "🤔", "🙄",
            "👍", "👎", "👏", "🙏", "💪", "✌️", "🤝", "👌",
            "❤️", "💔", "🔥", "✨", "⭐", "🎉", "🎮", "💯"};
    static final String NONJOIN = "اأإآدذرزوؤءةى";
    static final int BG = 0xFF0E0F2E;
    static final int KEY = 0xFF1C1F52;
    static final int SPECIAL = 0xFF2A2E7A;
    static final int ACCENT = 0xFF6A62F2;
    static final int CHIP = 0xFF14173F;
    static final HashMap<String, String[]> ALTS = new HashMap<>();

    static {
        String top = "qwertyuiop";
        String dig = "1234567890";
        for (int i = 0; i < 10; i++) ALTS.put("e:" + top.charAt(i), new String[]{String.valueOf(dig.charAt(i))});
        String ar = "ضصثقفغعهخح";
        String ad = "١٢٣٤٥٦٧٨٩٠";
        for (int i = 0; i < 10; i++) ALTS.put("a:" + ar.charAt(i), new String[]{String.valueOf(ad.charAt(i))});
        ALTS.put("a:ا", new String[]{"أ", "إ", "آ"});
        ALTS.put("a:ه", new String[]{"ة", "٨"});
        ALTS.put("a:و", new String[]{"ؤ"});
        ALTS.put("a:ي", new String[]{"ئ", "ى"});
        ALTS.put("a:ل", new String[]{"لا", "لأ", "لإ", "لآ"});
    }

    int lang = 0;   // 0 English, 1 Arabic
    int mode = 0;   // 0 letters, 1 symbols, 2 frames, 3 history, 4 emoji, 5 decor symbols
    int enStyle = 0;
    int arStyle = 0;
    int shift = 0;  // 0 off, 1 once, 2 caps
    int markIdx = 0;
    int lastLen = 0;
    float density = 2f;
    final ArrayList<TextView> letterViews = new ArrayList<>();
    final ArrayList<String> letterBase = new ArrayList<>();
    TextView shiftKey;
    PopupWindow pop;
    Runnable lpRun;
    LinearLayout root, keysBox, styleRow;
    final Handler handler = new Handler(Looper.getMainLooper());

    int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    String[] styleNames() {
        return lang == 0 ? EN_STYLES : AR_STYLES;
    }

    int currentStyle() {
        return lang == 0 ? enStyle : arStyle;
    }

    int selectedChip() {
        int n = styleNames().length;
        if (mode == 2) return n - 2;
        if (mode == 3) return n - 1;
        return currentStyle();
    }

    @Override
    public View onCreateInputView() {
        density = getResources().getDisplayMetrics().density;
        enStyle = getSharedPreferences("kb", MODE_PRIVATE).getInt("en", 0);
        arStyle = getSharedPreferences("kb", MODE_PRIVATE).getInt("ar", 0);
        if (enStyle < 0 || enStyle >= EN_STYLES.length - 2) enStyle = 0;
        if (arStyle < 0 || arStyle >= AR_STYLES.length - 2) arStyle = 0;
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

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        dismissPop();
    }

    void buildStyleRow() {
        styleRow.removeAllViews();
        final String[] names = styleNames();
        final int sel = selectedChip();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(13);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setTextColor(i == sel ? Color.WHITE : 0xFFB4B7E6);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(16));
            g.setColor(i == sel ? ACCENT : CHIP);
            t.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> pickStyle(idx, names.length));
            styleRow.addView(t);
        }
    }

    void pickStyle(int idx, int n) {
        markIdx = 0;
        if (idx == n - 2) {
            mode = 2;
        } else if (idx == n - 1) {
            mode = 3;
        } else {
            if (lang == 0) enStyle = idx; else arStyle = idx;
            getSharedPreferences("kb", MODE_PRIVATE).edit().putInt(lang == 0 ? "en" : "ar", idx).apply();
            mode = 0;
        }
        buildStyleRow();
        buildKeys();
    }

    LinearLayout newRow(int hDp) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(hDp)));
        return r;
    }

    LinearLayout newRow() {
        return newRow(46);
    }

    TextView key(String label, float weight, int color, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
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

    TextView fastKey(String label, float weight, int color, final Runnable action) {
        final TextView t = key(label, weight, color, null);
        t.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.6f);
                action.run();
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
            }
            return true;
        });
        return t;
    }

    TextView letterKey(final String k, String label) {
        final TextView t = key(label, 1f, KEY, null);
        final String[] alts = (mode == 0) ? ALTS.get((lang == 0 ? "e:" : "a:") + k) : null;
        t.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.6f);
                onKey(k);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (alts != null) {
                    lpRun = () -> {
                        if (alts.length == 1) replaceLast(alts[0]);
                        else showAlts(v, alts);
                    };
                    handler.postDelayed(lpRun, 380);
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                if (lpRun != null) {
                    handler.removeCallbacks(lpRun);
                    lpRun = null;
                }
            }
            return true;
        });
        return t;
    }

    void replaceLast(String alt) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (lastLen > 0) ic.deleteSurroundingText(lastLen, 0);
        onKey(alt);
    }

    void dismissPop() {
        if (pop != null) {
            try {
                pop.dismiss();
            } catch (Exception ignored) {
            }
            pop = null;
        }
    }

    void showAlts(View anchor, final String[] alts) {
        dismissPop();
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(SPECIAL);
        g.setCornerRadius(dp(10));
        ll.setBackground(g);
        ll.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (final String a : alts) {
            TextView t = new TextView(this);
            t.setText(a);
            t.setTextColor(Color.WHITE);
            t.setTextSize(22);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), dp(6), dp(14), dp(6));
            t.setOnClickListener(v -> {
                dismissPop();
                replaceLast(a);
            });
            ll.addView(t);
        }
        pop = new PopupWindow(ll, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pop.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pop.setOutsideTouchable(true);
        try {
            pop.showAsDropDown(anchor, 0, -(anchor.getHeight() + dp(56)));
        } catch (Exception e) {
            pop = null;
        }
    }

    void refreshLabels() {
        boolean up = (mode == 0 && lang == 0 && shift > 0);
        for (int i = 0; i < letterViews.size(); i++) {
            String base = letterBase.get(i);
            letterViews.get(i).setText(up ? base.toUpperCase() : base);
        }
    }

    void updateShiftUi() {
        if (shiftKey != null) {
            shiftKey.setText(shift == 2 ? "⇪" : "⇧");
            ((GradientDrawable) shiftKey.getBackground()).setColor(shift > 0 ? ACCENT : SPECIAL);
        }
        refreshLabels();
    }

    void addLetters(LinearLayout r, String letters) {
        for (int i = 0; i < letters.length(); i++) {
            final String k = String.valueOf(letters.charAt(i));
            String label = (mode == 0 && lang == 0 && shift > 0) ? k.toUpperCase() : k;
            TextView lv = letterKey(k, label);
            if (mode == 0 && lang == 0) {
                letterViews.add(lv);
                letterBase.add(k);
            }
            r.addView(lv);
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

    TextView spaceKey(String label) {
        final TextView t = key(label, 3f, KEY, null);
        t.setTextSize(13);
        final float[] downX = {0f};
        t.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                downX[0] = e.getX();
                v.setAlpha(0.6f);
            } else if (a == MotionEvent.ACTION_UP) {
                v.setAlpha(1f);
                if (Math.abs(e.getX() - downX[0]) > dp(60)) {
                    switchLang();
                } else {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    typeRaw(lang == 0 && mode == 0 && enStyle == 8 ? "\u3000" : " ");
                }
            } else if (a == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
            }
            return true;
        });
        return t;
    }

    void switchLang() {
        lang = 1 - lang;
        mode = 0;
        markIdx = 0;
        buildStyleRow();
        buildKeys();
    }

    void buildKeys() {
        dismissPop();
        keysBox.removeAllViews();
        letterViews.clear();
        letterBase.clear();
        shiftKey = null;
        if (mode == 0 && lang == 0) {
            LinearLayout r1 = newRow();
            addLetters(r1, "qwertyuiop");
            keysBox.addView(r1);
            LinearLayout r2 = newRow();
            r2.addView(spacer(0.5f));
            addLetters(r2, "asdfghjkl");
            r2.addView(spacer(0.5f));
            keysBox.addView(r2);
            LinearLayout r3 = newRow();
            shiftKey = key(shift == 2 ? "⇪" : "⇧", 1.5f, shift > 0 ? ACCENT : SPECIAL, () -> {
                shift = (shift + 1) % 3;
                updateShiftUi();
            });
            r3.addView(shiftKey);
            addLetters(r3, "zxcvbnm");
            r3.addView(backKey(1.5f));
            keysBox.addView(r3);
        } else if (mode == 0) {
            LinearLayout hr = newRow(34);
            hr.addView(spacer(1f));
            for (final String m : HARAKAT) {
                TextView hk = fastKey("\u25CC" + m, 1f, SPECIAL, () -> typeRaw(m));
                hk.setTextSize(16);
                hr.addView(hk);
            }
            hr.addView(spacer(1f));
            keysBox.addView(hr);
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
        } else if (mode == 2) {
            for (int row = 0; row < 3; row++) {
                LinearLayout fr = newRow();
                for (int c = 0; c < 6; c++) {
                    final int idx = row * 6 + c;
                    if (idx < FRAMES.length) {
                        TextView f = key(FRAMES[idx].replace("{t}", "…"), 1f, KEY, () -> wrapAll(FRAMES[idx]));
                        f.setTextSize(9);
                        f.setSingleLine(false);
                        f.setMaxLines(2);
                        fr.addView(f);
                    } else {
                        fr.addView(spacer(1f));
                    }
                }
                keysBox.addView(fr);
            }
        } else if (mode == 3) {
            String h = getSharedPreferences("kb", MODE_PRIVATE).getString("hist", "");
            final String[] items = h.isEmpty() ? new String[0] : h.split("\u0001");
            for (int row = 0; row < 4; row++) {
                LinearLayout hr = newRow();
                for (int c = 0; c < 2; c++) {
                    int idx = row * 2 + c;
                    if (items.length == 0 && idx == 0) {
                        TextView hint = key("انسخ ستايل من التطبيق وهيظهر هنا", 2f, KEY, null);
                        hint.setTextSize(13);
                        hr.addView(hint);
                        c++;
                    } else if (idx < items.length) {
                        final String it = items[idx];
                        TextView k = fastKey(it, 1f, KEY, () -> typeRaw(it));
                        k.setTextSize(13);
                        k.setEllipsize(TextUtils.TruncateAt.END);
                        hr.addView(k);
                    } else {
                        hr.addView(spacer(1f));
                    }
                }
                keysBox.addView(hr);
            }
        } else if (mode == 4) {
            for (int row = 0; row < 4; row++) {
                LinearLayout er = newRow();
                for (int c = 0; c < 8; c++) {
                    final String em = EMOJI[row * 8 + c];
                    TextView ek = fastKey(em, 1f, KEY, () -> typeRaw(em));
                    ek.setTextSize(20);
                    er.addView(ek);
                }
                keysBox.addView(er);
            }
        } else if (mode == 5) {
            String[] rows = {"★☆♥♡❤❥❦❧☙✿", "❀✾❁✧✦✩✪✫✬✭", "༒꧁꧂亗ッツ彡♛♚♔", "☠☯☮☢☣⚔⚡☄☾☽"};
            for (String s : rows) {
                LinearLayout r = newRow();
                addLetters(r, s);
                keysBox.addView(r);
            }
        } else {
            LinearLayout r1 = newRow();
            addLetters(r1, lang == 1 ? "١٢٣٤٥٦٧٨٩٠" : "1234567890");
            keysBox.addView(r1);
            LinearLayout r2 = newRow();
            addLetters(r2, "@#$%&*-+()");
            keysBox.addView(r2);
            LinearLayout r3 = newRow();
            addLetters(r3, lang == 1 ? "!؟'\":؛/_~" : "!?'\":;/_~");
            r3.addView(backKey(1.5f));
            keysBox.addView(r3);
            LinearLayout r4 = newRow();
            addLetters(r4, "★♥❥༒꧁꧂亗ッ");
            TextView more = key("✦ ›", 2f, SPECIAL, () -> {
                mode = 5;
                buildKeys();
            });
            r4.addView(more);
            keysBox.addView(r4);
        }

        LinearLayout b = newRow();
        boolean back = (mode == 1 || mode == 4 || mode == 5);
        String numLabel = back ? (lang == 1 ? "ع" : "ABC") : (lang == 1 ? "١٢٣" : "123");
        TextView numKey = key(numLabel, 1.3f, SPECIAL, () -> {
            mode = (mode == 1 || mode == 4 || mode == 5) ? 0 : 1;
            buildStyleRow();
            buildKeys();
        });
        numKey.setTextSize(15);
        numKey.setOnLongClickListener(v -> {
            mode = 4;
            buildStyleRow();
            buildKeys();
            return true;
        });
        b.addView(numKey);
        b.addView(key(lang == 1 ? "EN" : "ع", 1f, SPECIAL, this::switchLang));
        TextView globe = key("🌐", 0.9f, SPECIAL, () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        globe.setTextSize(16);
        b.addView(globe);
        b.addView(fastKey(lang == 1 ? "،" : ",", 0.8f, KEY, () -> onKey(lang == 1 ? "،" : ",")));
        String[] names = styleNames();
        int sel = (mode == 2 || mode == 3) ? selectedChip() : currentStyle();
        b.addView(spaceKey(names[sel]));
        b.addView(fastKey(".", 0.8f, KEY, () -> onKey(".")));
        b.addView(key("⏎", 1.3f, ACCENT, this::doEnter));
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
        if (mode == 0 && lang == 0) {
            String c = shift > 0 ? k.toUpperCase() : k;
            out = Fonts.conv(enStyle, c);
            if (shift == 1) {
                shift = 0;
                updateShiftUi();
            }
        } else if (mode == 0 && !k.equals("ء")) {
            boolean tat = (arStyle == 1 || arStyle == 4);
            boolean tash = (arStyle >= 2 && arStyle <= 4);
            if (tat) {
                char p = lastLetter(ic);
                if (p != 0 && joinsNext(p)) out = "\u0640" + k;
            }
            if (tash) {
                if (arStyle == 2) out = out + MARKS[0];
                else out = out + MARKS[markIdx++ % 3];
            }
        }
        lastLen = out.length();
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
        if (ic != null) {
            lastLen = s.length();
            ic.commitText(s, 1);
        }
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
        if (b != null && b.length() == 2) {
            if (Character.isHighSurrogate(b.charAt(0)) && Character.isLowSurrogate(b.charAt(1))) n = 2;
            else if (b.charAt(1) == '\uFE0F') n = 2;
        }
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
