package com.baeo.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Bæo Home (compact): floorplan with a button on every light on the left; Bæocom mark, next alarm, next event,
 * forecast, disco ball and door on the right; a thin Club media strip only while playing. Draws from a HomeState only.
 *
 * geo() decides where everything sits; BaeoBriefWidgetProvider sizes the tap zones from the same numbers.
 */
public final class BaeoHomeRender {
    // Bæo Sea glass, Night (graphite). Phone home screens stay Night.
    public static final int DEEP = 0xFF191D20;
    public static final int BG = 0xFF1E2326;
    public static final int PANEL = 0xFF293034;
    public static final int PANEL_2 = 0xFF333B40;
    public static final int RAISED = 0xFF3A4348;
    public static final int TRACK = 0xFF252B2E;
    public static final int LINE = 0xFF3F494E;
    public static final int INK = 0xFFEDF2EF;
    public static final int MUTE = 0xFFA7B4B1;
    public static final int SUBTLE = 0xFF7E8C89;
    public static final int ACCENT = 0xFF8CBDB5;
    public static final int ON_ACCENT = 0xFF12201E;
    public static final int SELECTED = 0xFF4F726D;
    public static final int HARBOUR = 0xFF8FB3C2;
    public static final int ROSE = 0xFFEAC3BD;
    public static final int BLUSH = 0xFFE6BCB6;
    public static final int APPLE = 0xFFD3D69A;
    public static final int WARN = 0xFFF5D76E;
    public static final int BAD = 0xFFFF9AA5;
    public static final int LIT = 0xFFF5D76E;       // lights-on yellow (Night)

    /**
     * Where everything sits in a w x h bitmap. The provider places every tap zone from these rects,
     * so the picture and the touch targets cannot drift apart.
     */
    public static final class Geo {
        public RectF feed, feedPrompt;
        public float u;   // column text scale, set by geo()
        public RectF plan, day, date, word, alarm, event, forecast, disco, lights, door, climate;
        public boolean tall;
        public RectF media, mArt, mInfo, mPrev, mPlay, mNext, mDown, mUp;
        public float badgeR;
        public float fxX(int i) { return plan.left + plan.width() * HomeState.FX_POS[i][0] / 100f; }
        public float fxY(int i) { return plan.top + plan.height() * HomeState.FX_POS[i][1] / 100f; }
    }

    /** Design height of the main area (floorplan + right column) at s = 1. Everything in the column scales with it. */
    static final float MAIN_H = 510f;
    /** Forecast block height at u = 1: day label, glyph, high/low. */
    static final float FC_H = 128f;
    /** Day rows are this many list rows tall (name + weather, then the event under it). */
    static final float DAY_ROW = 1.55f;
    /** Top rows (next on / next alarm) are a touch tighter than a list row; the "This Week" title is shorter still. */
    static final float TOP_ROW = 0.88f, WEEK_TITLE = 0.75f;
    /** Corner radius of the floorplan, shared by the player card. */
    static float PLAN_R = 26f;
    /** Floorplan outline: 0 rounded rectangle (PLAN_R), 1 app-icon arch. */
    public static int PLAN_SHAPE = 0;
    public static void setPlan(float r, int shape) { PLAN_R = r; PLAN_SHAPE = shape; }
    /** Top-corner radius of the feed's title bar and the player card (their bottoms are square). */
    static final float BAR_R = 12f;

    public static Geo geo(boolean media, int w, int h) {
        return geoWide(media, false, w, h);
    }

    /** status: the small status line (motion paused / another calendar event) is showing, so it gets a row. */
    public static Geo geo(boolean media, boolean status, int w, int h) {
        return geoWide(media, status, w, h);
    }

    /** True when the status row has something to say. */
    public static boolean hasStatus(HomeState st) {
        return !st.motionOn || (st.workPlace != null && !st.workPlace.isEmpty() && !eventIsWork(st)
                && st.nextEvent != null && !st.nextEvent.isEmpty() && !st.nextEvent.startsWith("Nothing"));
    }

    /** Player strip, full width. The art (or note) square opens Bæo Sets; the controls sit under the right column. */
    static void placeMedia(Geo g, int w, float s, float pad, float mediaH, float x0, float above) {
        float mTop = above + 32f * s;
        g.media = new RectF(pad, mTop, w - pad, mTop + mediaH);
        if (BUBBLE) {
            // Bubble-card layout: round art inset on the left, big round play button at the right end.
            float ins = 10f * s, side = mediaH - 2 * ins, cy = g.media.centerY();
            g.mArt = new RectF(g.media.left + ins, cy - side / 2f, g.media.left + ins + side, cy + side / 2f);
            float pd = mediaH - 2 * ins;
            g.mPlay = new RectF(g.media.right - ins - pd, cy - pd / 2f, g.media.right - ins, cy + pd / 2f);
            float bw = 76f * s;
            g.mNext = new RectF(g.mPlay.left - 8f * s - bw, g.media.top, g.mPlay.left - 8f * s, g.media.bottom);
            g.mPrev = new RectF(g.mNext.left - bw, g.media.top, g.mNext.left, g.media.bottom);
            g.mDown = g.mUp = new RectF(g.mPrev.left - 118f * s, g.media.top, g.mPrev.left, g.media.bottom);
            g.mInfo = new RectF(g.mArt.right, g.media.top, g.mUp.left, g.media.bottom);
            return;
        }
        // The arch's round top would clip anything in the corners, so the art sits further in.
        float inset = MEDIA_STYLE == 4 ? 28f * s : 12f * s;
        if (PILLS_IN_MEDIA) {
            // Narrow card under the floorplan; the pills take the right column's width in the same row.
            g.media = new RectF(pad, mTop, g.plan.right, mTop + mediaH);
            float side = mediaH - 24f * s;
            g.mArt = new RectF(g.media.left + 12f * s, g.media.centerY() - side / 2f, g.media.left + 12f * s + side, g.media.centerY() + side / 2f);
            float bw = 72f * s, cx0 = g.media.right - 14f * s - bw * 3f;
            g.mInfo = new RectF(g.mArt.right, g.media.top, cx0, g.media.bottom);
            g.mPrev = new RectF(cx0, g.media.top, cx0 + bw, g.media.bottom);
            g.mPlay = new RectF(cx0 + bw, g.media.top, cx0 + bw * 2, g.media.bottom);
            g.mNext = new RectF(cx0 + bw * 2, g.media.top, cx0 + bw * 3, g.media.bottom);
            g.mDown = g.mUp = new RectF(g.mNext.right, g.media.top, g.mNext.right, g.media.bottom);   // no readout
            return;
        }
        // Material style: the art fills the card's left end, full height, edge to edge.
        g.mArt = new RectF(g.media.left, g.media.top, g.media.left + mediaH, g.media.bottom);
        // prev / play / next, then a volume + time readout (no volume buttons).
        float bw = (g.media.right - 10f * s - x0) / 4.5f;
        g.mInfo = new RectF(g.mArt.right, g.media.top, x0, g.media.bottom);
        g.mPrev = new RectF(x0, g.media.top, x0 + bw, g.media.bottom);
        g.mPlay = new RectF(x0 + bw, g.media.top, x0 + bw * 2, g.media.bottom);
        g.mNext = new RectF(x0 + bw * 2, g.media.top, x0 + bw * 3, g.media.bottom);
        g.mDown = g.mUp = new RectF(x0 + bw * 3, g.media.top, g.media.right - inset, g.media.bottom);
    }

    /** Notify feed: prompt line, FEED_LINES notifications, cursor line, at 40 px a line (s = 1). */
    static final int FEED_LINES = 5;
    static final float FEED_LINE = 43f, FEED_H = FEED_LINE * (FEED_LINES + 2) + 14f;
    /** true: player above the feed; false: feed above the player. */
    public static boolean MEDIA_FIRST = true;
    /** Player card shape: 0 round top / square bottom, 1 pill, 2 floorplan corners, 3 round left / soft right, 4 app-icon arch. */
    public static int MEDIA_STYLE = 5;
    /** Preview option: forecast at the top of the right column. */
    public static boolean WEATHER_TOP = false;
    /** Option: disco / lights / door pills move into the player's row (only while the player shows). */
    public static boolean PILLS_IN_MEDIA = false;
    /** Option: the icon buttons sit at the top of the right column instead of the bottom. */
    public static boolean ICONS_TOP = false;

