package com.fontcopy.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
import android.widget.ScrollView;
import android.widget.Toast;
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
    static int BG = 0xFF0E0F2E;
    static int KEY = 0xFF1C1F52;
    static int SPECIAL = 0xFF2A2E7A;
    static int ACCENT = 0xFF6A62F2;
    static int CHIP = 0xFF14173F;
    static int TXT = 0xFFFFFFFF;
    static int TXT2 = 0xFFB4B7E6;
    // BG, KEY, SPECIAL, ACCENT, CHIP, TXT, TXT2
    // user-picked colors (index 0 = keep theme default); stored in the "kb" prefs as iconC / smallC
    static final int[] PALETTE = {0, 0xFFFFFFFF, 0xFFFFD36A, 0xFF2ECC71, 0xFF3D9BFF, 0xFFFF5CA8, 0xFFFF7A3D};
    // symbol pages (no emoji), 4 rows of 10 each
    static final String[][] DECOR = {
            {"★☆♥♡❥❦❧☙✿✽", "❀✾❁✧✦✩✪✫✬✭", "༒꧁꧂亗ッツ彡♛♚♔", "☠☯☮☢☣⚔ϟ☄☾☽"},
            {"꧁꧂༺༻≺≻⟪⟫〖〗", "【】❮❯⸨⸩〘〙⦅⦆⟦⟧", "✿❀❃❋✺✹✸✷✶✵", "ଓ୭ꕥ꙳✦⋆˚✧ᯓ★"},
            {"♡♥❣ღ❦❧❁✿❀☙", "➤➢➣➥➦➧➨➩➪➫", "♪♫♬♩♭♯ᯓ✦✪✯", "⊹⊱ꔪ˚✎✐✑✒✓✔✕✖"}
    };
    static final int[][] THEMES = {
            {0xFF0E0F2E, 0xFF1C1F52, 0xFF2A2E7A, 0xFF6A62F2, 0xFF14173F, 0xFFFFFFFF, 0xFFB4B7E6},
            {0xFF0B1A12, 0xFF16301F, 0xFF1F4A30, 0xFF2ECC71, 0xFF10261A, 0xFFFFFFFF, 0xFFA8D8B9},
            {0xFF101010, 0xFF242424, 0xFF383838, 0xFFE53935, 0xFF1A1A1A, 0xFFFFFFFF, 0xFFBBBBBB},
            {0xFF0A1A2E, 0xFF14304F, 0xFF1E4A78, 0xFF29B6F6, 0xFF0F2540, 0xFFFFFFFF, 0xFFA9CBE8},
            {0xFFEDEDF5, 0xFFFFFFFF, 0xFFC9CBE0, 0xFF6A62F2, 0xFFDADCEC, 0xFF1B1B3A, 0xFF454775}
    };
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
    int oneHand = 0;
    int decorPage = 0;
    // signature of the theme/color prefs the current keyboard view was built with
    String appliedSig = "";
    String prefSig() {
        SharedPreferences p = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        return p.getInt("theme", 0) + "," + p.getInt("iconC", 0) + "," + p.getInt("smallC", 0);
    }   // current page of the symbols/stickers keyboard (mode 5)
    boolean prog = false;
    boolean secret = false;
    final ArrayList<String> curKeys = new ArrayList<>();
    final ArrayList<String> curOuts = new ArrayList<>();
    final TextView[] sugg = new TextView[3];
    final String[] suggVals = new String[3];
    LinearLayout strip, tools;
    ClipboardManager cm;
    final ClipboardManager.OnPrimaryClipChangedListener clipL = this::onClip;
    float density = 2f;
    final ArrayList<TextView> letterViews = new ArrayList<>();
    final ArrayList<String> letterBase = new ArrayList<>();
    TextView shiftKey;
    PopupWindow pop;
    Runnable lpRun;
    boolean hasSel = false;
    boolean backHeld = false;
    boolean suggPending = false;
    String lastTyped = "";
    final Runnable suggRun = new Runnable() {
        @Override
        public void run() {
            suggPending = false;
            doSugg();
        }
    };
    final Runnable repRun = new Runnable() {
        @Override
        public void run() {
            if (!backHeld || getCurrentInputConnection() == null) {
                backHeld = false;
                return;
            }
            doBack(true);
            handler.postDelayed(this, 55);
        }
    };
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
    public void onCreate() {
        super.onCreate();
        Predict.load(this);
        cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            try {
                cm.addPrimaryClipChangedListener(clipL);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (cm != null) cm.removePrimaryClipChangedListener(clipL);
        } catch (Exception ignored) {
        }
        cancelPending();
        Predict.save(this);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        density = getResources().getDisplayMetrics().density;
        enStyle = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("en", 0);
        arStyle = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("ar", 0);
        if (enStyle < 0 || enStyle >= EN_STYLES.length - 2) enStyle = 0;
        if (arStyle < 0 || arStyle >= AR_STYLES.length - 2) arStyle = 0;
        applyTheme();
        appliedSig = prefSig();
        oneHand = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("hand", 0);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(3), dp(3), dp(3), dp(6));

        strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setVisibility(View.GONE);
        root.addView(tools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(styleRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildStrip();
        buildTools();
        applyHand();
        buildStyleRow();
        buildKeys();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // the keyboard view is cached between opens, so rebuild it when colors/theme changed in settings
        if (root != null && !prefSig().equals(appliedSig)) setInputView(onCreateInputView());
        SharedPreferences sp0 = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        int en0 = sp0.getInt("en", 0), ar0 = sp0.getInt("ar", 0);
        if (en0 < 0 || en0 >= EN_STYLES.length - 2) en0 = 0;
        if (ar0 < 0 || ar0 >= AR_STYLES.length - 2) ar0 = 0;
        if ((en0 != enStyle || ar0 != arStyle) && styleRow != null) {
            enStyle = en0;
            arStyle = ar0;
            buildStyleRow();
        }
        shift = 0;
        clearCur();
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        secret = (cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        hasSel = info.initialSelStart != info.initialSelEnd;
        if (!secret) handler.postDelayed(this::onClip, 300);
        if (keysBox != null) {
            if (restarting) updateShiftUi();
            else buildKeys();
        }
        flushVoice();
        handler.postDelayed(this::flushVoice, 300);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        cancelPending();
        dismissPop();
        Predict.save(this);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        hasSel = newSelStart != newSelEnd;
        if (hasSel) clearCur();
    }

    void cancelHold() {
        backHeld = false;
        handler.removeCallbacks(repRun);
        if (lpRun != null) {
            handler.removeCallbacks(lpRun);
            lpRun = null;
        }
    }

    void cancelPending() {
        cancelHold();
        handler.removeCallbacksAndMessages(null);
        suggPending = false;
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
            t.setTextColor(i == sel ? Color.WHITE : TXT2);
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
            getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit().putInt(lang == 0 ? "en" : "ar", idx).apply();
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
        t.setTextColor(TXT);
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
        final Runnable[] lpr = {null};
        t.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.6f);
                onKey(k);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (alts != null) {
                    if (lpr[0] != null) handler.removeCallbacks(lpr[0]);
                    lpr[0] = () -> {
                        lpr[0] = null;
                        if (!k.equals(lastTyped)) return;
                        if (alts.length == 1) replaceLast(alts[0]);
                        else showAlts(v, alts);
                    };
                    handler.postDelayed(lpr[0], 380);
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                if (lpr[0] != null) {
                    handler.removeCallbacks(lpr[0]);
                    lpr[0] = null;
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
        if (!curKeys.isEmpty()) {
            curKeys.remove(curKeys.size() - 1);
            curOuts.remove(curOuts.size() - 1);
        }
        prog = true;
        onKey(alt);
        prog = false;
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
            t.setTextColor(TXT);
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
        TextView b = key("⌫", w, SPECIAL, null);
        b.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                doBack(false);
                backHeld = true;
                handler.removeCallbacks(repRun);
                handler.postDelayed(repRun, 400);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                backHeld = false;
                handler.removeCallbacks(repRun);
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
                    onSpace();
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
        clearCur();
        markIdx = 0;
        buildStyleRow();
        buildKeys();
    }

    void buildKeys() {
        cancelHold();
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
            String h = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getString("hist", "");
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
            String[] rows = DECOR[decorPage];
            for (String s : rows) {
                LinearLayout r = newRow();
                addLetters(r, s);
                keysBox.addView(r);
            }
            // page navigation: prev | dots | next
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < DECOR.length; i++) dots.append(i == decorPage ? "●" : "○");
            LinearLayout nav = newRow(34);
            nav.addView(key("‹", 1f, SPECIAL, () -> {
                decorPage = (decorPage + DECOR.length - 1) % DECOR.length;
                buildKeys();
            }));
            nav.addView(key(dots.toString(), 3f, KEY, null));
            nav.addView(key("›", 1f, SPECIAL, () -> {
                decorPage = (decorPage + 1) % DECOR.length;
                buildKeys();
            }));
            keysBox.addView(nav);
        } else if (mode == 6) {
            ArrayList<String> items = clipList();
            ArrayList<String> ord = new ArrayList<>();
            for (String x : items) if (x.startsWith("\u0002")) ord.add(x);
            for (String x : items) if (!x.startsWith("\u0002")) ord.add(x);
            LinearLayout top = newRow(34);
            TextView clr = key("\uD83D\uDDD1 مسح غير المثبت", 1f, SPECIAL, () -> {
                ArrayList<String> keep = new ArrayList<>();
                for (String x : clipList()) if (x.startsWith("\u0002")) keep.add(x);
                saveClip(keep);
                buildKeys();
            });
            clr.setTextSize(13);
            top.addView(clr);
            keysBox.addView(top);
            ScrollView sv = new ScrollView(this);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            if (ord.isEmpty()) {
                LinearLayout hr = newRow();
                TextView hint = key("الحافظة فاضية، انسخ أي نص وهيظهر هنا", 1f, KEY, null);
                hint.setTextSize(13);
                hr.addView(hint);
                col.addView(hr);
            }
            for (final String raw : ord) {
                final boolean pinned = raw.startsWith("\u0002");
                final String txt = pinned ? raw.substring(1) : raw;
                LinearLayout cr = newRow(42);
                TextView it = key(txt.replace("\n", " "), 6f, KEY, () -> typeRaw(txt));
                it.setTextSize(13);
                it.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                it.setPadding(dp(10), 0, dp(10), 0);
                it.setEllipsize(TextUtils.TruncateAt.END);
                TextView pn = key("\uD83D\uDCCC", 1f, pinned ? ACCENT : SPECIAL, () -> togglePin(raw));
                pn.setTextSize(14);
                TextView dl = key("\u2715", 1f, SPECIAL, () -> delClip(raw));
                dl.setTextSize(14);
                cr.addView(it);
                cr.addView(pn);
                cr.addView(dl);
                col.addView(cr);
            }
            sv.addView(col);
            keysBox.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
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
                decorPage = 0;
                buildKeys();
            });
            r4.addView(more);
            keysBox.addView(r4);
        }

        LinearLayout b = newRow();
        boolean back = (mode == 1 || mode == 4 || mode == 5 || mode == 6);
        String numLabel = back ? (lang == 1 ? "ع" : "ABC") : (lang == 1 ? "١٢٣" : "123");
        TextView numKey = key(numLabel, 1.3f, SPECIAL, () -> {
            mode = (mode == 1 || mode == 4 || mode == 5 || mode == 6) ? 0 : 1;
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
        refreshSugg();
    }

    static boolean joinsNext(char p) {
        if (p == 0x0640 || p == 0x0626) return true;
        return p >= 0x0628 && p <= 0x064A && NONJOIN.indexOf(p) < 0;
    }

    void onKey(String k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String out = k;
        String typed = k;
        lastTyped = k;
        if (mode == 0 && lang == 0) {
            String c = shift > 0 ? k.toUpperCase() : k;
            typed = c;
            out = Fonts.conv(enStyle, c);
            if (shift == 1) {
                shift = 0;
                updateShiftUi();
            }
        } else if (mode == 0 && !k.equals("ء")) {
            boolean tat = (arStyle == 1 || arStyle == 4);
            boolean tash = (arStyle >= 2 && arStyle <= 4);
            if (tat) {
                char p = prevLetter(ic);
                if (p != 0 && joinsNext(p)) out = "\u0640" + k;
            }
            if (tash) {
                if (arStyle == 2) out = out + MARKS[0];
                else out = out + MARKS[markIdx++ % 3];
            }
        }
        lastLen = out.length();
        ic.commitText(out, 1);
        if (mode == 0 && k.length() > 0 && Character.isLetter(k.codePointAt(0))) {
            curKeys.add(typed);
            curOuts.add(out);
        } else {
            endWord();
        }
        refreshSugg();
    }

    char prevLetter(InputConnection ic) {
        if (!curKeys.isEmpty()) {
            String lk = curKeys.get(curKeys.size() - 1);
            if (!lk.isEmpty()) return lk.charAt(lk.length() - 1);
        }
        return lastLetter(ic);
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
        clearCur();
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        String txt = (et != null && et.text != null) ? et.text.toString().trim() : "";
        if (txt.isEmpty()) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText(frame.replace("{t}", txt), 1);
    }

    void typeRaw(String s) {
        markIdx = 0;
        endWord();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            lastLen = s.length();
            ic.commitText(s, 1);
        }
        refreshSugg();
    }

    void doBack(boolean rep) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!rep && !hasSel && curOuts.isEmpty()) {
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null && sel.length() > 0) hasSel = true;
        }
        if (hasSel) {
            ic.commitText("", 1);
            hasSel = false;
            clearCur();
            refreshSugg();
            return;
        }
        int n = 0;
        if (!curOuts.isEmpty()) {
            String lo = curOuts.get(curOuts.size() - 1);
            if (!lo.isEmpty()) n = Character.charCount(lo.codePointBefore(lo.length()));
        }
        if (n == 0) {
            n = 1;
            CharSequence b = ic.getTextBeforeCursor(2, 0);
            if (b != null && b.length() == 2) {
                if (Character.isHighSurrogate(b.charAt(0)) && Character.isLowSurrogate(b.charAt(1))) n = 2;
                else if (b.charAt(1) == '\uFE0F') n = 2;
            }
        }
        ic.deleteSurroundingText(n, 0);
        if (!curOuts.isEmpty()) {
            int last = curOuts.size() - 1;
            String lo = curOuts.get(last);
            if (lo.length() <= n) {
                curOuts.remove(last);
                curKeys.remove(last);
            } else {
                curOuts.set(last, lo.substring(0, lo.length() - n));
            }
        }
        refreshSugg();
    }

    void doEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        endWord();
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
        refreshSugg();
    }

    // ---------- word tracking / prediction ----------
    String curWord() {
        StringBuilder sb = new StringBuilder();
        for (String s : curKeys) sb.append(s);
        return sb.toString();
    }

    String curOutStr() {
        StringBuilder sb = new StringBuilder();
        for (String s : curOuts) sb.append(s);
        return sb.toString();
    }

    void clearCur() {
        curKeys.clear();
        curOuts.clear();
    }

    void endWord() {
        if (!prog && !secret && curKeys.size() >= 2) Predict.learn(this, curWord());
        clearCur();
    }

    void typeWord(String w) {
        prog = true;
        for (int i = 0; i < w.length(); i++) onKey(w.substring(i, i + 1));
        prog = false;
    }

    boolean replaceCur(String w) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        String expect = curOutStr();
        CharSequence b = ic.getTextBeforeCursor(expect.length(), 0);
        if (b == null || !b.toString().equals(expect)) {
            clearCur();
            return false;
        }
        boolean cap = lang == 0 && !curKeys.isEmpty() && curKeys.get(0).length() > 0
                && Character.isUpperCase(curKeys.get(0).charAt(0));
        ic.deleteSurroundingText(expect.length(), 0);
        clearCur();
        if (cap && shift == 0) {
            shift = 1;
            updateShiftUi();
        }
        typeWord(w);
        return true;
    }

    void onSpace() {
        if (mode == 0 && !secret && !curKeys.isEmpty()) {
            String f = Predict.fix(lang, curWord());
            if (f != null) replaceCur(f);
        }
        typeRaw(lang == 0 && mode == 0 && enStyle == 8 ? "\u3000" : " ");
    }

    void pickSugg(int i) {
        if (suggPending || i < 0 || i > 2 || suggVals[i] == null || mode != 0) return;
        String w = suggVals[i];
        if (!curKeys.isEmpty()) {
            if (!replaceCur(w)) {
                refreshSugg();
                return;
            }
        } else {
            typeWord(w);
        }
        typeRaw(" ");
    }

    void refreshSugg() {
        if (sugg[0] == null) return;
        suggPending = true;
        handler.removeCallbacks(suggRun);
        handler.postDelayed(suggRun, 60);
    }

    void doSugg() {
        if (sugg[0] == null) return;
        String[] r = (mode == 0 && !secret) ? Predict.suggest(lang, curWord()) : new String[0];
        for (int i = 0; i < 3; i++) {
            String nv = i < r.length ? r[i] : null;
            if (nv == null ? suggVals[i] != null : !nv.equals(suggVals[i])) {
                suggVals[i] = nv;
                sugg[i].setText(nv == null ? "" : nv);
            }
        }
    }

    // ---------- strip / tools ----------
    void buildStrip() {
        strip.removeAllViews();
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView s = key("", 2f, CHIP, null);
            s.setTextSize(15);
            s.setEllipsize(TextUtils.TruncateAt.END);
            s.setOnClickListener(v -> pickSugg(idx));
            sugg[i] = s;
            suggVals[i] = null;
            strip.addView(s);
        }
        TextView cl = key("\uD83D\uDCCB", 0.9f, SPECIAL, () -> {
            mode = (mode == 6) ? 0 : 6;
            buildStyleRow();
            buildKeys();
        });
        cl.setTextSize(16);
        strip.addView(cl);
        TextView mo = key("\u22EF", 0.9f, SPECIAL, () ->
                tools.setVisibility(tools.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        strip.addView(mo);
    }

    void buildTools() {
        tools.removeAllViews();
        String[] labels = {"\uD83D\uDCE5 لصق", "\uD83C\uDFA8 ثيم", "\u25D0 يد واحدة", "Aa\u21BB تحويل"};
        Runnable[] acts = {this::pasteLatest, this::cycleTheme, this::cycleHand, this::convertAll};
        for (int i = 0; i < labels.length; i++) {
            TextView t = key(labels[i], 1f, SPECIAL, acts[i]);
            t.setTextSize(13);
            tools.addView(t);
        }
    }

    void applyTheme() {
        int t = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("theme", 0);
        if (t < 0 || t >= THEMES.length) t = 0;
        int[] c = THEMES[t];
        BG = c[0];
        KEY = c[1];
        SPECIAL = c[2];
        ACCENT = c[3];
        CHIP = c[4];
        TXT = c[5];
        TXT2 = c[6];
        SharedPreferences kp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        int ic = kp.getInt("iconC", 0), sc = kp.getInt("smallC", 0);
        if (ic > 0 && ic < PALETTE.length) TXT = PALETTE[ic];
        if (sc > 0 && sc < PALETTE.length) TXT2 = PALETTE[sc];
    }

    void cycleTheme() {
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        sp.edit().putInt("theme", (sp.getInt("theme", 0) + 1) % THEMES.length).apply();
        setInputView(onCreateInputView());
    }

    void applyHand() {
        if (root == null) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        int pad = oneHand == 0 ? 0 : (int) (w * 0.22f);
        root.setPadding(dp(3) + (oneHand == 2 ? pad : 0), dp(3), dp(3) + (oneHand == 1 ? pad : 0), dp(6));
    }

    void cycleHand() {
        oneHand = (oneHand + 1) % 3;
        getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit().putInt("hand", oneHand).apply();
        applyHand();
    }

    // ---------- voice ----------
    void startVoice() {
        try {
            Intent i = new Intent(this, VoiceActivity.class);
            i.putExtra("lang", lang == 1 ? "ar-EG" : "en-US");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "الكتابة بالصوت مش متاحة", Toast.LENGTH_SHORT).show();
        }
    }

    void flushVoice() {
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        String v = sp.getString("voice", "");
        long ts = sp.getLong("voice_ts", 0);
        if (v == null || v.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        sp.edit().remove("voice").remove("voice_ts").apply();
        if (System.currentTimeMillis() - ts > 60000) return;
        clearCur();
        String out = lang == 0 ? Fonts.conv(enStyle, v) : v;
        ic.commitText(out + " ", 1);
        refreshSugg();
    }

    // ---------- clipboard ----------
    ArrayList<String> clipList() {
        String h = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getString("clip", "");
        ArrayList<String> l = new ArrayList<>();
        if (h != null && !h.isEmpty()) {
            for (String x : h.split("\u0001")) {
                if (!x.isEmpty()) l.add(x);
            }
        }
        return l;
    }

    void saveClip(ArrayList<String> l) {
        getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit().putString("clip", TextUtils.join("\u0001", l)).apply();
    }

    void onClip() {
        try {
            if (cm == null) return;
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            if (d.getDescription() != null && d.getDescription().getExtras() != null
                    && d.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) return;
            CharSequence cs = d.getItemAt(0).coerceToText(this);
            if (cs == null) return;
            String s = cs.toString().trim();
            if (s.isEmpty() || s.length() > 2000) return;
            addClip(s);
        } catch (Exception ignored) {
        }
    }

    void addClip(String s) {
        s = s.replace("\u0001", "").replace("\u0002", "");
        if (s.isEmpty()) return;
        ArrayList<String> l = clipList();
        for (int i = 0; i < l.size(); i++) {
            String x = l.get(i);
            if (x.equals(s)) {
                if (i == 0) return;
                l.remove(i);
                l.add(0, s);
                saveClip(l);
                return;
            }
            if (x.equals("\u0002" + s)) return;
        }
        l.add(0, s);
        int n = 0;
        for (int i = 0; i < l.size(); i++) if (!l.get(i).startsWith("\u0002")) n++;
        for (int i = l.size() - 1; i >= 0 && n > 30; i--) {
            if (!l.get(i).startsWith("\u0002")) {
                l.remove(i);
                n--;
            }
        }
        saveClip(l);
    }

    void togglePin(String raw) {
        ArrayList<String> l = clipList();
        int i = l.indexOf(raw);
        if (i < 0) return;
        l.set(i, raw.startsWith("\u0002") ? raw.substring(1) : "\u0002" + raw);
        saveClip(l);
        buildKeys();
    }

    void delClip(String raw) {
        ArrayList<String> l = clipList();
        l.remove(raw);
        saveClip(l);
        buildKeys();
    }

    void pasteLatest() {
        onClip();
        ArrayList<String> l = clipList();
        if (l.isEmpty()) return;
        String x = l.get(0);
        typeRaw(x.startsWith("\u0002") ? x.substring(1) : x);
    }

    // ---------- convert whole text ----------
    String convAr(String s) {
        StringBuilder sb = new StringBuilder();
        boolean tat = (arStyle == 1 || arStyle == 4);
        boolean tash = (arStyle >= 2 && arStyle <= 4);
        char p = 0;
        int mi = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0x0640 || (c >= 0x064B && c <= 0x0652)) continue;
            if (!(c >= 0x0621 && c <= 0x064A)) {
                sb.append(c);
                p = 0;
                continue;
            }
            String out = String.valueOf(c);
            if (tat && p != 0 && joinsNext(p)) out = "\u0640" + out;
            if (tash) out = arStyle == 2 ? out + MARKS[0] : out + MARKS[mi++ % 3];
            sb.append(out);
            p = c;
        }
        return sb.toString();
    }

    String convText(String s) {
        return lang == 0 ? Fonts.conv(enStyle, s) : convAr(s);
    }

    void convertAll() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        clearCur();
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText(convText(sel.toString()), 1);
            refreshSugg();
            return;
        }
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        String txt = (et != null && et.text != null) ? et.text.toString() : "";
        if (txt.trim().isEmpty()) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText(convText(txt), 1);
        refreshSugg();
    }
}
