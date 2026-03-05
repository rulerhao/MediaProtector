package com.rulerhao.media_protector;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.widget.RemoteViews;

import com.rulerhao.media_protector.data.FileConfig;

import java.io.File;

/**
 * Home screen widget that shows the count of protected files.
 */
public class ProtectedCountWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Count protected files
        int count = countProtectedFiles();

        // Build the RemoteViews
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_protected_count);
        views.setTextViewText(R.id.widgetCount, String.valueOf(count));
        views.setTextViewText(R.id.widgetLabel, context.getString(R.string.widget_protected_count, count));

        // Set click to open app
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("shortcut_action", "protected");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent);

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int countProtectedFiles() {
        File protectedFolder = FileConfig.getProtectedFolder();
        if (!protectedFolder.exists() || !protectedFolder.isDirectory()) {
            return 0;
        }

        File[] files = protectedFolder.listFiles((dir, name) ->
                name.endsWith(FileConfig.PROTECTED_EXTENSION));
        return files != null ? files.length : 0;
    }

    @Override
    public void onEnabled(Context context) {
        // Called when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Called when the last widget is removed
    }
}
