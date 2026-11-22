package com.baeo.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Preview, pin, sync and the Home Assistant connection. */
public class MainActivity extends Activity {
    private ImageView preview;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        if (win != null) {
            win.setStatusBarColor(BaeoHomeRender.DEEP);
            win.setNavigationBarColor(BaeoHomeRender.DEEP);
        }
        BaeoHomeRender.Fonts f = BaeoBriefWidgetProvider.fonts(this);
        SharedPreferences sp = HaClient.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BaeoHomeRender.BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("BÆO HOME");
        title.setTypeface(f.syne700);
        title.setTextColor(BaeoHomeRender.INK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setLetterSpacing(0.24f);
        root.addView(title);

        TextView sub = label("One widget for the whole flat. Sized for five columns by three rows; long-press it on the home screen to resize.", f.m500, BaeoHomeRender.MUTE, 13.5f);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.bottomMargin = dp(14);
        root.addView(preview, pl);

        root.addView(button("Add Bæo Home to the home screen", BaeoHomeRender.ACCENT, BaeoHomeRender.ON_ACCENT, f.m700, v -> {
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            if (mgr != null && mgr.isRequestPinAppWidgetSupported()) {
                mgr.requestPinAppWidget(new ComponentName(this, BaeoBriefWidgetProvider.class), null, null);
            } else {
                Toast.makeText(this, "Long-press the home screen, Widgets, BæoRemote", Toast.LENGTH_LONG).show();
            }
        }));
        root.addView(space(10));
        root.addView(button("Set Bæocom wallpaper", BaeoHomeRender.PANEL_2, BaeoHomeRender.INK, f.m600, v -> setWallpapers()));
        root.addView(space(10));
        root.addView(button("Sync now", BaeoHomeRender.PANEL_2, BaeoHomeRender.ACCENT, f.m600, v -> sync()));

        root.addView(space(26));
        root.addView(label("HOME ASSISTANT", f.m700, BaeoHomeRender.ACCENT, 11.5f));
        root.addView(space(8));
        final EditText url = field("Address (blank = built-in)", sp.getString("ha_url", ""), false, f.m500);
        root.addView(url);
        root.addView(space(8));
        final EditText token = field(sp.getString("ha_token", "").isEmpty()
                ? "Long-lived token (blank = built-in)" : "Token saved. Paste a new one to replace it", "", true, f.m500);
        root.addView(token);
        root.addView(space(10));
        root.addView(button("Save connection", BaeoHomeRender.PANEL_2, BaeoHomeRender.INK, f.m600, v -> {
            SharedPreferences.Editor ed = sp.edit().putString("ha_url", url.getText().toString().trim());
            String t = token.getText().toString().trim();
            if (!t.isEmpty()) ed.putString("ha_token", t);
            ed.apply();
            token.setText("");
            sync();
        }));

        setContentView(scroll);
        if (getIntent() != null && getIntent().getBooleanExtra("set_wallpaper", false)) setWallpapers();
        if (getIntent() != null && getIntent().getBooleanExtra("pin_terminal", false)) pinTerminal();
        refreshPreview();
        sync();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("set_wallpaper", false)) setWallpapers();
        if (intent != null && intent.getBooleanExtra("pin_terminal", false)) pinTerminal();
    }

    /** Home gets the plain graphite gradient (the widget carries the mark); the lock screen keeps BÆOCOM centred. */
    private void setWallpapers() {
        new Thread(() -> {
            String msg = "Wallpaper set";
            try {
                android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(this);
                try (java.io.InputStream in = getAssets().open("wallpaper_home.jpg")) {
                    wm.setStream(in, null, true, android.app.WallpaperManager.FLAG_SYSTEM);
                }
                try (java.io.InputStream in = getAssets().open("wallpaper_lock.jpg")) {
                    wm.setStream(in, null, true, android.app.WallpaperManager.FLAG_LOCK);
                }
            } catch (Throwable t) {
                msg = "Couldn't set the wallpaper";
            }
            final String m = msg;
            ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void sync() {
        HaClient.refreshAsync(this, null);
        ui.postDelayed(this::refreshPreview, 1500);
        ui.postDelayed(() -> {
            refreshPreview();
            if (!HaClient.prefs(this).getBoolean("online", true)) {
                Toast.makeText(this, "Can't reach Home Assistant", Toast.LENGTH_SHORT).show();
            }
        }, 5000);
    }

    private void refreshPreview() {
        if (preview != null) {
            preview.setImageBitmap(BaeoHomeRender.render(HaClient.state(this), BaeoBriefWidgetProvider.fonts(this), 980, 823));
        }
    }

    private TextView label(String text, Typeface tf, int color, float sp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTypeface(tf);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return tv;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private GradientDrawable box(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(1), BaeoHomeRender.LINE);
        return gd;
    }

    private EditText field(String hint, String value, boolean secret, Typeface tf) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTypeface(tf);
        e.setSingleLine(true);
        e.setTextColor(BaeoHomeRender.INK);
        e.setHintTextColor(BaeoHomeRender.SUBTLE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        e.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(box(BaeoHomeRender.PANEL));
        return e;
    }

    private TextView button(String text, int bg, int fg, Typeface tf, View.OnClickListener onClick) {
        TextView b = label(text, tf, fg, 14f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(box(bg));
        b.setOnClickListener(onClick);
        return b;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Asks the launcher to place the 1x1 Terminal tile (adb: am start ... --ez pin_terminal true). */
    private void pinTerminal() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (mgr != null && mgr.isRequestPinAppWidgetSupported())
            mgr.requestPinAppWidget(new ComponentName(this, TerminalTileProvider.class), null, null);
    }
}
