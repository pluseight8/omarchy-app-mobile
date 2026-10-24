package app.omaapp;

import org.qtproject.qt.android.bindings.QtActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Qt launcher activity that keeps device sync active only in the foreground. */
public final class OmaAppActivity extends QtActivity {
    private static native void refreshLauncherApps();

    private final Handler launcherRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshLauncherAfterPackageChange = OmaAppActivity::refreshLauncherApps;
    private boolean packageReceiverRegistered;

    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !isLauncherPackageChange(intent.getAction())) {
                return;
            }
            Uri packageUri = intent.getData();
            if (packageUri == null || !"package".equals(packageUri.getScheme())
                    || packageUri.getSchemeSpecificPart() == null
                    || packageUri.getSchemeSpecificPart().isEmpty()) {
                return;
            }

            // Install/replace broadcasts can arrive in pairs. Coalesce them before
            // querying PackageManager so the launcher model is reset only once.
            launcherRefreshHandler.removeCallbacks(refreshLauncherAfterPackageChange);
            launcherRefreshHandler.postDelayed(refreshLauncherAfterPackageChange, 250L);
        }
    };

    private static boolean isLauncherPackageChange(String action) {
        return Intent.ACTION_PACKAGE_ADDED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || Intent.ACTION_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Package broadcasts are protected system broadcasts. Exported
            // registration is required to receive them on modern Android.
            registerReceiver(packageChanges, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageChanges, filter);
        }
        packageReceiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        PairingBridge.setRealtimeSync(this, true);
        DeviceDiscovery.start(this);
        launcherRefreshHandler.removeCallbacks(refreshLauncherAfterPackageChange);
        refreshLauncherApps();
    }

    @Override
    protected void onPause() {
        PairingBridge.setRealtimeSync(this, false);
        DeviceDiscovery.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        launcherRefreshHandler.removeCallbacks(refreshLauncherAfterPackageChange);
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChanges);
            packageReceiverRegistered = false;
        }
        super.onDestroy();
    }
}
