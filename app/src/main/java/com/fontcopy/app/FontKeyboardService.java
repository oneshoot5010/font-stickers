package com.fontcopy.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
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
    // emoji pages, 4 rows of 8 each (page nav under the grid)
    static final String[][] EMOJI = {
            {"😀", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎",
             "🤩", "😢", "😭", "😡", "🥺", "😴", "🤔", "🙄",
             "👍", "👎", "👏", "🙏", "💪", "✌️", "🤝", "👌",
             "❤️", "💔", "🔥", "✨", "⭐", "🎉", "🎮", "💯"},
            {"🐱", "🐶", "🦁", "🐼", "🐸", "🐵", "🦊", "🐰",
             "🍕", "🍔", "🍟", "🌮", "🍩", "🍫", "☕", "🍉",
             "🌹", "🌸", "🌙", "☀️", "🌈", "❄️", "🌊", "🍀",
             "📱", "💻", "📷", "💡", "🎁", "🏆", "🎵", "📌"}
    };
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
    static final int[] PALETTE = {0, 0xFFFFFFFF, 0xFFFFD36A, 0xFF2ECC71, 0xFF3D9BFF, 0xFFFF5CA8, 0xFFFF7A3D,
            0xFFFF8A3D, 0xFFFFC145, 0xFFFF6B9D, 0xFF39FF14, 0xFF00E5FF, 0xFFFF2BD6};
    // key shapes: the index is what the "kb" prefs keep as "shape" (same order as SHAPES in index.html)
    static final String[] SHAPE_NAMES = {"كلاسيك", "حبوب", "زجاجي", "متدرّج", "iOS", "نيون", "3D", "هولو"};
    // symbol pages (no emoji), 4 rows of 10 each
    static final String[][] DECOR = {
            {"ツ☠☣♛♔♡✪❁✾✧", "☄ϟ★☆♥❥❦❧✿❀", "✦✩✫✬✭༒꧁꧂亗♚♕", "♖♗♘♙⚔☯☮☢☾☽"},
            {"★☆♥♡❥❦❧☙✿✽","❀✾❁✧✦✩✪✫✬✭", "༒꧁꧂亗ッツ彡♛♚♔", "☠☯☮☢☣⚔ϟ☄☾☽"},
            {"꧁꧂༺༻≺≻⟪⟫〖〗", "【】❮❯⸨⸩〘〙⦅⦆⟦⟧", "✿❀❃❋✺✹✸✷✶✵", "ଓ୭ꕥ꙳✦⋆˚✧ᯓ★"},
            {"♡♥❣ღ❦❧❁✿❀☙", "➤➢➣➥➦➧➨➩➪➫", "♪♫♬♩♭♯ᯓ✦✪✯", "⊹⊱ꔪ˚✎✐✑✒✓✔✕✖"},
            {"←↑→↓↔↕⇐⇒⇔↩", "±×÷≠≈≤≥∞√∑", "∏∫∂∆πΩ°℃℉‰", "①②③④⑤⑥⑦⑧⑨⑩"},
            {"■□▲△▼▽◆◇○●", "◎◐◑◒◓◔◕▣▤▥", "▦▧▨▩▪▫▬▭▮▯", "░▒▓█▀▄▌▐◢◣"},
            {"─│┌┐└┘├┤┬┴", "═║╔╗╚╝╠╣╦╩", "€£¥₹₽₩¢฿₪₺", "⁰¹²³⁴⁵⁶⁷⁸⁹"}
    };
    static final int[][] THEMES = {
            {0xFF0E0F2E, 0xFF1C1F52, 0xFF2A2E7A, 0xFF6A62F2, 0xFF14173F, 0xFFFFFFFF, 0xFFB4B7E6},
            {0xFF0B1A12, 0xFF16301F, 0xFF1F4A30, 0xFF2ECC71, 0xFF10261A, 0xFFFFFFFF, 0xFFA8D8B9},
            {0xFF101010, 0xFF242424, 0xFF383838, 0xFFE53935, 0xFF1A1A1A, 0xFFFFFFFF, 0xFFBBBBBB},
            {0xFF0A1A2E, 0xFF14304F, 0xFF1E4A78, 0xFF29B6F6, 0xFF0F2540, 0xFFFFFFFF, 0xFFA9CBE8},
            {0xFFEDEDF5, 0xFFFFFFFF, 0xFFC9CBE0, 0xFF6A62F2, 0xFFDADCEC, 0xFF1B1B3A, 0xFF454775},
            {0xFF2B1410, 0xFF4A241A, 0xFF6B3322, 0xFFFF8A3D, 0xFF3A1D15, 0xFFFFF4E6, 0xFFFFC9A0},
            {0xFF2A2010, 0xFF4A3A1A, 0xFF6E5622, 0xFFFFC145, 0xFF3A2D14, 0xFFFFFBEA, 0xFFFFE199},
            {0xFF090711, 0xFF1C0F2E, 0xFF2E1248, 0xFFFF2BD6, 0xFF140A22, 0xFFFFFFFF, 0xFFFF9BEB},
            {0xFF06100A, 0xFF0E2416, 0xFF163A22, 0xFF39FF14, 0xFF0B1C10, 0xFFFFFFFF, 0xFFB6FF9E},
            // custom theme: the colors come from the app settings ("cBG", "cKEY", "cACC", "cTXT", "cTXT2")
            {0xFF0E0F2E, 0xFF1C1F52, 0xFF2A2E7A, 0xFF6A62F2, 0xFF14173F, 0xFFFFFFFF, 0xFFB4B7E6}
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
    int emojiPage = 0;
    int emoCat = 1;   // 0 = recent, 1..8 = EmojiData.CAT
    int emoPage = 0;  // page inside the current category
    // hide emoji the device font cannot draw (they show as empty boxes on older phones)
    static boolean emojiOk(String e) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        return new android.graphics.Paint().hasGlyph(e);
    }
    static String[] okOnly(String[] in) {
        ArrayList<String> o = new ArrayList<>();
        for (String e : in) if (!e.isEmpty() && emojiOk(e)) o.add(e);
        return o.toArray(new String[0]);
    }
    void addRecent(String em) {
        SharedPreferences p = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        ArrayList<String> l = new ArrayList<>();
        l.add(em);
        for (String x : p.getString("emo_r", "").split("\u0001")) {
            if (!x.isEmpty() && !x.equals(em) && l.size() < 24) l.add(x);
        }
        p.edit().putString("emo_r", TextUtils.join("\u0001", l)).apply();
    }
    int shape = 0;  // key shape template, read from the "kb" prefs in applyTheme()
    TextView micKey;
    TextView shapeKey;
    boolean shapePick = false;  // true while the style row shows the shape chips
    org.vosk.android.SpeechService speech;
    boolean listening = false;
    // signature of the theme/color prefs the current keyboard view was built with
    String appliedSig = "";
    String prefSig() {
        SharedPreferences p = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        return p.getInt("theme", 0) + "," + p.getInt("iconC", 0) + "," + p.getInt("smallC", 0) + "," + p.getInt("size", 1) + "," + p.getInt("shape", 0)
                + "," + p.getInt("cBG", 0) + "," + p.getInt("cKEY", 0) + "," + p.getInt("cACC", 0) + "," + p.getInt("cTXT", 0) + "," + p.getInt("cTXT2", 0) + "," + p.getInt("wall", 0) + "," + p.getInt("keyvis", 0);
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
    HorizontalScrollView styleScroll;  // the scrollable row that holds styleRow
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
        sz = sizeFactor(getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("size", 1));
        oneHand = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("hand", 0);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (wall > 0 && wallDrawable(wall) != null) root.setBackground(wallDrawable(wall));
        else root.setBackgroundColor(BG);
        root.setPadding(dp(3), dp(3), dp(3), dp(6));

        strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setVisibility(View.GONE);
        root.addView(tools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        styleScroll = hs;
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
        preloadVoice();
        if (transOn && !restarting) {
            transOn = false;
            resetTransBuf();
            if (styleRow != null) {
                buildStyleRow();
                buildKeys();
            }
        }
        // the shape picker closes when the keyboard is shown again
        boolean pickWasOpen = shapePick;
        shapePick = false;
        // the keyboard view is cached between opens, so rebuild it when colors/theme changed in settings
        if (root != null && !prefSig().equals(appliedSig)) setInputView(onCreateInputView());
        else if (pickWasOpen && styleRow != null) {
            buildStrip();
            buildStyleRow();
        }
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
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        stopVoice();
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
        if (transOn) {
            buildTransChips();
            return;
        }
        if (shapePick) {
            buildShapeChips();
            return;
        }
        final String[] names = styleNames();
        final int sel = selectedChip();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            // English style chips show their own look, e.g. "Bold Script" written in bold script
            boolean stylePreview = lang == 0 && i < names.length - 2;
            t.setText(stylePreview ? Fonts.conv(i, names[i]) : names[i]);
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

    // shape picker: the style row turns into shape chips, and every chip is drawn in the shape it names
    void buildShapeChips() {
        for (int i = 0; i < SHAPE_NAMES.length; i++) {
            final int idx = i;
            final boolean on = i == shape;
            TextView t = new TextView(this);
            t.setText(SHAPE_NAMES[i]);
            t.setTextSize(13);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setTextColor(on ? Color.WHITE : TXT2);
            t.setBackground(keyBg(on ? ACCENT : CHIP, i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> pickShape(idx));
            styleRow.addView(t);
        }
        TextView done = new TextView(this);
        done.setText("تم ✓");
        done.setTextSize(13);
        done.setSingleLine(true);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(14), 0, dp(14), 0);
        done.setTextColor(TXT);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(SPECIAL);
        done.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        done.setLayoutParams(lp);
        done.setOnClickListener(v -> toggleShapePick());
        styleRow.addView(done);
    }

    void toggleShapePick() {
        shapePick = !shapePick;
        if (shapeKey != null) shapeKey.setBackground(keyBg(shapePick ? ACCENT : SPECIAL));
        buildStyleRow();
    }

    // saves the shape and rebuilds the keyboard with it; the picker stays open so the user can keep changing it
    void pickShape(int i) {
        if (i == shape) return;
        final int scrollX = styleScroll != null ? styleScroll.getScrollX() : 0;
        shape = i;
        getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit().putInt("shape", i).apply();
        setInputView(onCreateInputView());
        // the keyboard is rebuilt, so put the chip row back where the user was scrolling
        if (styleScroll != null) styleScroll.post(() -> styleScroll.scrollTo(scrollX, 0));
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

    void cycleFont() {
        int n = styleNames().length - 2;
        pickStyle((currentStyle() + 1) % n, n + 2);
    }

    // keyboard height scale: 0 small, 1 medium (default), 2 large
    float sz = 1f;

    float sizeFactor(int s) {
        return s == 0 ? 0.86f : (s == 2 ? 1.16f : 1f);
    }

    void cycleSize() {
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        int s = (sp.getInt("size", 1) + 1) % 3;
        sp.edit().putInt("size", s).apply();
        toast(s == 0 ? "حجم الكيبورد: صغير" : (s == 1 ? "حجم الكيبورد: وسط" : "حجم الكيبورد: كبير"));
        setInputView(onCreateInputView());
    }

    LinearLayout newRow(int hDp) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(Math.round(hDp * sz))));
        return r;
    }

    LinearLayout newRow() {
        return newRow(46);
    }

    // key shapes: 0 classic, 1 pill, 2 glass, 3 gradient, 4 iOS-like (soft shadow), 5 neon, 6 3D keycap
    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    Drawable keyBg(int color) {
        return keyBg(color, shape);
    }

    // shp is the shape to draw, so the shape picker can show every shape
    Drawable keyBg(int color, int shp) {
        if (keyVis == 2 && color != ACCENT) return new ColorDrawable(Color.TRANSPARENT); // no key capsule at all
        Drawable d = keyBgRaw(color, shp);
        if (keyVis == 1) d.setAlpha(80);
        else if (wall > 0) d.setAlpha(140); // keys stay see-through so the wallpaper shows
        return d;
    }

    Drawable keyBgRaw(int color, int shp) {
        GradientDrawable g = new GradientDrawable();
        switch (shp) {
            case 1: // pill
                g.setColor(color);
                g.setCornerRadius(dp(60));
                break;
            case 2: // glass: slightly see-through with a light border
                g.setColor((color & 0x00FFFFFF) | 0xB3000000);
                g.setCornerRadius(dp(10));
                g.setStroke(dp(1), 0x59FFFFFF);
                break;
            case 3: // gradient: lighter top, darker bottom
                g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                g.setColors(new int[]{mix(color, 0xFFFFFFFF, 0.14f), mix(color, 0xFF000000, 0.12f)});
                g.setCornerRadius(dp(10));
                break;
            case 4: // iOS-like: rounded keys, shadow added in key()
                g.setColor(color);
                g.setCornerRadius(dp(7));
                break;
            case 5: // neon: dark fill with a glowing accent border
                g.setColor(mix(color, 0xFF000000, 0.35f));
                g.setCornerRadius(dp(10));
                g.setStroke(dp(2), ACCENT);
                break;
            case 7: { // holo foil: iridescent pastel keys, dark glass for special keys, purple-teal for the accent
                if (color == SPECIAL || color == CHIP) {
                    g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                    g.setColors(new int[]{0x8C28235A, 0x8C141232});
                    g.setStroke(dp(1), 0x66FFFFFF);
                } else if (color == ACCENT) {
                    g.setOrientation(GradientDrawable.Orientation.TL_BR);
                    g.setColors(new int[]{0xE67C4DFF, 0xE600E5C3});
                    g.setStroke(dp(1), 0x99FFFFFF);
                } else {
                    g.setOrientation(GradientDrawable.Orientation.TL_BR);
                    g.setColors(new int[]{0x6BFF9AD5, 0x619AD8FF, 0x5CB8FFD9, 0x66FFE79A});
                    g.setStroke(dp(1), 0x99FFFFFF);
                }
                g.setCornerRadius(dp(12));
                break;
            }
            case 6: { // 3D keycap: a darker base with the key face sitting on top of it
                GradientDrawable base = new GradientDrawable();
                base.setColor(mix(color, 0xFF000000, 0.45f));
                base.setCornerRadius(dp(8));
                GradientDrawable face = new GradientDrawable();
                face.setColor(color);
                face.setCornerRadius(dp(8));
                return new LayerDrawable(new Drawable[]{base, new InsetDrawable(face, 0, 0, 0, dp(3))});
            }
            default: // classic
                g.setColor(color);
                g.setCornerRadius(dp(8));
                break;
        }
        return g;
    }

    TextView key(String label, float weight, int color, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(TXT);
        t.setTextSize(18);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setBackground(keyBg(color));
        if (shape == 4) t.setElevation(dp(2));
        if (shape == 7 || wall > 0 || keyVis > 0) t.setShadowLayer(dp(3), 0, dp(1), 0xCC000000);
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
            shiftKey.setBackground(keyBg(shift > 0 ? ACCENT : SPECIAL));
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
        transSrcTV = null;
        transOutTV = null;
        if (transOn && mode == 0) buildTransPanel();
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
            r3.addView(spacer(1.5f));
            keysBox.addView(r3);
        } else if (mode == 0) {
            LinearLayout r1 = newRow();
            addLetters(r1, "ضصثقفغعهخحجد");
            keysBox.addView(r1);
            LinearLayout r2 = newRow();
            r2.addView(spacer(0.5f));
            addLetters(r2, "شسيبلاتنمكط");
            r2.addView(spacer(0.5f));
            keysBox.addView(r2);
            LinearLayout r3 = newRow();
            r3.addView(spacer(0.75f));
            addLetters(r3, "ئءؤرىةوزظذ");
            r3.addView(spacer(0.75f));
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
            final SharedPreferences ep = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
            final int skin = ep.getInt("skin", 0);
            final String rec = ep.getString("emo_r", "");
            final String[] emoList = okOnly((emoCat == 0)
                    ? (rec.isEmpty() ? new String[0] : rec.split("\u0001"))
                    : EmojiData.CAT[emoCat].split(" "));
            final int perPage = 24;
            final int pages = Math.max(1, (emoList.length + perPage - 1) / perPage);
            if (emoPage >= pages) emoPage = 0;
            final int pg = emoPage;
            // row 1: categories
            LinearLayout cr = newRow(34);
            for (int i = 0; i < EmojiData.ICONS.length; i++) {
                final int ci = i;
                TextView ck = key(EmojiData.ICONS[i], 1f, ci == emoCat ? ACCENT : SPECIAL, () -> {
                    emoCat = ci;
                    emoPage = 0;
                    buildKeys();
                });
                ck.setTextSize(14);
                cr.addView(ck);
            }
            keysBox.addView(cr);
            // rows 2-4: emoji grid, 3 x 8
            for (int row = 0; row < 3; row++) {
                LinearLayout er = newRow();
                for (int c = 0; c < 8; c++) {
                    int idx = pg * perPage + row * 8 + c;
                    if (idx < emoList.length) {
                        final String base = emoList[idx];
                        final String em = (emoCat == 2 && skin > 0) ? base + EmojiData.SKIN[skin] : base;
                        TextView ek = fastKey(em, 1f, KEY, () -> {
                            typeRaw(em);
                            addRecent(em);
                        });
                        ek.setTextSize(20);
                        er.addView(ek);
                    } else {
                        er.addView(spacer(1f));
                    }
                }
                keysBox.addView(er);
            }
            // row 5: skin tones (6) + page nav (2)
            LinearLayout sr = newRow(34);
            for (int i = 0; i < EmojiData.SKIN_LABEL.length; i++) {
                final int si = i;
                TextView sk = key(EmojiData.SKIN_LABEL[i], 1f, si == skin ? ACCENT : SPECIAL, () -> {
                    ep.edit().putInt("skin", si).apply();
                    buildKeys();
                });
                sk.setTextSize(15);
                sr.addView(sk);
            }
            sr.addView(key("‹", 1f, SPECIAL, () -> {
                emoPage = (emoPage + pages - 1) % pages;
                buildKeys();
            }));
            sr.addView(key("›", 1f, SPECIAL, () -> {
                emoPage = (emoPage + 1) % pages;
                buildKeys();
            }));
            keysBox.addView(sr);
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
            refreshShot();
            if (shotUri != null && shotThumb != null) {
                TextView st = key("آخر سكرين شوت: اضغط للنسخ", 1f, KEY, this::copyShot);
                st.setTextSize(13);
                st.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                st.setPadding(dp(8), 0, dp(10), 0);
                android.graphics.drawable.Drawable pd = new android.graphics.drawable.BitmapDrawable(getResources(), shotThumb);
                pd.setBounds(0, 0, dp(44), dp(44));
                st.setCompoundDrawables(pd, null, null, null);
                st.setCompoundDrawablePadding(dp(8));
                LinearLayout sr = newRow(52);
                sr.addView(st);
                keysBox.addView(sr);
            }
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
            r3.addView(spacer(1.5f));
            keysBox.addView(r3);
            LinearLayout r4 = newRow();
            addLetters(r4, "★♥❥༒꧁꧂亗ッ");
            TextView more = key("✦ ›", 2f, SPECIAL, () -> {
                mode = 5;
                decorPage = 0;
                buildKeys();
            });
            r4.addView(more);
            // visible way into the emoji page (long-press on 123 still works too)
            r4.addView(key("😀", 1.2f, SPECIAL, () -> {
                mode = 4;
                emojiPage = 0;
                buildStyleRow();
                buildKeys();
            }));
            keysBox.addView(r4);
        }

        LinearLayout b = newRow();
        boolean back = (mode == 1 || mode == 4 || mode == 5 || mode == 6);
        String numLabel = back ? (lang == 1 ? "ع" : "ABC") : (lang == 1 ? "١٢٣" : "123");
        TextView numKey = key(numLabel, 1.2f, SPECIAL, () -> {
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
        // fixed emoji button: opens the emoji panel, tapping again goes back to letters
        TextView emoKey = key("😀", 1f, mode == 4 ? ACCENT : SPECIAL, () -> {
            mode = (mode == 4) ? 0 : 4;
            buildStyleRow();
            buildKeys();
        });
        emoKey.setTextSize(17);
        b.addView(emoKey);
        // font key: cycles the text style (English fonts / Arabic tashkeel + tatweel); the language is switched by swiping the space bar
        TextView fontKey = key("✦" + (lang == 0 ? Fonts.conv(enStyle, "Aa") : convAr("عر")), 1f, ACCENT, this::cycleFont);
        fontKey.setTextSize(13);
        b.addView(fontKey);
        // tap = translation panel, long press = the Android keyboard picker
        TextView globe = key("A\u21C4ع", 0.9f, transOn ? ACCENT : SPECIAL, this::toggleTrans);
        globe.setOnLongClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
            return true;
        });
        globe.setTextSize(13);
        b.addView(globe);
        TextView toolsKey = key("\u22EF", 0.8f, SPECIAL, () ->
                tools.setVisibility(tools.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        b.addView(toolsKey);
        b.addView(fastKey(lang == 1 ? "،" : ",", 0.7f, KEY, () -> onKey(lang == 1 ? "،" : ",")));
        String[] names = styleNames();
        int sel = (mode == 2 || mode == 3) ? selectedChip() : currentStyle();
        b.addView(spaceKey(names[sel]));
        b.addView(fastKey(".", 0.7f, KEY, () -> onKey(".")));
        b.addView(backKey(1.1f)); // same place in every keyboard mode
        b.addView(key("⏎", 1.2f, ACCENT, this::doEnter));
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
        if (transOn) {
            sendTrans();
            return;
        }
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
        if (transOn) scheduleTrans();
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
        micKey = key("\uD83C\uDFA4", 0.9f, SPECIAL, this::toggleVoice);
        micKey.setTextSize(16);
        strip.addView(micKey);
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
        refreshShot();
        TextView cl = key(clipLabel(), 0.9f, SPECIAL, () -> {
            mode = (mode == 6) ? 0 : 6;
            buildStyleRow();
            buildKeys();
        });
        cl.setTextSize(9);
        android.graphics.drawable.Drawable sd = getResources().getDrawable(R.drawable.ic_clip).mutate();
        sd.setColorFilter(TXT, android.graphics.PorterDuff.Mode.SRC_IN);
        sd.setBounds(0, 0, dp(20), dp(20));
        cl.setCompoundDrawables(null, sd, null, null);
        cl.setCompoundDrawablePadding(dp(1));
        clipKey = cl;
        strip.addView(cl);
        shapeKey = key("\u25E7", 0.9f, shapePick ? ACCENT : SPECIAL, this::toggleShapePick);
        shapeKey.setTextSize(16);
        strip.addView(shapeKey);
        TextView sizeBtn = key("\u21D5", 0.9f, SPECIAL, this::cycleSize);
        sizeBtn.setTextSize(18);
        strip.addView(sizeBtn);
    }

    TextView toolKey(String icon, String name, final Runnable act) {
        TextView t = key(icon + "\n" + name, 1f, SPECIAL, act);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setTextSize(11);
        t.setLineSpacing(0, 0.95f);
        return t;
    }

    void buildTools() {
        tools.removeAllViews();
        LinearLayout a = new LinearLayout(this);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.addView(toolKey("\uD83D\uDCC4", "نسخ", () -> copyText(false)));
        a.addView(toolKey("\u2702\uFE0F", "قص", () -> copyText(true)));
        a.addView(toolKey("\uD83D\uDCE5", "لصق", this::pasteLatest));
        a.addView(toolKey("\u2611", "تحديد الكل", this::selectAllText));
        a.addView(toolKey("\u21B6", "تراجع", this::undoText));
        a.addView(toolKey("\u21B7", "إعادة", this::redoText));
        final TextView cur = toolKey("\u25C0 \u25B6", "مؤشر", null);
        cur.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                moveCursor(e.getX() < v.getWidth() / 2f ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            return true;
        });
        a.addView(cur);
        tools.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.addView(toolKey("A\u21C4ع", "ترجمة", this::toggleTrans));
        b.addView(toolKey("\uD83D\uDDBC", "خلفية", this::cycleWall));
        b.addView(toolKey("\uD83C\uDFA8", "ثيم", this::cycleTheme));
        b.addView(toolKey("\u25D0", "يد واحدة", this::cycleHand));
        b.addView(toolKey("Aa\u21BB", "تحويل", this::convertAll));
        b.addView(toolKey("\uD83D\uDDD1", "مسح الكل", this::clearAllText));
        b.addView(toolKey("\u2328", "تبديل الكيبورد", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));
        tools.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
    }

    // ---------- edit tools: copy / cut / select all / undo / redo / cursor / clear ----------
    final ArrayList<String> undoStack = new ArrayList<>();
    final ArrayList<String> redoStack = new ArrayList<>();

    String fieldText(InputConnection ic) {
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null) return et.text.toString();
        CharSequence b = ic.getTextBeforeCursor(5000, 0), a = ic.getTextAfterCursor(5000, 0);
        return (b == null ? "" : b.toString()) + (a == null ? "" : a.toString());
    }

    void pushUndo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        undoStack.add(fieldText(ic));
        if (undoStack.size() > 15) undoStack.remove(0);
        redoStack.clear();
    }

    void replaceAll(String t) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText(t, 1);
        refreshSugg();
    }

    void undoText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        clearCur();
        if (!undoStack.isEmpty()) {
            redoStack.add(fieldText(ic));
            replaceAll(undoStack.remove(undoStack.size() - 1));
        } else sendCtrl(KeyEvent.KEYCODE_Z, false);
    }

    void redoText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        clearCur();
        if (!redoStack.isEmpty()) {
            undoStack.add(fieldText(ic));
            replaceAll(redoStack.remove(redoStack.size() - 1));
        } else sendCtrl(KeyEvent.KEYCODE_Z, true);
    }

    void sendCtrl(int code, boolean shiftToo) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int meta = KeyEvent.META_CTRL_ON | (shiftToo ? KeyEvent.META_SHIFT_ON : 0);
        long t = System.currentTimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta));
    }

    void copyText(boolean cut) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || cm == null) return;
        clearCur();
        CharSequence sel = ic.getSelectedText(0);
        boolean all = sel == null || sel.length() == 0;
        String txt = all ? fieldText(ic) : sel.toString();
        if (txt.isEmpty()) return;
        try {
            cm.setPrimaryClip(ClipData.newPlainText("text", txt));
        } catch (Exception ignored) {
        }
        if (cut) {
            pushUndo();
            if (all) replaceAll("");
            else ic.commitText("", 1);
        }
        toast(cut ? "تم القص" : "تم النسخ");
    }

    void selectAllText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        clearCur();
        ic.performContextMenuAction(android.R.id.selectAll);
    }

    void clearAllText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        clearCur();
        if (fieldText(ic).isEmpty()) return;
        pushUndo();
        replaceAll("");
    }

    void moveCursor(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        clearCur();
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    // ---------- translation (online, MyMemory) ----------
    static final String[] TR_NAMES = {"English", "العربية", "Español", "Français", "Deutsch", "Türkçe", "Русский", "中文", "日本語", "हिन्दी", "Italiano"};
    static final String[] TR_CODES = {"en", "ar", "es", "fr", "de", "tr", "ru", "zh-CN", "ja", "hi", "it"};
    static final String[] TR_TTS = {"en", "ar", "es", "fr", "de", "tr", "ru", "zh", "ja", "hi", "it"};
    boolean transOn = false;
    final StringBuilder transBuf = new StringBuilder(); // text typed inside the translation box
    String transComp = "";
    boolean transSel = false;
    TransIC transIC;

    // while the translation box is open every key goes into transBuf instead of the app's field
    class TransIC extends android.view.inputmethod.InputConnectionWrapper {
        final InputConnection realRef;

        TransIC(InputConnection t) {
            super(t, true);
            realRef = t;
        }

        String all() {
            return transBuf.toString() + transComp;
        }

        void changed() {
            handler.post(FontKeyboardService.this::onTransChanged);
        }

        @Override
        public boolean commitText(CharSequence t, int p) {
            if (transSel) {
                transBuf.setLength(0);
                transSel = false;
            }
            transComp = "";
            transBuf.append(t);
            changed();
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence t, int p) {
            transComp = t.toString();
            changed();
            return true;
        }

        @Override
        public boolean finishComposingText() {
            transBuf.append(transComp);
            transComp = "";
            changed();
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int b, int a) {
            String s = all();
            int n = Math.min(b, s.length());
            transBuf.setLength(0);
            transBuf.append(s, 0, s.length() - n);
            transComp = "";
            changed();
            return true;
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int f) {
            String s = all();
            return s.substring(Math.max(0, s.length() - n));
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int f) {
            return "";
        }

        @Override
        public CharSequence getSelectedText(int f) {
            return transSel ? all() : null;
        }

        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest r, int f) {
            ExtractedText et = new ExtractedText();
            et.text = all();
            et.selectionStart = et.selectionEnd = et.text.length();
            return et;
        }

        @Override
        public boolean performContextMenuAction(int id) {
            if (id == android.R.id.selectAll) transSel = true;
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent e) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                deleteSurroundingText(1, 0);
            }
            return true;
        }

        @Override
        public boolean performEditorAction(int a) {
            return true;
        }

        @Override
        public boolean setSelection(int a, int b) {
            return true;
        }

        @Override
        public boolean setComposingRegion(int a, int b) {
            return true;
        }
    }

    @Override
    public InputConnection getCurrentInputConnection() {
        InputConnection real = super.getCurrentInputConnection();
        if (!transOn || real == null) return real;
        if (transIC == null || transIC.realRef != real) transIC = new TransIC(real);
        return transIC;
    }

    void onTransChanged() {
        transSrc = (transBuf.toString() + transComp).trim();
        updateTransUI();
        scheduleTrans();
    }

    void resetTransBuf() {
        transBuf.setLength(0);
        transComp = "";
        transSel = false;
    }
    int transTarget = 0;
    String transResult = "";
    String transSrc = "";
    String transReqKey = "";
    TextView transSrcTV, transOutTV;
    android.speech.tts.TextToSpeech tts;
    final Runnable transRun = this::runTrans;

    void toggleTrans() {
        clearCur();
        transOn = !transOn;
        resetTransBuf();
        transResult = "";
        transSrc = "";
        transReqKey = "";
        transTarget = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).getInt("ttarget", 0);
        if (transTarget < 0 || transTarget >= TR_CODES.length) transTarget = 0;
        tools.setVisibility(View.GONE);
        mode = 0;
        buildStyleRow();
        buildKeys();
        if (transOn) scheduleTrans();
    }

    void scheduleTrans() {
        if (!transOn) return;
        handler.removeCallbacks(transRun);
        handler.postDelayed(transRun, 700);
    }

    static boolean hasArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    String transSource() {
        String t = (transBuf.toString() + transComp).trim();
        return t.length() > 240 ? t.substring(t.length() - 240) : t;
    }

    void updateTransUI() {
        if (transSrcTV != null) {
            transSrcTV.setText(transSrc.isEmpty() ? "اكتب هنا… ▏" : transSrc + "▏");
            transSrcTV.setTextColor(transSrc.isEmpty() ? TXT2 : TXT);
        }
        if (transOutTV != null) transOutTV.setText(transResult.isEmpty() ? "…" : transResult);
    }

    void runTrans() {
        if (!transOn) return;
        final String src = transSource();
        transSrc = src;
        if (src.isEmpty()) {
            transResult = "";
            updateTransUI();
            return;
        }
        final String from = hasArabic(src) ? "ar" : "en";
        String to = TR_CODES[transTarget];
        if (to.equals(from)) to = from.equals("ar") ? "en" : "ar";
        final String fto = to;
        final String key = src + "|" + from + "|" + to;
        if (key.equals(transReqKey)) {
            updateTransUI();
            return;
        }
        transReqKey = key;
        transResult = "…";
        updateTransUI();
        new Thread(() -> {
            String out;
            try {
                java.net.URL u = new java.net.URL("https://api.mymemory.translated.net/get?q=" + java.net.URLEncoder.encode(src, "UTF-8") + "&langpair=" + from + "|" + fto);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = r.readLine()) != null) sb.append(ln);
                r.close();
                org.json.JSONObject j = new org.json.JSONObject(sb.toString());
                out = j.getJSONObject("responseData").getString("translatedText");
                out = android.text.Html.fromHtml(out).toString().trim();
                if (j.optInt("responseStatus", 200) != 200 || out.isEmpty()) out = "تعذّرت الترجمة (حد الاستخدام اليومي؟)";
            } catch (Exception e) {
                out = "تعذّرت الترجمة، تأكد من الإنترنت";
            }
            final String fo = out;
            handler.post(() -> {
                if (!key.equals(transReqKey)) return;
                transResult = fo;
                updateTransUI();
            });
        }).start();
    }

    boolean transOk() {
        return !transResult.isEmpty() && !transResult.equals("…") && !transResult.startsWith("تعذّرت");
    }

    void sendTrans() {
        InputConnection ic = super.getCurrentInputConnection();
        if (ic == null || !transOk()) return;
        clearCur();
        ic.commitText(transResult, 1); // the real field of the app
        resetTransBuf();
        transResult = "";
        transSrc = "";
        transReqKey = "";
        updateTransUI();
        refreshSugg();
    }

    void speakTrans() {
        if (!transOk()) return;
        final String t = transResult;
        final java.util.Locale loc = java.util.Locale.forLanguageTag(TR_TTS[transTarget]);
        if (tts == null) {
            tts = new android.speech.tts.TextToSpeech(this, st -> {
                if (st == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts.setLanguage(loc);
                    tts.speak(t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tr");
                }
            });
        } else {
            tts.setLanguage(loc);
            tts.speak(t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tr");
        }
    }

    // language chips replace the style row while translating
    void buildTransChips() {
        for (int i = 0; i < TR_NAMES.length; i++) {
            final int idx = i;
            final boolean on = i == transTarget;
            TextView t = new TextView(this);
            t.setText(TR_NAMES[i]);
            t.setTextSize(13);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setTextColor(on ? Color.WHITE : TXT2);
            t.setBackground(keyBg(on ? ACCENT : CHIP, shape));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                transTarget = idx;
                getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS).edit().putInt("ttarget", idx).apply();
                transReqKey = "";
                buildStyleRow();
                scheduleTrans();
            });
            styleRow.addView(t);
        }
    }

    void buildTransPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x99101230);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), 0x55FFFFFF);
        p.setBackground(g);
        p.setPadding(dp(10), dp(6), dp(10), dp(4));
        transSrcTV = new TextView(this);
        transSrcTV.setTextSize(17);
        transSrcTV.setTextColor(TXT);
        transSrcTV.setMaxLines(2);
        transSrcTV.setEllipsize(TextUtils.TruncateAt.START);
        transSrcTV.setPadding(0, 0, 0, dp(4));
        p.addView(transSrcTV);
        transOutTV = new TextView(this);
        transOutTV.setTextSize(15);
        transOutTV.setTextColor(0xFFFFE79A);
        transOutTV.setMaxLines(2);
        transOutTV.setEllipsize(TextUtils.TruncateAt.END);
        transOutTV.setTypeface(null, android.graphics.Typeface.BOLD);
        p.addView(transOutTV);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView send = key("✔ إرسال الترجمة", 2.2f, ACCENT, this::sendTrans);
        send.setTextSize(14);
        TextView cp = key("📄 نسخ", 1f, SPECIAL, () -> {
            if (transOk() && cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("tr", transResult));
                toast("تم نسخ الترجمة");
            }
        });
        cp.setTextSize(13);
        TextView sp = key("🔊 نطق", 1f, SPECIAL, this::speakTrans);
        sp.setTextSize(13);
        TextView cl = key("✕", 0.6f, SPECIAL, this::toggleTrans);
        cl.setTextSize(15);
        r.addView(send);
        r.addView(cp);
        r.addView(sp);
        r.addView(cl);
        p.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), dp(2), dp(2), dp(3));
        keysBox.addView(p, lp);
        updateTransUI();
    }

    void applyTheme() {
        SharedPreferences kp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        int t = kp.getInt("theme", 0);
        if (t < 0 || t >= THEMES.length) t = 0;
        int[] c = THEMES[t];
        if (t == THEMES.length - 1) {
            int bg = kp.getInt("cBG", c[0]), ky = kp.getInt("cKEY", c[1]), acc = kp.getInt("cACC", c[3]);
            int tx = kp.getInt("cTXT", c[5]), tx2 = kp.getInt("cTXT2", c[6]);
            c = new int[]{bg, ky, blend(ky, 0xFFFFFFFF, 0.12f), acc, blend(bg, ky, 0.5f), tx, tx2};
        }
        BG = c[0];
        KEY = c[1];
        SPECIAL = c[2];
        ACCENT = c[3];
        CHIP = c[4];
        TXT = c[5];
        TXT2 = c[6];
        int ic = kp.getInt("iconC", 0), sc = kp.getInt("smallC", 0);
        if (ic > 0 && ic < PALETTE.length) TXT = PALETTE[ic];
        if (sc > 0 && sc < PALETTE.length) TXT2 = PALETTE[sc];
        keyVis = kp.getInt("keyvis", 0);
        if (keyVis < 0 || keyVis > 2) keyVis = 0;
        wall = kp.getInt("wall", 0);
        if (wall < 0 || wall > WALLS) wall = 0;
        if (wall > 0) {
            // dark translucent keys + light text so every wallpaper stays readable
            if (lum(KEY) > 128) KEY = blend(KEY, 0xFF000000, 0.65f);
            if (lum(SPECIAL) > 128) SPECIAL = blend(SPECIAL, 0xFF000000, 0.65f);
            if (lum(CHIP) > 128) CHIP = blend(CHIP, 0xFF000000, 0.65f);
            if (lum(TXT) < 128) TXT = 0xFFFFFFFF;
            if (lum(TXT2) < 128) TXT2 = 0xFFDDDDDD;
        }
        shape = kp.getInt("shape", 0);
        if (shape < 0 || shape >= SHAPE_NAMES.length) shape = 0;
    }

    // mixes two colors: t = 0 gives a, t = 1 gives b
    static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static final int WALLS = 15;
    int wall = 0;
    int keyVis = 0; // key background: 0 normal, 1 light, 2 fully transparent
    static final HashMap<Integer, Bitmap> WALL_CACHE = new HashMap<>();

    static int lum(int c) {
        return (((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF)) / 10;
    }

    // wallpaper from assets/walls/wN.jpg, drawn center-cropped with a light dark scrim
    Drawable wallDrawable(int n) {
        Bitmap b = WALL_CACHE.get(n);
        if (b == null) {
            try {
                java.io.InputStream in = getAssets().open("walls/w" + n + ".jpg");
                b = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                if (b != null) WALL_CACHE.put(n, b);
            } catch (Exception e) {
                return null;
            }
        }
        if (b == null) return null;
        final Bitmap bm = b;
        return new Drawable() {
            final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);

            @Override
            public void draw(android.graphics.Canvas c) {
                android.graphics.Rect r = getBounds();
                if (r.width() <= 0 || r.height() <= 0) return;
                float sc = Math.max(r.width() / (float) bm.getWidth(), r.height() / (float) bm.getHeight());
                float dw = bm.getWidth() * sc, dh = bm.getHeight() * sc;
                android.graphics.RectF dst = new android.graphics.RectF(
                        r.left + (r.width() - dw) / 2f, r.top + (r.height() - dh) / 2f,
                        r.left + (r.width() + dw) / 2f, r.top + (r.height() + dh) / 2f);
                c.save();
                c.clipRect(r);
                c.drawBitmap(bm, null, dst, pt);
                c.restore();
                pt.setColor(0x1F000000);
                c.drawRect(r, pt);
                pt.setColor(0xFF000000);
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.OPAQUE;
            }
        };
    }

    void cycleWall() {
        SharedPreferences sp = getSharedPreferences("kb", MODE_PRIVATE | MODE_MULTI_PROCESS);
        sp.edit().putInt("wall", (sp.getInt("wall", 0) + 1) % (WALLS + 1)).apply();
        setInputView(onCreateInputView());
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

    void toast(String m) {
        Toast.makeText(this, m, m.length() > 40 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    // ---------- offline voice typing (Vosk, no Google) ----------
    void toggleVoice() {
        if (listening) {
            stopVoice();
            return;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toast("افتح التطبيق واسمح بالميكروفون من الإعدادات");
            return;
        }
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            startGoogle();
            return;
        }
        if (!VoiceInput.installed(this)) {
            toast("افتح التطبيق وحمّل الكتابة الصوتية من الإعدادات");
            return;
        }
        if (loadingVoice) return;
        loadingVoice = true;
        if (VoiceInput.model == null) toast("جارٍ تجهيز الصوت…");
        final android.content.Context ctx = getApplicationContext();
        new Thread(() -> {
            String stage = "model";
            try {
                org.vosk.Model vm = VoiceInput.load(ctx);
                stage = "recognizer";
                org.vosk.Recognizer rec = new org.vosk.Recognizer(vm, 16000.0f);
                final org.vosk.Recognizer fr = rec;
                handler.post(() -> {
                    loadingVoice = false;
                    startSpeech(fr);
                });
            } catch (Throwable e) {
                final String st = stage;
                final Throwable ex = e;
                handler.post(() -> {
                    loadingVoice = false;
                    voiceFail(st, ex);
                });
            }
        }).start();
    }

    boolean loadingVoice;

    // ---- live dictation through the system recognizer (Google): partial text shows while speaking ----
    android.speech.SpeechRecognizer gsr;
    boolean gMode;

    void startGoogle() {
        gMode = true;
        listening = true;
        clearCur();
        setMicUi();
        gListen();
    }

    void gListen() {
        if (!listening || !gMode) return;
        try {
            if (gsr == null) {
                gsr = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
                gsr.setRecognitionListener(new android.speech.RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(android.os.Bundle b) {
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                    }

                    @Override
                    public void onRmsChanged(float v) {
                    }

                    @Override
                    public void onBufferReceived(byte[] bytes) {
                    }

                    @Override
                    public void onEndOfSpeech() {
                    }

                    @Override
                    public void onEvent(int i, android.os.Bundle b) {
                    }

                    @Override
                    public void onPartialResults(android.os.Bundle b) {
                        String t = gText(b);
                        InputConnection ic = getCurrentInputConnection();
                        if (t != null && ic != null) ic.setComposingText(t, 1);
                    }

                    @Override
                    public void onResults(android.os.Bundle b) {
                        String t = gText(b);
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            if (t != null) ic.commitText(t + " ", 1);
                            else ic.finishComposingText();
                        }
                        refreshSugg();
                        handler.postDelayed(() -> gListen(), 250);
                    }

                    @Override
                    public void onError(int err) {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) ic.finishComposingText();
                        if (err == 7 || err == 6 || err == 5) {
                            handler.postDelayed(() -> gListen(), 300);
                        } else if (err == 8) {
                            handler.postDelayed(() -> gListen(), 600);
                        } else {
                            stopVoice();
                            toast(err == 9 ? "اسمح بالميكروفون من إعدادات التطبيق"
                                    : err == 2 || err == 1 ? "الكتابة الصوتية محتاجة إنترنت" : "تعذر الصوت (كود " + err + ")");
                        }
                    }
                });
            }
            android.content.Intent i = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
            i.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            i.putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            gsr.startListening(i);
        } catch (Throwable e) {
            voiceFail("google", e);
        }
    }

    static String gText(android.os.Bundle b) {
        if (b == null) return null;
        java.util.ArrayList<String> l = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() || l.get(0).trim().isEmpty() ? null : l.get(0).trim();
    }
    static boolean preloading;

    // loads the speech model in the background as soon as the keyboard opens, so the first mic tap starts instantly
    void preloadVoice() {
        if (VoiceInput.model != null || preloading) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (!VoiceInput.installed(this)) return;
        preloading = true;
        final android.content.Context ctx = getApplicationContext();
        new Thread(() -> {
            try {
                VoiceInput.load(ctx);
            } catch (Throwable ignored) {
            }
            preloading = false;
        }).start();
    }

    void startSpeech(org.vosk.Recognizer rec) {
        String stage = "mic";
        try {
            speech = new org.vosk.android.SpeechService(rec, 16000.0f);
            stage = "listen";
            speech.startListening(new org.vosk.android.RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                }

                @Override
                public void onResult(String hypothesis) {
                    handler.post(() -> commitVoice(hypothesis));
                }

                @Override
                public void onFinalResult(String hypothesis) {
                    handler.post(() -> {
                        commitVoice(hypothesis);
                        stopVoice();
                    });
                }

                @Override
                public void onError(Exception e) {
                    handler.post(() -> stopVoice());
                }

                @Override
                public void onTimeout() {
                    handler.post(() -> stopVoice());
                }
            });
            listening = true;
            setMicUi();
        } catch (Throwable e) {
            voiceFail(stage, e);
        }
    }

    void voiceFail(String stage, Throwable e) {
        stopVoice();
        String msg = String.valueOf(e.getMessage());
        if (msg.length() > 90) msg = msg.substring(0, 90);
        toast("تعذر بدء الصوت [" + stage + "] " + e.getClass().getSimpleName() + ": " + msg);
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            FontApp.writeCrash(getApplicationContext(), Thread.currentThread(),
                    new RuntimeException("voice stage=" + stage + " availMB=" + (mi.availMem / 1048576L)
                            + " totalMB=" + (mi.totalMem / 1048576L) + " abi=" + android.os.Build.SUPPORTED_ABIS[0]
                            + " sdk=" + android.os.Build.VERSION.SDK_INT + "\nMODEL FILES:\n" + VoiceInput.diag(getApplicationContext()), e));
        } catch (Throwable ignored) {
        }
    }

    void commitVoice(String json) {
        try {
            String t = new org.json.JSONObject(json).optString("text", "").trim();
            if (t.isEmpty()) return;
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                clearCur();
                ic.commitText(t + " ", 1);
                refreshSugg();
            }
        } catch (Exception ignored) {
        }
    }

    void stopVoice() {
        listening = false;
        if (gsr != null) {
            try {
                gsr.cancel();
                gsr.destroy();
            } catch (Throwable ignored) {
            }
            gsr = null;
            try {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.finishComposingText();
            } catch (Throwable ignored) {
            }
        }
        gMode = false;
        if (speech != null) {
            try {
                speech.stop();
                speech.shutdown();
            } catch (Exception ignored) {
            }
            speech = null;
        }
        setMicUi();
    }

    void setMicUi() {
        if (micKey == null) return;
        micKey.setText(listening ? "⏹" : "🎤");
        micKey.setTextColor(listening ? ACCENT : TXT);
    }

    // ---------- last screenshot + clipboard preview ----------
    Bitmap shotThumb = null;
    Uri shotUri = null;
    long shotAt = 0;
    TextView clipKey = null;

    // finds the newest screenshot in the gallery (needs the photo permission the app asks for)
    void refreshShot() {
        if (System.currentTimeMillis() - shotAt < 5000) return;
        shotAt = System.currentTimeMillis();
        shotThumb = null;
        shotUri = null;
        try {
            String perm = android.os.Build.VERSION.SDK_INT >= 33
                    ? "android.permission.READ_MEDIA_IMAGES" : "android.permission.READ_EXTERNAL_STORAGE";
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, perm)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            android.database.Cursor c = getContentResolver().query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.Images.Media._ID},
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%screenshot%"},
                    android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (c == null) return;
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                shotUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                shotThumb = android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                        getContentResolver(), id, android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            c.close();
        } catch (Exception ignored) {
            shotThumb = null;
            shotUri = null;
        }
    }

    // puts the screenshot on the clipboard as an image, so it can be pasted in any app
    void copyShot() {
        if (shotUri == null || cm == null) return;
        try {
            cm.setPrimaryClip(ClipData.newUri(getContentResolver(), "screenshot", shotUri));
            Toast.makeText(this, "تم نسخ السكرين شوت، الصقه في التطبيق", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    String clipPreview() {
        ArrayList<String> l = clipList();
        if (l.isEmpty()) return "";
        String x = l.get(0);
        if (x.startsWith("\u0002")) x = x.substring(1);
        x = x.replace("\n", " ");
        return x.length() > 7 ? x.substring(0, 7) + "…" : x;
    }

    String clipLabel() {
        String p = clipPreview();
        String head = "";
        if (p.isEmpty()) return head;
        return head.isEmpty() ? p : head + "\n" + p;
    }

    void refreshClipKey() {
        if (clipKey != null) clipKey.setText(clipLabel());
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
            if (d.getDescription() != null && d.getDescription().hasMimeType("image/*")) return;
            if (d.getDescription() != null && d.getDescription().getExtras() != null
                    && d.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) return;
            CharSequence cs = d.getItemAt(0).coerceToText(this);
            if (cs == null) return;
            String s = cs.toString().trim();
            if (s.isEmpty() || s.length() > 2000) return;
            addClip(s);
            refreshClipKey();
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
        pushUndo();
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText(convText(txt), 1);
        refreshSugg();
    }
}
