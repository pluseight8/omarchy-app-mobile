package app.omaapp;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.ext.SdkExtensions;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Foreground DNS-SD rediscovery for a computer whose TLS identity was paired by QR. */
public final class DeviceDiscovery {
    private static final String SERVICE_TYPE = "_omaapp._tcp.";
    private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();
    private static Context appContext;
    private static NsdManager manager;
    private static NsdManager.DiscoveryListener listener;
    private static WifiManager.MulticastLock multicastLock;
    private static volatile boolean active;

    private DeviceDiscovery() {
    }

    public static synchronized void start(Context context) {
        if (active || context == null || !PairingBridge.hasTrustedDevice(context)) {
            return;
        }
        final Context discoveryContext = context.getApplicationContext();
        final NsdManager discoveryManager = (NsdManager) discoveryContext
                .getSystemService(Context.NSD_SERVICE);
        if (discoveryManager == null) {
            return;
        }
        try {
            acquireLegacyMulticastLock(discoveryContext);
        } catch (RuntimeException error) {
            releaseMulticastLock();
        }
        appContext = discoveryContext;
        manager = discoveryManager;
        active = true;
        listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (!active || service == null || service.getServiceName() == null
                        || !service.getServiceName().startsWith("OmaApp-")) {
                    return;
                }
                String key = service.getServiceName() + "\u001f" + service.getServiceType();
                if (!RESOLVING.add(key)) {
                    return;
                }
                try {
                    discoveryManager.resolveService(service, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo ignored, int errorCode) {
                            RESOLVING.remove(key);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo resolved) {
                            RESOLVING.remove(key);
                            if (!active || resolved == null) {
                                return;
                            }
                            String fingerprint = advertisedFingerprint(resolved.getAttributes());
                            InetAddress address = resolved.getHost();
                            if (fingerprint.isEmpty() || !(address instanceof Inet4Address)
                                    || !address.isSiteLocalAddress()
                                    || resolved.getPort() < 1 || resolved.getPort() > 65535) {
                                return;
                            }
                            PairingBridge.reconnectDiscovered(discoveryContext,
                                    address.getHostAddress(), resolved.getPort(), fingerprint);
                        }
                    });
                } catch (IllegalArgumentException error) {
                    RESOLVING.remove(key);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                if (service != null) {
                    RESOLVING.remove(service.getServiceName() + "\u001f" + service.getServiceType());
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                stop();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                stop();
            }
        };
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (RuntimeException error) {
            stop();
        }
    }

    public static synchronized void stop() {
        active = false;
        RESOLVING.clear();
        if (manager != null && listener != null) {
            try {
                manager.stopServiceDiscovery(listener);
            } catch (IllegalArgumentException ignored) {
                // Discovery may already have stopped after a network change.
            }
        }
        listener = null;
        manager = null;
        appContext = null;
        releaseMulticastLock();
    }

    private static void releaseMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            try {
                multicastLock.release();
            } catch (RuntimeException ignored) {
                // The Wi-Fi service can release platform resources after a network change.
            }
        }
        multicastLock = null;
    }

    private static String advertisedFingerprint(Map<String, byte[]> attributes) {
        if (attributes == null) {
            return "";
        }
        byte[] value = attributes.get("fingerprint");
        if (value == null || value.length != 64) {
            return "";
        }
        String fingerprint = new String(value, StandardCharsets.US_ASCII).toLowerCase(java.util.Locale.ROOT);
        return fingerprint.matches("[a-f0-9]{64}") ? fingerprint : "";
    }

    private static void acquireLegacyMulticastLock(Context context) {
        boolean required = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < 7);
        if (!required) {
            return;
        }
        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            return;
        }
        multicastLock = wifi.createMulticastLock("OmaApp.local-discovery");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();
    }
}
