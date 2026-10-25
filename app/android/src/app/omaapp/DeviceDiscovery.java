package app.omaapp;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.util.Log;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Foreground DNS-SD rediscovery for a computer whose TLS identity was paired by QR. */
public final class DeviceDiscovery {
    private static final String SERVICE_TYPE = "_omaapp._tcp.";
    private static final String TAG = "OmaAppDiscovery";
    private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();
    private static final Map<String, NsdManager.ServiceInfoCallback> SERVICE_CALLBACKS =
            new ConcurrentHashMap<>();
    private static NsdManager manager;
    private static NsdManager.DiscoveryListener listener;
    private static WifiManager.MulticastLock multicastLock;
    private static volatile boolean active;
    private static volatile long generation;

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
        final long discoveryGeneration = ++generation;
        try {
            acquireLegacyMulticastLock(discoveryContext);
        } catch (RuntimeException error) {
            releaseMulticastLock();
        }
        manager = discoveryManager;
        active = true;
        listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (!isCurrent(discoveryGeneration) || service == null) {
                    return;
                }
                String serviceName = service.getServiceName();
                if (!matchesTrustedServiceName(discoveryContext, serviceName)) {
                    debugLog(discoveryContext, "Ignored a DNS-SD name without the saved certificate hint.");
                    return;
                }
                debugLog(discoveryContext, "DNS-SD candidate found.");
                String key = discoveryGeneration + "\u001f" + serviceName
                        + "\u001f" + service.getServiceType();
                if (!RESOLVING.add(key)) {
                    return;
                }
                if (supportsServiceInfoUpdates()) {
                    registerServiceUpdates(discoveryManager, discoveryContext, service, key,
                            discoveryGeneration);
                    return;
                }
                resolveServiceLegacy(discoveryManager, service, discoveryContext, key,
                        discoveryGeneration);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                if (service != null) {
                    String key = discoveryGeneration + "\u001f" + service.getServiceName()
                            + "\u001f" + service.getServiceType();
                    if (!SERVICE_CALLBACKS.containsKey(key)) {
                        RESOLVING.remove(key);
                    }
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                stopAfterPlatformFailure(discoveryGeneration, discoveryContext,
                        "DNS-SD discovery start failed with platform code " + errorCode + ".");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                stopAfterPlatformFailure(discoveryGeneration, discoveryContext,
                        "DNS-SD discovery stop failed with platform code " + errorCode + ".");
            }
        };
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (RuntimeException error) {
            stop();
        }
    }

    public static synchronized void stop() {
        stopInternal(true);
    }

    // Serialize registration with stop() so an old callback cannot be installed after cleanup.
    private static synchronized void registerServiceUpdates(NsdManager discoveryManager,
                                                            Context context,
                                                            NsdServiceInfo service,
                                                            String key,
                                                            long discoveryGeneration) {
        if (!isCurrent(discoveryGeneration)) {
            RESOLVING.remove(key);
            return;
        }
        NsdManager.ServiceInfoCallback callback = new NsdManager.ServiceInfoCallback() {
            @Override
            public void onServiceUpdated(NsdServiceInfo updated) {
                handleResolved(context, discoveryGeneration, updated);
            }

            @Override
            public void onServiceInfoCallbackRegistrationFailed(int errorCode) {
                SERVICE_CALLBACKS.remove(key, this);
                if (isCurrent(discoveryGeneration)) {
                    debugLog(context, "DNS-SD service update registration failed with platform code "
                            + errorCode + "; using the legacy resolver.");
                    resolveServiceLegacy(discoveryManager, service, context, key, discoveryGeneration);
                } else {
                    RESOLVING.remove(key);
                }
            }

            @Override
            public void onServiceInfoCallbackUnregistered() {
                SERVICE_CALLBACKS.remove(key, this);
                RESOLVING.remove(key);
            }

            @Override
            public void onServiceLost() {
                debugLog(context, "DNS-SD service is temporarily unavailable.");
            }
        };
        SERVICE_CALLBACKS.put(key, callback);
        try {
            discoveryManager.registerServiceInfoCallback(service, Runnable::run, callback);
        } catch (RuntimeException error) {
            SERVICE_CALLBACKS.remove(key, callback);
            if (isCurrent(discoveryGeneration)) {
                debugLog(context, "DNS-SD service update registration could not start; using the legacy resolver.");
                resolveServiceLegacy(discoveryManager, service, context, key, discoveryGeneration);
            } else {
                RESOLVING.remove(key);
            }
        }
    }

    // Serialize result delivery with stop() so stale generations cannot launch new reconnects.
    private static synchronized void handleResolved(Context context, long discoveryGeneration,
                                                    NsdServiceInfo resolved) {
        if (!isCurrent(discoveryGeneration) || resolved == null) {
            return;
        }
        String fingerprint = advertisedFingerprint(resolved.getAttributes());
        List<String> candidateAddresses = siteLocalIpv4Addresses(resolved);
        int addressCount = supportsServiceInfoUpdates()
                ? resolved.getHostAddresses().size() : candidateAddresses.size();
        if (fingerprint.isEmpty() || candidateAddresses.isEmpty()
                || resolved.getPort() < 1 || resolved.getPort() > 65535) {
            debugLog(context, "DNS-SD candidate resolved but failed validation: "
                    + "addresses=" + addressCount
                    + ", privateIpv4=" + candidateAddresses.size()
                    + ", fingerprint=" + !fingerprint.isEmpty()
                    + ", port=" + (resolved.getPort() >= 1 && resolved.getPort() <= 65535)
                    + ".");
            return;
        }
        debugLog(context, "DNS-SD candidate resolved with "
                + addressCount + " address(es) and a valid trust hint.");
        PairingBridge.reconnectDiscovered(context, candidateAddresses,
                resolved.getPort(), fingerprint);
    }

    private static synchronized void stopAfterPlatformFailure(long failedGeneration, Context context,
                                                              String message) {
        if (!isCurrent(failedGeneration)) {
            return;
        }
        debugLog(context, message);
        stopInternal(false);
    }

    private static synchronized void stopInternal(boolean requestPlatformStop) {
        active = false;
        ++generation;
        RESOLVING.clear();
        NsdManager oldManager = manager;
        NsdManager.DiscoveryListener oldListener = listener;
        manager = null;
        listener = null;
        if (oldManager != null) {
            for (NsdManager.ServiceInfoCallback callback : SERVICE_CALLBACKS.values()) {
                try {
                    oldManager.unregisterServiceInfoCallback(callback);
                } catch (RuntimeException ignored) {
                    // The platform may have already ended a service callback on network change.
                }
            }
        }
        SERVICE_CALLBACKS.clear();
        if (requestPlatformStop && oldManager != null && oldListener != null) {
            try {
                oldManager.stopServiceDiscovery(oldListener);
            } catch (IllegalArgumentException ignored) {
                // Discovery may already have stopped after a network change.
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop DNS-SD discovery.", error);
            }
        }
        releaseMulticastLock();
    }

    private static boolean isCurrent(long expectedGeneration) {
        synchronized (DeviceDiscovery.class) {
            return active && generation == expectedGeneration;
        }
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

    @SuppressWarnings("deprecation")
    private static void resolveServiceLegacy(NsdManager manager, NsdServiceInfo service,
                                             Context context, String key, long discoveryGeneration) {
        try {
            manager.resolveService(service, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo ignored, int errorCode) {
                    RESOLVING.remove(key);
                    if (isCurrent(discoveryGeneration)) {
                        debugLog(context, "DNS-SD resolve failed with platform code " + errorCode + ".");
                    }
                }

                @Override
                public void onServiceResolved(NsdServiceInfo resolved) {
                    RESOLVING.remove(key);
                    handleResolved(context, discoveryGeneration, resolved);
                }
            });
        } catch (RuntimeException error) {
            RESOLVING.remove(key);
            if (isCurrent(discoveryGeneration)) {
                debugLog(context, "DNS-SD legacy resolution could not start.");
            }
        }
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

    /** The short name is only a prefilter; the full TLS certificate and token are checked later. */
    private static boolean matchesTrustedServiceName(Context context, String serviceName) {
        if (context == null || serviceName == null
                || !serviceName.matches("OmaApp-[a-fA-F0-9]{12}")) {
            return false;
        }
        JSONObject credentials = PairingStore.readCredential(context);
        if (credentials == null || credentials.optString("token", "").isEmpty()) {
            return false;
        }
        String fingerprint = credentials.optString("fingerprint", "");
        return fingerprint.matches("[a-fA-F0-9]{64}")
                && serviceName.equalsIgnoreCase("OmaApp-" + fingerprint.substring(0, 12));
    }

    private static List<String> siteLocalIpv4Addresses(NsdServiceInfo service) {
        List<String> result = new ArrayList<>();
        if (supportsServiceInfoUpdates()) {
            for (InetAddress address : service.getHostAddresses()) {
                if (address instanceof Inet4Address && address.isSiteLocalAddress()
                        && !result.contains(address.getHostAddress())) {
                    result.add(address.getHostAddress());
                }
            }
            return result;
        }
        InetAddress address = service.getHost();
        if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
            result.add(address.getHostAddress());
        }
        return result;
    }

    private static void debugLog(Context context, String message) {
        if (context != null && (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.i(TAG, message);
        }
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

    private static boolean supportsServiceInfoUpdates() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7);
    }
}
