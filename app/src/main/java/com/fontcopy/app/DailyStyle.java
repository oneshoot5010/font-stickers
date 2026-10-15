package com.fontcopy.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.Calendar;
import java.util.Random;

// Daily style notification: once a day at HOUR:00 it shows one random styled phrase.
// The alarm is inexact (Android may shift it a little to save battery).
public class DailyStyle extends BroadcastReceiver {
    static final String ACTION = "com.fontcopy.app.DAILY_STYLE";
    static final String CH = "daily_style";
    static final int NOTIF_ID = 7001;
    static final int HOUR = 9;
    static final String[] PHRASES = {
            "Good Morning", "Stay Strong", "Have a nice day", "Be yourself", "Keep smiling",
            "Dream big", "Never give up", "Stay Cool", "Good Vibes Only", "Love yourself"
    };

    static boolean enabled(Context c) {
        return c.getSharedPreferences("kb", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                .getInt("daily", 0) == 1;
    }

    static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, DailyStyle.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, HOUR);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, alarmIntent(c));
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
        NotificationManagerCompat.from(c).cancel(NOTIF_ID);
    }

    // called on every app start: keeps the alarm alive after updates
    static void ensure(Context c) {
        if (enabled(c)) schedule(c);
    }

    static void show(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Random r = new Random();
        String phrase = PHRASES[r.nextInt(PHRASES.length)];
        int style = 1 + r.nextInt(7); // styles 1..7 (skip the plain one)
        String styled = Fonts.conv(style, phrase);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CH, "ستايل يومي", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("ستايل جديد كل يوم");
                nm.createNotificationChannel(ch);
            }
        }

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent po = PendingIntent.getActivity(c, 0, open, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("ستايل النهارده ✨")
                .setContentText(styled)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(styled + "\n\nافتح التطبيق وانسخه")
                        )
                .setContentIntent(po)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            ensure(c);
            return;
        }
        if (ACTION.equals(a) && enabled(c)) show(c);
    }
}
