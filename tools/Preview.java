import android.graphics.Bitmap;
import android.graphics.Typeface;

import com.baeo.widgets.BaeoHomeRender;
import com.baeo.widgets.HomeState;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.io.File;

import javax.imageio.ImageIO;

/** Desktop preview of the Bæo Home widget with made-up state. Run via tools/preview.sh. */
public class Preview {
    public static void main(String[] a) throws Exception {
        String fonts = a[0], out = a[1];
        BaeoHomeRender.Fonts f = new BaeoHomeRender.Fonts();
        f.m500 = Typeface.createFromFile(fonts + "/manrope_500.ttf");
        f.m600 = Typeface.createFromFile(fonts + "/manrope_600.ttf");
        f.m700 = Typeface.createFromFile(fonts + "/manrope_700.ttf");
        f.syne700 = Typeface.createFromFile(fonts + "/syne_700.ttf");
        f.syne400 = Typeface.createFromFile(fonts + "/syne_400.ttf");
        f.icons = Typeface.createFromFile(fonts + "/material_round.otf");

        HomeState calm = base();
        save(BaeoHomeRender.render(calm, f, 980, 823), out + "/home_calm.png");

        HomeState party = base();
        party.lightOn[16] = true;
        party.mediaShow = true;
        party.mediaPlaying = true;
        party.mediaTitle = "Padam Padam";
        party.mediaArtist = "Kylie Minogue";
        party.volume = 0.42f;
        party.art = realArt();
        party.mirrorOn = true;
        party.activeScene = 0;
        on(party, 0, 1, 2, 4, 6, 7, 9);
        party.door = HomeState.DOOR_ARMED;
        save(BaeoHomeRender.render(party, f, 980, 823), out + "/home_party.png");
        for (int sh = 0; sh <= 2; sh++) { BaeoHomeRender.MEDIA_SHAPE = sh; save(BaeoHomeRender.render(party, f, 980, 823), out + "/home_shape" + sh + ".png"); }
        BaeoHomeRender.MEDIA_SHAPE = 1;

        HomeState alert = base();
        alert.alertId = "washer";
        alert.alertSeverity = "info";
        alert.alertTitle = "Washing's done";
        alert.alertSub = "Done 9:31 PM · Tap when emptied";
        alert.door = HomeState.DOOR_UNLOCKED; alert.doorSinceMs = System.currentTimeMillis() - 14 * 60_000L;
        alert.online = false;
        alert.lastSync = "2:41";
        alert.lightOn = new boolean[17]; on(alert, 10, 14, 15, 13);
        save(BaeoHomeRender.render(alert, f, 980, 823), out + "/home_alert.png");

        HomeState paused = base();
        paused.motionOn = false;
        paused.mediaShow = true;
        paused.mediaTitle = "TV";
        paused.mediaPlaying = true;
        paused.volume = 0.2f;
        save(BaeoHomeRender.render(paused, f, 980, 548), out + "/home_short.png");
        save(BaeoHomeRender.render(base(), f, 980, 900), out + "/home_tall.png");

        HomeState wfh = base(); wfh.haMs = 42; wfh.syncMs = System.currentTimeMillis() - 120_000L;
        wfh.workWfh = true; wfh.workPlace = "Home"; wfh.workWhen = "Mon · 8:30 - WFH"; wfh.nextRel = "Tomorrow"; wfh.workTime = "8:30"; wfh.workDay = "Mon";
        wfh.fcDate = new String[]{"23 Nov", "24 Nov", "25 Nov", "26 Nov"};
        wfh.fcEvent = new String[]{"8:30 WFH", "8:30 WFH", "8:30 CTH", ""};
        wfh.mediaShow = true; wfh.mediaTitle = "TV"; wfh.mediaPlaying = true; wfh.volume = 0.2f;
        save(BaeoHomeRender.render(wfh, f, 980, 823), out + "/home_wfh.png");
        save(BaeoHomeRender.render(wfh, f, 980, 741), out + "/home_wfh741.png");
        f.mono = Typeface.createFromFile("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf");
        f.monoBold = Typeface.createFromFile("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf");
        wfh.feedTime = new String[]{"09:41", "08:00", "07:58", "07:00", "06:30"};
        wfh.feedSrc = new String[]{"home-handler", "unifi", "washer", "ha", "unifi"};
        wfh.feedTitle = new String[]{"House notes", "UniFi weekly review", "Washing's done", "Updating sensors", "UniFi 24h"};
        wfh.feedMsg = new String[]{"Door was unlocked 12 min this morning", "2 new devices, 0 threats blocked", "Finished 7:58 · tap when emptied", "", "52 events, 0 threats"};
        save(BaeoHomeRender.render(wfh, f, 980, 988), out + "/home_feed.png");
        wfh.art = realArt(); wfh.mediaTitle = "Warum Nisht!"; wfh.mediaArtist = "Kurd Maverick"; wfh.mediaAlbum = "Sessions Eight (AU)"; wfh.mediaGroup = 1; wfh.mediaDuration = 247; wfh.mediaPosition = 96; wfh.volume = 0.45f;
        save(BaeoHomeRender.render(wfh, f, 980, 1112), out + "/home_feed_art.png");
        for (int ps = 1; ps <= 3; ps++) { BaeoHomeRender.PROMPT_STYLE = ps; save(BaeoHomeRender.render(wfh, f, 980, 988), out + "/prompt" + ps + ".png"); }
        BaeoHomeRender.PROMPT_STYLE = 3;
        float[] radii = {14f, 26f, 40f, 64f, 40f};
        for (int k = 0; k < radii.length; k++) {
            com.baeo.widgets.BaeoHomeRender.setPlan(radii[k], k == 4 ? 1 : 0);
            save(BaeoHomeRender.render(wfh, f, 980, 741), out + "/plan_r" + k + ".png");
        }
        com.baeo.widgets.BaeoHomeRender.setPlan(26f, 2);
        save(BaeoHomeRender.render(wfh, f, 980, 741), out + "/plan_tl.png");
        com.baeo.widgets.BaeoHomeRender.setPlan(26f, 0);
        BaeoHomeRender.PILLS_IN_MEDIA = true;
        save(BaeoHomeRender.render(wfh, f, 980, 741), out + "/pills_row.png");
        save(BaeoHomeRender.render(wfh, f, 980, 988), out + "/pills_row_feed.png");
        BaeoHomeRender.ICON_BUTTONS = true;
        save(BaeoHomeRender.render(wfh, f, 980, 988), out + "/icons_row_feed.png");
        HomeState un = Preview.base(); un.workWfh = true; un.workPlace = "Home"; un.workTime = "8:30"; un.nextRel = "Tomorrow";
        un.mediaShow = true; un.mediaTitle = "TV"; un.mediaPlaying = true; un.volume = 0.2f; un.mirrorOn = true;
        un.door = HomeState.DOOR_UNLOCKED; un.doorSinceMs = System.currentTimeMillis() - 14 * 60_000L;
        un.fcDate = wfh.fcDate; un.fcEvent = wfh.fcEvent;
        save(BaeoHomeRender.render(un, f, 980, 741), out + "/icons_row_on.png");
        BaeoHomeRender.PILLS_IN_MEDIA = false;
        save(BaeoHomeRender.render(un, f, 980, 741), out + "/icons_col_on.png");
        BaeoHomeRender.ICONS_TOP = true;
        save(BaeoHomeRender.render(wfh, f, 980, 741), out + "/icons_top.png");
        save(BaeoHomeRender.render(un, f, 980, 741), out + "/icons_top_on.png");
        BaeoHomeRender.ICONS_TOP = false;
        BaeoHomeRender.ICON_BUTTONS = true;
        for (int ms = 0; ms <= 5; ms++) { BaeoHomeRender.MEDIA_STYLE = ms; save(BaeoHomeRender.render(wfh, f, 980, 741), out + "/media_style" + ms + ".png"); }
        BaeoHomeRender.MEDIA_STYLE = 5;
        BaeoHomeRender.WEATHER_TOP = true;
        save(BaeoHomeRender.render(wfh, f, 980, 988), out + "/home_feed_wtop.png");
        BaeoHomeRender.WEATHER_TOP = false;
        BaeoHomeRender.MEDIA_FIRST = false;
        save(BaeoHomeRender.render(wfh, f, 980, 988), out + "/home_feed_art_below.png");
        BaeoHomeRender.MEDIA_FIRST = true;
    }

