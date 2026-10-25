package app.omaapp.widgetfixture;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class CiInteractiveWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_CLICK = "app.omaapp.widgetfixture.CLICK";
    private static final String STATE = "ci_widget_clicks";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) {
            update(context, manager, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !ACTION_CLICK.equals(intent.getAction())) {
            return;
        }
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                STATE, Context.MODE_PRIVATE);
        preferences.edit().putInt(Integer.toString(id),
                preferences.getInt(Integer.toString(id), 0) + 1).commit();
        update(context, AppWidgetManager.getInstance(context), id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        int clicks = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
                .getInt(Integer.toString(id), 0);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ci_widget);
        views.setTextViewText(R.id.ci_widget_value,
                clicks == 0 ? context.getString(R.string.ci_widget_ready)
                        : clicks == 1 ? context.getString(R.string.ci_widget_updated)
                                : context.getString(R.string.ci_widget_updated_twice));
        views.setTextViewText(R.id.ci_widget_button, context.getString(R.string.ci_widget_action));

        Intent click = new Intent(ACTION_CLICK).setClass(context, CiInteractiveWidgetProvider.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, click,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.ci_widget_button, pendingIntent);
        manager.updateAppWidget(id, views);
    }
}
