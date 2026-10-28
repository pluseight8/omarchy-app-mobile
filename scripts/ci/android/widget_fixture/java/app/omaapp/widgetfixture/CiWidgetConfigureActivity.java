package app.omaapp.widgetfixture;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/** Test-only configuration screen that follows the platform AppWidgetHost result contract. */
public final class CiWidgetConfigureActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(Activity.RESULT_CANCELED, result);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        Button configure = new Button(this);
        configure.setText("CI_WIDGET_CONFIGURE");
        setContentView(configure);
        configure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(Activity.RESULT_OK, new Intent()
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
                finish();
            }
        });
    }
}