    static Geo geoWide(boolean media, boolean status, int w, int h) {
        float s = w / 980f;
        float pad = 6f * s;
        Geo g = new Geo();
        float mediaH = media ? 116f * s : 0f;
        // The feed only appears once the widget is tall enough (one row taller than the 5x3 default):
        // the floorplan keeps at least its 3-row size.
        float feedH = FEED_H * s, feedGap = 26f * s;
        boolean feed = h - 2 * pad - (media ? mediaH + 32f * s : 0f) - feedH - feedGap >= 540f * s;
        float mainTop = pad, mainBot = h - pad - (media ? mediaH + 32f * s : 0f) - (feed ? feedH + feedGap : 0f);
        float mainH = Math.max(1f, mainBot - mainTop);

        // Floorplan on the left: as tall as the main area, 1.04:1 up to 1.12:1, never wider than 56 %.
        float pw = Math.min(mainH * 1.12f, w * 0.60f);
        float ph = Math.min(mainH, pw / 1.04f);
        float ptop = mainTop;
        g.plan = new RectF(pad, ptop, pad + pw, ptop + ph);
        g.badgeR = pw * 0.041f;

        // Right column, aligned to the plan's top and bottom, split by fixed fractions.
        float x0 = g.plan.right + 34f * s, x1 = w - pad;
        float colTop = g.plan.top, colH = g.plan.height();
        // One list, every row the same height: next work day, alarm, (status), gap, three forecast days,
        // gap, then the pills on the plan's bottom edge.
        float u = s * Math.max(0.62f, Math.min(1.25f, ph / s / MAIN_H));
        g.u = u;
        float pillH = 84f * u;   // bigger buttons; This Week's day cards close up to make room
        // The launcher rounds the widget's corners: keep the top rows clear of the top-right curve.
        float cornerIn = 6f * s;
        float nRows = (status ? 3 : 2) * TOP_ROW + WEEK_TITLE + 3f * DAY_ROW;
        // Rows grow to fill the column (up to 54u), leaving about one row's gap between the groups.
        float rowH = Math.max(FC_H / 3f * u, Math.min(54f * u, (colH - pillH - cornerIn - 20f * u) / (nRows + 1.2f)));
        float fcH = rowH * (WEEK_TITLE + 3f * DAY_ROW);   // "This Week" title + three two-line day cards
        float topH = rowH * TOP_ROW;
        RectF[] rows = new RectF[7];
        float y = colTop + cornerIn;
        RectF topIcons = null;
        if (ICONS_TOP) { topIcons = new RectF(x0, colTop, x1, colTop + pillH); y = topIcons.bottom + 10f * u; }
        rows[0] = new RectF(x0, y, x1, y);
        rows[1] = new RectF(x0, y, x1, y);
        float xr = x1 - 14f * s;   // and clear of the right side of that curve
        rows[2] = new RectF(x0, y, xr, y += topH);
        rows[3] = new RectF(x0, y, xr, y += topH);
        rows[4] = new RectF(x0, y, x1, y += status ? topH : 0f);
        // Justified: top rows at the top, pills on the plan's bottom, "This Week" stretched to fill between.
        float sectGap = 16f * u;
        boolean pillsBelow = (PILLS_IN_MEDIA && media) || ICONS_TOP;
        rows[5] = new RectF(x0, y + sectGap, x1, pillsBelow ? g.plan.bottom : g.plan.bottom - pillH - sectGap);
        rows[6] = new RectF(x0, g.plan.bottom - pillH, x1, g.plan.bottom);
        if (ICONS_TOP) rows[6] = topIcons;
        else if (pillsBelow) {   // same row as the (narrow) player, centred on it
            float mc = g.plan.bottom + 32f * s + mediaH / 2f;
            rows[6] = new RectF(x0, mc - pillH / 2f, x1, mc + pillH / 2f);
        }
        g.day = rows[0];      // today's date + short rule
        g.date = rows[1];     // "Upcoming Event" title
        g.word = rows[2];     // the work / event line (kept the field name; tap = HA app)
        g.alarm = rows[3];
        g.event = rows[4];
        g.forecast = rows[5];
        RectF chips = rows[6];
        // Disco lines up with the first forecast third; door takes the rest.
        // Disco | lights-on count (tap = all off) | door.
        float gap = 12f * s, discoW = (chips.width() - 2 * gap) * 0.24f, lightsW = (chips.width() - 2 * gap) * 0.26f;
        g.disco = new RectF(chips.left, chips.top, chips.left + discoW, chips.bottom);
        g.lights = new RectF(g.disco.right + gap, chips.top, g.disco.right + gap + lightsW, chips.bottom);
        g.door = new RectF(g.lights.right + gap, chips.top, chips.right, chips.bottom);
        if (ICON_BUTTONS) {   // two bubble buttons: lights just wide enough for its badge + a count, door takes the rest
            float bph = Math.min(chips.height(), 82f * u), bgap = 12f * u;
            g.lights = new RectF(chips.left, chips.bottom - bph, chips.left + bph + 34f * u, chips.bottom);
            g.door = new RectF(g.lights.right + bgap, chips.bottom - bph, chips.right, chips.bottom);
        }

        // Plan, then the player and the feed in either order.
        if (media && MEDIA_FIRST) placeMedia(g, w, s, pad, mediaH, x0, g.plan.bottom);
        if (feed) {
            float fTop = (media && MEDIA_FIRST ? g.media.bottom : g.plan.bottom) + feedGap;
            g.feed = new RectF(pad, fTop, w - pad, fTop + feedH);
            float py = g.feed.top + 33f * s + FEED_LINE * s * (FEED_LINES + 1);   // the "$" line's baseline
            g.feedPrompt = new RectF(pad, py - FEED_LINE * s * 0.8f, w - pad, g.feed.bottom);
        }
        if (media && !MEDIA_FIRST) placeMedia(g, w, s, pad, mediaH, x0, feed ? g.feed.bottom - 6f * s : g.plan.bottom);
        return g;
    }

    public static final class Fonts {
        public Typeface m500, m600, m700, syne400, syne700, mono, monoBold, icons;
    }

    private BaeoHomeRender() {}

