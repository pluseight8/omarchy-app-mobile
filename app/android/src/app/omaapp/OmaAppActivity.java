package app.omaapp;

import org.qtproject.qt.android.bindings.QtActivity;

/** Qt launcher activity that keeps device sync active only in the foreground. */
public final class OmaAppActivity extends QtActivity {
    @Override
    protected void onResume() {
        super.onResume();
        PairingBridge.setRealtimeSync(this, true);
    }

    @Override
    protected void onPause() {
        PairingBridge.setRealtimeSync(this, false);
        super.onPause();
    }
}
