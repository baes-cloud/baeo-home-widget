package com.baeo.widgets;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps the last few Home Assistant notifications (house agent, UniFi review, washer...) for the widget's
 * notify feed. Reads only the HA companion app's notifications; everything else is ignored.
 * Needs notification access, which Bae grants in Settings (Device & app notifications).
 */
public class BaeoNotifyListener extends NotificationListenerService {
    static final String HA = "io.homeassistant.companion.android";
    static final String KEY = "notify_feed";
    static final int KEEP = 6;

    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] act = getActiveNotifications();
            if (act == null) return;
            java.util.Arrays.sort(act, (a, b) -> Long.compare(a.getPostTime(), b.getPostTime()));
            for (StatusBarNotification sbn : act) add(this, sbn, false);
            BaeoBriefWidgetProvider.updateAll(this);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (add(this, sbn, true)) BaeoBriefWidgetProvider.updateAll(this);
    }

    /** Adds one HA notification to the feed (newest first). Same tag + title replaces the older line. */
    static boolean add(Context ctx, StatusBarNotification sbn, boolean live) {
        try {
            if (sbn == null || !HA.equals(sbn.getPackageName())) return false;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;   // sensor worker etc.
            Bundle ex = n.extras;
            String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
            if (title.isEmpty() && text.isEmpty()) return false;
            String tag = sbn.getTag() == null ? "" : sbn.getTag();
            String src = source(tag, title, text);
            String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(sbn.getPostTime()));

            SharedPreferences sp = HaClient.prefs(ctx);
            JSONArray old = new JSONArray(sp.getString(KEY, "[]"));
            JSONArray out = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", time).put("src", src).put("title", title).put("msg", text.replace('\n', ' '))
                    .put("key", tag + "|" + title).put("ms", sbn.getPostTime());
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < KEEP; i++) {
                JSONObject x = old.getJSONObject(i);
                if (x.optString("key").equals(o.optString("key"))) continue;
                if (!live && x.optLong("ms") == o.optLong("ms")) continue;
                out.put(x);
            }
            sp.edit().putString(KEY, out.toString()).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** A line from this phone (a typed command, or the agent's reply) straight into the feed. */
    static void addLocal(Context ctx, String src, String msg) {
        try {
            SharedPreferences sp = HaClient.prefs(ctx);
            JSONArray old = new JSONArray(sp.getString(KEY, "[]"));
            JSONArray out = new JSONArray();
            long now = System.currentTimeMillis();
            out.put(new JSONObject().put("t", new SimpleDateFormat("HH:mm", Locale.US).format(new Date(now)))
                    .put("src", src).put("title", "").put("msg", msg).put("key", "local|" + now).put("ms", now));
            for (int i = 0; i < old.length() && out.length() < KEEP; i++) out.put(old.getJSONObject(i));
            sp.edit().putString(KEY, out.toString()).apply();
        } catch (Throwable ignored) {}
    }

    /** A short source name for the feed's coloured column. */
    static String source(String tag, String title, String text) {
        String t = tag == null ? "" : tag.toLowerCase(Locale.US);
        String all = (t + " " + title + " " + text).toLowerCase(Locale.US);
        if (all.contains("houseagent") || all.contains("house notes") || all.contains("house agent") || all.contains("home-handler")) return "home-handler";
        if (t.startsWith("house_health")) return "health";   // the homelab health check (remotes, NAS, devices)
        if (all.contains("unifi")) return "unifi";
        if (all.contains("wash") || all.contains("dryer")) return "washer";
        if (all.matches("(?s).*\\b(leak|water)\\b.*")) return "leak";
        // Whole words only: "block", "clock" or "unlocked bootloader" must not read as the front door.
        if (all.matches("(?s).*\\b(front door|door|lock|locked|unlocked|doorbell)\\b.*")) return "door";
        if (all.matches("(?s).*\\balarm\\b.*")) return "alarm";
        return "ha";
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString().trim(); }
}
