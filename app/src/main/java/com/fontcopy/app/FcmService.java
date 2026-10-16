package com.fontcopy.app;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

// Instant update notification: the release workflow sends a data message to the "updates" topic.
public class FcmService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        if (d == null) return;
        int remote;
        try {
            remote = Integer.parseInt(d.get("versionCode"));
        } catch (Exception e) {
            return;
        }
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (remote <= pi.versionCode) return; // already on this version or newer
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        String vn = d.get("versionName");
        UpdateCheck.notifyUpdate(this, vn == null ? "" : vn);
    }
}
