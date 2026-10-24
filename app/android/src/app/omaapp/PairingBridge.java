package app.omaapp;

import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class PairingBridge {
    public static final String PREFERENCES = "omaapp_pairing";

    private static final String PHASE_KEY = "phase";
    private static final String HOST_NAME_KEY = "host_name";
    private static final String LAST_ERROR_KEY = "last_error";
    private static final String THEME_CACHE_KEY = "theme_cache";
    private static final String UPDATED_AT_KEY = "updated_at";
    static final String LAST_CONTACT_KEY = "last_contact";
    private static final String WALLPAPER_PATH_KEY = "wallpaper_path";
    private static final String WALLPAPER_ID_KEY = "wallpaper_id";
    private static final String FEATURES_CACHE_KEY = "features_cache";
    private static final String FEATURE_MESSAGE_KEY = "feature_message";
    private static final int MAX_JSON_BYTES = 128 * 1024;
    private static final int MAX_CLIPBOARD_BYTES = 64 * 1024;
    private static final int MAX_CLIPBOARD_JSON_BYTES = MAX_CLIPBOARD_BYTES * 6 + 1024;
    private static final int MAX_WALLPAPER_BYTES = 30 * 1024 * 1024;
    private static final long PAIRING_TIMEOUT_MILLIS = 180_000L;
    private static final long CONNECTED_WINDOW_MILLIS = 45_000L;
    private static final int DEFAULT_REQUEST_READ_TIMEOUT_MILLIS = 10_000;
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final ExecutorService EVENTS = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object OPERATION_LOCK = new Object();
    private static final AtomicBoolean REALTIME_SYNC_ENABLED = new AtomicBoolean(false);
    private static final AtomicBoolean EVENT_LOOP_RUNNING = new AtomicBoolean(false);
    private static final EventRefreshQueue EVENT_REFRESH_QUEUE = new EventRefreshQueue();
    private static boolean operationRunning;
    private static volatile boolean cancelRequested;
    private static long lastRefreshRequested;
    private static long lastDiscoveredRefresh;
    private static boolean discoveredRetryQueued;
    private static List<String> queuedDiscoveryAddresses;
    private static int queuedDiscoveryPort;
    private static String queuedDiscoveryFingerprint;
    private static volatile HttpsURLConnection eventConnection;

    private PairingBridge() {}

    public static void startQrScanner(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, QrScannerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startFileTransfer(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, FileTransferActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void pushClipboard(Context context) {
        if (context == null) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            setFeatureMessage(context, "There is no text in the phone clipboard.");
            return;
        }
        CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
        if (value == null || value.toString().getBytes(StandardCharsets.UTF_8).length > MAX_CLIPBOARD_BYTES) {
            setFeatureMessage(context, "Clipboard text is empty or exceeds 64 KiB.");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("text", value.toString());
        } catch (JSONException error) {
            setFeatureMessage(context, "Could not read phone clipboard text.");
            return;
        }
        runFeatureRequest(context, "/v1/clipboard", "POST", body, true);
    }

    public static void pullClipboard(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        JSONObject credentials = PairingStore.readCredential(appContext);
        if (credentials == null || !credentials.has("token")) {
            setFeatureMessage(appContext, "Pair OmaApp with a computer first.");
            return;
        }
        setFeatureMessage(appContext, "Reading computer clipboard…");
        NETWORK.execute(() -> {
            try {
                HttpResult response = executeJson(credentials, "/v1/clipboard", "GET", null, true);
                if (response.status != 200) {
                    throw new IllegalStateException(featureError(response));
                }
                String text = response.body.getString("text");
                if (text.getBytes(StandardCharsets.UTF_8).length > MAX_CLIPBOARD_BYTES) {
                    throw new IllegalStateException("The computer clipboard exceeds 64 KiB.");
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    ClipboardManager clipboard = (ClipboardManager) appContext
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard == null) {
                        setFeatureMessage(appContext, "Android clipboard is unavailable.");
                    } else {
                        ClipData clip = ClipData.newPlainText("Omarchy computer", text);
                        PersistableBundle extras = new PersistableBundle();
                        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                        clip.getDescription().setExtras(extras);
                        clipboard.setPrimaryClip(clip);
                        setFeatureMessage(appContext, "Computer text copied to the phone clipboard.");
                    }
                });
            } catch (Exception error) {
                setFeatureMessage(appContext, safeMessage(error));
            }
        });
    }

    public static void activateWorkspace(Context context, int workspaceId) {
        if (context == null || workspaceId < 0 || workspaceId > 1000) {
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("workspaceId", workspaceId);
        } catch (JSONException error) {
            return;
        }
        runFeatureRequest(context, "/v1/workspaces/activate", "POST", body, false);
    }

    public static void mediaAction(Context context, String player, String action) {
        if (context == null || player == null || action == null
                || !player.matches("[A-Za-z0-9_.-]{1,128}")
                || !action.matches("play-pause|play|pause|next|previous|stop")) {
            setFeatureMessage(context, "This media action is not allowed.");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("player", player);
            body.put("action", action);
        } catch (JSONException error) {
            return;
        }
        runFeatureRequest(context, "/v1/media", "POST", body, false);
    }

    public static void executeCommand(Context context, String commandId) {
        if (context == null || commandId == null
                || !commandId.matches("[a-z][a-z0-9-]{0,47}")) {
            setFeatureMessage(context, "This computer command is not configured.");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("commandId", commandId);
        } catch (JSONException error) {
            return;
        }
        runFeatureRequest(context, "/v1/commands/execute", "POST", body, false);
    }

    static void uploadFile(Context context, Uri uri, String displayName, long size) {
        if (context == null || uri == null || displayName == null || size < 1
                || size > 100L * 1024L * 1024L
                || !displayName.matches("[A-Za-z0-9][A-Za-z0-9 ._()@+-]{0,159}")) {
            setFeatureMessage(context, "The selected file name or size is not supported.");
            return;
        }
        Context appContext = context.getApplicationContext();
        JSONObject credentials = PairingStore.readCredential(appContext);
        if (credentials == null || !credentials.has("token")) {
            setFeatureMessage(appContext, "Pair OmaApp with a computer first.");
            return;
        }
        setFeatureMessage(appContext, "Sending " + displayName + " to the computer…");
        NETWORK.execute(() -> {
            HttpsURLConnection connection = null;
            try {
                String encodedName = java.net.URLEncoder.encode(displayName, "UTF-8").replace("+", "%20");
                connection = openConnection(credentials, "/v1/files", "POST", true);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(size);
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setRequestProperty("X-OmaApp-File-Name", encodedName);
                try (InputStream input = appContext.getContentResolver().openInputStream(uri);
                     java.io.OutputStream output = connection.getOutputStream()) {
                    if (input == null) {
                        throw new IllegalStateException("Android could not open this file.");
                    }
                    byte[] buffer = new byte[16 * 1024];
                    long copied = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        copied += count;
                        if (copied > size || copied > 100L * 1024L * 1024L) {
                            throw new IllegalArgumentException("The selected file changed while sending.");
                        }
                        output.write(buffer, 0, count);
                    }
                    if (copied != size) {
                        throw new IllegalArgumentException("The selected file changed while sending.");
                    }
                }
                int code = connection.getResponseCode();
                if (code != 201) {
                    throw new IllegalStateException("Computer rejected the file (HTTP " + code + ").");
                }
                setFeatureMessage(appContext, "Sent " + displayName + " to Downloads/OmaApp.");
            } catch (Exception error) {
                setFeatureMessage(appContext, safeMessage(error));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static String consumeScannedQr(Context context) {
        if (context == null) {
            return "";
        }
        return PairingStore.consumePendingScan(context);
    }

    public static void beginPairing(Context context, String rawQr) {
        if (context == null || rawQr == null || rawQr.length() > 2048) {
            setUiState(context, "error", "", "The pairing QR code is invalid.");
            return;
        }
        final Context appContext = context.getApplicationContext();
        final JSONObject request;
        try {
            request = validatePairingQr(rawQr);
        } catch (Exception error) {
            setUiState(appContext, "error", "", "This is not a valid, unexpired OmaApp QR code.");
            return;
        }
        final JSONObject existing = PairingStore.readCredential(appContext);
        final boolean reconnect = existing != null && existing.has("token");
        if (reconnect && !request.optString("fingerprint").equalsIgnoreCase(
                existing.optString("fingerprint"))) {
            setUiState(appContext, "error", request.optString("hostName"),
                    "This QR belongs to a different computer identity. Forget the old computer before trusting a new one.");
            return;
        }

        synchronized (OPERATION_LOCK) {
            if (operationRunning) {
                return;
            }
            operationRunning = true;
            cancelRequested = false;
        }
        setUiState(appContext, "requesting", request.optString("hostName"), "");
        NETWORK.execute(() -> {
            try {
                if (reconnect) {
                    reconnectKnownDevice(appContext, request, existing);
                } else {
                    submitPairingRequest(appContext, request);
                }
            } catch (Exception error) {
                JSONObject saved = PairingStore.readCredential(appContext);
                if (saved != null && saved.has("token")) {
                    setUiState(appContext, "offline", saved.optString("hostName"),
                            "Pairing finished, but the computer could not send its first theme update.");
                } else {
                    PairingStore.clearCredential(appContext);
                    setUiState(appContext, "error", request.optString("hostName"),
                            "Could not connect. Check that both devices use the same local network.");
                }
            } finally {
                synchronized (OPERATION_LOCK) {
                    operationRunning = false;
                    cancelRequested = false;
                }
            }
        });
    }

    public static void cancelPairing(Context context) {
        if (context == null) {
            return;
        }
        cancelRequested = true;
        JSONObject credentials = PairingStore.readCredential(context);
        if (credentials == null || !credentials.has("requestId")) {
            PairingStore.clearCredential(context);
            setUiState(context, "unpaired", "", "");
        }
    }

    public static void refresh(Context context) {
        if (context == null || System.currentTimeMillis() - lastRefreshRequested < 30_000L) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final JSONObject credentials = PairingStore.readCredential(appContext);
        if (credentials == null) {
            return;
        }
        synchronized (OPERATION_LOCK) {
            if (operationRunning) {
                return;
            }
            operationRunning = true;
            lastRefreshRequested = System.currentTimeMillis();
        }
        NETWORK.execute(() -> {
            try {
                if (credentials.has("requestId")) {
                    pollPairingOnce(appContext, credentials);
                } else if (credentials.has("token")) {
                    acknowledgePairing(appContext, credentials);
                    fetchStatus(appContext, credentials);
                }
            } catch (Exception error) {
                setUiState(appContext, "offline", credentials.optString("hostName"),
                        "The paired computer is not reachable. Showing the last saved theme.");
            } finally {
                synchronized (OPERATION_LOCK) {
                    operationRunning = false;
                }
            }
        });
    }

    public static void retry(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        JSONObject credentials = PairingStore.readCredential(appContext);
        if (credentials == null || !credentials.has("token")) {
            return;
        }
        synchronized (OPERATION_LOCK) {
            if (operationRunning) {
                return;
            }
            operationRunning = true;
            lastRefreshRequested = System.currentTimeMillis();
        }
        setUiState(appContext, "connecting", credentials.optString("hostName"), "");
        NETWORK.execute(() -> {
            try {
                if (credentials.has("requestId")) {
                    pollPairingOnce(appContext, credentials);
                } else if (credentials.has("token")) {
                    acknowledgePairing(appContext, credentials);
                    fetchStatus(appContext, credentials);
                }
            } catch (Exception error) {
                setUiState(appContext, "offline", credentials.optString("hostName"),
                        "The paired computer is not reachable. Showing the last saved theme.");
            } finally {
                synchronized (OPERATION_LOCK) {
                    operationRunning = false;
                }
            }
        });
    }

    public static boolean hasTrustedDevice(Context context) {
        if (context == null) {
            return false;
        }
        JSONObject credentials = PairingStore.readCredential(context.getApplicationContext());
        return credentials != null && credentials.has("token")
                && !credentials.optString("token", "").isEmpty();
    }

    /** Updates a saved endpoint only after its TLS certificate matches the QR-pinned hash. */
    public static void reconnectDiscovered(Context context, String address, int port,
                                           String advertisedFingerprint) {
        reconnectDiscovered(context, address == null ? Collections.emptyList()
                : Collections.singletonList(address), port, advertisedFingerprint);
    }

    /** Tries each resolved private IPv4 address; multi-homed hosts may expose one unreachable route. */
    public static void reconnectDiscovered(Context context, List<String> candidateAddresses,
                                           int port, String advertisedFingerprint) {
        if (context == null || candidateAddresses == null || port < 1 || port > 65535
                || advertisedFingerprint == null
                || !advertisedFingerprint.matches("[a-fA-F0-9]{64}")) {
            return;
        }
        List<String> uniqueAddresses = new ArrayList<>();
        for (String address : candidateAddresses) {
            if (uniqueAddresses.size() >= 4) {
                break;
            }
            if (address != null && isPrivateIpv4(address) && !uniqueAddresses.contains(address)) {
                uniqueAddresses.add(address);
            }
        }
        if (uniqueAddresses.isEmpty()) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final JSONObject saved = PairingStore.readCredential(appContext);
        if (saved == null || !saved.has("token")) {
            debugDiscoveryLog(appContext, "Ignored DNS-SD candidate because no paired token is saved.");
            return;
        }
        if (!advertisedFingerprint.equalsIgnoreCase(saved.optString("fingerprint", ""))) {
            debugDiscoveryLog(appContext, "Ignored DNS-SD candidate because its certificate hint did not match.");
            return;
        }
        String savedUrl = saved.optString("serverUrl", "");
        List<String> changedAddresses = new ArrayList<>();
        for (String address : uniqueAddresses) {
            if (!("https://" + address + ":" + port).equals(savedUrl)) {
                changedAddresses.add(address);
            }
        }
        final List<String> addresses = changedAddresses;
        if (addresses.isEmpty()) {
            debugDiscoveryLog(appContext, "DNS-SD resolved the currently saved endpoint.");
            return;
        }
        boolean queueRetry = false;
        boolean startNetwork = false;
        synchronized (OPERATION_LOCK) {
            long now = System.currentTimeMillis();
            if (operationRunning) {
                queuedDiscoveryAddresses = new ArrayList<>(addresses);
                queuedDiscoveryPort = port;
                queuedDiscoveryFingerprint = advertisedFingerprint;
                if (!discoveredRetryQueued) {
                    discoveredRetryQueued = true;
                    queueRetry = true;
                }
            } else if (now - lastDiscoveredRefresh < 10_000L) {
                debugDiscoveryLog(appContext, "Skipped duplicate DNS-SD reconnect within the retry window.");
                return;
            } else {
                operationRunning = true;
                lastDiscoveredRefresh = now;
                startNetwork = true;
            }
        }
        if (queueRetry) {
            debugDiscoveryLog(appContext, "Queued DNS-SD reconnect until the current network operation finishes.");
            if (!MAIN.postDelayed(() -> retryQueuedDiscovery(appContext), 250L)) {
                synchronized (OPERATION_LOCK) {
                    discoveredRetryQueued = false;
                    queuedDiscoveryAddresses = null;
                    queuedDiscoveryFingerprint = null;
                    queuedDiscoveryPort = 0;
                }
            }
            return;
        }
        if (!startNetwork) {
            // A retry was already posted. Keep only the newest resolved endpoint in its slot.
            return;
        }
        debugDiscoveryLog(appContext, "Checking a discovered endpoint with pinned TLS and token auth.");
        NETWORK.execute(() -> {
            try {
                for (int index = 0; index < addresses.size(); index++) {
                    String serverUrl = "https://" + addresses.get(index) + ":" + port;
                    try {
                        JSONObject candidate = new JSONObject(saved.toString());
                        candidate.put("serverUrl", serverUrl);
                        HttpResult response = executeJson(candidate, "/v1/status", "GET", null, true);
                        if (response.status != 200
                                || response.body.optInt("protocolVersion", 0) != 1) {
                            throw new IllegalStateException("discovered computer is not a valid OmaApp service");
                        }
                        JSONObject current = PairingStore.readCredential(appContext);
                        if (current == null
                                || !saved.optString("deviceId", "").equals(current.optString("deviceId", ""))
                                || !saved.optString("fingerprint", "").equalsIgnoreCase(
                                        current.optString("fingerprint", ""))
                                || !saved.optString("token", "").equals(current.optString("token", ""))) {
                            return;
                        }
                        current.put("serverUrl", serverUrl);
                        current.put("hostName", saved.optString("hostName", "Omarchy computer"));
                        current.put("fingerprint", saved.optString("fingerprint", ""));
                        PairingStore.writeCredential(appContext, current);
                        debugDiscoveryLog(appContext, "Updated the trusted endpoint after TLS and token verification.");
                        setUiState(appContext, "connecting", current.optString("hostName"), "");
                        applyStatus(appContext, current, response.body);
                        return;
                    } catch (Exception error) {
                        // A DNS-SD address is only a hint. Try the other resolved private IPv4
                        // routes, but never save one until the pinned TLS identity and token pass.
                        debugDiscoveryLog(appContext, "DNS-SD address " + (index + 1) + " of "
                                + addresses.size() + " failed validation ("
                                + error.getClass().getSimpleName() + ").");
                    }
                }
            } finally {
                synchronized (OPERATION_LOCK) {
                    operationRunning = false;
                }
            }
        });
    }

    private static void retryQueuedDiscovery(Context context) {
        List<String> addresses;
        String fingerprint;
        int port;
        synchronized (OPERATION_LOCK) {
            addresses = queuedDiscoveryAddresses == null
                    ? Collections.emptyList() : new ArrayList<>(queuedDiscoveryAddresses);
            port = queuedDiscoveryPort;
            fingerprint = queuedDiscoveryFingerprint;
            queuedDiscoveryAddresses = null;
            queuedDiscoveryPort = 0;
            queuedDiscoveryFingerprint = null;
            discoveredRetryQueued = false;
        }
        if (!addresses.isEmpty() && fingerprint != null) {
            reconnectDiscovered(context, addresses, port, fingerprint);
        }
    }

    private static void debugDiscoveryLog(Context context, String message) {
        if (context != null && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.i("OmaAppDiscovery", message);
        }
    }

    public static void setRealtimeSync(Context context, boolean enabled) {
        if (enabled) {
            REALTIME_SYNC_ENABLED.set(true);
            Context appContext = context == null ? null : context.getApplicationContext();
            if (appContext != null && EVENT_LOOP_RUNNING.compareAndSet(false, true)) {
                EVENTS.execute(() -> watchEvents(appContext));
            }
            return;
        }
        REALTIME_SYNC_ENABLED.set(false);
        HttpsURLConnection connection = eventConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    private static boolean isPrivateIpv4(String address) {
        if (!address.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")) {
            return false;
        }
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed instanceof Inet4Address && parsed.isSiteLocalAddress()
                    && parsed.getHostAddress().equals(address);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void watchEvents(Context context) {
        try {
            while (REALTIME_SYNC_ENABLED.get()) {
                JSONObject credentials = PairingStore.readCredential(context);
                if (credentials == null || !credentials.has("token")) {
                    sleepQuietly(1_000L);
                    continue;
                }
                android.content.SharedPreferences preferences =
                        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
                long revision = EventRevisionCursor.current(preferences);
                HttpsURLConnection connection = null;
                try {
                    connection = openConnection(credentials,
                            "/v1/events?since=" + Math.max(0L, revision), "GET", true, 35_000);
                    eventConnection = connection;
                    int status = connection.getResponseCode();
                    if (status == 400) {
                        EventRevisionCursor.reset(preferences);
                        continue;
                    }
                    if (status != 200) {
                        sleepQuietly(2_000L);
                        continue;
                    }
                    markConnectionAlive(context);
                    byte[] bytes = readBounded(connection.getInputStream(), 16 * 1024);
                    JSONObject event = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    String kind = event.optString("kind", "");
                    if (EventRevisionCursor.acceptEvent(preferences, event)) {
                        if ("theme".equals(kind) || "features".equals(kind)) {
                            queueEventRefresh(context, credentials, kind);
                        }
                    }
                } catch (Exception error) {
                    if (REALTIME_SYNC_ENABLED.get()) {
                        sleepQuietly(2_000L);
                    }
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    if (eventConnection == connection) {
                        eventConnection = null;
                    }
                }
            }
        } finally {
            EVENT_LOOP_RUNNING.set(false);
            if (REALTIME_SYNC_ENABLED.get() && EVENT_LOOP_RUNNING.compareAndSet(false, true)) {
                EVENTS.execute(() -> watchEvents(context));
            }
        }
    }

    private static void queueEventRefresh(Context context, JSONObject credentials, String kind) {
        if (!EVENT_REFRESH_QUEUE.enqueue(kind)) {
            return;
        }
        NETWORK.execute(() -> {
            int pending;
            while ((pending = EVENT_REFRESH_QUEUE.takePending()) != 0) {
                try {
                    if ((pending & EventRefreshQueue.THEME) != 0) {
                        fetchStatus(context, credentials);
                    } else if ((pending & EventRefreshQueue.FEATURES) != 0) {
                        fetchFeatures(context, credentials);
                    }
                } catch (Exception error) {
                    setFeatureMessage(context, safeMessage(error));
                }
            }
        });
    }

    private static void sleepQuietly(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public static void revoke(Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final JSONObject credentials = PairingStore.readCredential(appContext);
        if (credentials == null || !credentials.has("token")) {
            PairingStore.clearCredential(appContext);
            setUiState(appContext, "unpaired", "", "");
            return;
        }
        synchronized (OPERATION_LOCK) {
            if (operationRunning) {
                return;
            }
            operationRunning = true;
        }
        setUiState(appContext, "revoking", credentials.optString("hostName"), "");
        NETWORK.execute(() -> {
            try {
                executeJson(credentials, "/v1/revoke", "POST", null, true);
                PairingStore.clearCredential(appContext);
                clearThemeCache(appContext);
                setUiState(appContext, "unpaired", "", "");
            } catch (Exception error) {
                setUiState(appContext, "offline", credentials.optString("hostName"),
                        "Could not revoke access while the computer is offline. Try again when it reconnects.");
            } finally {
                synchronized (OPERATION_LOCK) {
                    operationRunning = false;
                }
            }
        });
    }

    public static void forget(Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        synchronized (OPERATION_LOCK) {
            if (operationRunning) {
                // Do not drop the user's Forget action during a bounded reconnect request.
                // The single-thread executor runs it after the active operation releases the lock.
                NETWORK.execute(() -> forget(appContext));
                return;
            }
            operationRunning = true;
        }
        NETWORK.execute(() -> {
            JSONObject credentials = PairingStore.readCredential(appContext);
            boolean hostRevoked = credentials == null || !credentials.has("token");
            if (credentials != null && credentials.has("token")) {
                try {
                    executeJson(credentials, "/v1/revoke", "POST", null, true);
                    hostRevoked = true;
                } catch (Exception ignored) {
                    // Forget locally even if the host is offline; the widget can revoke its saved entry.
                }
            }
            PairingStore.clearCredential(appContext);
            clearThemeCache(appContext);
            setUiState(appContext, "unpaired", "", hostRevoked ? ""
                    : "The computer is offline. Remove this phone from its OmaApp widget to revoke its token.");
            synchronized (OPERATION_LOCK) {
                operationRunning = false;
            }
        });
    }

    public static String stateJson(Context context) {
        if (context == null) {
            return "{}";
        }
        Context appContext = context.getApplicationContext();
        JSONObject state = new JSONObject();
        JSONObject credentials = PairingStore.readCredential(appContext);
        String phase = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(PHASE_KEY, "unpaired");
        long updatedAt = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getLong(UPDATED_AT_KEY, 0);
        long lastContact = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getLong(LAST_CONTACT_KEY, updatedAt);
        if (credentials != null && credentials.has("token")
                && System.currentTimeMillis() - lastContact > CONNECTED_WINDOW_MILLIS) {
            phase = "offline";
        }
        try {
            state.put("phase", phase);
            state.put("connected", "connected".equals(phase));
            state.put("hostName", appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(HOST_NAME_KEY, ""));
            state.put("lastError", appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(LAST_ERROR_KEY, ""));
            state.put("updatedAt", updatedAt);
            String theme = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(THEME_CACHE_KEY, "{}");
            state.put("snapshot", new JSONObject(theme));
            state.put("features", new JSONObject(appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(FEATURES_CACHE_KEY, "{}")));
            state.put("featureMessage", appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(FEATURE_MESSAGE_KEY, ""));
            state.put("wallpaperPath", appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(WALLPAPER_PATH_KEY, ""));
            state.put("hasPairedDevice", credentials != null && credentials.has("token"));
            state.put("hasPendingRequest", credentials != null && credentials.has("requestId"));
        } catch (JSONException ignored) {
            return "{}";
        }
        return state.toString();
    }

    private static JSONObject validatePairingQr(String raw) throws Exception {
        JSONObject qr = new JSONObject(raw);
        if (qr.optInt("schema", 0) != 1) {
            throw new IllegalArgumentException("unsupported protocol");
        }
        String rawUrl = qr.optString("url", "");
        Uri uri = Uri.parse(rawUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getPort() < 1 || uri.getPort() > 65535
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("invalid local service address");
        }
        String host = uri.getHost();
        if (!host.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")) {
            throw new IllegalArgumentException("only numeric local IPv4 addresses are accepted");
        }
        InetAddress address = InetAddress.getByName(host);
        if (address.getAddress().length != 4
                || (!address.isSiteLocalAddress() && !address.isLoopbackAddress())) {
            throw new IllegalArgumentException("the computer must be on a local network");
        }
        String fingerprint = qr.optString("fingerprint", "").toLowerCase(Locale.ROOT);
        String secret = qr.optString("pairSecret", "");
        long expiresAt = qr.optLong("expiresAt", 0);
        long now = System.currentTimeMillis() / 1000L;
        if (!fingerprint.matches("[a-f0-9]{64}") || secret.length() < 32 || secret.length() > 128
                || expiresAt <= now || expiresAt - now > 300L) {
            throw new IllegalArgumentException("pairing QR has expired or is malformed");
        }

        JSONObject result = new JSONObject();
        result.put("serverUrl", "https://" + host + ":" + uri.getPort());
        result.put("fingerprint", fingerprint);
        result.put("pairSecret", secret);
        result.put("expiresAt", expiresAt);
        result.put("hostName", qr.optString("hostName", "Omarchy computer").substring(0,
                Math.min(80, qr.optString("hostName", "Omarchy computer").length())));
        return result;
    }

    private static void submitPairingRequest(Context context, JSONObject request) throws Exception {
        String deviceName = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        if (deviceName.length() > 80) {
            deviceName = deviceName.substring(0, 80);
        }
        JSONObject body = new JSONObject();
        body.put("pairSecret", request.getString("pairSecret"));
        body.put("deviceId", PairingStore.deviceId(context));
        body.put("deviceName", deviceName.isEmpty() ? "Android device" : deviceName);
        HttpResult result = executeJson(request, "/v1/pair/request", "POST", body, false);
        if (result.status != 202) {
            throw new IllegalStateException("pairing request rejected");
        }
        JSONObject credentials = new JSONObject(request.toString());
        credentials.remove("pairSecret");
        credentials.put("requestId", result.body.getString("requestId"));
        credentials.put("deviceId", PairingStore.deviceId(context));
        PairingStore.writeCredential(context, credentials);
        setUiState(context, "pending", request.optString("hostName"), "");

        long deadline = Math.min(request.optLong("expiresAt", 0) * 1000L,
                System.currentTimeMillis() + PAIRING_TIMEOUT_MILLIS);
        while (!cancelRequested && System.currentTimeMillis() < deadline) {
            Thread.sleep(2_000L);
            if (cancelRequested) {
                cancelRemotePairing(context, credentials);
                PairingStore.clearCredential(context);
                setUiState(context, "unpaired", "", "");
                return;
            }
            if (pollPairingOnce(context, credentials)) {
                return;
            }
        }
        if (cancelRequested) {
            cancelRemotePairing(context, credentials);
            PairingStore.clearCredential(context);
            setUiState(context, "unpaired", "", "");
        } else {
            PairingStore.clearCredential(context);
            setUiState(context, "error", request.optString("hostName"),
                    "The computer did not approve the request before the QR code expired.");
        }
    }

    private static void reconnectKnownDevice(Context context, JSONObject request,
                                             JSONObject existing) throws Exception {
        JSONObject credentials = new JSONObject(request.toString());
        credentials.put("deviceId", existing.getString("deviceId"));
        credentials.put("token", existing.getString("token"));
        JSONObject body = new JSONObject();
        body.put("deviceId", existing.getString("deviceId"));
        body.put("pairSecret", request.getString("pairSecret"));
        HttpResult result = executeJson(credentials, "/v1/reconnect", "POST", body, true);
        if (result.status != 200 || !"reconnected".equals(result.body.optString("status"))) {
            throw new IllegalStateException("computer did not accept the reconnect request");
        }

        credentials.remove("pairSecret");
        credentials.remove("expiresAt");
        PairingStore.writeCredential(context, credentials);
        setUiState(context, "connecting", request.optString("hostName"), "");
        fetchStatus(context, credentials);
    }

    private static boolean pollPairingOnce(Context context, JSONObject credentials) throws Exception {
        String requestId = credentials.optString("requestId", "");
        if (requestId.isEmpty()) {
            return false;
        }
        HttpResult result = executeJson(credentials,
                "/v1/pair/status/" + requestId, "GET", null, false);
        if (result.status == 410 || result.status == 404) {
            PairingStore.clearCredential(context);
            setUiState(context, "error", credentials.optString("hostName"),
                    "The pairing request expired. Scan a new QR code.");
            return true;
        }
        String status = result.body.optString("status");
        if ("rejected".equals(status)) {
            PairingStore.clearCredential(context);
            setUiState(context, "error", credentials.optString("hostName"),
                    "The request was rejected on the computer.");
            return true;
        }
        if (!"approved".equals(status)) {
            return false;
        }
        credentials.put("token", result.body.getString("token"));
        credentials.put("approvalId", requestId);
        credentials.remove("requestId");
        credentials.remove("expiresAt");
        PairingStore.writeCredential(context, credentials);
        setUiState(context, "connecting", credentials.optString("hostName"), "");
        acknowledgePairing(context, credentials);
        fetchStatus(context, credentials);
        return true;
    }

    private static void acknowledgePairing(Context context, JSONObject credentials) throws Exception {
        String approvalId = credentials.optString("approvalId", "");
        if (approvalId.isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        body.put("requestId", approvalId);
        HttpResult result = executeJson(credentials, "/v1/pair/complete", "POST", body, true);
        if (result.status != 200 || !"completed".equals(result.body.optString("status"))) {
            throw new IllegalStateException("computer did not confirm the pairing token");
        }
        credentials.remove("approvalId");
        PairingStore.writeCredential(context, credentials);
    }

    private static void cancelRemotePairing(Context context, JSONObject credentials) {
        try {
            JSONObject body = new JSONObject();
            body.put("requestId", credentials.getString("requestId"));
            executeJson(credentials, "/v1/pair/cancel", "POST", body, false);
        } catch (Exception ignored) {
            // The request expires on the Omarchy service even if this phone goes offline.
        }
    }

    private static void fetchStatus(Context context, JSONObject credentials) throws Exception {
        HttpResult result = executeJson(credentials, "/v1/status", "GET", null, true);
        applyStatus(context, credentials, result.body);
    }

    private static void applyStatus(Context context, JSONObject credentials,
                                    JSONObject snapshot) throws Exception {
        if (snapshot.optInt("protocolVersion", 0) != 1) {
            throw new IllegalStateException("unsupported status protocol");
        }
        JSONObject wallpaper = snapshot.optJSONObject("wallpaper");
        downloadWallpaperIfNeeded(context, credentials, wallpaper);
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(THEME_CACHE_KEY, snapshot.toString())
                .putLong(UPDATED_AT_KEY, System.currentTimeMillis())
                .apply();
        try {
            fetchFeatures(context, credentials);
        } catch (Exception error) {
            setFeatureMessage(context, safeMessage(error));
        }
        setUiState(context, "connected", snapshot.optString("hostName"), "");
        markConnectionAlive(context);
    }

    private static void fetchFeatures(Context context, JSONObject credentials) throws Exception {
        HttpResult features = executeJson(credentials, "/v1/features", "GET", null, true);
        if (features.status != 200) {
            throw new IllegalStateException(featureError(features));
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(FEATURES_CACHE_KEY, features.body.toString())
                .apply();
        markConnectionAlive(context);
    }

    private static void runFeatureRequest(Context context, String path, String method,
                                          JSONObject body, boolean clipboard) {
        Context appContext = context.getApplicationContext();
        JSONObject credentials = PairingStore.readCredential(appContext);
        if (credentials == null || !credentials.has("token")) {
            setFeatureMessage(appContext, "Pair OmaApp with a computer first.");
            return;
        }
        setFeatureMessage(appContext, "Sending command to the computer…");
        NETWORK.execute(() -> {
            try {
                HttpResult response = executeJson(credentials, path, method, body, true);
                if (response.status < 200 || response.status >= 300) {
                    throw new IllegalStateException(featureError(response));
                }
                setFeatureMessage(appContext, clipboard
                        ? "Phone clipboard sent to the computer."
                        : "Computer action completed.");
            } catch (Exception error) {
                setFeatureMessage(appContext, safeMessage(error));
            }
        });
    }

    static String featureError(HttpResult result) {
        String error = result.body.optString("error", "request_failed");
        if ("permission_required".equals(error)) {
            return "Enable this feature for this phone in the Omarchy plugin permissions.";
        }
        if ("clipboard_unavailable".equals(error)) {
            return "The computer clipboard is unavailable in this session.";
        }
        return "Computer feature request failed (HTTP " + result.status + ").";
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Computer feature request failed. Check the local network and plugin permissions.";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    static void setFeatureMessage(Context context, String message) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(FEATURE_MESSAGE_KEY, message == null ? "" : message).apply();
    }

    private static void downloadWallpaperIfNeeded(Context context, JSONObject credentials,
                                                  JSONObject wallpaper) throws Exception {
        if (wallpaper == null) {
            String oldPath = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(WALLPAPER_PATH_KEY, "");
            if (!oldPath.isEmpty()) {
                new File(oldPath).delete();
            }
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                    .remove(WALLPAPER_ID_KEY).remove(WALLPAPER_PATH_KEY).apply();
            return;
        }
        String id = wallpaper.optString("id", "");
        if (!id.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("invalid wallpaper id");
        }
        String previousId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(WALLPAPER_ID_KEY, "");
        String previousPath = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(WALLPAPER_PATH_KEY, "");
        if (id.equals(previousId) && !previousPath.isEmpty() && new File(previousPath).isFile()) {
            return;
        }
        String suffix;
        switch (wallpaper.optString("mimeType")) {
            case "image/jpeg": suffix = ".jpg"; break;
            case "image/png": suffix = ".png"; break;
            case "image/webp": suffix = ".webp"; break;
            case "image/avif": suffix = ".avif"; break;
            case "image/bmp": suffix = ".bmp"; break;
            default: throw new IllegalArgumentException("unsupported wallpaper format");
        }
        File directory = new File(context.getFilesDir(), "omarchy-theme");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("could not create theme cache");
        }
        File destination = new File(directory, "wallpaper-" + id + suffix);
        File temporary = new File(directory, destination.getName() + ".tmp");
        try {
            downloadBytes(credentials, "/v1/wallpaper", temporary);
        } catch (Exception error) {
            temporary.delete();
            throw error;
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IllegalStateException("could not update wallpaper cache");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("could not install wallpaper cache");
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(WALLPAPER_ID_KEY, id)
                .putString(WALLPAPER_PATH_KEY, destination.getAbsolutePath())
                .apply();
        if (!previousId.isEmpty()) {
            File[] cachedFiles = directory.listFiles();
            if (cachedFiles != null) {
                for (File file : cachedFiles) {
                    if (file.getName().startsWith("wallpaper-" + previousId + ".")) {
                        file.delete();
                    }
                }
            }
        }
    }

    private static void downloadBytes(JSONObject credentials, String path, File destination)
            throws Exception {
        HttpsURLConnection connection = openConnection(credentials, path, "GET", true);
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_WALLPAPER_BYTES) {
                    throw new IllegalArgumentException("wallpaper exceeds the size limit");
                }
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }
    }

    static HttpResult executeJson(JSONObject credentials, String path, String method,
                                  JSONObject body, boolean authenticated) throws Exception {
        HttpsURLConnection connection = openConnection(credentials, path, method, authenticated);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            int responseLimit = "/v1/clipboard".equals(path)
                    ? MAX_CLIPBOARD_JSON_BYTES : MAX_JSON_BYTES;
            byte[] bytes = readBounded(stream, responseLimit);
            JSONObject response = bytes.length == 0 ? new JSONObject()
                    : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            return new HttpResult(code, response);
        } finally {
            connection.disconnect();
        }
    }

    static HttpsURLConnection openConnection(JSONObject credentials, String path,
                                             String method, boolean authenticated) throws Exception {
        return openConnection(credentials, path, method, authenticated,
                DEFAULT_REQUEST_READ_TIMEOUT_MILLIS);
    }

    static HttpsURLConnection openConnection(JSONObject credentials, String path,
                                             String method, boolean authenticated,
                                             int readTimeout) throws Exception {
        String serverUrl = credentials.getString("serverUrl");
        URL url = new URL(serverUrl + path);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(pinnedSocketFactory(credentials.getString("fingerprint")));
        // The scanned certificate fingerprint is the peer identity. Pinning allows the same
        // computer to be reached after its router assigns it a new private IPv4 address.
        connection.setHostnameVerifier((hostname, session) -> true);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(Math.max(1_000, Math.min(readTimeout, 60_000)));
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (authenticated) {
            connection.setRequestProperty("Authorization", "Bearer " + credentials.getString("token"));
            connection.setRequestProperty("X-OmaApp-Device", credentials.getString("deviceId"));
        }
        return connection;
    }

    private static javax.net.ssl.SSLSocketFactory pinnedSocketFactory(String expectedFingerprint)
            throws Exception {
        byte[] expected = decodeHex(expectedFingerprint);
        X509TrustManager pinnedTrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException("client certificates are not accepted");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (chain == null || chain.length != 1) {
                    throw new CertificateException("unexpected certificate chain");
                }
                try {
                    chain[0].checkValidity();
                    byte[] actual = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                    if (!MessageDigest.isEqual(expected, actual)) {
                        throw new CertificateException("computer identity does not match the QR code");
                    }
                } catch (CertificateException error) {
                    throw error;
                } catch (Exception error) {
                    throw new CertificateException("could not verify the computer identity", error);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{pinnedTrustManager}, new SecureRandom());
        return context.getSocketFactory();
    }

    private static byte[] decodeHex(String text) throws IllegalArgumentException {
        if (text == null || !text.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("invalid certificate fingerprint");
        }
        byte[] result = new byte[text.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(text.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        if (input == null) {
            return new byte[0];
        }
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4 * 1024];
            int total = 0;
            int count;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("server response exceeds the size limit");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void setUiState(Context context, String phase, String hostName, String error) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(PHASE_KEY, phase)
                .putString(HOST_NAME_KEY, hostName == null ? "" : hostName)
                .putString(LAST_ERROR_KEY, error == null ? "" : error)
                .apply();
    }

    private static void markConnectionAlive(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_CONTACT_KEY, System.currentTimeMillis())
                .putString(PHASE_KEY, "connected")
                .putString(LAST_ERROR_KEY, "")
                .apply();
    }

    private static void clearThemeCache(Context context) {
        String wallpaperPath = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(WALLPAPER_PATH_KEY, "");
        if (!wallpaperPath.isEmpty()) {
            new File(wallpaperPath).delete();
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .remove(THEME_CACHE_KEY).remove(UPDATED_AT_KEY)
                .remove(LAST_CONTACT_KEY)
                .remove(WALLPAPER_PATH_KEY).remove(WALLPAPER_ID_KEY)
                .remove(FEATURES_CACHE_KEY).remove(FEATURE_MESSAGE_KEY)
                .remove(EventRevisionCursor.KEY)
                .apply();
    }

    static final class HttpResult {
        final int status;
        final JSONObject body;

        HttpResult(int status, JSONObject body) {
            this.status = status;
            this.body = body;
        }
    }
}