    static HomeState base() {
        HomeState s = new HomeState();
        s.dayName = "Sun";
        s.dateRest = "15 Nov";
        s.temp = 16;
        s.cond = "rainy";
        s.condLabel = "Rainy";
        s.low = 11;
        s.high = 16;
        s.fcDay = new String[]{"Mon", "Tue", "Wed", "Thu"};
        s.fcCond = new String[]{"partlycloudy", "cloudy", "partlycloudy", "sunny"};
        s.fcHi = new int[]{15, 16, 19, 22};
        s.fcLo = new int[]{10, 7, 11, 12};
        s.nextEvent = "Mon 8:30 WFH";
        s.nextAlarm = "7:20 Mon";
        s.workLine = "Richmond · 8:30 Mon";
        s.workPlace = "Richmond Town Hall";
        s.workWhen = "Mon · 8:30 - Richmond"; s.workTime = "8:30"; s.nextRel = "Tomorrow";
        s.insideTemp = "20.8";
        s.insideHum = "65";
        s.airconLine = "Aircon off";
        s.alarmActive = true;
        try { s.plan = new Bitmap(ImageIO.read(new File("assets/floorplan.png"))); } catch (Exception e) { throw new RuntimeException(e); }
        on(s, 1, 2, 8);
        s.activeScene = 1;
        return s;
    }

    static void on(HomeState s, int... idx) { for (int i : idx) s.lightOn[i] = true; }

    static Bitmap realArt() {
        try { java.io.File f = new java.io.File("tools/art.png"); if (f.exists()) return new Bitmap(ImageIO.read(f)); } catch (Exception e) {}
        return fakeArt();
    }

    static Bitmap fakeArt() {
        Bitmap b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Graphics2D g = b.img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xE6BCB6), 200, 200, new Color(0x456A78)));
        g.fillRect(0, 0, 200, 200);
        return b;
    }

    static void save(Bitmap b, String path) throws Exception {
        ImageIO.write(b.img, "png", new File(path));
        System.out.println(path);
    }
}
