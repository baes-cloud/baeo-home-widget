package com.baeo.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;

/** The Bæo Home widget. Keeps v1's class name so the Bæo Brief already placed turns into it. */
public class BaeoBriefWidgetProvider extends AppWidgetProvider {
    static final String ACTION_DUMP_PREVIEWS = "com.baeo.widgets.ACTION_DUMP_PREVIEWS";

    private static final int[] FX_VIEWS = {
        R.id.hit_fx_0, R.id.hit_fx_1, R.id.hit_fx_2, R.id.hit_fx_3, R.id.hit_fx_4, R.id.hit_fx_5, R.id.hit_fx_6, R.id.hit_fx_7,
        R.id.hit_fx_8, R.id.hit_fx_9, R.id.hit_fx_10, R.id.hit_fx_11, R.id.hit_fx_12, R.id.hit_fx_13, R.id.hit_fx_14, R.id.hit_fx_15, R.id.hit_fx_16
    };
    private static BaeoHomeRender.Fonts fonts;

    static synchronized BaeoHomeRender.Fonts fonts(Context ctx) {
        if (fonts == null) {
            BaeoHomeRender.Fonts f = new BaeoHomeRender.Fonts();
            try {
                f.m500 = Typeface.createFromAsset(ctx.getAssets(), "fonts/manrope_500.ttf");
                f.m600 = Typeface.createFromAsset(ctx.getAssets(), "fonts/manrope_600.ttf");
                f.m700 = Typeface.createFromAsset(ctx.getAssets(), "fonts/manrope_700.ttf");
                f.syne700 = Typeface.createFromAsset(ctx.getAssets(), "fonts/syne_700.ttf");
                f.syne400 = Typeface.createFromAsset(ctx.getAssets(), "fonts/syne_400.ttf");
                f.mono = Typeface.MONOSPACE;
                f.icons = Typeface.createFromAsset(ctx.getAssets(), "fonts/material_round.otf");
                f.monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            } catch (Throwable t) {
                f.m500 = Typeface.SANS_SERIF;
                f.m600 = f.m700 = f.syne700 = Typeface.DEFAULT_BOLD;
                f.syne400 = Typeface.SANS_SERIF;
            }
            fonts = f;
        }
        return fonts;
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        schedulePeriodicSync(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        schedulePeriodicSync(context);
        updateWidgets(context, mgr, ids);
        HaClient.refreshAsync(context, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions);
        updateWidgets(context, mgr, new int[]{id});
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                schedulePeriodicSync(context);
                HaClient.refreshAsync(context, goAsync());
                break;
            case ACTION_DUMP_PREVIEWS:
                dump(context, goAsync());
                break;
            default:
                break;
        }
    }

