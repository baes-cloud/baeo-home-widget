package com.baeo.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** 1x1 Sea glass tile that opens the Android Terminal (Debian VM) app. Pixel Launcher has no custom icons. */
public class TerminalTileProvider extends AppWidgetProvider {
    static final String TARGET = "com.android.virtualization.terminal";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(TARGET);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_tile);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(android.R.id.background, pi);
        }
        for (int id : ids) mgr.updateAppWidget(id, v);
    }
}
