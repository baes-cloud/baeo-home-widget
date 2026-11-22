package com.baeo.widgets;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The widget's "$" line: a small terminal over the home screen that talks to the house agent.
 * Fixed commands (status, waiting, spotted, done <id>, reopen <id>, help) or a free-text question.
 * Each command and reply also goes into the widget's notify feed.
 */
public class AgentTerminalActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SpannableStringBuilder log = new SpannableStringBuilder();
    private TextView out;
    private ScrollView scroll;
    private EditText in;
    private boolean busy;

    private float dp(float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Typeface mono = Typeface.MONOSPACE, monoB = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) dp(16);
        root.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00C1013);
        bg.setCornerRadius(dp(18));
        bg.setStroke((int) dp(1), 0x598CBDB5);
        root.setBackground(bg);

        TextView head = new TextView(this);
        head.setTypeface(mono);
        head.setTextSize(12);
        head.setTextColor(BaeoHomeRender.SUBTLE);
        head.setText("bae@home: ~/home-handler   (help for commands)");
        root.addView(head);

        scroll = new ScrollView(this);
        out = new TextView(this);
        out.setTypeface(mono);
        out.setTextSize(13);
        out.setTextColor(0xEBEDF2EF);
        out.setLineSpacing(dp(3), 1f);
        out.setTextIsSelectable(true);
        scroll.addView(out);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(220));
        lp.topMargin = (int) dp(8);
        root.addView(scroll, lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView prompt = new TextView(this);
        prompt.setTypeface(monoB);
        prompt.setTextSize(15);
        prompt.setTextColor(BaeoHomeRender.ACCENT);
        prompt.setText("$ ");
        row.addView(prompt);
        in = new EditText(this);
        in.setTypeface(mono);
        in.setTextSize(15);
        in.setTextColor(BaeoHomeRender.INK);
        in.setHintTextColor(0x667E8C89);
        in.setHint("status, waiting, or ask anything");
        in.setBackground(null);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setImeOptions(EditorInfo.IME_ACTION_SEND);
        in.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id == EditorInfo.IME_ACTION_SEND || enter) { send(); return true; }
            return false;
        });
        row.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        setContentView(root);
        ViewGroup.LayoutParams wl = root.getLayoutParams();
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), ViewGroup.LayoutParams.WRAP_CONTENT);
        getWindow().setGravity(Gravity.BOTTOM);
        getWindow().getAttributes().y = (int) dp(24);
        in.requestFocus();
        ui.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(in, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void append(String text, int color) {
        int s = log.length();
        log.append(text).append('\n');
        log.setSpan(new ForegroundColorSpan(color), s, log.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setText(log);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void send() {
        final String text = in.getText().toString().trim();
        if (text.isEmpty() || busy) return;
        in.setText("");
        busy = true;
        append("$ " + text, BaeoHomeRender.ACCENT);
        append("…", BaeoHomeRender.SUBTLE);
        BaeoNotifyListener.addLocal(this, "you", "$ " + text);
        new Thread(() -> {
            String[] lines;
            int col = BaeoHomeRender.ROSE;
            try {
                JSONObject res = post(text);
                JSONArray a = res.optJSONArray("lines");
                lines = new String[a == null ? 0 : a.length()];
                for (int i = 0; i < lines.length; i++) lines[i] = a.optString(i);
                if ("error".equals(res.optString("kind"))) col = BaeoHomeRender.WARN;
            } catch (Throwable t) {
                lines = new String[]{"can't reach home-handler (" + t.getClass().getSimpleName() + ")"};
                col = BaeoHomeRender.BAD;
            }
            final String[] fl = lines;
            final int fc = col;
            ui.post(() -> {
                int n = log.length();
                if (n >= 2) log.delete(n - 2, n);   // drop the "…" line
                for (String l : fl) append(l, fc);
                BaeoNotifyListener.addLocal(this, "home-handler", String.join(" · ", fl));
                BaeoBriefWidgetProvider.updateAll(this);
                busy = false;
            });
        }).start();
    }

    /** Home LAN first; if that can't connect, through Cloudflare with the widget's Access service token. */
    private static JSONObject post(String text) throws Exception {
        try {
            return post(text, false);
        } catch (java.io.IOException e) {
            if (Secrets.AGENT_CLOUD_URL.isEmpty() || Secrets.CF_ID.isEmpty()) throw e;
            return post(text, true);
        }
    }

    private static JSONObject post(String text, boolean cloud) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL((cloud ? Secrets.AGENT_CLOUD_URL : Secrets.AGENT_URL)
                + "/api/command").openConnection();
        if (cloud) {
            c.setRequestProperty("CF-Access-Client-Id", Secrets.CF_ID);
            c.setRequestProperty("CF-Access-Client-Secret", Secrets.CF_SECRET);
        }
        c.setConnectTimeout(cloud ? 8000 : 3000);
        c.setReadTimeout(130_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + Secrets.AGENT_TOKEN);
        c.setRequestProperty("X-Requested-With", "houseagent");
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(new JSONObject().put("text", text).toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code != 200) throw new IllegalStateException("HTTP " + code);
        try (InputStream is = c.getInputStream()) {
            return new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
