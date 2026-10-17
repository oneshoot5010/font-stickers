package com.fontcopy.app;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.vosk.Model;

// Offline Arabic speech model (Vosk). Downloaded once into the app's private storage; nothing is sent to Google.
final class VoiceInput {
    static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip";
    static final String MODEL_DIR = "vosk-ar-s";
    static Model model;

    interface Progress {
        void on(int pct);
    }

    static File dir(Context c) {
        return new File(c.getFilesDir(), MODEL_DIR);
    }

    static boolean installed(Context c) {
        return new File(dir(c), "am/final.mdl").exists();
    }

    // loads the model once per process (the keyboard process and the app process each keep their own copy)
    static synchronized Model load(Context c) throws IOException {
        if (model == null) model = new Model(dir(c).getAbsolutePath());
        return model;
    }

    // compact tree of the extracted model (relative path:size) so a broken install can be seen in the error report
    static String diag(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            walk(dir(c), "", sb, 0);
        } catch (Throwable t) {
            sb.append("diag err ").append(t);
        }
        return sb.toString();
    }

    private static void walk(File f, String rel, StringBuilder sb, int depth) {
        File[] kids = f.listFiles();
        if (kids == null) return;
        java.util.Arrays.sort(kids);
        for (File k : kids) {
            String r = rel + k.getName();
            if (k.isDirectory()) {
                sb.append(r).append("/\n");
                if (depth < 3) walk(k, r + "/", sb, depth + 1);
            } else {
                sb.append(r).append(':').append(k.length()).append('\n');
            }
        }
    }

    // streams the zip straight into the model folder (no big temporary file), reporting progress
    static void download(Context c, Progress p) throws IOException {
        File d = dir(c);
        deleteRec(new File(c.getFilesDir(), "vosk-ar"));
        deleteRec(d);
        d.mkdirs();
        HttpURLConnection conn = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        long total = conn.getContentLengthLong();
        long done = 0;
        int last = -1;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(conn.getInputStream()))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                int s = n.indexOf('/');
                if (s < 0 || n.endsWith("/") || n.contains("..")) {
                    zin.closeEntry();
                    continue;
                }
                File out = new File(d, n.substring(s + 1));
                out.getParentFile().mkdirs();
                try (FileOutputStream fo = new FileOutputStream(out)) {
                    int r;
                    while ((r = zin.read(buf)) > 0) {
                        fo.write(buf, 0, r);
                        done += r;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != last && pct < 100) {
                                last = pct;
                                p.on(pct);
                            }
                        }
                    }
                }
                zin.closeEntry();
            }
        } finally {
            conn.disconnect();
        }
        if (!installed(c)) throw new IOException("model incomplete");
        model = null;
    }

    static void delete(Context c) {
        model = null;
        deleteRec(new File(c.getFilesDir(), "vosk-ar"));
        deleteRec(dir(c));
    }

    static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }
}
