package com.baeo.widgets;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * Every widget tap that changes something in the house. Declared android:exported="false",
 * so other apps on the phone cannot send these (v1 took door toggles from anyone).
 */
public class BaeoActionReceiver extends BroadcastReceiver {
    public static final String ACTION_REFRESH = "com.baeo.widgets.ACTION_REFRESH";
    public static final String ACTION_DOOR = "com.baeo.widgets.ACTION_DOOR";
    public static final String ACTION_MIRROR = "com.baeo.widgets.ACTION_MIRROR";
    public static final String ACTION_STATUS = "com.baeo.widgets.ACTION_STATUS";
    public static final String ACTION_RESOLVE_ALERT = "com.baeo.widgets.ACTION_RESOLVE_ALERT";
    public static final String ACTION_SCENE = "com.baeo.widgets.ACTION_SCENE";
    public static final String ACTION_ALL_OFF = "com.baeo.widgets.ACTION_ALL_OFF";
    public static final String ACTION_LIGHT = "com.baeo.widgets.ACTION_LIGHT";
    public static final String ACTION_MEDIA = "com.baeo.widgets.ACTION_MEDIA";

    /** Unlocking takes two taps inside this window. Locking takes one. */
    static final long DOOR_ARM_MS = 5000L;

    public static PendingIntent pi(Context ctx, String action, String arg, int reqCode) {
        Intent i = new Intent(ctx, BaeoActionReceiver.class);
        i.setAction(action);
        if (arg != null) {
            i.setData(Uri.parse("baeo://" + action + "/" + arg));
            i.putExtra("arg", arg);
        }
        return PendingIntent.getBroadcast(ctx, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final SharedPreferences sp = HaClient.prefs(context);
        String arg = intent.getStringExtra("arg");

        switch (intent.getAction()) {
            case ACTION_REFRESH:
                BaeoBriefWidgetProvider.schedulePeriodicSync(context);
                HaClient.refreshAsync(context, goAsync());
                break;

            case ACTION_DOOR:
                door(context, sp);
                break;

            case ACTION_MIRROR: {
                boolean cur = sp.getBoolean("mirror_on", false);
                HaClient.call(context, "fan", "toggle", HaClient.entity(HaClient.MIRROR),
                        () -> sp.edit().putBoolean("mirror_on", !cur).apply(), goAsync());
                break;
            }

            case ACTION_STATUS:
                // The status row: "motion lights paused" (tap resumes), else just refresh.
                // Alerts have their own card now (where the forecast sits), so this row only resumes motion lights.
                if (!sp.getBoolean("motion_on", true)) {
                    HaClient.call(context, "input_boolean", "turn_on", HaClient.entity(HaClient.MOTION),
                            () -> sp.edit().putBoolean("motion_on", true).apply(), goAsync());
                } else {
                    HaClient.refreshAsync(context, goAsync());
                }
                break;

            case ACTION_RESOLVE_ALERT:
                resolveAlert(context, sp, goAsync());
                break;

            case ACTION_SCENE: {
                int idx = parse(arg);
                if (idx < 0 || idx >= HaClient.SCENES.length) break;
                HaClient.call(context, "scene", "turn_on", HaClient.entity(HaClient.SCENES[idx]),
                        () -> sp.edit().putInt("active_scene", idx).apply(), goAsync());
                break;
            }

            case ACTION_ALL_OFF:
                HaClient.call(context, "script", "turn_on", HaClient.entity(HaClient.ALL_OFF), () -> {
                    SharedPreferences.Editor ed = sp.edit().putInt("active_scene", -1);
                    for (int k = 0; k < HomeState.FX_ID.length; k++) ed.putBoolean("fx_on_" + k, false);
                    ed.apply();
                }, goAsync());
                break;

            case ACTION_LIGHT:
                HaClient.toggleLight(context, parse(arg), goAsync());
                break;

            case ACTION_MEDIA:
                if (arg != null) HaClient.media(context, arg, goAsync());
                break;

            default:
                break;
        }
    }

    private void door(Context context, SharedPreferences sp) {
        boolean unlocked = "unlocked".equals(sp.getString("door_state", "locked"));
        long now = System.currentTimeMillis();
        if (unlocked) {
            HaClient.call(context, "lock", "lock", HaClient.entity(HaClient.DOOR),
                    () -> sp.edit().putString("door_state", "locked").putLong("door_arm_until", 0L).apply(), goAsync());
        } else if (now < sp.getLong("door_arm_until", 0L)) {
            HaClient.call(context, "lock", "unlock", HaClient.entity(HaClient.DOOR),
                    () -> sp.edit().putString("door_state", "unlocked").putLong("door_arm_until", 0L).apply(), goAsync());
        } else {
            sp.edit().putLong("door_arm_until", now + DOOR_ARM_MS).apply();
            final Context app = context.getApplicationContext();
            BaeoBriefWidgetProvider.updateAll(app);
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    Thread.sleep(DOOR_ARM_MS + 150L);
                    BaeoBriefWidgetProvider.updateAll(app);   // "Tap again" goes back to "Locked"
                } catch (Throwable ignored) {
                } finally {
                    HaClient.finish(pr);
                }
            }).start();
        }
    }

    private static void resolveAlert(Context context, SharedPreferences sp, PendingResult pending) {
        Runnable clear = () -> sp.edit().putString("alert_id", "").apply();
        switch (sp.getString("alert_id", "")) {
            case "washer":
                HaClient.call(context, "input_boolean", "turn_off", HaClient.entity("input_boolean.washer_done"), clear, pending);
                break;
            case "intruder":
                HaClient.call(context, "input_boolean", "turn_off", HaClient.entity("input_boolean.intruder_alert_active"), clear, pending);
                break;
            case "door":
                HaClient.call(context, "lock", "lock", HaClient.entity(HaClient.DOOR),
                        () -> sp.edit().putString("alert_id", "").putString("door_state", "locked").apply(), pending);
                break;
            default:
                // A leak clears when the sensor dries, not when you tap it.
                HaClient.refreshAsync(context, pending);
                break;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }
}
