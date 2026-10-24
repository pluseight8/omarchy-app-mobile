package app.omaapp.launcherfixture;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.net.InetAddress;
import java.util.Collections;

/** Test-only DNS-SD announcer in a different package from OmaApp's discovery client. */
public final class NsdFixtureService extends Service {
    private static final String TAG = "OmaAppNsdFixture";
    private NsdManager manager;
    private NsdManager.RegistrationListener listener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String name = intent == null ? "" : intent.getStringExtra("serviceName");
            String address = intent == null ? "" : intent.getStringExtra("serviceHost");
            String fingerprint = intent == null ? "" : intent.getStringExtra("fingerprint");
            int port = intent == null ? 0 : intent.getIntExtra("servicePort", 0);
            if (name == null || !name.startsWith("OmaApp-") || address == null
                    || fingerprint == null || !fingerprint.matches("[a-fA-F0-9]{64}")
                    || port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid CI DNS-SD fixture parameters.");
            }
            manager = (NsdManager) getSystemService(NSD_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("Android NSD is unavailable.");
            }
            NsdServiceInfo service = new NsdServiceInfo();
            service.setServiceName(name);
            service.setServiceType("_omaapp._tcp.");
            service.setPort(port);
            InetAddress host = InetAddress.getByName(address);
            if (Build.VERSION.SDK_INT >= 34) {
                service.setHostAddresses(Collections.singletonList(host));
            } else {
                service.setHost(host);
            }
            service.setAttribute("fingerprint", fingerprint);
            listener = new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo registered) {
                    Log.i(TAG, "Registered the CI discovery fixture.");
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo failed, int errorCode) {
                    Log.e(TAG, "CI discovery fixture registration failed: " + errorCode);
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo unregistered) {
                    Log.i(TAG, "Unregistered the CI discovery fixture.");
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo failed, int errorCode) {
                    Log.w(TAG, "CI discovery fixture unregistration failed: " + errorCode);
                }
            };
            manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception error) {
            Log.e(TAG, "Could not start the CI DNS-SD fixture.", error);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (manager != null && listener != null) {
            try {
                manager.unregisterService(listener);
            } catch (IllegalArgumentException ignored) {
                // Android may already have removed the announcement after a network change.
            }
        }
        listener = null;
        manager = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
