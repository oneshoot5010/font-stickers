package com.fontcopy.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import org.json.JSONObject;

// Background update check: at 10:00 and 20:00 it reads latest.json and,
// if a newer build exists, shows a notification. The app itself checks on every open.
public class UpdateCheck extends BroadcastReceiver {
    static final String ACTION = "com.fontcopy.app.UPD_CHECK";
    static final String CH = "app_update";
    static final int NOTIF_ID = 7002;
    static final int[] HOURS = {10, 20};

    static PendingIntent alarmIntent(Context c, int hour) {
        Intent i = new Intent(c, UpdateCheck.class).setAction(ACTION).putExtra("hour", hour);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 500 + hour, i, flags);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int h : HOURS) {
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, h);
            t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_MONTH, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, alarmIntent(c, h));
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            schedule(c);
            return;
        }
        if (!ACTION.equals(a)) return;
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                check(c);
            } catch (Exception ignored) {
                // no internet or bad response: try again at the next time
            } finally {
                pr.finish();
            }
        }).start();
    }

    static void check(Context c) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(MainActivity.UPD_URL).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setInstanceFollowRedirects(true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = con.getInputStream()) {
            byte[] b = new byte[4096];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        } finally {
            con.disconnect();
        }
        JSONObject j = new JSONObject(bo.toString("UTF-8"));
        int remote = j.optInt("versionCode", 0);
        PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        if (remote > pi.versionCode) notifyUpdate(c, j.optString("versionName", ""));
    }

    static void notifyUpdate(Context c, String versionName) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CH, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("إشعار لما تكون فيه نسخة جديدة");
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent po = PendingIntent.getActivity(c, 0, open, flags);

        String text = "نسخة " + versionName + " جاهزة، افتح التطبيق وحدّث";
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("فيه تحديث جديد لاستيكر فونت ⬆️")
                .setContentText(text)
                .setContentIntent(po)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
    }
}