    /** adb-only check: writes what the widget currently draws to the app's external files dir. Changes nothing in the house. */
    private static void dump(Context context, PendingResult pr) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                HaClient.fetchAndStore(app);
                File dir = app.getExternalFilesDir(null);
                if (dir != null) {
                    Bitmap b = BaeoHomeRender.render(HaClient.state(app), fonts(app), 980, 823);
                    try (FileOutputStream fos = new FileOutputStream(new File(dir, "home_live.png"))) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                }
                updateAll(app);
            } catch (Throwable ignored) {
            } finally {
                HaClient.finish(pr);
            }
        }).start();
    }

    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, BaeoBriefWidgetProvider.class));
        if (ids != null && ids.length > 0) updateWidgets(context, mgr, ids);
    }

    private static void updateWidgets(Context context, AppWidgetManager mgr, int[] ids) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        HomeState st = HaClient.state(context);
        for (int id : ids) {
            Bundle opts = mgr.getAppWidgetOptions(id);
            int wDp = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 390) : 390;
            int hDp = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300) : 300;
            if (wDp <= 0) wDp = 390;
            if (hDp <= 0) hDp = 300;

            // One bitmap, capped at 980 wide: sharp on a Pixel, well under the RemoteViews size limit.
            float scale = Math.min(dm.density, 980f / wDp);
            int wPx = Math.round(wDp * scale), hPx = Math.round(hDp * scale);
            BaeoHomeRender.Geo g = BaeoHomeRender.geo(st.mediaShow, BaeoHomeRender.hasStatus(st), wPx, hPx);

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_home);
            rv.setImageViewBitmap(R.id.home_canvas, BaeoHomeRender.render(st, fonts(context), wPx, hPx));

            // Tap zones: same geometry as the picture, converted from bitmap px to dp.
            if (g.feed != null) {
                place(rv, R.id.hit_feed, new RectF(g.feed.left, g.feed.top + 46f * (wPx / 980f), g.feed.right, g.feedPrompt.top), scale,
                        openHomeAssistant(context));
                place(rv, R.id.hit_cmd, g.feedPrompt, scale, openTerminal(context));   // the "$" line
            }
            rv.setViewVisibility(R.id.hit_feed, g.feed != null ? View.VISIBLE : View.GONE);
            rv.setViewVisibility(R.id.hit_cmd, g.feed != null ? View.VISIBLE : View.GONE);
            // Both top rows (Tomorrow, Next alarm) open the Clock app's alarms.
            boolean alerting = BaeoHomeRender.hasAlert(st);   // the alert sits in these two rows; a tap resolves it
            place(rv, R.id.hit_now, g.word, scale, alerting ? tap(context, BaeoActionReceiver.ACTION_RESOLVE_ALERT, null, 14) : openAlarms(context));
            place(rv, R.id.hit_alarm, g.alarm, scale, alerting ? tap(context, BaeoActionReceiver.ACTION_RESOLVE_ALERT, null, 16) : openAlarms(context));
            place(rv, R.id.hit_event, g.event, scale, tap(context, BaeoActionReceiver.ACTION_STATUS, null, 11));
            // While an alert is showing it sits where the forecast was, and a tap resolves it.
            // This Week: each day card opens the calendar on that day (an alert card resolves instead).
            int[] dayIds = {R.id.hit_day_0, R.id.hit_day_1, R.id.hit_day_2};
            boolean al = false;   // the day cards stay put now that alerts use the top rows
            RectF[] cards = BaeoHomeRender.dayCards(g);
            for (int i = 0; i < 3; i++) {
                if (!al && cards != null) place(rv, dayIds[i], cards[i], scale, openCalendar(context, i + 1, 54 + i));
                rv.setViewVisibility(dayIds[i], !al && cards != null ? View.VISIBLE : View.GONE);
            }
            if (g.feed != null) place(rv, R.id.hit_feed_title, new RectF(g.feed.left, g.feed.top, g.feed.right, g.feed.top + 46f * (wPx / 980f)), scale, openUrl(context, Secrets.AGENT_PAGE_URL, 57));
            rv.setViewVisibility(R.id.hit_feed_title, g.feed != null ? View.VISIBLE : View.GONE);
            place(rv, R.id.hit_forecast, g.forecast, scale, tap(context, BaeoActionReceiver.ACTION_REFRESH, null, 10));
            // Icon buttons sit in equal thirds of the pill row; tap zones follow them.
            RectF prow = new RectF(g.disco.left, g.disco.top, g.door.right, g.disco.bottom);
            float t3 = prow.width() / 3f;
            float h2 = prow.width() / 2f;
            RectF[] slot = BaeoHomeRender.ICON_BUTTONS   // no disco button: lights left half, door right half
                    ? new RectF[]{null, g.lights, g.door}
                    : new RectF[]{g.disco, g.lights, g.door};
            if (slot[0] != null) place(rv, R.id.hit_disco, slot[0], scale, tap(context, BaeoActionReceiver.ACTION_MIRROR, null, 12));
            rv.setViewVisibility(R.id.hit_disco, slot[0] != null ? View.VISIBLE : View.GONE);
            place(rv, R.id.hit_lights, slot[1], scale, tap(context, BaeoActionReceiver.ACTION_ALL_OFF, null, 15));
            place(rv, R.id.hit_door, slot[2], scale, tap(context, BaeoActionReceiver.ACTION_DOOR, null, 13));
            if (g.climate != null) place(rv, R.id.hit_climate, g.climate, scale, openHomeAssistant(context));
            rv.setViewVisibility(R.id.hit_climate, g.climate != null ? View.VISIBLE : View.GONE);
            int[] mediaIds = {R.id.hit_media_info, R.id.hit_media_prev, R.id.hit_media_play, R.id.hit_media_next,
                    R.id.hit_media_vol_down, R.id.hit_media_vol_up, R.id.hit_sets};
            if (st.mediaShow) {
                place(rv, R.id.hit_sets, g.mArt, scale, openSets(context));   // the art / note opens Bæo Sets
                place(rv, R.id.hit_media_info, g.mInfo, scale, openApp(context, "com.sonos.acr2", 58));   // track info opens Sonos
                place(rv, R.id.hit_media_prev, g.mPrev, scale, tap(context, BaeoActionReceiver.ACTION_MEDIA, "prev", 40));
                place(rv, R.id.hit_media_play, g.mPlay, scale, tap(context, BaeoActionReceiver.ACTION_MEDIA, "play", 41));
                place(rv, R.id.hit_media_next, g.mNext, scale, tap(context, BaeoActionReceiver.ACTION_MEDIA, "next", 42));
                // No volume buttons any more: the readout's area just refreshes.
                place(rv, R.id.hit_media_vol_up, g.mUp, scale, tap(context, BaeoActionReceiver.ACTION_REFRESH, null, 10));
            }
            for (int mid : mediaIds) rv.setViewVisibility(mid, st.mediaShow ? View.VISIBLE : View.GONE);
            rv.setViewVisibility(R.id.hit_media_vol_down, View.GONE);

            // A light's tap target is a square 3.2 badge-radii wide, centred on its badge.
            float box = g.badgeR * 3.2f;
            for (int i = 0; i < FX_VIEWS.length; i++) {
                RectF r = new RectF(g.fxX(i) - box / 2f, g.fxY(i) - box / 2f, g.fxX(i) + box / 2f, g.fxY(i) + box / 2f);
                place(rv, FX_VIEWS[i], r, scale, tap(context, BaeoActionReceiver.ACTION_LIGHT, String.valueOf(i), 60 + i));
            }
            mgr.updateAppWidget(id, rv);
        }
    }

    private static void place(RemoteViews rv, int viewId, RectF r, float scale, PendingIntent pi) {
        int dip = TypedValue.COMPLEX_UNIT_DIP;
        rv.setViewLayoutWidth(viewId, r.width() / scale, dip);
        rv.setViewLayoutHeight(viewId, r.height() / scale, dip);
        rv.setViewLayoutMargin(viewId, RemoteViews.MARGIN_START, r.left / scale, dip);
        rv.setViewLayoutMargin(viewId, RemoteViews.MARGIN_TOP, r.top / scale, dip);
        rv.setOnClickPendingIntent(viewId, pi);
    }

    private static PendingIntent tap(Context ctx, String action, String arg, int reqCode) {
        return BaeoActionReceiver.pi(ctx, action, arg, reqCode);
    }

    /** Tapping the date opens the Home Assistant app, or this app's screen if it isn't installed. */
    private static PendingIntent openHomeAssistant(Context ctx) {
        Intent i = null;
        try {
            i = ctx.getPackageManager().getLaunchIntentForPackage("io.homeassistant.companion.android");
        } catch (Throwable ignored) {}
        if (i == null) i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 50, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Google Calendar (or any calendar app) on today + daysAhead. */
    private static PendingIntent openCalendar(Context ctx, int daysAhead, int req) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_YEAR, daysAhead);
        c.set(java.util.Calendar.HOUR_OF_DAY, 8);
        android.net.Uri u = android.net.Uri.parse("content://com.android.calendar/time/" + c.getTimeInMillis());
        Intent i = new Intent(Intent.ACTION_VIEW, u);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The Clock app's alarm list. */
    private static PendingIntent openAlarms(Context ctx) {
        Intent i = new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 55 + 10, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openUrl(Context ctx, String url, int req) {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** An installed app by package, else the HA app. */
    private static PendingIntent openApp(Context ctx, String pkg, int req) {
        Intent i = null;
        try { i = ctx.getPackageManager().getLaunchIntentForPackage(pkg); } catch (Throwable ignored) {}
        if (i == null) return openHomeAssistant(ctx);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The house agent terminal (the feed's "$" line). */
    private static PendingIntent openTerminal(Context ctx) {
        Intent i = new Intent(ctx, AgentTerminalActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(ctx, 52, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The Bæo Sets app (com.baeo.sets); falls back to this app's screen if it isn't installed. */
    private static PendingIntent openSets(Context ctx) {
        Intent i = null;
        try {
            i = ctx.getPackageManager().getLaunchIntentForPackage("com.baeo.sets");
        } catch (Throwable ignored) {}
        if (i == null) i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 51, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Every minute while the Club is playing (so the track keeps up), every four otherwise. */
    public static void schedulePeriodicSync(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long every = HaClient.prefs(ctx).getBoolean("media_show", false) ? 60_000L : 240_000L;
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + every, every,
                    tap(ctx, BaeoActionReceiver.ACTION_REFRESH, null, 77));
        } catch (Throwable ignored) {}
    }
}
