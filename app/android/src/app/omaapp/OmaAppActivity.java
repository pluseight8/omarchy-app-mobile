package app.omaapp;

import android.content.Intent;
import android.os.Bundle;

import org.qtproject.qt.android.bindings.QtActivity;

/** Qt launcher activity that keeps device sync active only in the foreground. */
public final class OmaAppActivity extends QtActivity {
    private static native void notifyNativeAndroidWidgetsChanged();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidWidgetHost.restoreInstanceState(savedInstanceState);
        AndroidWidgetHost.attach(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        AndroidWidgetHost.onStart(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AndroidWidgetHost.attach(this);
        notifyAndroidWidgetsChanged();
        PairingBridge.setRealtimeSync(this, true);
    }

    @Override
    protected void onPause() {
        PairingBridge.setRealtimeSync(this, false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        AndroidWidgetHost.onStop(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        AndroidWidgetHost.saveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        AndroidWidgetHost.detach(this);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        AndroidWidgetHost.onActivityResult(this, requestCode, resultCode, data);
    }

    static void notifyAndroidWidgetsChanged() {
        try {
            notifyNativeAndroidWidgetsChanged();
        } catch (UnsatisfiedLinkError error) {
            // QML may not have loaded its native library during the first Activity resume.
        }
    }
}
