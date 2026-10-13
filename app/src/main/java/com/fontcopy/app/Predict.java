package com.fontcopy.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class Predict {
    private Predict() {}

    static final String[] EN = ("the i to and you a it is in that of for was on are with have this be at not but we they what so can "
            + "your my do just me all if will get there like would about up out know one go good how now when time no from her his she he by or an as "
            + "want need see think make please thanks thank hello hi hey ok okay yes love well really back more some than then them been had has did were who why where which "
            + "because people today tomorrow tonight night morning evening day home work game play name friend friends best nice great new very much too also only over after before "
            + "again always never maybe sorry welcome happy birthday congratulations message call phone number video photo picture send share follow comment free online account "
            + "password download install update version app keyboard font style text words word language english arabic here still every another because between both through "
            + "something anything everything nothing someone anyone everyone little long look come came take took give gave tell told ask asked try tried feel felt keep left "
            + "right first last next other own same different should could must might shall may let put say said use used find found help helped start stop open close "
            + "read write written listen watch wait waiting meet met bring buy bought sell money price cheap expensive big small high low early late soon already almost "
            + "together without within during until while since though although however actually probably definitely exactly finally suddenly seriously totally awesome amazing "
            + "beautiful wonderful perfect crazy funny sweet sorry brother sister mother father family girl boy man woman baby school class teacher student exam study "
            + "music song movie show series team player winner level score online server clan squad profile status story post reel live stream channel group").split(" ");

    static final String[] AR = ("أنا في مش على إيه من ده دي دول احنا إنت إنتي إنتو هو هي هما إزيك إزاي ليه فين إمتى مين كده دلوقتي النهاردة بكرة إمبارح "
            + "عايز عايزة عاوز عاوزة اللي بس لسه خلاص تمام ماشي أوي قوي جدا كتير شوية حاجة حاجات يعني طيب لا أيوه أه نعم والله الحمد لله الله يبارك شكرا عفوا أهلا مرحبا "
            + "السلام عليكم ورحمة وبركاته صباح مساء الخير النور سلام كيف حالك بخير إن شاء كان كانت يكون هذا هذه ذلك التي الذي لكن أو ثم قد لم لن ما هل كل بعد قبل مع عن "
            + "إلى حتى عند بين فوق تحت هنا هناك دائما أحيانا اكتب كتابة نص نصوص خط خطوط زخرفة اسم أسماء لعبة ألعاب حساب تطبيق كيبورد لوحة مفاتيح صورة فيديو رسالة "
            + "رسايل مكالمة تليفون موبايل رقم واتساب فيسبوك انستجرام تيك توك يوتيوب جروب صاحبي صديقي حبيبي أخويا أخي أختي ماما بابا ولاد بنات شغل بيت مدرسة جامعة امتحان "
            + "فلوس سعر كام ممكن ينفع محتاج عايزين هعمل هروح هاجي جاي رايح بعمل بقول قولي قال قالت شوف شوفي تعالى تعال استنى استنا خليك خلي سيب سبني "
            + "مبروك كل سنة وأنت طيب عيد سعيد رمضان كريم صباحك مساءك ربنا يخليك يحفظك يوفقك يارب ياعم يا باشا يا غالي يا حبيبي يا صاحبي عامل إيه عاملة إيه "
            + "اشتغل شغال بيشتغل لعب العب بلعب نلعب كلان فريق لاعب مستوى سيرفر ستوري بوست لايف قناة اشتراك متابعة لايك كومنت شير رد ردي ابعت ابعتلي ابعتلك "
            + "دلوقتي بعدين قبلها بعدها زمان قريب بعيد حلو حلوة جميل جميلة وحش وحشة كويس كويسة زي كمان برضه برده بدل بدون بس خالص تماما فعلا طبعا أكيد يمكن "
            + "ممكن تساعدني ساعدني شكرا جزيلا جزاك الله خير تسلم تسلمي الله يسلمك الله يعافيك").split(" ");

    static final HashMap<String, String> FIX_EN = new HashMap<>();
    static final HashMap<String, String> FIX_AR = new HashMap<>();
    static final HashMap<String, Integer> L = new HashMap<>();
    static boolean loaded = false;
    static int dirty = 0;
    static int gen = -1;

    static {
        String[] p = {"teh", "the", "adn", "and", "i", "I", "im", "I'm", "ive", "I've", "dont", "don't", "cant", "can't", "wont", "won't",
                "didnt", "didn't", "doesnt", "doesn't", "isnt", "isn't", "wasnt", "wasn't", "thats", "that's", "youre", "you're",
                "recieve", "receive", "definately", "definitely", "wierd", "weird", "becuase", "because", "thier", "their",
                "alot", "a lot", "goverment", "government", "tommorow", "tomorrow", "wich", "which", "freind", "friend"};
        for (int i = 0; i + 1 < p.length; i += 2) FIX_EN.put(p[i], p[i + 1]);
        String[] a = {"اللة", "الله", "هاذا", "هذا", "هاذه", "هذه", "لاكن", "لكن", "ذالك", "ذلك", "انشاء", "إنشاء"};
        for (int i = 0; i + 1 < a.length; i += 2) FIX_AR.put(a[i], a[i + 1]);
        FIX_AR.remove("انشاء");
    }

    static void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            String s = c.getSharedPreferences("kb", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).getString("learn", "");
            if (s == null || s.isEmpty()) return;
            for (String line : s.split("\n")) {
                int t = line.indexOf('\t');
                if (t <= 0) continue;
                try {
                    L.put(line.substring(0, t), Integer.parseInt(line.substring(t + 1)));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    static void save(Context c) {
        sync(c);
        if (dirty == 0) return;
        dirty = 0;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : L.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        c.getSharedPreferences("kb", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).edit().putString("learn", sb.toString()).apply();
    }

    // the main app bumps "learnGen" when the user clears learned words; drop our in-memory copy too
    static void sync(Context c) {
        int g = c.getSharedPreferences("kb", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).getInt("learnGen", 0);
        if (gen == -1) {
            gen = g;
            return;
        }
        if (g != gen) {
            gen = g;
            L.clear();
            dirty = 0;
        }
    }

    static boolean isAr(String w) {
        if (w.isEmpty()) return false;
        char c = w.charAt(0);
        return c >= 0x0600 && c <= 0x06FF;
    }

    static String norm(String s, int lang) {
        if (lang == 0) return s.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 0x064B && ch <= 0x0652) || ch == 0x0640) continue;
            if (ch == 'أ' || ch == 'إ' || ch == 'آ') ch = 'ا';
            else if (ch == 'ى') ch = 'ي';
            else if (ch == 'ة') ch = 'ه';
            sb.append(ch);
        }
        return sb.toString();
    }

    static void learn(Context c, String w) {
        load(c);
        sync(c);
        if (w == null || w.length() < 2 || w.length() > 20) return;
        for (int i = 0; i < w.length(); i++) {
            if (!Character.isLetter(w.charAt(i))) return;
        }
        String key = isAr(w) ? norm(w, 1) : w.toLowerCase();
        if (isAr(w)) key = w;
        Integer n = L.get(key);
        L.put(key, n == null ? 1 : n + 1);
        if (L.size() > 1500) {
            Iterator<Map.Entry<String, Integer>> it = L.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() <= 1) it.remove();
            }
        }
        dirty++;
        if (dirty >= 8) save(c);
    }

    static String fix(int lang, String w) {
        if (w == null || w.isEmpty()) return null;
        return lang == 0 ? FIX_EN.get(w.toLowerCase()) : FIX_AR.get(w);
    }

    static String[] suggest(int lang, String prefix) {
        String p = norm(prefix, lang);
        ArrayList<String> out = new ArrayList<>();
        ArrayList<Map.Entry<String, Integer>> cand = new ArrayList<>();
        for (Map.Entry<String, Integer> e : L.entrySet()) {
            String w = e.getKey();
            if (isAr(w) != (lang == 1)) continue;
            String nw = norm(w, lang);
            if (p.isEmpty()) {
                if (e.getValue() < 2) continue;
            } else if (!nw.startsWith(p) || nw.equals(p)) {
                continue;
            }
            cand.add(e);
        }
        Collections.sort(cand, (x, y) -> y.getValue() - x.getValue());
        for (Map.Entry<String, Integer> e : cand) {
            if (out.size() >= 3) break;
            out.add(e.getKey());
        }
        String[] d = lang == 1 ? AR : EN;
        for (String w : d) {
            if (out.size() >= 3) break;
            String nw = norm(w, lang);
            if (!p.isEmpty() && (!nw.startsWith(p) || nw.equals(p))) continue;
            boolean dup = false;
            for (String o : out) {
                if (norm(o, lang).equals(nw)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(w);
        }
        return out.toArray(new String[0]);
    }
}
