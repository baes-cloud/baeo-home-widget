package com.baeo.widgets;

import android.graphics.Bitmap;

/** Everything the Bæo Home widget draws. No Android services in here, so it renders on a desktop too. */
public final class HomeState {
    public static final int DOOR_LOCKED = 0, DOOR_ARMED = 1, DOOR_UNLOCKED = 2;

    /** The lights on the floorplan: entity, x% and y% on floorplan.png, icon. Tap zones follow these. */
    public static final String[] FX_ID = {
        "light.hue_play", "light.tv_art_lights", "light.art_group", "light.bar_spotlights",
        "light.couch", "light.downlights", "light.kitchen_console_candles", "light.club_led_group",
        "light.bedroom_cupboard_light", "light.kitchen_group", "light.bedroom_led", "light.office_lights",
        "light.wardrobes", "light.bathroom_downlights", "light.bedroom_lights", "light.bedlamps",
        "fan.mirror_ball"
    };
    public static final float[][] FX_POS = {
        {16f, 10f}, {45f, 12f}, {64f, 9f}, {84f, 17f},
        {49f, 29f}, {65f, 29f}, {90f, 36f}, {13f, 46f},
        {51f, 54f}, {73f, 54f}, {29f, 70f}, {93.5f, 82f},
        {76f, 86f}, {56.5f, 82f}, {19f, 89f}, {39f, 89f},
        {94.5f, 55.5f}   // disco ball button by the office door (2026-11-22)
    };
    public static final String[] FX_KIND = {
        "tree", "art", "art", "spot", "sofa", "bulb", "candles", "led",
        "art", "bulb", "bulb", "desk", "shirt", "bulb", "lamp", "lamp",
        "disco"
    };

    public String dayName = "Sun";
    public String dateRest = "15 Nov";

    public int temp = 14;
    public String cond = "cloudy";
    public String condLabel = "Cloudy";
    public int low = 10, high = 16;
    public String[] fcDay = new String[0];
    public String[] fcCond = new String[0];
    public int[] fcHi = new int[0];
    public int[] fcLo = new int[0];
    /** Work calendar's first event on each forecast day, "8:30 WFH" ("" = nothing). */
    public String[] fcEvent = new String[0];
    /** Forecast days' dates, "23 Nov". */
    public String[] fcDate = new String[0];

    public String nextEvent = "Nothing coming up";
    public String nextAlarm = "No alarm";
    /** Next workday from sensor.work_start, e.g. "WFH · 9:00". Empty = show nextEvent instead. */
    public String workLine = "";
    public boolean workWfh = false;
    /** Split of workLine for the headline: "Richmond" / "Thu · from 8:30". */
    public String workPlace = "";
    public String workWhen = "";
    /** When the next event is, relative to now: "Today", "Tomorrow", "Thursday", "Next week". */
    public String nextRel = "";
    /** Start time of the next work day, "8:30". */
    public String workTime = "";
    /** Short day of the next work day, "Mon". */
    public String workDay = "";
    /** Inside climate: "20.8", "65", aircon line ("Aircon off" / "Aircon cooling to 22°"). */
    public String insideTemp = "", insideHum = "", airconLine = "";
    public boolean airconOn = false;
    public boolean alarmActive = false;
    public boolean motionOn = true;
    public boolean mirrorOn = false;
    public int door = DOOR_LOCKED;
    /** When the door last changed state (ms), 0 if unknown. */
    public long doorSinceMs = 0L;
    /** Last HA sync (ms), how long /api/states took (ms, -1 unknown) and how many entities came back. */
    public long syncMs = 0L;
    public int haMs = -1, haEntities = 0;
    /** Last sync went over the public URL (away from home). */
    public boolean haCloud = false;

    public String alertId = "";
    public String alertSeverity = "";
    public String alertTitle = "";
    public String alertSub = "";

    public int activeScene = -1;

    public boolean mediaShow = false;
    public boolean mediaPlaying = false;
    public String mediaTitle = "";
    public String mediaArtist = "";
    public float volume = 0f;
    /** Other speakers grouped with the Club, album, and the track's duration / position in seconds. */
    public int mediaGroup = 0, mediaDuration = 0, mediaPosition = 0;
    public String mediaAlbum = "";
    public Bitmap art;
    /** Recent Home Assistant notifications, newest first: time "09:41", source "house-agent", title, message. */
    public String[] feedTime = new String[0], feedSrc = new String[0], feedTitle = new String[0], feedMsg = new String[0];

    public boolean[] lightOn = new boolean[FX_ID.length];
    /** floorplan.png, or null to draw a plain panel. */
    public Bitmap plan;

    public boolean online = true;
    public String lastSync = "";
}