    static Paint paint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(color);
        return p;
    }

    static Paint stroke(int color, float width) {
        Paint p = paint(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    static Paint text(int color, Typeface tf, float size) {
        Paint p = paint(color);
        p.setTypeface(tf);
        p.setTextSize(size);
        return p;
    }

    /** Text on the bare wallpaper gets a soft shadow, like At a Glance, so it holds up on any wallpaper. */
    static Paint shaded(int color, Typeface tf, float size) {
        Paint p = text(color, tf, size);
        p.setShadowLayer(size * 0.18f, 0f, size * 0.05f, 0x66000000);
        return p;
    }

    static int alpha(int color, float a) {
        return (Math.round(255 * a) << 24) | (color & 0x00FFFFFF);
    }

    static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Rounded rect with its own radius per corner (tl, tr, br, bl). Cubic corners, works on Android and in the preview shim. */
    static Path corners(RectF r, float tl, float tr, float br, float bl) {
        float k = 0.5523f;
        Path p = new Path();
        p.moveTo(r.left + tl, r.top);
        p.lineTo(r.right - tr, r.top);
        p.cubicTo(r.right - tr + tr * k, r.top, r.right, r.top + tr - tr * k, r.right, r.top + tr);
        p.lineTo(r.right, r.bottom - br);
        p.cubicTo(r.right, r.bottom - br + br * k, r.right - br + br * k, r.bottom, r.right - br, r.bottom);
        p.lineTo(r.left + bl, r.bottom);
        p.cubicTo(r.left + bl - bl * k, r.bottom, r.left, r.bottom - bl + bl * k, r.left, r.bottom - bl);
        p.lineTo(r.left, r.top + tl);
        p.cubicTo(r.left, r.top + tl - tl * k, r.left + tl - tl * k, r.top, r.left + tl, r.top);
        p.close();
        return p;
    }

    /** Media card shape: 0 = pill, 1 = app-icon arch (round top, soft square bottom), 2 = the arch upside down. */
    public static int MEDIA_SHAPE = 1;

    static Path shaped(RectF r, int shape, float big, float small) {
        if (shape == 1) return corners(r, big, big, small, small);
        if (shape == 2) return corners(r, small, small, big, big);
        return corners(r, big, big, big, big);
    }

    static String fit(String s, Paint p, float maxW) {
        if (s == null) return "";
        if (p.measureText(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && p.measureText(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    /** No card: the widget sits straight on the wallpaper like At a Glance. Pills use Pixel-style tonal fills. */
    static final int TONAL = 0xFF39434A;
    static final float TONAL_ALPHA = 0.82f;

    public static Bitmap render(HomeState st, Fonts f, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = w / 980f;
        Geo g = geo(st.mediaShow, hasStatus(st), w, h);
        float u = s * Math.max(0.62f, Math.min(1.25f, g.plan.height() / s / MAIN_H));
        float iconX = iconColumn(st, f, g.word, u);
        drawWork(c, st, f, g.word, u, iconX);
        drawAlarm(c, st, f, g.alarm, u, iconX);
        if (g.event.height() > 0) drawEvent(c, st, f, g.event, u, iconX);
        // A house alert (washing done, door left open, leak...) takes the forecast's place until it's resolved.
        if (hasAlert(st)) drawAlert(c, st, f, g.forecast, u);
        else drawForecast(c, st, f, g.forecast, u, iconX);
        drawChips(c, st, f, g, u);
        if (g.feed != null) drawFeed(c, st, f, g.feed, s);
        if (st.mediaShow) drawMedia(c, st, f, g, s);
        drawPlan(c, st, f, g, s);
        return bmp;
    }

    // ---------------------------------------------------------------- right column
    /** Today's date in accent, with a short semi-transparent rule under it (not full width). */
    private static void drawDay(Canvas c, HomeState st, Fonts f, RectF r, float u) {
        Paint dp = shaded(ACCENT, f.m600, 23f * u);
        String d = st.dayName + " " + st.dateRest;
        c.drawText(fit(d, dp, r.width()), r.left, r.top + r.height() * 0.50f, dp);
        float ly = r.bottom - r.height() * 0.14f;
        float lw = Math.min(r.width(), Math.max(dp.measureText(d) + 24f * u, r.width() * 0.42f));
        c.drawLine(r.left, ly, r.left + lw, ly, stroke(alpha(INK, 0.25f), 1.8f * u));
    }

    // ---- list rows: label on the left, icon in a shared column, value right-aligned ----
    static Paint rowLabel(Fonts f, float u) { return shaded(MUTE, f.m500, 23f * u); }
    static Paint rowValue(Fonts f, float u, int col) { return shaded(col, f.m600, 23f * u); }

    static boolean hasWork(HomeState st) {
        return st.workPlace != null && !st.workPlace.isEmpty() && st.workTime != null && !st.workTime.isEmpty();
    }
    static String workValue(HomeState st) {
        if (!hasWork(st)) return fitEventHead(st);
        return st.workTime + " " + (st.workWfh ? "WFH" : st.workPlace);
    }
    static String alarmValue(HomeState st) {
        String a = st.nextAlarm == null ? "" : st.nextAlarm.trim();
        if (a.isEmpty()) return "None";
        String[] p = a.split(" ");   // "7:20 Mon" -> "Mon 7:20", matching "Mon 8:30 WFH"
        return p.length == 2 && Character.isDigit(p[0].charAt(0)) ? p[1] + " " + p[0] : a;
    }

    /** x of the shared icon column: just left of the widest value, but never into the labels. */
    static float iconColumn(HomeState st, Fonts f, RectF r, float u) {
        Paint v = rowValue(f, u, INK);
        float w = Math.max(v.measureText(workValue(st)), v.measureText(alarmValue(st)));
        // Never into the labels: the widest of "Next alarm", "Tomorrow" and the forecast day names.
        Paint lp = rowLabel(f, u);
        float lw = Math.max(lp.measureText("Next alarm"), lp.measureText(st.nextRel == null ? "" : st.nextRel));
        return Math.max(r.left + lw + 24f * u, r.right - w - 26f * u);
    }

    private static void rowText(Canvas c, RectF r, float u, String label, Paint lp, String value, Paint vp, float iconX) {
        float base = r.centerY() + 8f * u;
        c.drawText(fit(label, lp, iconX - 16f * u - r.left), r.left, base, lp);
        float room = r.right - iconX - 20f * u;
        while (vp.measureText(value) > room && vp.getTextSize() > 19.5f * u) vp.setTextSize(vp.getTextSize() - 0.5f * u);
        String v = fit(value, vp, room);
        c.drawText(v, r.right - vp.measureText(v), base, vp);
    }

    /** "Tomorrow   [briefcase]   8:30 WFH" (or the next calendar event when there's no work day). */
    private static void drawWork(Canvas c, HomeState st, Fonts f, RectF r, float u, float iconX) {
        boolean work = hasWork(st);
        String label = st.nextRel == null || st.nextRel.isEmpty() ? "Next" : st.nextRel;
        if (work) drawBriefcaseGlyph(c, iconX, r.centerY() - 1f * u, 12f * u, ACCENT);
        else drawCalendarGlyph(c, iconX, r.centerY(), 11f * u, ACCENT);
        rowText(c, r, u, label, rowLabel(f, u), workValue(st), rowValue(f, u, INK), iconX);
    }

    /** True when the calendar's next event is just the work day already shown under Next event. */
    static boolean eventIsWork(HomeState st) {
        if (st.nextEvent == null || st.workPlace == null || st.workPlace.isEmpty()) return false;
        String e = st.nextEvent.toLowerCase(Locale.US), p = st.workPlace.toLowerCase(Locale.US);
        if (st.workWfh && (e.contains("wfh") || e.contains("home"))) return true;
        String first = p.split("[ ,]")[0];
        return e.contains(p) || (first.length() >= 4 && e.contains(first));
    }

    private static String fitEventHead(HomeState st) {
        return st.nextEvent == null || st.nextEvent.isEmpty() ? "Nothing on" : st.nextEvent;
    }

    /** "Alarm   [clock]   7:20 Mon" with the time in rose. */
    private static void drawAlarm(Canvas c, HomeState st, Fonts f, RectF r, float u, float iconX) {
        String a = st.nextAlarm == null ? "" : st.nextAlarm.trim();
        boolean timed = st.alarmActive && !a.isEmpty() && Character.isDigit(a.charAt(0));
        int col = timed ? ROSE : SUBTLE;
        drawAlarmGlyph(c, iconX, r.centerY() - 1f * u, 12f * u, col);
        rowText(c, r, u, "Next alarm", rowLabel(f, u), alarmValue(st), rowValue(f, u, col), iconX);
    }

    public static boolean hasAlert(HomeState st) { return st.alertId != null && !st.alertId.isEmpty(); }

    /** Alert card, same colours as the remotes and the Hub: alarm = red, warning = yellow, info = teal. Tap resolves. */
    private static void drawAlert(Canvas c, HomeState st, Fonts f, RectF r, float u) {
        int col = "alarm".equals(st.alertSeverity) ? BAD : ("warning".equals(st.alertSeverity) ? WARN : ACCENT);
        float cy = r.centerY();
        float ch = Math.min(r.height() * 0.40f, 38f * u);
        RectF pill = new RectF(r.left, cy - ch, r.right, cy + ch);
        c.drawRoundRect(pill, ch, ch, paint(alpha(blend(TONAL, col, 0.30f), 0.92f)));
        c.drawRoundRect(pill, ch, ch, stroke(alpha(col, 0.45f), 1f * u));
        drawAlertGlyph(c, st.alertId, pill.left + 28f * u, cy, 13f * u, col);
        Paint t1 = text(INK, f.m700, 22f * u);
        Paint t2 = text(alpha(INK, 0.78f), f.m500, 16f * u);
        float tx = pill.left + 54f * u, tw = pill.right - 18f * u - tx;
        boolean two = st.alertSub != null && !st.alertSub.isEmpty();
        c.drawText(fit(st.alertTitle, t1, tw), tx, two ? cy - 3f * u : cy + 8f * u, t1);
        if (two && t2.measureText(st.alertSub) > tw) t2.setTextSize(14.5f * u);
        if (two) c.drawText(fit(st.alertSub, t2, tw), tx, cy + 19f * u, t2);
    }

    /** Status row in the same list style: motion lights paused, or a calendar event that isn't the work day. */
    private static void drawEvent(Canvas c, HomeState st, Fonts f, RectF r, float u, float iconX) {
        if (!st.motionOn) {
            drawWalkerGlyph(c, iconX, r.centerY(), 12f * u, WARN);
            rowText(c, r, u, "Motion", rowLabel(f, u), "Paused", rowValue(f, u, WARN), iconX);
        } else if (hasWork(st) && !eventIsWork(st)) {
            drawCalendarGlyph(c, iconX, r.centerY(), 11f * u, MUTE);
            rowText(c, r, u, "Calendar", rowLabel(f, u), st.nextEvent, rowValue(f, u, INK), iconX);
        }
    }

    static String fullDay(String d) {
        switch (d == null ? "" : d) {
            case "Mon": return "Monday"; case "Tue": return "Tuesday"; case "Wed": return "Wednesday";
            case "Thu": return "Thursday"; case "Fri": return "Friday"; case "Sat": return "Saturday";
            case "Sun": return "Sunday"; default: return d == null ? "" : d;
        }
    }

    /** The three day cards' rects (below the This Week heading), for tap zones. Same maths as drawForecast. */
    public static RectF[] dayCards(Geo g) {
        if (g.forecast == null || g.u <= 0f) return null;
        RectF r = g.forecast;
        float top = r.top + 40f * g.u, h = (r.bottom - top) / 3f;
        return new RectF[]{new RectF(r.left, top, r.right, top + h), new RectF(r.left, top + h, r.right, top + 2 * h),
                new RectF(r.left, top + 2 * h, r.right, r.bottom)};
    }

    /** "This Week" on a title bar like the notify feed's, then three day cards filling the rest: day + date and
     *  that day's event on the left; the weather icon and high over low on the right. */
    private static void drawForecast(Canvas c, HomeState st, Fonts f, RectF r, float u, float iconX) {
        int days = Math.min(3, st.fcDay.length);
        float barH = 40f * u, pad = 14f * u;
        RectF bar = new RectF(r.left, r.top, r.right, r.top + barH);
        separator(c, f, bar, "\ue878", "This Week", u);   // Material "event"

        float left = r.left + 4f * u, right = r.right - 4f * u;   // line up with the separator's icon and line
        float cardH = (r.bottom - bar.bottom) / 3f;
        Paint lab = rowLabel(f, u);
        Paint hi = shaded(INK, f.m700, 25f * u);
        Paint lo = shaded(SUBTLE, f.m500, 20f * u);
        Paint ev = shaded(alpha(INK, 0.86f), f.m500, 19f * u);
        Paint none = shaded(SUBTLE, f.m500, 19f * u);
        Paint dp = shaded(SUBTLE, f.m500, 17f * u);
        for (int i = 0; i < days; i++) {
            float top = bar.bottom + cardH * i, mid = top + cardH * 0.5f;
            float l1 = mid - 4f * u, l2 = mid + 22f * u;   // two lines centred in the card
            if (i > 0) c.drawLine(left, top, right, top, stroke(alpha(INK, 0.07f), 1f * u));
            // Left: weather icon, then high over low.
            float gr = Math.min(17f * u, cardH * 0.32f);
            drawWeatherGlyph(c, st.fcCond[i], left + gr, mid + 2f * u, gr);
            String a = st.fcHi[i] + "°", b = st.fcLo[i] + "°";
            float tx0 = left + gr * 2f + 10f * u;
            c.drawText(a, tx0, l1, hi);
            c.drawText(b, tx0, l2, lo);
            float tw = Math.max(hi.measureText("00°"), lo.measureText("00°"));
            // Then the day name, and the date + that day's event under it.
            float x2 = tx0 + tw + 14f * u;
            String dn = fullDay(st.fcDay[i]);
            c.drawText(fit(dn, lab, right - x2), x2, l1, lab);
            String dt = i < st.fcDate.length && st.fcDate[i] != null ? st.fcDate[i] : "";
            float ex = x2;
            if (!dt.isEmpty()) { c.drawText(dt, x2, l2, dp); ex = x2 + dp.measureText(dt) + 12f * u; }
            String e = i < st.fcEvent.length && st.fcEvent[i] != null ? st.fcEvent[i] : "";
            boolean has = !e.isEmpty();
            int gc = has ? ACCENT : SUBTLE;
            if (e.contains("WFH")) drawHouseGlyph(c, ex + 9f * u, l2 - 6f * u, 9f * u, gc);
            else drawCalendarGlyph(c, ex + 9f * u, l2 - 6f * u, 8.5f * u, gc);
            c.drawText(fit(has ? e : "Nothing on", has ? ev : none, right - ex - 26f * u), ex + 26f * u, l2, has ? ev : none);
        }
    }

    /** Option: disco / lights / door drawn as icon-shaped buttons instead of pills. */
    public static boolean ICON_BUTTONS = true;

    /** Material Symbols (Rounded) codepoints for the icon buttons. */
    static final String MI_BLUR_ON = "\ue3a5", MI_BULB = "\ue0f0", MI_LOCK = "\ue897", MI_LOCK_OPEN = "\ue898";

    /** Two pills on the floorplan's bottom edge: lights (bulb + "5 On" / "Off") and door (lock + "Locked" / "Open 14m"). */
    private static void drawIconButtons(Canvas c, HomeState st, Fonts f, Geo g, float u) {
        RectF lp = g.lights, dp = g.door;   // sized in geo(), so the tap zones match
        if (f.icons == null) return;
        Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        ip.setTypeface(f.icons);
        ip.setTextSize(lp.height() * 0.56f);

        int on = 0;
        for (int k = 0; k < st.lightOn.length; k++) if (st.lightOn[k] && HomeState.FX_ID[k].startsWith("light.")) on++;
        pill(c, f, ip, lp, on > 0 ? alpha(blend(TONAL, LIT, 0.30f), 0.92f) : alpha(TONAL, 0.72f),
                MI_BULB, on > 0 ? LIT : MUTE, null, on > 0 ? String.valueOf(on) : "Off", on > 0 ? LIT : INK, u);

        boolean open = st.door == HomeState.DOOR_UNLOCKED, armed = st.door == HomeState.DOOR_ARMED;
        long openMin = st.doorSinceMs > 0 ? (System.currentTimeMillis() - st.doorSinceMs) / 60_000L : -1;
        String dl = open ? (openMin < 0 ? "Unlocked" : "Open " + (openMin < 60 ? openMin + "m" : (openMin / 60) + "h"))
                : armed ? "Tap again" : "Locked";
        int bg = open ? (openMin >= 0 && openMin < 10 ? alpha(blend(TONAL, BLUSH, 0.35f), 0.92f) : BLUSH)
                : armed ? WARN : alpha(TONAL, 0.72f);
        int fg = open ? (openMin >= 0 && openMin < 10 ? INK : 0xFF3E1C26) : armed ? 0xFF2A2405 : INK;
        int icon = open ? fg : armed ? fg : ACCENT;
        pill(c, f, ip, dp, bg, open ? MI_LOCK_OPEN : MI_LOCK, icon, null, dl, fg, u);
    }

    /** Bubble Card separator: a small icon, the label, and a soft rounded line running to the right edge. */
    private static void separator(Canvas c, Fonts f, RectF r, String glyph, String label, float u) {
        float cy = r.centerY(), x = r.left + 4f * u;
        if (f.icons != null) {
            Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
            ip.setTypeface(f.icons);
            ip.setTextSize(24f * u);
            ip.setColor(MUTE);
            c.drawText(glyph, x, cy + 12f * u, ip);
            x += 32f * u;
        }
        Paint tp = shaded(MUTE, f.m600, 20f * u);
        c.drawText(label, x, cy + 7f * u, tp);
        float lx = x + tp.measureText(label) + 14f * u;
        if (lx < r.right - 10f * u) c.drawLine(lx, cy, r.right - 4f * u, cy, stroke(alpha(INK, 0.16f), 3f * u));
    }

    /** A pill with a Material icon and a label, centred as a group. */
    private static void pill(Canvas c, Fonts f, Paint ip, RectF r, int bg, String glyph, int gc, String label, int tc, float u) {
        pill(c, f, ip, r, bg, glyph, gc, null, label, tc, u);
    }

    /** Bubble Card button: a pill, a round icon badge at its left end, then name over state. */
    private static void pill(Canvas c, Fonts f, Paint ip, RectF r, int bg, String glyph, int gc, String name, String label, int tc, float u) {
        float rr = r.height() / 2f;
        c.drawRoundRect(r, rr, rr, paint(bg));
        float d = r.height() * 0.74f, ins = (r.height() - d) / 2f, bx = r.left + ins + d / 2f, cy = r.centerY();
        c.drawCircle(bx, cy, d / 2f, paint(alpha(gc, 0.22f)));
        float S = d * 0.62f;
        ip.setTextSize(S);
        ip.setColor(gc);
        ip.setTextAlign(Paint.Align.CENTER);
        c.drawText(glyph, bx, cy + S / 2f, ip);
        float tx = r.left + ins + d + 10f * u, room = r.right - 12f * u - tx;
        if (name != null) {
            Paint np = text(alpha(tc, 0.70f), f.m500, 16f * u);
            c.drawText(fit(name, np, room), tx, cy - 3f * u, np);
            Paint lt = text(tc, f.m700, 20f * u);
            c.drawText(fit(label, lt, room), tx, cy + 18f * u, lt);
        } else {
            Paint lt = text(tc, f.m700, 26f * u);   // same size in both buttons
            c.drawText(fit(label, lt, room), tx, cy + 10f * u, lt);
        }
    }

    private static void drawChips(Canvas c, HomeState st, Fonts f, Geo g, float u) {
        if (ICON_BUTTONS) { drawIconButtons(c, st, f, g, u); return; }
        float ch = Math.min(g.disco.height() * 0.5f, 34f * u);   // 68u tall pills, matching geo()
        float by = g.disco.bottom - ch;   // pills sit on the floorplan's bottom edge
        RectF ball = new RectF(g.disco.left, by - ch, g.disco.right, by + ch);
        c.drawRoundRect(ball, ch, ch, paint(st.mirrorOn ? alpha(blend(TONAL, LIT, 0.35f), 0.95f) : alpha(TONAL, 0.70f)));
        c.drawRoundRect(ball, ch, ch, stroke(alpha(INK, 0.10f), 1f * u));
        if (g.tall) {
            float gx = ball.left + 34f * u;
            drawMirrorBallGlyph(c, gx, by + 1f * u, Math.min(15f * u, ch * 0.54f), st.mirrorOn);
            Paint bt = text(st.mirrorOn ? LIT : INK, f.m600, 22f * u);
            c.drawText(st.mirrorOn ? "Disco ball on" : "Disco ball", gx + 30f * u, by + 7.5f * u, bt);
        } else {
            drawMirrorBallGlyph(c, ball.centerX(), by + 1f * u, Math.min(15f * u, ch * 0.54f), st.mirrorOn);
        }

        // Lights on: bulb + count, yellow-tinted while anything's on; tapping it turns everything off.
        int on = 0;
        for (int k = 0; k < st.lightOn.length; k++) if (st.lightOn[k] && HomeState.FX_ID[k].startsWith("light.")) on++;
        RectF lp = new RectF(g.lights.left, by - ch, g.lights.right, by + ch);
        c.drawRoundRect(lp, ch, ch, paint(on > 0 ? alpha(blend(TONAL, LIT, 0.28f), 0.92f) : alpha(TONAL, 0.70f)));
        c.drawRoundRect(lp, ch, ch, stroke(alpha(INK, 0.10f), 1f * u));
        Paint lt = text(on > 0 ? LIT : MUTE, f.m700, 23f * u);
        String ls = String.valueOf(on);
        float bw2 = 22f * u, lw2 = lt.measureText(ls), lx = lp.centerX() - (bw2 + 6f * u + lw2) / 2f;
        drawBulbGlyph(c, lx + bw2 / 2f, by - 1f * u, 13f * u, on > 0 ? LIT : MUTE, on > 0);
        c.drawText(ls, lx + bw2 + 6f * u, by + 8f * u, lt);

        float cy = g.door.bottom - ch;
        RectF door = new RectF(g.door.left, cy - ch, g.door.right, cy + ch);
        int fg, bg;
        String dl;
        long openMin = st.doorSinceMs > 0 ? (System.currentTimeMillis() - st.doorSinceMs) / 60_000L : -1;
        String openFor = openMin < 0 ? "" : openMin < 60 ? " " + openMin + "m" : " " + (openMin / 60) + "h";
        if (st.door == HomeState.DOOR_UNLOCKED && openMin >= 0 && openMin < 10) {
            bg = alpha(blend(TONAL, BLUSH, 0.30f), 0.92f); fg = INK; dl = "Open" + openFor;   // blush only after 10 min
        } else if (st.door == HomeState.DOOR_UNLOCKED) { bg = BLUSH; fg = 0xFF3E1C26; dl = openFor.isEmpty() ? "Unlocked" : "Open" + openFor; }
        else if (st.door == HomeState.DOOR_ARMED) { bg = WARN; fg = 0xFF2A2405; dl = g.tall ? "Tap again to unlock" : "Tap again"; }
        else { bg = alpha(TONAL, 0.70f); fg = INK; dl = g.tall ? "Front door locked" : "Locked"; }
        c.drawRoundRect(door, ch, ch, paint(bg));
        if (st.door == HomeState.DOOR_LOCKED) c.drawRoundRect(door, ch, ch, stroke(alpha(INK, 0.10f), 1f * u));
        Paint dt = text(fg, f.m600, 23f * u);
        float iw = 20f * u, gap = 10f * u, room = door.width() - iw - gap - 32f * u;
        while (dt.measureText(dl) > room && dt.getTextSize() > 17f * u) dt.setTextSize(dt.getTextSize() - 0.5f * u);
        String lab = fit(dl, dt, room);
        float lw = dt.measureText(lab);
        float gx = g.tall ? door.left + 24f * u : door.centerX() - (iw + gap + lw) / 2f;
        int lockCol = st.door == HomeState.DOOR_LOCKED ? ACCENT : fg;
        drawPadlockGlyph(c, gx + iw / 2f, cy - 4f * u, 10.5f * u, lockCol, st.door == HomeState.DOOR_UNLOCKED,
                st.door == HomeState.DOOR_LOCKED ? TONAL : (bg >>> 24) < 0xF0 ? TONAL : bg);
        c.drawText(lab, gx + iw + gap, cy + 7.5f * u, dt);
    }

    // ---------------------------------------------------------------- notify feed (terminal style, no panel)
    private static int feedColour(String src) {
        switch (src == null ? "" : src) {
            case "house-agent": return ROSE;
            case "you": return ACCENT;
            case "unifi": return HARBOUR;
            case "health": return APPLE;
            case "ha": return 0xFFC3CFD8;
            case "washer": return 0xFFA3BBAE;
            case "door": case "alarm": return WARN;
            case "leak": return BAD;
            default: return ACCENT;
        }
    }

    /** bae@home: ~/notify, the last few HA notifications as log lines, then a "$" prompt with a block cursor. */
    private static void drawFeed(Canvas c, HomeState st, Fonts f, RectF r, float s) {
        Typeface mono = f.mono != null ? f.mono : f.m500, monoB = f.monoBold != null ? f.monoBold : f.m700;
        float lh = FEED_LINE * s, x = r.left + 24f * s, y = r.top + 33f * s;
        // Bubble-style separator, like This Week: icon, label, then a rounded line to the edge.
        // Terminal window title bar, rounded to match the bubble cards: tonal fill, faint outline,
        // terminal icon + title on the left, three window dots on the right.
        // The whole feed sits in a semi-transparent terminal window; the title bar is its top strip.
        float wr = 22f * s;
        c.drawRoundRect(r, wr, wr, paint(alpha(0xFF0C1013, 0.42f)));
        RectF bar = new RectF(r.left, r.top, r.right, r.top + 46f * s);
        c.drawPath(corners(bar, wr, wr, 0f, 0f), paint(alpha(TONAL, 0.50f)));
        c.drawLine(bar.left, bar.bottom, bar.right, bar.bottom, stroke(alpha(INK, 0.10f), 1f * s));
        c.drawRoundRect(r, wr, wr, stroke(alpha(INK, 0.14f), 1.4f * s));
        float bx = bar.left + 18f * s, bcy = bar.centerY();
        if (f.icons != null) {
            Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
            ip.setTypeface(f.icons);
            ip.setTextSize(24f * s);
            ip.setColor(ACCENT);
            c.drawText("\ueb8e", bx, bcy + 12f * s, ip);   // Material "terminal"
            bx += 34f * s;
        }
        c.drawText("bae@home: ~/notify", bx, bcy + 7f * s, text(INK, mono, 21f * s));
        // House status on the right, boot-log style: [ OK ], [WARN] ..., [FAIL] ... (worst thing first).
        String tag, stMsg; int stCol;
        if (hasAlert(st)) {
            boolean alarm = "alarm".equals(st.alertSeverity), warn = "warning".equals(st.alertSeverity);
            tag = alarm ? "[FAIL]" : warn ? "[WARN]" : "[INFO]";
            stMsg = st.alertTitle == null ? "" : st.alertTitle.toLowerCase(Locale.US);
            stCol = alarm ? BAD : warn ? WARN : HARBOUR;
        } else if (!st.online) { tag = "[FAIL]"; stMsg = "ha offline"; stCol = BAD; }
        else if (st.door == HomeState.DOOR_UNLOCKED) { tag = "[WARN]"; stMsg = "door unlocked"; stCol = WARN; }
        else if (!st.motionOn) { tag = "[WARN]"; stMsg = "motion paused"; stCol = WARN; }
        else { tag = "[ OK ]"; stMsg = ""; stCol = ACCENT; }
        Paint tg = text(stCol, monoB, 19f * s), mp = text(alpha(INK, 0.80f), mono, 19f * s);
        float left = bx + text(INK, mono, 21f * s).measureText("bae@home: ~/notify") + 30f * s;
        float right = bar.right - 20f * s, tw = tg.measureText(tag);
        String stM = stMsg.isEmpty() ? "" : fit(stMsg, mp, right - left - tw - 10f * s);
        float mw = stM.isEmpty() ? 0f : mp.measureText(" " + stM);
        c.drawText(tag, right - mw - tw, bcy + 6.5f * s, tg);
        if (!stM.isEmpty()) c.drawText(" " + stM, right - mw, bcy + 6.5f * s, mp);
        int n = Math.min(FEED_LINES, st.feedTitle.length);
        if (n == 0) {
            y += lh;
            c.drawText("# no notifications yet", x, y, shaded(alpha(MUTE, 0.8f), mono, 22f * s));
        }
        for (int i = 0; i < n; i++) {
            y += lh;
            Paint ts = shaded(SUBTLE, mono, 22f * s), src = shaded(feedColour(st.feedSrc[i]), monoB, 22f * s),
                    msg = shaded(alpha(INK, 0.92f), mono, 22f * s);
            String a = "[" + st.feedTime[i] + "]";
            c.drawText(a, x, y, ts);
            float xx = x + ts.measureText(a + " ");
            String sname = st.feedSrc[i] == null ? "ha" : st.feedSrc[i];
            String padded = String.format(Locale.US, "%-12s", sname.length() > 11 ? sname.substring(0, 11) : sname);
            c.drawText(padded, xx, y, src);
            xx += src.measureText(padded);
            String t = st.feedTitle[i] == null ? "" : st.feedTitle[i].toLowerCase(Locale.US);
            String m = st.feedMsg[i] == null ? "" : st.feedMsg[i];
            String line = t.isEmpty() ? m : (m.isEmpty() ? t : t + ": " + m);
            c.drawText(fit(line, msg, r.right - 20f * s - xx), xx, y, msg);
        }
        // A full pending prompt, bash colours in Sea glass: bae@home (teal) : ~ (harbour) $ then a blush cursor.
        y = r.top + 33f * s + lh * (FEED_LINES + 1);
        Paint pu = shaded(ACCENT, monoB, 22f * s), pp = shaded(HARBOUR, monoB, 22f * s), pd = shaded(INK, mono, 22f * s);
        float px = x;
        if (PROMPT_STYLE == 1) {   // powerline: coloured segments with arrow ends
            float h = 34f * s, top = y - 25f * s;
            px = powerSeg(c, "bae@home", px, top, h, ACCENT, 0xFF12201E, monoB, s, true);
            px = powerSeg(c, "~", px - 12f * s, top, h, HARBOUR, 0xFF12201E, monoB, s, false);
            c.drawRect(new RectF(px + 14f * s, y - 19f * s, px + 27f * s, y + 4f * s), paint(BLUSH));
            return;
        }
        if (PROMPT_STYLE == 2) {   // a hint of what it's for, dimmed, with the cursor after
            c.drawText("bae@home", px, y, pu); px += pu.measureText("bae@home");
            c.drawText(":", px, y, pd); px += pd.measureText(":");
            c.drawText("~", px, y, pp); px += pp.measureText("~");
            c.drawText("$ ", px, y, pd); px += pd.measureText("$ ");
            Paint hint = shaded(alpha(MUTE, 0.55f), mono, 22f * s);
            c.drawRect(new RectF(px, y - 19f * s, px + 13f * s, y + 4f * s), paint(BLUSH));
            c.drawText("ask the house agent", px + 20f * s, y, hint);
            return;
        }
        if (PROMPT_STYLE == 3) {   // starship-style: path, host, chevron; time on the right
            c.drawText("~", px, y, pp); px += pp.measureText("~ ");
            Paint host = shaded(ROSE, monoB, 22f * s);
            c.drawText("@home-agent", px, y, host); px += host.measureText("@home-agent ");
            Paint chev = shaded(ACCENT, monoB, 24f * s);
            c.drawText("\u276F", px, y, chev); px += chev.measureText("\u276F ");
            c.drawRect(new RectF(px, y + 1f * s, px + 15f * s, y + 4.5f * s), paint(BLUSH));   // underscore cursor
            Paint tm = shaded(SUBTLE, mono, 20f * s);
            tm.setTextAlign(Paint.Align.RIGHT);
            // System readout instead of a clock: HA round trip and how long since the last sync.
            String lat = (st.haCloud ? "cloud " : "ha ") + (st.haMs < 0 ? "--" : st.haMs + "ms");
            long ago = st.syncMs > 0 ? (System.currentTimeMillis() - st.syncMs) / 60_000L : -1;
            String sync = ago < 0 ? "" : ago < 1 ? " · sync now" : ago < 60 ? " · sync " + ago + "m" : " · sync " + (ago / 60) + "h";
            c.drawText(lat + sync, r.right - 20f * s, y, tm);
            return;
        }
        c.drawText("bae@home", px, y, pu); px += pu.measureText("bae@home");
        c.drawText(":", px, y, pd); px += pd.measureText(":");
        c.drawText("~", px, y, pp); px += pp.measureText("~");
        c.drawText("$ ", px, y, pd); px += pd.measureText("$ ");
        c.drawRect(new RectF(px, y - 19f * s, px + 13f * s, y + 4f * s), paint(BLUSH));
    }

    /** Prompt look: 0 bash colours, 1 powerline segments, 2 bash + hint, 3 starship-style chevron. */
    public static int PROMPT_STYLE = 3;
    /** Media card in the Bubble Card style (pill, round art, big play button on the right). */
    public static boolean BUBBLE = true;

    /** One powerline segment: filled block with an arrow on its right; returns the x after the arrow. */
    private static float powerSeg(Canvas c, String label, float x, float top, float h, int bg, int fg, Typeface tf, float s, boolean first) {
        Paint t = text(fg, tf, 20f * s);
        float w = t.measureText(label) + (first ? 26f : 30f) * s, ar = h / 2f;
        Path p = new Path();
        p.moveTo(x, top);
        p.lineTo(x + w, top);
        p.lineTo(x + w + ar * 0.8f, top + ar);
        p.lineTo(x + w, top + h);
        p.lineTo(x, top + h);
        if (!first) p.lineTo(x + ar * 0.8f, top + ar);
        p.close();
        if (first) c.drawPath(corners(new RectF(x, top, x + w, top + h), 8f * s, 0f, 0f, 8f * s), paint(bg));
        c.drawPath(p, paint(bg));
        c.drawText(label, x + (first ? 13f : 22f) * s, top + h / 2f + 7f * s, t);
        return x + w + ar * 0.8f;
    }

    // ---------------------------------------------------------------- media (thin strip, only while playing)
    private static void drawMedia(Canvas c, HomeState st, Fonts f, Geo g, float s) {
        if (BUBBLE) { drawBubble(c, st, f, g, s); return; }
        RectF panel = g.media;
        float u = Math.min(s * 1.25f, panel.height() / 120f);
        float pr = Math.min(panel.height() / 2f, 48f * s);
        Path pclip;
        if (MEDIA_STYLE == 1) pclip = corners(panel, panel.height() / 2f, panel.height() / 2f, panel.height() / 2f, panel.height() / 2f);   // pill
        else if (MEDIA_STYLE == 2) pclip = corners(panel, PLAN_R * s, PLAN_R * s, PLAN_R * s, PLAN_R * s);   // floorplan corners
        else if (MEDIA_STYLE == 3) pclip = corners(panel, panel.height() / 2f, 14f * s, 14f * s, panel.height() / 2f);   // round left, soft right
        else if (MEDIA_STYLE == 5) pclip = corners(panel, PLAN_R * s, PLAN_R * s, 6f * s, 6f * s);   // floorplan top corners, barely-rounded bottom
        else if (MEDIA_STYLE == 4) pclip = shaped(panel, 1, panel.height() * 0.5f, panel.height() * 0.17f);   // app-icon arch
        else pclip = corners(panel, BAR_R * s, BAR_R * s, 0f, 0f);   // round top, square bottom
        float cy = panel.centerY();
        RectF art = g.mArt;
        c.save();
        c.clipPath(pclip);
        c.drawRect(panel, paint(alpha(TONAL, 0.30f)));
        // The art fills the left end, unfaded; its colour then washes across the rest of the card, fading out.
        int wash = TONAL;
        if (st.art != null) {
            c.drawBitmap(st.art, null, art, new Paint(Paint.FILTER_BITMAP_FLAG));
            wash = blend(averageColour(st.art), DEEP, 0.30f);
            // Light covers (white, pastel) would wash out the white text: pull them down to a readable tone.
            int lum = (((wash >> 16) & 255) * 3 + ((wash >> 8) & 255) * 6 + (wash & 255)) / 10;
            if (lum > 110) wash = blend(wash, DEEP, Math.min(0.7f, (lum - 110) / 120f + 0.2f));
        } else {
            c.drawRect(art, paint(alpha(PANEL_2, 0.70f)));
            drawNoteGlyph(c, art.centerX(), art.centerY(), art.width() * 0.2f, SUBTLE);
        }
        float x0 = art.right, x1 = panel.right;
        int n = 160;   // fine steps so the fade has no visible bands
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float a = 0.92f * (1f - t) * (1f - t) + 0.08f;   // eases out towards the right
            c.drawRect(new RectF(x0 + (x1 - x0) * i / n, panel.top, x0 + (x1 - x0) * (i + 1) / n + 1f, panel.bottom), paint(alpha(wash, a)));
        }
        c.restore();

        float tx = art.right + 22f * s, tw = g.mInfo.right - 14f * s - tx;
        // "Club + 1 · Now playing", muted and tracked, then the title, then artist · album.
        Paint h = text(alpha(INK, 0.62f), f.m600, 18f * u);
        h.setLetterSpacing(0.08f);
        String room = "Club" + (st.mediaGroup > 0 ? " + " + st.mediaGroup : "");
        String vol0 = PILLS_IN_MEDIA ? " · " + Math.round(Math.max(0f, Math.min(1f, st.volume)) * 100) + "%" : "";
        c.drawText(fit(room + " · " + (st.mediaPlaying ? "Now playing" : "Paused") + vol0, h, tw), tx, panel.top + 25f * u, h);
        Paint t1 = text(INK, f.m700, 24f * u);
        String title = st.mediaTitle == null || st.mediaTitle.isEmpty() ? "Club" : st.mediaTitle;
        c.drawText(fit(title, t1, tw), tx, cy + 7f * u, t1);
        String sub = st.mediaArtist == null ? "" : st.mediaArtist;
        if (st.mediaAlbum != null && !st.mediaAlbum.isEmpty()) sub = sub.isEmpty() ? st.mediaAlbum : sub + " · " + st.mediaAlbum;
        if (!sub.isEmpty()) c.drawText(fit(sub, text(alpha(INK, 0.66f), f.m500, 18f * u), tw), tx, cy + 30f * u, text(alpha(INK, 0.66f), f.m500, 18f * u));

        // Controls centred between the card top and the progress line, as big as the card allows.
        float ccy = (panel.top + panel.bottom - 7f * s) / 2f;
        float pr2 = Math.min(36f * u, (panel.height() - 18f * s) / 2f);
        drawSkipGlyph(c, g.mPrev.centerX(), ccy, 17f * u, INK, false);
        c.drawCircle(g.mPlay.centerX(), ccy, pr2, paint(ACCENT));
        drawPlayGlyph(c, g.mPlay.centerX(), ccy, pr2 * 0.44f, ON_ACCENT, st.mediaPlaying);
        drawSkipGlyph(c, g.mNext.centerX(), ccy, 17f * u, INK, true);

        if (PILLS_IN_MEDIA) {   // narrow card: just the progress line, volume lives in the header
            if (st.mediaDuration > 0) {
                float pl = tx, prr = panel.right - 16f * s, py = panel.bottom - 7f * s, ph = 1.5f * u;
                c.drawRoundRect(new RectF(pl, py - ph, prr, py + ph), ph, ph, paint(alpha(INK, 0.14f)));
                float pf = Math.max(0f, Math.min(1f, st.mediaPosition / (float) st.mediaDuration));
                if (pf > 0.005f) c.drawRoundRect(new RectF(pl, py - ph, pl + (prr - pl) * pf, py + ph), ph, ph, paint(ACCENT));
            }
            return;
        }
        // Right: speaker + volume %, and elapsed / length under it.
        RectF vr = g.mUp;
        Paint vp = text(INK, f.m700, 25f * u);
        vp.setTextAlign(Paint.Align.RIGHT);
        String vol = Math.round(Math.max(0f, Math.min(1f, st.volume)) * 100) + "%";
        float vx = vr.right - 6f * u;
        boolean timed = st.mediaDuration > 0;
        float vy = timed ? ccy - 2f * u : ccy + 9f * u;
        c.drawText(vol, vx, vy, vp);
        drawSpeakerGlyph(c, vx - vp.measureText(vol) - 19f * u, vy - 9f * u, 11f * u, MUTE);
        if (timed) {
            Paint tp = text(SUBTLE, f.m500, 18f * u);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(mmss(st.mediaPosition) + " / " + mmss(st.mediaDuration), vx, ccy + 25f * u, tp);
            // Thin progress line along the bottom of the card.
            float pl = tx, prr = panel.right - 26f * s, py = panel.bottom - 7f * s, ph = 1.5f * u;
            c.drawRoundRect(new RectF(pl, py - ph, prr, py + ph), ph, ph, paint(alpha(INK, 0.14f)));
            float pf = Math.max(0f, Math.min(1f, st.mediaPosition / (float) st.mediaDuration));
            if (pf > 0.005f) c.drawRoundRect(new RectF(pl, py - ph, pl + (prr - pl) * pf, py + ph), ph, ph, paint(ACCENT));
        }
    }

    /** Bubble Card look: a pill, the art's colour glowing from the left and fading out, round art, title + artist,
     *  bare prev / next, volume %, and a big round play button at the right end. */
    private static void drawBubble(Canvas c, HomeState st, Fonts f, Geo g, float s) {
        RectF panel = g.media;
        float u = Math.min(s * 1.25f, panel.height() / 120f), R = panel.height() / 2f, cy = panel.centerY();
        Path pill = corners(panel, R, R, R, R);
        c.save();
        c.clipPath(pill);
        c.drawRect(panel, paint(alpha(TONAL, 0.55f)));
        int wash = TONAL;
        if (st.art != null) {
            wash = blend(averageColour(st.art), DEEP, 0.30f);
            int lum = (((wash >> 16) & 255) * 3 + ((wash >> 8) & 255) * 6 + (wash & 255)) / 10;
            if (lum > 110) wash = blend(wash, DEEP, Math.min(0.7f, (lum - 110) / 120f + 0.2f));
        }
        float x0 = panel.left, x1 = panel.left + panel.width() * 0.62f;
        int n = 120;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1), a = 0.85f * (1f - t) * (1f - t);
            c.drawRect(new RectF(x0 + (x1 - x0) * i / n, panel.top, x0 + (x1 - x0) * (i + 1) / n + 1f, panel.bottom), paint(alpha(wash, a)));
        }
        c.restore();

        RectF art = g.mArt;
        Path ac = new Path();
        ac.addCircle(art.centerX(), art.centerY(), art.width() / 2f, Path.Direction.CW);
        c.save();
        c.clipPath(ac);
        if (st.art != null) c.drawBitmap(st.art, null, art, new Paint(Paint.FILTER_BITMAP_FLAG));
        else { c.drawRect(art, paint(PANEL_2)); drawNoteGlyph(c, art.centerX(), art.centerY(), art.width() * 0.22f, SUBTLE); }
        c.restore();

        float tx = art.right + 18f * s, tw = g.mInfo.right - 10f * s - tx;
        Paint h = text(alpha(INK, 0.60f), f.m600, 16f * u);
        h.setLetterSpacing(0.06f);
        String room = "Club" + (st.mediaGroup > 0 ? " + " + st.mediaGroup : "");
        c.drawText(fit(room + " · " + (st.mediaPlaying ? "Now playing" : "Paused"), h, tw), tx, cy - 20f * u, h);
        Paint t1 = text(INK, f.m700, 23f * u);
        c.drawText(fit(st.mediaTitle == null || st.mediaTitle.isEmpty() ? "Club" : st.mediaTitle, t1, tw), tx, cy + 6f * u, t1);
        String sub = st.mediaArtist == null ? "" : st.mediaArtist;
        Paint t2 = text(alpha(INK, 0.70f), f.m500, 18f * u);
        if (!sub.isEmpty()) c.drawText(fit(sub, t2, tw), tx, cy + 28f * u, t2);

        RectF vr = g.mUp;
        Paint vp = text(INK, f.m600, 21f * u);
        String vol = Math.round(Math.max(0f, Math.min(1f, st.volume)) * 100) + "%";
        float vw = vp.measureText(vol), gx = vr.centerX() - (vw + 26f * u) / 2f;
        drawSpeakerGlyph(c, gx + 9f * u, cy, 10f * u, MUTE);
        c.drawText(vol, gx + 26f * u, cy + 7.5f * u, vp);
        drawSkipGlyph(c, g.mPrev.centerX(), cy, 15f * u, INK, false);
        drawSkipGlyph(c, g.mNext.centerX(), cy, 15f * u, INK, true);
        float pr = g.mPlay.width() / 2f;
        c.drawCircle(g.mPlay.centerX(), cy, pr, paint(ACCENT));
        drawPlayGlyph(c, g.mPlay.centerX(), cy, pr * 0.40f, ON_ACCENT, st.mediaPlaying);
    }

    /** The art's average colour (a 4x4 squash), kept saturated enough to read as a colour. */
    static int averageColour(Bitmap art) {
        Bitmap t = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        new Canvas(t).drawBitmap(art, null, new RectF(0, 0, 4, 4), new Paint(Paint.FILTER_BITMAP_FLAG));
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            int p = t.getPixel(x, y);
            r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
        }
        return 0xFF000000 | ((int) (r / 16) << 16) | ((int) (g / 16) << 8) | (int) (b / 16);
    }

    static String mmss(int sec) { return (sec / 60) + ":" + String.format(Locale.US, "%02d", sec % 60); }

    static void drawSpeakerGlyph(Canvas c, float cx, float cy, float r, int col) {
        Path p = new Path();
        p.moveTo(cx - r, cy - r * 0.35f); p.lineTo(cx - r * 0.45f, cy - r * 0.35f); p.lineTo(cx + r * 0.15f, cy - r * 0.9f);
        p.lineTo(cx + r * 0.15f, cy + r * 0.9f); p.lineTo(cx - r * 0.45f, cy + r * 0.35f); p.lineTo(cx - r, cy + r * 0.35f); p.close();
        c.drawPath(p, paint(col));
        Paint w = stroke(col, Math.max(1.6f, r * 0.18f));
        c.drawArc(new RectF(cx - r * 0.2f, cy - r * 0.55f, cx + r * 0.9f, cy + r * 0.55f), -50f, 100f, false, w);
    }

    // ---------------------------------------------------------------- floorplan
    // Rooms on floorplan.png (x0, y0, x1, y1 in %) and which fixtures light them. Dark rooms get dimmed.
    private static final float[][] SHADE = {
        {0f, 0f, 100f, 55.8f}, {43.5f, 55.8f, 100f, 63f},
        {0f, 55.8f, 43.5f, 100f}, {43.5f, 63f, 68.2f, 100f}, {68.2f, 63f, 100f, 100f}
    };
    private static final int[][] SHADE_FX = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {10, 14, 15}, {13}, {11, 12}
    };

    private static void drawPlan(Canvas c, HomeState st, Fonts f, Geo g, float s) {
        RectF p = g.plan;
        float rr = PLAN_R * s;
        Path clip = PLAN_SHAPE == 1 ? shaped(p, 1, p.width() * 0.5f, rr)   // app-icon arch
                : PLAN_SHAPE == 2 ? corners(p, rr, 0f, 0f, 0f)   // only the top-left rounded
                : corners(p, rr, rr, rr, rr);
        c.save();
        c.clipPath(clip);
        if (st.plan != null) c.drawBitmap(st.plan, null, p, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        else c.drawRect(p, paint(PANEL));

        for (int r = 0; r < SHADE.length; r++) {
            boolean lit = false;
            for (int i : SHADE_FX[r]) lit |= st.lightOn[i];
            float[] q = SHADE[r];
            c.drawRect(new RectF(p.left + p.width() * q[0] / 100f, p.top + p.height() * q[1] / 100f,
                    p.left + p.width() * q[2] / 100f, p.top + p.height() * q[3] / 100f),
                    paint(alpha(DEEP, lit ? 0.14f : 0.52f)));
        }

        // Soft pools of light under whatever is on
        for (int i = 0; i < HomeState.FX_ID.length; i++) {
            if (!st.lightOn[i]) continue;
            float x = g.fxX(i), y = g.fxY(i), gr = p.width() * 0.16f;
            for (int k = 16; k >= 1; k--) c.drawCircle(x, y, gr * k / 16f, paint(alpha(LIT, 0.02f)));
        }

        for (int i = 0; i < HomeState.FX_ID.length; i++) {
            boolean on = st.lightOn[i];
            float x = g.fxX(i), y = g.fxY(i), r = g.badgeR;
            c.drawCircle(x, y, r, paint(on ? LIT : alpha(DEEP, 0.72f)));
            c.drawCircle(x, y, r, stroke(on ? alpha(INK, 0.85f) : alpha(MUTE, 0.55f), 1.7f * s));
            drawFixtureGlyph(c, HomeState.FX_KIND[i], x, y, r * 0.5f, on ? ON_ACCENT : INK);
        }
        c.restore();

        if (!st.online) {
            float u = Math.max(0.7f * s, Math.min(s, p.width() / 900f));
            Paint tp = text(WARN, f.m600, 19f * u);
            tp.setTextAlign(Paint.Align.CENTER);
            String msg = st.lastSync == null || st.lastSync.isEmpty() ? "Offline" : "Offline · last " + st.lastSync;
            float tw = tp.measureText(msg), ph = 20f * u, cy = p.top + p.height() * 0.40f;
            RectF pill = new RectF(p.centerX() - tw / 2f - 20f * u, cy - ph, p.centerX() + tw / 2f + 20f * u, cy + ph);
            c.drawRoundRect(pill, ph, ph, paint(blend(BG, WARN, 0.14f)));
            c.drawRoundRect(pill, ph, ph, stroke(WARN, 1.6f * u));
            c.drawText(msg, pill.centerX(), cy + 7f * u, tp);
        }
    }

    static void drawFixtureGlyph(Canvas c, String kind, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(1.6f, r * 0.22f));
        switch (kind) {
            case "art":
                c.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), r * 0.25f, r * 0.25f, p);
                c.drawLine(cx - r * 0.5f, cy + r * 0.45f, cx - r * 0.05f, cy - r * 0.15f, p);
                c.drawLine(cx - r * 0.05f, cy - r * 0.15f, cx + r * 0.5f, cy + r * 0.45f, p);
                break;
            case "sofa":
                c.drawRoundRect(new RectF(cx - r * 1.1f, cy - r * 0.6f, cx + r * 1.1f, cy + r * 0.6f), r * 0.35f, r * 0.35f, p);
                c.drawLine(cx - r * 0.55f, cy + r * 0.05f, cx + r * 0.55f, cy + r * 0.05f, p);
                break;
            case "lamp":
                c.drawLine(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy - r, p);
                c.drawLine(cx - r * 0.45f, cy - r, cx - r * 0.9f, cy, p);
                c.drawLine(cx + r * 0.45f, cy - r, cx + r * 0.9f, cy, p);
                c.drawLine(cx - r * 0.9f, cy, cx + r * 0.9f, cy, p);
                c.drawLine(cx, cy, cx, cy + r, p);
                c.drawLine(cx - r * 0.5f, cy + r, cx + r * 0.5f, cy + r, p);
                break;
            case "led":
            case "strip":
                c.drawLine(cx - r * 1.05f, cy + r * 0.4f, cx + r * 1.05f, cy + r * 0.4f, p);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * r * 0.7f, cy - r * 0.15f, cx + i * r * 0.7f, cy - r * 0.6f, p);
                break;
            case "tree":
                c.drawLine(cx, cy - r * 1.05f, cx - r * 0.85f, cy + r * 0.55f, p);
                c.drawLine(cx, cy - r * 1.05f, cx + r * 0.85f, cy + r * 0.55f, p);
                c.drawLine(cx - r * 0.85f, cy + r * 0.55f, cx + r * 0.85f, cy + r * 0.55f, p);
                c.drawLine(cx, cy + r * 0.55f, cx, cy + r * 1.05f, p);
                break;
            case "candles":
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * r * 0.7f, top = cy - (i == 0 ? r * 0.35f : 0f);
                    c.drawLine(x, top, x, cy + r * 0.95f, p);
                    c.drawCircle(x, top - r * 0.5f, r * 0.14f, paint(col));
                }
                break;
            case "desk":
                c.drawRoundRect(new RectF(cx - r * 1.05f, cy - r * 0.85f, cx + r * 1.05f, cy + r * 0.4f), r * 0.2f, r * 0.2f, p);
                c.drawLine(cx, cy + r * 0.4f, cx, cy + r * 0.95f, p);
                c.drawLine(cx - r * 0.5f, cy + r * 0.95f, cx + r * 0.5f, cy + r * 0.95f, p);
                break;
            case "disco": {   // mirror ball: hanging line, ball, two facet bands
                c.drawLine(cx, cy - r * 1.15f, cx, cy - r * 0.75f, p);
                c.drawCircle(cx, cy + r * 0.12f, r * 0.82f, p);
                c.drawLine(cx - r * 0.8f, cy + r * 0.12f, cx + r * 0.8f, cy + r * 0.12f, p);
                c.drawOval(new RectF(cx - r * 0.36f, cy - r * 0.7f, cx + r * 0.36f, cy + r * 0.94f), p);
                break;
            }
            case "shirt": {   // wardrobe: a T-shirt
                Path t = new Path();
                t.moveTo(cx - r * 0.38f, cy - r * 0.92f);
                t.lineTo(cx - r * 1.08f, cy - r * 0.55f);
                t.lineTo(cx - r * 0.78f, cy - r * 0.05f);
                t.lineTo(cx - r * 0.55f, cy - r * 0.2f);
                t.lineTo(cx - r * 0.55f, cy + r * 0.95f);
                t.lineTo(cx + r * 0.55f, cy + r * 0.95f);
                t.lineTo(cx + r * 0.55f, cy - r * 0.2f);
                t.lineTo(cx + r * 0.78f, cy - r * 0.05f);
                t.lineTo(cx + r * 1.08f, cy - r * 0.55f);
                t.lineTo(cx + r * 0.38f, cy - r * 0.92f);
                t.quadTo(cx, cy - r * 0.45f, cx - r * 0.38f, cy - r * 0.92f);
                t.close();
                c.drawPath(t, p);
                break;
            }
            case "spot":
                c.drawCircle(cx, cy, r * 0.95f, p);
                c.drawCircle(cx, cy, r * 0.3f, paint(col));
                break;
            default:
                drawBulbGlyph(c, cx, cy, r * 1.15f, col, false);
                break;
        }
    }

    // ---------------------------------------------------------------- glyphs
    static void drawWeatherGlyph(Canvas c, String cond, float cx, float cy, float r) {
        String k = cond == null ? "cloudy" : cond.toLowerCase(Locale.US);
        int sun = 0xFFF5D76E, cloud = 0xFFC9D6D2, rain = HARBOUR;
        if (k.contains("night") && k.contains("clear")) {
            c.drawCircle(cx, cy, r * 0.85f, paint(sun));
            c.drawCircle(cx - r * 0.36f, cy - r * 0.28f, r * 0.72f, paint(BG));
            return;
        }
        if (k.contains("sunny") || "clear".equals(k)) {
            c.drawCircle(cx, cy, r * 0.58f, paint(sun));
            Paint ray = stroke(sun, r * 0.14f);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                c.drawLine(cx + (float) Math.cos(a) * r * 0.80f, cy + (float) Math.sin(a) * r * 0.80f,
                        cx + (float) Math.cos(a) * r * 1.02f, cy + (float) Math.sin(a) * r * 1.02f, ray);
            }
            return;
        }
        if (k.contains("partly")) c.drawCircle(cx + r * 0.30f, cy - r * 0.28f, r * 0.44f, paint(sun));
        boolean wet = k.contains("rain") || k.contains("pour") || k.contains("hail") || k.contains("snow");
        float clY = wet ? cy - r * 0.14f : cy + r * 0.06f;
        Paint cp = paint(cloud);
        c.drawCircle(cx - r * 0.36f, clY + r * 0.10f, r * 0.42f, cp);
        c.drawCircle(cx + r * 0.02f, clY - r * 0.14f, r * 0.52f, cp);
        c.drawCircle(cx + r * 0.42f, clY + r * 0.12f, r * 0.38f, cp);
        c.drawRoundRect(new RectF(cx - r * 0.72f, clY + r * 0.04f, cx + r * 0.76f, clY + r * 0.50f), r * 0.24f, r * 0.24f, cp);
        if (wet) {
            Paint drop = stroke(k.contains("snow") ? INK : rain, Math.max(2f, r * 0.14f));
            for (int i = -1; i <= 1; i++) {
                float dx = cx + i * r * 0.34f;
                c.drawLine(dx, clY + r * 0.68f, dx - r * 0.12f, clY + r * 0.98f, drop);
            }
        }
        if (k.contains("lightning")) {
            Paint bolt = stroke(sun, Math.max(2f, r * 0.15f));
            c.drawLine(cx + r * 0.10f, clY + r * 0.56f, cx - r * 0.10f, clY + r * 0.84f, bolt);
            c.drawLine(cx - r * 0.10f, clY + r * 0.84f, cx + r * 0.14f, clY + r * 0.84f, bolt);
            c.drawLine(cx + r * 0.14f, clY + r * 0.84f, cx - r * 0.04f, clY + r * 1.12f, bolt);
        }
    }

    static void drawCalendarGlyph(Canvas c, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(1.8f, r * 0.18f));
        c.drawRoundRect(new RectF(cx - r, cy - r * 0.85f, cx + r, cy + r), r * 0.28f, r * 0.28f, p);
        c.drawLine(cx - r, cy - r * 0.25f, cx + r, cy - r * 0.25f, p);
        c.drawLine(cx - r * 0.45f, cy - r * 1.15f, cx - r * 0.45f, cy - r * 0.65f, p);
        c.drawLine(cx + r * 0.45f, cy - r * 1.15f, cx + r * 0.45f, cy - r * 0.65f, p);
    }

    static void drawHouseGlyph(Canvas c, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(1.8f, r * 0.17f));
        c.drawLine(cx - r, cy - r * 0.05f, cx, cy - r * 0.95f, p);
        c.drawLine(cx, cy - r * 0.95f, cx + r, cy - r * 0.05f, p);
        c.drawLine(cx - r * 0.72f, cy - r * 0.28f, cx - r * 0.72f, cy + r * 0.9f, p);
        c.drawLine(cx + r * 0.72f, cy - r * 0.28f, cx + r * 0.72f, cy + r * 0.9f, p);
        c.drawLine(cx - r * 0.72f, cy + r * 0.9f, cx + r * 0.72f, cy + r * 0.9f, p);
        c.drawLine(cx - r * 0.2f, cy + r * 0.9f, cx - r * 0.2f, cy + r * 0.35f, p);
        c.drawLine(cx - r * 0.2f, cy + r * 0.35f, cx + r * 0.2f, cy + r * 0.35f, p);
        c.drawLine(cx + r * 0.2f, cy + r * 0.35f, cx + r * 0.2f, cy + r * 0.9f, p);
    }

    static void drawBriefcaseGlyph(Canvas c, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(1.8f, r * 0.17f));
        c.drawRoundRect(new RectF(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.85f), r * 0.2f, r * 0.2f, p);
        c.drawRoundRect(new RectF(cx - r * 0.38f, cy - r * 0.92f, cx + r * 0.38f, cy - r * 0.5f), r * 0.12f, r * 0.12f, p);
        c.drawLine(cx - r, cy + r * 0.12f, cx + r, cy + r * 0.12f, p);
    }

    static void drawAlarmGlyph(Canvas c, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(1.8f, r * 0.18f));
        c.drawCircle(cx, cy + r * 0.08f, r * 0.88f, p);
        c.drawLine(cx, cy + r * 0.08f, cx, cy - r * 0.36f, p);
        c.drawLine(cx, cy + r * 0.08f, cx + r * 0.36f, cy + r * 0.24f, p);
        c.drawLine(cx - r * 0.88f, cy - r * 0.68f, cx - r * 0.46f, cy - r * 0.98f, p);
        c.drawLine(cx + r * 0.88f, cy - r * 0.68f, cx + r * 0.46f, cy - r * 0.98f, p);
    }

    static void drawWalkerGlyph(Canvas c, float cx, float cy, float r, int col) {
        c.drawCircle(cx + r * 0.08f, cy - r * 0.78f, r * 0.24f, paint(col));
        Paint p = stroke(col, Math.max(2.2f, r * 0.19f));
        c.drawLine(cx + r * 0.04f, cy - r * 0.44f, cx - r * 0.08f, cy + r * 0.18f, p);
        c.drawLine(cx + r * 0.02f, cy - r * 0.36f, cx - r * 0.44f, cy - r * 0.02f, p);
        c.drawLine(cx + r * 0.02f, cy - r * 0.36f, cx + r * 0.44f, cy - r * 0.06f, p);
        c.drawLine(cx - r * 0.08f, cy + r * 0.18f, cx - r * 0.38f, cy + r * 0.88f, p);
        c.drawLine(cx - r * 0.08f, cy + r * 0.18f, cx + r * 0.32f, cy + r * 0.88f, p);
    }

    static void drawMirrorBallGlyph(Canvas c, float cx, float cy, float r, boolean on) {
        Paint p = stroke(on ? LIT : MUTE, Math.max(2f, r * 0.13f));
        c.drawLine(cx, cy - r * 1.15f, cx, cy - r * 0.78f, p);
        c.drawCircle(cx, cy + r * 0.08f, r * 0.82f, p);
        c.drawOval(new RectF(cx - r * 0.42f, cy - r * 0.74f, cx + r * 0.42f, cy + r * 0.90f), p);
        c.drawLine(cx - r * 0.80f, cy + r * 0.08f, cx + r * 0.80f, cy + r * 0.08f, p);
        c.drawLine(cx - r * 0.68f, cy - r * 0.32f, cx + r * 0.68f, cy - r * 0.32f, p);
        c.drawLine(cx - r * 0.68f, cy + r * 0.48f, cx + r * 0.68f, cy + r * 0.48f, p);
        if (on) {
            float sx = cx + r * 1.12f, sy = cy - r * 0.78f, sr = r * 0.26f;
            c.drawLine(sx - sr, sy, sx + sr, sy, p);
            c.drawLine(sx, sy - sr, sx, sy + sr, p);
        }
    }

    static void drawPadlockGlyph(Canvas c, float cx, float cy, float r, int col, boolean unlocked, int holeCol) {
        c.drawRoundRect(new RectF(cx - r * 0.78f, cy - r * 0.08f, cx + r * 0.78f, cy + r * 1.02f), r * 0.26f, r * 0.26f, paint(col));
        Paint sh = stroke(col, Math.max(2.2f, r * 0.22f));
        if (unlocked) {
            c.drawArc(new RectF(cx - r * 0.48f, cy - r * 1.08f, cx + r * 0.48f, cy - r * 0.14f), 180f, 145f, false, sh);
            c.drawLine(cx - r * 0.48f, cy - r * 0.61f, cx - r * 0.48f, cy - r * 0.05f, sh);
        } else {
            c.drawArc(new RectF(cx - r * 0.48f, cy - r * 0.95f, cx + r * 0.48f, cy - r * 0.02f), 180f, 180f, false, sh);
            c.drawLine(cx - r * 0.48f, cy - r * 0.48f, cx - r * 0.48f, cy - r * 0.02f, sh);
            c.drawLine(cx + r * 0.48f, cy - r * 0.48f, cx + r * 0.48f, cy - r * 0.02f, sh);
        }
        c.drawCircle(cx, cy + r * 0.46f, r * 0.17f, paint(holeCol));
    }

    static void drawAlertGlyph(Canvas c, String alertId, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(2f, r * 0.18f));
        if ("washer".equals(alertId)) {
            c.drawRoundRect(new RectF(cx - r * 0.8f, cy - r * 0.9f, cx + r * 0.8f, cy + r * 0.9f), r * 0.22f, r * 0.22f, p);
            c.drawCircle(cx, cy + r * 0.12f, r * 0.44f, p);
        } else {
            c.drawCircle(cx, cy, r * 0.88f, p);
            c.drawLine(cx, cy - r * 0.42f, cx, cy + r * 0.08f, p);
            c.drawCircle(cx, cy + r * 0.42f, r * 0.10f, paint(col));
        }
    }

    static void drawPowerGlyph(Canvas c, float cx, float cy, float r, int col) {
        Paint p = stroke(col, Math.max(2f, r * 0.22f));
        c.drawArc(new RectF(cx - r, cy - r * 0.9f, cx + r, cy + r * 1.1f), -55f, 290f, false, p);
        c.drawLine(cx, cy - r * 1.1f, cx, cy - r * 0.05f, p);
    }

    static void drawPlayGlyph(Canvas c, float cx, float cy, float r, int col, boolean playing) {
        if (playing) {
            c.drawRoundRect(new RectF(cx - r * 0.82f, cy - r, cx - r * 0.22f, cy + r), r * 0.2f, r * 0.2f, paint(col));
            c.drawRoundRect(new RectF(cx + r * 0.22f, cy - r, cx + r * 0.82f, cy + r), r * 0.2f, r * 0.2f, paint(col));
        } else {
            Path t = new Path();
            t.moveTo(cx - r * 0.62f, cy - r);
            t.lineTo(cx + r * 0.95f, cy);
            t.lineTo(cx - r * 0.62f, cy + r);
            t.close();
            c.drawPath(t, paint(col));
        }
    }

    static void drawSkipGlyph(Canvas c, float cx, float cy, float r, int col, boolean next) {
        float d = next ? 1f : -1f;
        Path t = new Path();
        t.moveTo(cx - d * r * 0.85f, cy - r * 0.85f);
        t.lineTo(cx + d * r * 0.45f, cy);
        t.lineTo(cx - d * r * 0.85f, cy + r * 0.85f);
        t.close();
        c.drawPath(t, paint(col));
        c.drawLine(cx + d * r * 0.80f, cy - r * 0.78f, cx + d * r * 0.80f, cy + r * 0.78f, stroke(col, Math.max(2.4f, r * 0.26f)));
    }

    static void drawNoteGlyph(Canvas c, float cx, float cy, float r, int col) {
        c.drawOval(new RectF(cx - r * 1.0f, cy + r * 0.3f, cx - r * 0.05f, cy + r * 1.05f), paint(col));
        Paint p = stroke(col, Math.max(2f, r * 0.2f));
        c.drawLine(cx - r * 0.12f, cy + r * 0.66f, cx - r * 0.12f, cy - r * 1.0f, p);
        c.drawLine(cx - r * 0.12f, cy - r * 1.0f, cx + r * 0.8f, cy - r * 0.62f, p);
    }

    static void drawBulbGlyph(Canvas c, float cx, float cy, float r, int col, boolean lit) {
        Paint p = stroke(col, Math.max(1.8f, r * 0.17f));
        if (lit) c.drawCircle(cx, cy - r * 0.18f, r * 0.62f, paint(alpha(col, 0.35f)));
        c.drawCircle(cx, cy - r * 0.18f, r * 0.62f, p);
        c.drawLine(cx - r * 0.26f, cy + r * 0.70f, cx + r * 0.26f, cy + r * 0.70f, p);
        c.drawLine(cx - r * 0.16f, cy + r * 1.0f, cx + r * 0.16f, cy + r * 1.0f, p);
    }
}
