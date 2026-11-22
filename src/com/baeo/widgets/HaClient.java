package com.baeo.widgets;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to Home Assistant, caches what the widget needs in prefs, and builds the HomeState. */
public final class HaClient {
    private static final String TAG = "BaeoHA";
    private static final String PREFS = "baeo_ha_state";
    private static final String CHANNEL_ID = "baeo_house_alerts";
    private static final String ART_FILE = "club_art.img";

    public static final String DOOR = "lock.lock_pro_0fa0";
    public static final String MIRROR = "fan.mirror_ball";
    public static final String MOTION = "input_boolean.light_automations";
    public static final String CLUB_PLAYER = "media_player.club";
    public static final String ALL_OFF = "script.scene_all_off";

    public static final String[] SCENES = {
        "scene.home_home_tokyo", "scene.home_home_soho", "scene.home_home_santorini", "scene.home_home_disturbia"
    };

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private HaClient() {}

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ async entry points
    public static void refreshAsync(Context ctx, BroadcastReceiver.PendingResult pending) {
        final Context app = ctx.getApplicationContext();
        EXEC.execute(() -> {
            try {
                fetchAndStore(app);
                BaeoBriefWidgetProvider.updateAll(app);
            } finally {
                finish(pending);
            }
        });
    }

    /** Applies the optimistic change, redraws, calls the service, then re-reads the real state. */
    public static void call(Context ctx, String domain, String service, JSONObject payload,
                            Runnable optimistic, BroadcastReceiver.PendingResult pending) {
        final Context app = ctx.getApplicationContext();
        if (optimistic != null) {
            optimistic.run();
            BaeoBriefWidgetProvider.updateAll(app);
        }
        EXEC.execute(() -> {
            try {
                try {
                    httpPost(app, "/api/services/" + domain + "/" + service, payload.toString());
                    Thread.sleep(950);
                } catch (Throwable t) {
                    Log.w(TAG, "Service call failed: " + domain + "." + service, t);
                }
                fetchAndStore(app);
                BaeoBriefWidgetProvider.updateAll(app);
            } finally {
                finish(pending);
            }
        });
    }

    public static JSONObject entity(String... ids) {
        JSONObject o = new JSONObject();
        try {
            if (ids.length == 1) {
                o.put("entity_id", ids[0]);
            } else {
                JSONArray a = new JSONArray();
                for (String id : ids) a.put(id);
                o.put("entity_id", a);
            }
        } catch (Throwable ignored) {}
        return o;
    }

    public static void toggleLight(Context ctx, int i, BroadcastReceiver.PendingResult pending) {
        if (i < 0 || i >= HomeState.FX_ID.length) { finish(pending); return; }
        SharedPreferences sp = prefs(ctx);
        boolean on = sp.getBoolean("fx_on_" + i, false);
        String domain = HomeState.FX_ID[i].substring(0, HomeState.FX_ID[i].indexOf('.'));   // light, or fan (mirror ball)
        call(ctx, domain, on ? "turn_off" : "turn_on", entity(HomeState.FX_ID[i]),
                () -> sp.edit().putBoolean("fx_on_" + i, !on).apply(), pending);
    }

    public static void media(Context ctx, String cmd, BroadcastReceiver.PendingResult pending) {
        SharedPreferences sp = prefs(ctx);
        try {
            JSONObject body = entity(CLUB_PLAYER);
            switch (cmd) {
                case "play": {
                    boolean playing = sp.getBoolean("media_playing", false);
                    call(ctx, "media_player", "media_play_pause", body,
                            () -> sp.edit().putBoolean("media_playing", !playing).apply(), pending);
                    return;
                }
                case "prev":
                    call(ctx, "media_player", "media_previous_track", body, null, pending);
                    return;
                case "next":
                    call(ctx, "media_player", "media_next_track", body, null, pending);
                    return;
                case "vol_up":
                case "vol_down": {
                    float cur = sp.getFloat("media_volume", 0.2f);
                    float next = Math.max(0f, Math.min(1f, cur + ("vol_up".equals(cmd) ? 0.04f : -0.04f)));
                    body.put("volume_level", Math.round(next * 100f) / 100.0);
                    call(ctx, "media_player", "volume_set", body,
                            () -> sp.edit().putFloat("media_volume", next).apply(), pending);
                    return;
                }
                default:
                    break;
            }
        } catch (Throwable ignored) {}
        finish(pending);
    }

