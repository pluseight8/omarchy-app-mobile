package app.omaapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Real RemoteViews provider injected only into emulator builds for AppWidgetHost tests. */
public final class CiAppWidgetProvider extends AppWidgetProvider {
    static final String ACTION_WIDGET_TAPPED = "app.omaapp.ci.WIDGET_TAPPED";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateWidgets(context, manager, ids, "CI live Android widget");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !ACTION_WIDGET_TAPPED.equals(intent.getAction())) {
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CiAppWidgetProvider.class));
        updateWidgets(context, manager, ids, "Widget tap received by Android provider");
    }

    private static void updateWidgets(Context context, AppWidgetManager manager,
            int[] ids, String label) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.omaapp_ci_widget);
            views.setTextViewText(R.id.widget_label, label);
            Intent tap = new Intent(context, CiAppWidgetProvider.class)
                    .setAction(ACTION_WIDGET_TAPPED)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent);
            manager.updateAppWidget(id, views);
        }
    }
}
