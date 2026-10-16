package com.fontcopy.app;

import android.app.Application;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FontApp extends Application {
    static final String CRASH_FILE = "crash.log";

    @Override
    public void onCreate() {
        super.onCreate();
        DailyStyle.ensure(this);
        UpdateCheck.schedule(this);
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("updates");
        } catch (Throwable ignored) {
            // Firebase not configured: instant updates are off, the 10:00/20:00 checks still work
        }
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                writeCrash(getApplicationContext(), t, e);
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    // appends the stack trace to a small log file; the file is reset once it grows past ~60 KB
    static void writeCrash(Context c, Thread t, Throwable e) throws Exception {
        File f = new File(c.getFilesDir(), CRASH_FILE);
        if (f.length() > 60000L) f.delete();
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String entry = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())
                + " | " + t.getName() + "\n" + sw + "\n";
        FileOutputStream fo = new FileOutputStream(f, true);
        fo.write(entry.getBytes("UTF-8"));
        fo.close();
    }
}
