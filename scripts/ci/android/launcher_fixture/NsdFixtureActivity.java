package app.omaapp.launcherfixture;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/** Starts or stops the CI DNS-SD service while Android considers this fixture foreground. */
public final class NsdFixtureActivity extends Activity {
    private static final String STOP_ACTION = "app.omaapp.launcherfixture.STOP_NSD_FIXTURE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (STOP_ACTION.equals(getIntent().getAction())) {
            stopService(new Intent(this, NsdFixtureService.class));
        } else {
            Intent service = new Intent(this, NsdFixtureService.class);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                service.putExtras(extras);
            }
            startService(service);
        }

        Intent omaApp = new Intent().setComponent(new ComponentName(
                "app.omaapp", "app.omaapp.OmaAppActivity"));
        omaApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(omaApp);
        finish();
    }
}