    static void finish(BroadcastReceiver.PendingResult pending) {
        if (pending != null) {
            try { pending.finish(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ http
    private static String baseUrl(Context ctx) {
        String u = prefs(ctx).getString("ha_url", "");
        return u.isEmpty() ? Secrets.HA_URL : u;
    }

    private static String token(Context ctx) {
        String t = prefs(ctx).getString("ha_token", "");
        return t.isEmpty() ? Secrets.HA_TOKEN : t;
    }

    /** Away from home HA is reached at its public URL (Access bypass there; the HA token is the gate).
     *  After the LAN fails, the public route is used for 5 minutes, then the LAN is tried again. */
    static volatile long cloudUntil = 0L;
    static volatile boolean viaCloud = false;

    static boolean cloudAvailable() { return !Secrets.HA_CLOUD_URL.isEmpty(); }

    private static HttpURLConnection open(Context ctx, String path, String method) throws Exception {
        boolean cloud = cloudAvailable() && System.currentTimeMillis() < cloudUntil;
        viaCloud = cloud;
        HttpURLConnection conn = (HttpURLConnection) new URL((cloud ? Secrets.HA_CLOUD_URL : baseUrl(ctx)) + path)
                .openConnection(Proxy.NO_PROXY);
        conn.setConnectTimeout(cloud ? 7000 : 2500);
        conn.setReadTimeout(cloud ? 9000 : 4500);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + token(ctx));
        conn.setRequestProperty("Content-Type", "application/json");
        return conn;
    }

    private static byte[] httpGetBytes(Context ctx, String path) throws Exception {
        try {
            return httpGetBytesOnce(ctx, path);
        } catch (java.io.IOException e) {
            if (!cloudAvailable() || System.currentTimeMillis() < cloudUntil) throw e;
            cloudUntil = System.currentTimeMillis() + 300_000L;
            return httpGetBytesOnce(ctx, path);
        }
    }

    private static byte[] httpGetBytesOnce(Context ctx, String path) throws Exception {
        HttpURLConnection conn = open(ctx, path, "GET");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
            return readAll(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static String httpPost(Context ctx, String path, String jsonBody) throws Exception {
        try {
            return httpPostOnce(ctx, path, jsonBody);
        } catch (java.io.IOException e) {
            if (!cloudAvailable() || System.currentTimeMillis() < cloudUntil) throw e;
            cloudUntil = System.currentTimeMillis() + 300_000L;
            return httpPostOnce(ctx, path, jsonBody);
        }
    }

    private static String httpPostOnce(Context ctx, String path, String jsonBody) throws Exception {
        HttpURLConnection conn = open(ctx, path, "POST");
        conn.setDoOutput(true);
        try {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
            return new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ------------------------------------------------------------------ fetch
    public static synchronized void fetchAndStore(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        try {
            long t0 = System.nanoTime();
            JSONArray arr = new JSONArray(new String(httpGetBytes(ctx, "/api/states"), StandardCharsets.UTF_8));
            sp.edit().putInt("ha_ms", (int) ((System.nanoTime() - t0) / 1_000_000L)).putInt("ha_entities", arr.length())
                    .putBoolean("ha_cloud", viaCloud).apply();
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean("online", true);
            ed.putLong("last_sync_ms", System.currentTimeMillis());

            ZoneId zone = ZoneId.systemDefault();
            JSONObject weatherObj = null, calObj = null, workObj = null;
            List<ZonedDateTime> alarms = new ArrayList<>();
            List<String> alarmIds = new ArrayList<>();
            boolean alarmsEnabled = true, alarmsOffToday = false;
            boolean leakOn = false, intruderOn = false, washerDone = false;
            String washerSince = "";
            boolean doorUnlocked = false, doorKeepUnlocked = false;
            long doorChangedMs = 0L;

            long[] sceneMs = new long[SCENES.length];
            long allOffMs = 0L;
            boolean[] fxOn = new boolean[HomeState.FX_ID.length];
            boolean mediaWas = sp.getBoolean("media_show", false);
            boolean mediaShow = false;
            String artPath = "";

            for (int i = 0; i < arr.length(); i++) {
                JSONObject st = arr.getJSONObject(i);
                String eid = st.optString("entity_id", "");
                String state = st.optString("state", "");
                JSONObject attrs = st.optJSONObject("attributes");
                if (attrs == null) attrs = new JSONObject();
                boolean on = "on".equalsIgnoreCase(state);

                switch (eid) {
                    case "weather.forecast_home": weatherObj = st; break;
                    case "sensor.work_start": workObj = st; break;
                    case "climate.aircon": {
                        double cur = attrs.optDouble("current_temperature", Double.NaN);
                        double tgt = attrs.optDouble("temperature", Double.NaN);
                        boolean acOn = !"off".equals(state) && !"unavailable".equals(state) && !"unknown".equals(state);
                        ed.putString("inside_temp", Double.isNaN(cur) ? "" : String.format(Locale.US, "%.1f", cur));
                        String verb = "cool".equals(state) ? "cooling" : "heat".equals(state) ? "heating" : "dry".equals(state) ? "drying"
                                : "fan_only".equals(state) ? "fan only" : state;
                        ed.putString("aircon_line", !acOn ? "Aircon off"
                                : "Aircon " + verb + (Double.isNaN(tgt) ? "" : String.format(Locale.US, " to %.0f°", tgt)));
                        ed.putBoolean("aircon_on", acOn);
                        break;
                    }
                    case "sensor.timmerflotte_temp_hmd_sensor_humidity":
                        try { ed.putString("inside_hum", String.valueOf(Math.round(Double.parseDouble(state)))); }
                        catch (Throwable t) { ed.putString("inside_hum", ""); }
                        break;
                    case "input_boolean.work_alarms_enabled": alarmsEnabled = on; break;
                    case "input_boolean.work_alarms_off_today": alarmsOffToday = on; break;
                    case "sensor.work_alarm_1":
                    case "sensor.work_alarm_2":
                    case "sensor.work_alarm_wfh": {
                        ZonedDateTime z = parseIsoTime(state, zone);
                        if (z != null) { alarms.add(z); alarmIds.add(eid); }
                        break;
                    }
                    case DOOR: {
                        doorUnlocked = "unlocked".equalsIgnoreCase(state) || "unlocking".equalsIgnoreCase(state);
                        ed.putString("door_state", doorUnlocked ? "unlocked" : "locked");
                        ZonedDateTime dc = parseIsoTime(st.optString("last_changed", ""), zone);
                        if (dc != null) doorChangedMs = dc.toInstant().toEpochMilli();
                        ed.putLong("door_changed_ms", doorChangedMs);
                        break;
                    }
                    case "input_boolean.front_door_keep_unlocked": doorKeepUnlocked = on; break;
                    case MOTION: ed.putBoolean("motion_on", on); break;
                    case MIRROR: ed.putBoolean("mirror_on", on); break;
                    case "binary_sensor.0x0c2a6ffffe605def_water_leak": leakOn = on; break;
                    case "input_boolean.intruder_alert_active": intruderOn = on; break;
                    case "input_boolean.washer_done": {
                        washerDone = on;
                        ZonedDateTime wc = parseIsoTime(st.optString("last_changed", ""), zone);
                        if (wc != null) washerSince = wc.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
                        break;
                    }
                    case ALL_OFF: {
                        ZonedDateTime z = parseIsoTime(attrs.optString("last_triggered", ""), zone);
                        if (z != null) allOffMs = z.toInstant().toEpochMilli();
                        break;
                    }
                    case CLUB_PLAYER: {
                        boolean playing = "playing".equalsIgnoreCase(state);
                        boolean paused = "paused".equalsIgnoreCase(state);
                        ZonedDateTime mc = parseIsoTime(st.optString("last_changed", ""), zone);
                        long ago = mc == null ? Long.MAX_VALUE : System.currentTimeMillis() - mc.toInstant().toEpochMilli();
                        // Stays up for ten minutes after a pause so you can resume from the widget.
                        mediaShow = playing || (paused && ago < 600_000L);
                        ed.putBoolean("media_show", mediaShow);
                        ed.putBoolean("media_playing", playing);
                        ed.putString("media_title", attrs.optString("media_title", ""));
                        ed.putString("media_artist", attrs.optString("media_artist", ""));
                        ed.putFloat("media_volume", (float) attrs.optDouble("volume_level", 0.0));
                        JSONArray gm = attrs.optJSONArray("group_members");
                        ed.putInt("media_group", gm == null ? 0 : Math.max(0, gm.length() - 1));
                        ed.putString("media_album", attrs.optString("media_album_name", ""));
                        ed.putInt("media_duration", attrs.optInt("media_duration", 0));
                        ed.putInt("media_position", attrs.optInt("media_position", 0));
                        ZonedDateTime pu = parseIsoTime(attrs.optString("media_position_updated_at", ""), zone);
                        ed.putLong("media_position_at", pu == null ? 0L : pu.toInstant().toEpochMilli());
                        artPath = attrs.optString("entity_picture", "");
                        break;
                    }
                    default:
                        if (eid.equals(Secrets.CALENDAR)) calObj = st;   // the configured work calendar
                        break;
                }

                if (eid.startsWith("scene.")) {
                    for (int k = 0; k < SCENES.length; k++) {
                        if (SCENES[k].equals(eid)) {
                            ZonedDateTime z = parseIsoTime(state, zone);
                            if (z != null) sceneMs[k] = z.toInstant().toEpochMilli();
                        }
                    }
                } else if (on && (eid.startsWith("light.") || eid.equals(MIRROR))) {
                    for (int k = 0; k < fxOn.length; k++) {
                        if (HomeState.FX_ID[k].equals(eid)) fxOn[k] = true;
                    }
                }
            }

            boolean anyLit = false;
            for (int k = 0; k < fxOn.length; k++) {
                ed.putBoolean("fx_on_" + k, fxOn[k]);
                anyLit |= fxOn[k];
            }

            int active = -1;
            long newest = allOffMs;
            for (int k = 0; k < SCENES.length; k++) {
                if (sceneMs[k] > newest) { newest = sceneMs[k]; active = k; }
            }
            ed.putInt("active_scene", anyLit ? active : -1);

            if (weatherObj != null) {
                JSONObject wa = weatherObj.optJSONObject("attributes");
                ed.putString("weather_cond", weatherObj.optString("state", "cloudy"));
                ed.putInt("weather_temp", wa != null ? (int) Math.round(wa.optDouble("temperature", 14.0)) : 14);
            }

            if (calObj != null) ed.putString("next_event", describeEvent(calObj)).putString("event_date", eventDate(calObj));
            describeWork(workObj, zone, ed);

            // Same rule as the remotes' Next up line: "off" only when alarms are disabled; "off today" just skips
            // today's alarms (the WFH one excepted), so tomorrow's still shows.
            if (!alarmsEnabled) {
                ed.putString("next_alarm", "Alarms off");
            } else {
                ZonedDateTime now = ZonedDateTime.now(zone), next = null;
                for (int k = 0; k < alarms.size(); k++) {
                    ZonedDateTime z = alarms.get(k);
                    if (!z.isAfter(now)) continue;
                    if (alarmsOffToday && !"sensor.work_alarm_wfh".equals(alarmIds.get(k))
                            && z.toLocalDate().equals(now.toLocalDate())) continue;
                    if (next == null || z.isBefore(next)) next = z;
                }
                ed.putString("next_alarm", next == null ? "No alarm"
                        : next.format(DateTimeFormatter.ofPattern("h:mm EEE", Locale.US)));
            }

            // House alerts, same priority as the Astrion remotes and the Hub.
            long nowMs = System.currentTimeMillis();
            boolean doorAlert = doorUnlocked && !doorKeepUnlocked && doorChangedMs > 0 && nowMs - doorChangedMs >= 600_000L;
            String id = "", sev = "", title = "", sub = "", action = "";
            if (leakOn) {
                id = "leak"; sev = "alarm"; title = "Water leak"; sub = "By the washing machine"; action = "Dismiss";
            } else if (intruderOn) {
                id = "intruder"; sev = "alarm"; title = "Intruder alert"; sub = "Radar saw motion while away"; action = "All clear";
            } else if (doorAlert) {
                id = "door"; sev = "warning"; title = "Door unlocked";
                sub = Math.max(10L, (nowMs - doorChangedMs) / 60_000L) + " min · Tap to lock"; action = "Lock it";
            } else if (washerDone) {
                id = "washer"; sev = "info"; title = "Washing's done";
                sub = washerSince.isEmpty() ? "Tap when emptied" : "Done " + washerSince + " · Tap when emptied"; action = "Emptied";
            }
            ed.putString("alert_id", id).putString("alert_severity", sev).putString("alert_title", title)
                    .putString("alert_sub", sub).putString("alert_action", action);

            fetchForecast(ctx, ed, zone);
            fetchDayEvents(ctx, ed, zone);
            ed.apply();

            syncArt(ctx, sp, mediaShow ? artPath : "");
            if (mediaShow != mediaWas) BaeoBriefWidgetProvider.schedulePeriodicSync(ctx);
            syncHouseNotification(ctx, id, sev, title, sub, action);
        } catch (Throwable e) {
            Log.w(TAG, "HA fetch offline or failed", e);
            sp.edit().putBoolean("online", false).apply();
        }
    }

    /**
     * The next workday from sensor.work_start (packages/work_alarms.yaml): "WFH · 9:00" or "Richmond · 8:30 Fri".
     * Empty when there is no workday coming, so the widget falls back to the next calendar event.
     */
    private static void describeWork(JSONObject workObj, ZoneId zone, SharedPreferences.Editor ed) {
        String line = "", place = "", when = "", date = "";
        boolean wfh = false;
        if (workObj != null) {
            ZonedDateTime z = parseIsoTime(workObj.optString("state", ""), zone);
            JSONObject a = workObj.optJSONObject("attributes");
            if (z != null && a != null && z.isAfter(ZonedDateTime.now(zone).minusHours(10))) {
                wfh = "true".equalsIgnoreCase(String.valueOf(a.opt("wfh")));
                String loc = a.optString("location", "").trim();
                String sum = a.optString("summary", "").trim();
                place = wfh ? "WFH" : (!loc.isEmpty() ? loc : sum);
                int comma = place.indexOf(',');
                if (comma > 0) place = place.substring(0, comma).trim();
                if (place.isEmpty()) place = "Work";
                boolean today = z.toLocalDate().equals(ZonedDateTime.now(zone).toLocalDate());
                date = z.toLocalDate().toString();
                ed.putString("work_time", z.format(DateTimeFormatter.ofPattern("h:mm", Locale.US)))
                        .putString("work_day", z.format(DateTimeFormatter.ofPattern("EEE", Locale.US)));
                line = place + " · " + z.format(DateTimeFormatter.ofPattern(today ? "h:mm" : "h:mm EEE", Locale.US));
                // As Bae drew it: "Mon · 8:30 - WFH", "Today · 9:00 - Richmond Town Hall".
                when = (today ? "Today" : z.format(DateTimeFormatter.ofPattern("EEE", Locale.US))) + " · "
                        + z.format(DateTimeFormatter.ofPattern("h:mm", Locale.US)) + " - " + place;
            }
        }
        ed.putString("work_line", line).putBoolean("work_wfh", wfh).putString("work_place", place).putString("work_when", when)
                .putString("work_date", date);
    }

    /** The calendar event's start date (yyyy-mm-dd), today if it's on now, "" if unknown. */
    private static String eventDate(JSONObject calObj) {
        if ("on".equalsIgnoreCase(calObj.optString("state", ""))) return LocalDate.now().toString();
        JSONObject ca = calObj.optJSONObject("attributes");
        String raw = ca != null ? ca.optString("start_time", "") : "";
        try { return LocalDateTime.parse(raw.replace(" ", "T")).toLocalDate().toString(); } catch (Throwable t) { return ""; }
    }

    /** "Today", "Tomorrow", a weekday within the week ("Thursday"), "Next week", or "Sat 12 Dec". Computed at draw time. */
    static String relDay(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            LocalDate d = LocalDate.parse(isoDate), today = LocalDate.now();
            long n = java.time.temporal.ChronoUnit.DAYS.between(today, d);
            if (n <= 0) return "Today";
            if (n == 1) return "Tomorrow";
            if (n < 7) return d.format(DateTimeFormatter.ofPattern("EEEE", Locale.US));
            if (n < 14) return "Next week";
            return d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US));
        } catch (Throwable t) { return ""; }
    }

    private static String describeEvent(JSONObject calObj) {
        JSONObject ca = calObj.optJSONObject("attributes");
        String msg = ca != null ? ca.optString("message", "") : "";
        if (msg.isEmpty()) return "Nothing coming up";
        String loc = ca.optString("location", "");
        String startRaw = ca.optString("start_time", "");
        String title = msg;
        int sep = title.indexOf(" - ");
        if (sep > 0) title = title.substring(0, sep);
        if (!loc.isEmpty() && loc.length() <= 8) title = loc;
        String prefix = "";
        if ("on".equalsIgnoreCase(calObj.optString("state", ""))) {
            prefix = "Now ";
        } else if (!startRaw.isEmpty()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(startRaw.replace(" ", "T"));
                boolean today = ldt.toLocalDate().equals(LocalDate.now());
                prefix = ldt.format(DateTimeFormatter.ofPattern(today ? "h:mm" : "EEE h:mm", Locale.US)) + " ";
            } catch (Throwable ignored) {}
        }
        return (prefix + title).trim();
    }

    /** The work calendar's events for the next few days: {"2026-11-23": "8:30 WFH", ...} (first event per day, "+1" if more). */
    private static void fetchDayEvents(Context ctx, SharedPreferences.Editor ed, ZoneId zone) {
        try {
            LocalDate today = LocalDate.now(zone);
            String start = today.atStartOfDay(zone).toOffsetDateTime().toString();
            String end = today.plusDays(5).atStartOfDay(zone).toOffsetDateTime().toString();
            String path = "/api/calendars/" + Secrets.CALENDAR + "?start=" + java.net.URLEncoder.encode(start, "UTF-8")
                    + "&end=" + java.net.URLEncoder.encode(end, "UTF-8");
            JSONArray evs = new JSONArray(new String(httpGetBytes(ctx, path), StandardCharsets.UTF_8));
            JSONObject byDay = new JSONObject(), count = new JSONObject();
            for (int i = 0; i < evs.length(); i++) {
                JSONObject e = evs.getJSONObject(i);
                JSONObject st = e.optJSONObject("start");
                if (st == null) continue;
                String text, day;
                if (st.has("dateTime")) {
                    ZonedDateTime z = parseIsoTime(st.optString("dateTime"), zone);
                    if (z == null) continue;
                    day = z.toLocalDate().toString();
                    text = z.format(DateTimeFormatter.ofPattern("h:mm", Locale.US)) + " " + shortTitle(e);
                } else {
                    day = st.optString("date");
                    text = shortTitle(e);
                }
                count.put(day, count.optInt(day, 0) + 1);
                if (!byDay.has(day)) byDay.put(day, text);
            }
            java.util.Iterator<String> it = byDay.keys();
            JSONObject out = new JSONObject();
            while (it.hasNext()) {
                String d = it.next();
                int n = count.optInt(d, 1);
                out.put(d, byDay.getString(d) + (n > 1 ? "  +" + (n - 1) : ""));
            }
            ed.putString("day_events", out.toString());
        } catch (Throwable t) {
            Log.w(TAG, "calendar events failed", t);
        }
    }

    /** "WFH" for work-from-home, else the event title without its " - extra" or "(...)" tail. */
    private static String shortTitle(JSONObject e) {
        String loc = e.optString("location", "").trim(), sum = e.optString("summary", "").trim();
        if ("WFH".equalsIgnoreCase(loc) || sum.toLowerCase(Locale.US).contains("work from home")) return "WFH";
        if (!loc.isEmpty() && loc.length() <= 8 && !loc.contains(" ")) return loc;   // short codes like CTH
        int k = sum.indexOf(" - ");
        if (k > 0) sum = sum.substring(0, k);
        k = sum.indexOf(" (");
        if (k > 0) sum = sum.substring(0, k);
        return sum.isEmpty() ? loc : sum;
    }

    private static void fetchForecast(Context ctx, SharedPreferences.Editor ed, ZoneId zone) {
        try {
            JSONObject req = new JSONObject();
            req.put("entity_id", "weather.forecast_home");
            req.put("type", "daily");
            JSONObject root = new JSONObject(httpPost(ctx, "/api/services/weather/get_forecasts?return_response=true", req.toString()));
            JSONObject sr = root.optJSONObject("service_response");
            JSONObject wf = sr != null ? sr.optJSONObject("weather.forecast_home") : null;
            JSONArray fc = wf != null ? wf.optJSONArray("forecast") : null;
            if (fc == null || fc.length() == 0) return;
            JSONObject today = fc.getJSONObject(0);
            ed.putInt("today_high", (int) Math.round(today.optDouble("temperature", 16)));
            ed.putInt("today_low", (int) Math.round(today.optDouble("templow", 10)));
            JSONArray compact = new JSONArray();
            for (int i = 0; i < Math.min(6, fc.length()); i++) {
                JSONObject f = fc.getJSONObject(i);
                ZonedDateTime dt = parseIsoTime(f.optString("datetime", ""), zone);
                JSONObject item = new JSONObject();
                item.put("date", dt != null ? dt.toLocalDate().toString() : "");
                item.put("day", dt != null ? dt.format(DateTimeFormatter.ofPattern("EEE", Locale.US)) : "---");
                item.put("cond", f.optString("condition", "cloudy"));
                item.put("hi", (int) Math.round(f.optDouble("temperature", 16)));
                item.put("lo", (int) Math.round(f.optDouble("templow", 10)));
                compact.put(item);
            }
            ed.putString("forecast_json", compact.toString());
        } catch (Throwable t) {
            Log.w(TAG, "Forecast fetch warning", t);
        }
    }

    private static void syncArt(Context ctx, SharedPreferences sp, String path) {
        File f = new File(ctx.getFilesDir(), ART_FILE);
        if (path == null || path.isEmpty()) {
            if (f.exists()) f.delete();
            sp.edit().putString("media_art_url", "").apply();
            return;
        }
        if (path.equals(sp.getString("media_art_url", "")) && f.exists()) return;
        try {
            if (!path.startsWith("/")) throw new IllegalArgumentException("art is not served by HA");
            byte[] bytes = httpGetBytes(ctx, path);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(bytes);
            }
            sp.edit().putString("media_art_url", path).apply();
        } catch (Throwable t) {
            Log.w(TAG, "Art fetch failed", t);
            if (f.exists()) f.delete();
            sp.edit().putString("media_art_url", "").apply();
        }
    }

    private static android.graphics.Bitmap planBmp;

    private static synchronized android.graphics.Bitmap plan(Context ctx) {
        if (planBmp == null) {
            try (InputStream is = ctx.getAssets().open("floorplan.png")) {
                planBmp = BitmapFactory.decodeStream(is);
            } catch (Throwable ignored) {}
        }
        return planBmp;
    }

    // ------------------------------------------------------------------ state for the renderer
    public static HomeState state(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        HomeState s = new HomeState();
        LocalDate today = LocalDate.now();
        s.dayName = today.format(DateTimeFormatter.ofPattern("EEE", Locale.US));
        s.dateRest = today.format(DateTimeFormatter.ofPattern("d MMM", Locale.US));

        s.temp = sp.getInt("weather_temp", 14);
        String cond = sp.getString("weather_cond", "cloudy");
        boolean wxDown = cond == null || cond.isEmpty() || "unavailable".equals(cond) || "unknown".equals(cond);
        s.cond = wxDown ? "cloudy" : cond;
        s.condLabel = wxDown ? "" : prettyCondition(cond);
        s.low = sp.getInt("today_low", 10);
        s.high = sp.getInt("today_high", 16);

        try {
            JSONArray fc = new JSONArray(sp.getString("forecast_json", "[]"));
            List<JSONObject> days = new ArrayList<>();
            for (int i = 0; i < fc.length(); i++) {
                JSONObject o = fc.getJSONObject(i);
                String d = o.optString("date", "");
                // Today is already the big number on the left, so the strip starts tomorrow.
                if (!d.isEmpty() && d.compareTo(today.toString()) <= 0) continue;
                days.add(o);
            }
            int n = Math.min(4, days.size());
            s.fcDay = new String[n];
            s.fcCond = new String[n];
            s.fcHi = new int[n];
            s.fcLo = new int[n];
            s.fcEvent = new String[n];
            s.fcDate = new String[n];
            JSONObject dayEv = new JSONObject(sp.getString("day_events", "{}"));
            for (int i = 0; i < n; i++) {
                s.fcDay[i] = days.get(i).optString("day", "");
                s.fcCond[i] = days.get(i).optString("cond", "cloudy");
                s.fcHi[i] = days.get(i).optInt("hi", 0);
                s.fcLo[i] = days.get(i).optInt("lo", 0);
                s.fcEvent[i] = dayEv.optString(days.get(i).optString("date", ""), "");
                try { s.fcDate[i] = LocalDate.parse(days.get(i).optString("date", "")).format(DateTimeFormatter.ofPattern("d MMM", Locale.US)); }
                catch (Throwable ignored) { s.fcDate[i] = ""; }
            }
        } catch (Throwable ignored) {}

        s.nextEvent = sp.getString("next_event", "Nothing coming up");
        s.workLine = sp.getString("work_line", "");
        s.workWfh = sp.getBoolean("work_wfh", false);
        s.workPlace = sp.getString("work_place", "");
        s.workWhen = sp.getString("work_when", "");
        s.workTime = sp.getString("work_time", "");
        s.workDay = sp.getString("work_day", "");
        s.nextRel = relDay(s.workPlace.isEmpty() ? sp.getString("event_date", "") : sp.getString("work_date", ""));
        s.insideTemp = sp.getString("inside_temp", "");
        s.insideHum = sp.getString("inside_hum", "");
        s.airconLine = sp.getString("aircon_line", "");
        s.airconOn = sp.getBoolean("aircon_on", false);
        s.nextAlarm = sp.getString("next_alarm", "No alarm");
        String al = s.nextAlarm.toLowerCase(Locale.US);
        s.alarmActive = !al.contains("off") && !al.startsWith("no ");
        s.motionOn = sp.getBoolean("motion_on", true);
        s.mirrorOn = sp.getBoolean("mirror_on", false);
        if ("unlocked".equals(sp.getString("door_state", "locked"))) s.door = HomeState.DOOR_UNLOCKED;
        else if (System.currentTimeMillis() < sp.getLong("door_arm_until", 0L)) s.door = HomeState.DOOR_ARMED;
        else s.door = HomeState.DOOR_LOCKED;
        s.doorSinceMs = sp.getLong("door_changed_ms", 0L);

        s.alertId = sp.getString("alert_id", "");
        s.alertSeverity = sp.getString("alert_severity", "");
        s.alertTitle = sp.getString("alert_title", "");
        s.alertSub = sp.getString("alert_sub", "");
        s.activeScene = sp.getInt("active_scene", -1);

        s.mediaShow = sp.getBoolean("media_show", false);
        s.mediaPlaying = sp.getBoolean("media_playing", false);
        s.mediaTitle = sp.getString("media_title", "");
        s.mediaArtist = sp.getString("media_artist", "");
        s.volume = sp.getFloat("media_volume", 0f);
        s.mediaGroup = sp.getInt("media_group", 0);
        s.mediaAlbum = sp.getString("media_album", "");
        s.mediaDuration = sp.getInt("media_duration", 0);
        int pos = sp.getInt("media_position", 0);
        long at = sp.getLong("media_position_at", 0L);
        if (s.mediaPlaying && at > 0) pos += (int) ((System.currentTimeMillis() - at) / 1000L);
        s.mediaPosition = s.mediaDuration > 0 ? Math.min(pos, s.mediaDuration) : pos;
        if (s.mediaShow && !sp.getString("media_art_url", "").isEmpty()) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                s.art = BitmapFactory.decodeFile(new File(ctx.getFilesDir(), ART_FILE).getPath(), o);
            } catch (Throwable ignored) {}
        }

        try {
            org.json.JSONArray fa = new org.json.JSONArray(sp.getString(BaeoNotifyListener.KEY, "[]"));
            int n = fa.length();
            s.feedTime = new String[n]; s.feedSrc = new String[n]; s.feedTitle = new String[n]; s.feedMsg = new String[n];
            for (int i = 0; i < n; i++) {
                JSONObject o = fa.getJSONObject(i);
                s.feedTime[i] = o.optString("t"); s.feedSrc[i] = o.optString("src");
                String key = o.optString("key", "");
                if (!key.startsWith("local|")) {   // re-label stored HA lines with the current rules
                    int bar = key.indexOf('|');
                    s.feedSrc[i] = BaeoNotifyListener.source(bar > 0 ? key.substring(0, bar) : "", o.optString("title"), o.optString("msg"));
                }
                s.feedTitle[i] = o.optString("title"); s.feedMsg[i] = o.optString("msg");
            }
        } catch (Throwable ignored) {}

        for (int k = 0; k < s.lightOn.length; k++) s.lightOn[k] = sp.getBoolean("fx_on_" + k, false);
        s.plan = plan(ctx);

        s.online = sp.getBoolean("online", true);
        long last = sp.getLong("last_sync_ms", 0L);
        s.syncMs = last;
        s.haMs = sp.getInt("ha_ms", -1);
        s.haEntities = sp.getInt("ha_entities", 0);
        s.haCloud = sp.getBoolean("ha_cloud", false);
        s.lastSync = last == 0L ? "" : Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("h:mm", Locale.US));
        return s;
    }

    // ------------------------------------------------------------------ notification
    private static void syncHouseNotification(Context ctx, String alertId, String severity,
                                              String title, String sub, String actionLabel) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            SharedPreferences sp = prefs(ctx);
            if (alertId == null || alertId.isEmpty()) {
                nm.cancel(4201);
                sp.edit().putString("last_notified_alert", "").apply();
                return;
            }
            if (alertId.equals(sp.getString("last_notified_alert", ""))) return;

            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Bæo House Alerts",
                    "alarm".equals(severity) ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Live house alerts from Home Assistant (washer, door, leak, security)");
            nm.createNotificationChannel(ch);

            PendingIntent resolvePi = BaeoActionReceiver.pi(ctx, BaeoActionReceiver.ACTION_RESOLVE_ALERT, null, 91);
            PendingIntent openPi = PendingIntent.getActivity(ctx, 92, new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            int accent = "alarm".equals(severity) ? BaeoHomeRender.BAD
                    : ("warning".equals(severity) ? BaeoHomeRender.WARN : BaeoHomeRender.ACCENT);

            Notification.Builder nb = new Notification.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setContentText(sub)
                    .setColor(accent)
                    .setContentIntent(openPi)
                    .setAutoCancel(true);
            if (actionLabel != null && !actionLabel.isEmpty()) {
                nb.addAction(new Notification.Action.Builder(null, actionLabel, resolvePi).build());
            }
            nm.notify(4201, nb.build());
            sp.edit().putString("last_notified_alert", alertId).apply();
        } catch (Throwable ignored) {}
    }

    private static ZonedDateTime parseIsoTime(String raw, ZoneId zone) {
        if (raw == null || raw.isEmpty() || "unavailable".equals(raw) || "unknown".equals(raw)) return null;
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(zone);
        } catch (Throwable t1) {
            try {
                return Instant.parse(raw).atZone(zone);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    public static String prettyCondition(String cond) {
        if (cond == null || cond.isEmpty()) return "Cloudy";
        switch (cond.toLowerCase(Locale.US)) {
            case "partlycloudy": return "Partly cloudy";
            case "clear-night": return "Clear";
            case "sunny": return "Sunny";
            case "rainy": return "Rainy";
            case "pouring": return "Heavy rain";
            case "lightning":
            case "lightning-rainy": return "Stormy";
            case "windy":
            case "windy-variant": return "Windy";
            case "cloudy": return "Cloudy";
            case "fog": return "Foggy";
            case "snowy-rainy": return "Sleet";
            default: return cond.substring(0, 1).toUpperCase(Locale.US) + cond.substring(1);
        }
    }
}
