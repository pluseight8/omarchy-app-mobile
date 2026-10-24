Warning: truncated output (original token count: 20124)
Total output lines: 1612

package app.omaapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PersistableBundle;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.app.UiAutomation;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/** Debug APK instrumentation used by CI to exercise the real Android pairing client. */
public final class PairingTestInstrumentation extends Instrumentation {
    private static final String TAG = "OmaAppPairingSmoke";
    private static final long PAIRING_TIMEOUT_MILLIS = 60_000L;
    private static final long REVOCATION_TIMEOUT_MILLIS = 20_000L;
    private Bundle testArguments = new Bundle();

    @Override
    public void onCreate(Bundle arguments) {
        Log.i(TAG, "Instrumentation onCreate entered.");
        super.onCreate(arguments);
        testArguments = arguments == null ? new Bundle() : new Bundle(arguments);
        Log.i(TAG, "Instrumentation arguments received.");
        // Instrumentation invokes onStart on its test thread only after this call.
        start();
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Instrumentation onStart entered.");
        super.onStart();
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext().getApplicationContext();
            Log.i(TAG, "Target application context is ready.");
            if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                throw new IllegalStateException("Pairing smoke tests require a debug APK.");
            }
            if (Boolean.parseBoolean(testArguments.getString("nativeQtTranslations"))) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(
                        context.getPackageName());
                if (launch == null) {
                    throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
                SystemClock.sleep(2_000L);
                String translated = NativeStrings.get("Computer files");
                if (!"Файлы компьютера".equals(translated)) {
                    throw new IllegalStateException("Native Android UI did not use the Russian Qt catalog: "
                            + translated);
                }
                result.putString("stage", "native_qt_translations_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (Boolean.parseBoolean(testArguments.getString("appWidgetHost"))) {
                runAppWidgetHostPoc(context);
                result.putString("stage", "app_widget_host_overlay_and_touch_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            runEventRefreshQueueSmoke(context);
            runFileTransferStateSmoke();
            runOwlRuntimeSmoke();
            if (Boolean.parseBoolean(testArguments.getString("launcherShortcuts"))) {
                runLauncherShortcutSmoke(context);
                result.putString("stage", "launcher_shortcuts_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            String dnsSdPort = testArguments.getString("dnsSdPort", "");
            if (!dnsSdPort.isEmpty()) {
                runDnsSdReconnectSmoke(context, Integer.parseInt(dnsSdPort),
                        testArguments.getString("dnsSdFingerprint", ""));
                result.putString("stage", "dns_sd_reconnected");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            String reconnectTheme = testArguments.getString("assertReconnect", "");
            if (!reconnectTheme.isEmpty()) {
                awaitPluginRestartReconnect(context, reconnectTheme);
                result.putString("stage", "plugin_restart_reconnected");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) {
                throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
            SystemClock.sleep(2_000L);

            String encodedQr = testArguments.getString("qr64", "");
            if (encodedQr.isEmpty() || encodedQr.length() > 4096) {
                throw new IllegalArgumentException("A bounded pairing QR payload is required.");
            }
            String qr = new String(Base64.decode(encodedQr, Base64.NO_WRAP), StandardCharsets.UTF_8);
            Log.i(TAG, "Starting PairingBridge.beginPairing.");
            PairingBridge.beginPairing(context, qr);
            Log.i(TAG, "PairingBridge.beginPairing returned.");
            reportProgress(context, "started");

            String expectedTheme = testArguments.getString("expectedTheme", "");
            JSONObject connected = awaitTheme(context, expectedTheme, PAIRING_TIMEOUT_MILLIS, false);
            runConnectivityHeartbeatSmoke(context);
            report("paired", connected);

            String updatedTheme = testArguments.getString("updatedTheme", "");
            if (!updatedTheme.isEmpty()) {
                JSONObject updated = awaitTheme(context, updatedTheme, PAIRING_TIMEOUT_MILLIS, true);
                report("theme_updated", updated);
            }

            if (Boolean.parseBoolean(testArguments.getString("featureSmoke"))) {
                runFeatureSmoke(context,
                        Boolean.parseBoolean(testArguments.getString("clipboardStress")));
            }

            if (Boolean.parseBoolean(testArguments.getString("revoke"))) {
                PairingBridge.revoke(context);
                awaitRevocation(context);
                result.putString("stage", "revoked");
                sendStatus(2, result);
            }
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            Log.e(TAG, "Pairing instrumentation failed.", error);
            result.putString("stage", "failed");
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            sendStatus(2, result);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Exercises Android's real widget binding, RemoteViews, host view, and QtActivity overlay. */
    private void runAppWidgetHostPoc(Context context) throws Exception {
        reportAppWidgetStage("activity_starting");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                launch.getComponent().getClassName(), null, false);
        addMonitor(monitor);
        Activity activity;
        try {
            context.startActivity(launch);
            activity = monitor.waitForActivityWithTimeout(15_000L);
        } finally {
            removeMonitor(monitor);
        }
        if (activity == null || activity.isFinishing()) {
            throw new IllegalStateException("Could not open Qt Quick Home for the Android widget overlay test.");
        }
        reportAppWidgetStage("activity_started");
        UiAutomation automation = getUiAutomation();
        AccessibilityServiceInfo serviceInfo = automation.getServiceInfo();
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(serviceInfo);
        if (!awaitAccessibleControlClick(automation, "Home", 8_000L)) {
            throw new IllegalStateException("The Home navigation control did not become clickable.");
        }
        reportAppWidgetStage("home_control_clicked");
        if (!awaitAccessibilityText(automation, "Home page 1 of", 8_000L)) {
            throw new IllegalStateException("Could not select the existing Qt Quick Home page.");
        }
        reportAppWidgetStage("home_selected");

        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        AppWidgetProviderInfo provider = null;
        for (AppWidgetProviderInfo candidate : manager.getInstalledProviders()) {
            if (candidate.provider != null
                    && "app.omaapp".equals(candidate.provider.getPackageName())
                    && candidate.provider.getClassName().endsWith("CiAppWidgetProvider")) {
                provider = candidate;
                break;
            }
        }
        if (provider == null) {
            throw new IllegalStateException("The debug AppWidgetProvider fixture was not installed.");
        }

        AppWidgetHost host = new AppWidgetHost(activity, 0x4f4d41);
        host.startListening();
        int widgetId = host.allocateAppWidgetId();
        FrameLayout content = activity.findViewById(android.R.id.content);
        AtomicReference<AppWidgetHostView> hostViewResult = new AtomicReference<>();
        AtomicReference<String> viewError = new AtomicReference<>("");
        try {
            reportAppWidgetStage("binding_started");
            if (!manager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)) {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider);
                activity.runOnUiThread(() -> activity.startActivityForResult(bind, 0x4f4d42));
                long deadline = SystemClock.uptimeMillis() + 20_000L;
                while (SystemClock.uptimeMillis() < deadline
                        && manager.getAppWidgetInfo(widgetId) == null) {
                    clickWidgetBindConfirmation(automation);
                    SystemClock.sleep(250L);
                }
            }
            if (manager.getAppWidgetInfo(widgetId) == null) {
                Log.w(TAG, "AppWidget confirmation accessibility nodes: "
                        + describeInteractiveWindows(automation));
                throw new IllegalStateException("Android did not confirm AppWidget binding.");
            }
            reportAppWidgetStage("binding_confirmed");

            activity.runOnUiThread(() -> {
                try {
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            Math.min(dp(activity, 340), Math.max(dp(activity, 220), content.getWidth() - dp(activity, 24))),
                            dp(activity, 132), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                    params.topMargin = dp(activity, 300);
                    AppWidgetHostView widgetView = host.createView(
                            activity, widgetId, manager.getAppWidgetInfo(widgetId));
                    widgetView.setContentDescription("OmaApp live AppWidgetHostView proof of concept");
                    content.addView(widgetView, params);
                    widgetView.bringToFront();
                    hostViewResult.set(widgetView);
                } catch (RuntimeException error) {
                    viewError.set(error.getClass().getSimpleName() + ": " + error.getMessage());
                }
            });
            if (!awaitLatchlessView(hostViewResult, viewError, 10_000L)) {
                throw new IllegalStateException("Could not attach AppWidgetHostView above Qt Quick: "
                        + viewError.get());
            }
            reportAppWidgetStage("view_attached");

            if (!awaitAccessibilityText(automation, "CI live Android widget", 10_000L)) {
                throw new IllegalStateException("The real RemoteViews content was not visible over Qt Quick Home.");
            }
            reportAppWidgetStage("content_visible");
            saveAppWidgetScreenshot(activity, automation);
            reportAppWidgetStage("screenshot_saved");

            AppWidgetHostView widgetView = hostViewResult.get();
            Rect buttonBounds = new Rect();
            boolean buttonFound = false;
            long buttonDeadline = SystemClock.uptimeMillis() + 10_000L;
            while (!buttonFound && SystemClock.uptimeMillis() < buttonDeadline) {
                AtomicReference<Boolean> found = new AtomicReference<>(false);
                runOnMainSync(() -> {
                    ArrayList<View> matches = new ArrayList<>();
                    widgetView.findViewsWithText(matches, "Tap widget", View.FIND_VIEWS_WITH_TEXT);
                    if (!matches.isEmpty()) {
                        found.set(matches.get(0).getGlobalVisibleRect(buttonBounds));
                    }
                });
                buttonFound = found.get();
                if (!buttonFound) {
                    SystemClock.sleep(200L);
                }
            }
            if (!buttonFound || buttonBounds.isEmpty()) {
                throw new IllegalStateException("The host view did not expose the widget's clickable RemoteViews button.");
            }

            float x = buttonBounds.centerX();
            float y = buttonBounds.centerY();
            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(
                    downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
            try {
                sendPointerSync(down);
            } finally {
                down.recycle();
            }
            MotionEvent up = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            try {
                sendPointerSync(up);
            } finally {
                up.recycle();
            }
            reportAppWidgetStage("touch_sent");
            if (!awaitAccessibilityText(automation, "Widget tap received by Android provider", 10_000L)) {
                throw new IllegalStateException("A touch on AppWidgetHostView did not reach its real provider.");
            }
            reportAppWidgetStage("touch_received");
            Log.i(TAG, "Real AppWidgetHostView RemoteViews overlay and provider click passed.");
        } finally {
            AppWidgetHostView widgetView = hostViewResult.get();
            if (widgetView != null) {
                activity.runOnUiThread(() -> {
                    ViewGroup parent = (ViewGroup) widgetView.getParent();
                    if (parent != null) {
                        parent.removeView(widgetView);
                    }
                });
            }
            host.stopListening();
            host.deleteAppWidgetId(widgetId);
        }
    }

    private void reportAppWidgetStage(String stage) {
        Bundle status = new Bundle();
        status.putString("stage", "app_widget_" + stage);
        sendStatus(2, status);
    }

    /** Exercises real Android NSD discovery/resolution and the saved TLS trust gate. */
    private void runDnsSdReconnectSmoke(Context context, int port, String fingerprint) throws Exception {
        if (port < 1 || port > 65535 || fingerprint == null
                || !fingerprint.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("A bounded CI DNS-SD endpoint is required.");
        }
        JSONObject saved = PairingStore.readCredential(context);
        if (saved == null || !saved.has("token") || !saved.has("fingerprint")) {
            throw new IllegalStateException("DNS-SD reconnect requires an existing QR-trusted device.");
        }
        String originalUrl = saved.optString("serverUrl", "");
        String pinnedFingerprint = saved.optString("fingerprint", "");
        String token = saved.optString("token", "");
        if (!fingerprint.equalsIgnoreCase(pinnedFingerprint)) {
            throw new IllegalStateException("The DNS-SD fixture does not represent a changed endpoint for the pinned host.");
        }

        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
        SystemClock.sleep(2_000L);
        DeviceDiscovery.start(context);

        TestTlsProxy proxy = new TestTlsProxy(port);
        try {
            proxy.start();
            InetAddress host = proxy.getLocalAddress();
            int discoveryPort = proxy.getPort();
            String expectedUrl = "https://" + host.getHostAddress() + ":" + discoveryPort;
            if (expectedUrl.equals(originalUrl)) {
                throw new IllegalStateException("The DNS-SD fixture did not change the saved endpoint.");
            }
            Intent untrusted = startDnsSdFixture(context,
                    "OmaApp-ci-rogue-" + Long.toHexString(SystemClock.elapsedRealtime()),
                    host, discoveryPort, "0".repeat(64));
            try {
                SystemClock.sleep(3_000L);
                JSONObject stillSaved = PairingStore.readCredential(context);
                if (stillSaved == null || !originalUrl.equals(stillSaved.optString("serverUrl", ""))) {
                    throw new IllegalStateException("An untrusted DNS-SD fingerprint changed the saved endpoint.");
                }
            } finally {
                context.stopService(untrusted);
                SystemClock.sleep(500L);
            }

            Intent trusted = startDnsSdFixture(context,
                    "OmaApp-" + fingerprint.substring(0, 12) + "-ci-" + discoveryPort,
                    host, discoveryPort, fingerprint);
            try {
                long deadline = SystemClock.uptimeMillis() + 25_000L;
                JSONObject updated = PairingStore.readCredential(context);
                while (SystemClock.uptimeMillis() < deadline
                        && (updated == null || !expectedUrl.equals(updated.optString("serverUrl", "")))) {
                    SystemClock.sleep(200L);
                    updated = PairingStore.readCredential(context);
                }
                if (updated == null || !expectedUrl.equals(updated.optString("serverUrl", ""))) {
                    throw new IllegalStateException("Android NSD did not reconnect to the TLS-pinned endpoint.");
                }
                if (!token.equals(updated.optString("token", ""))
                        || !pinnedFingerprint.equalsIgnoreCase(updated.optString("fingerprint", ""))) {
                    throw new IllegalStateException("DNS-SD reconnect changed saved pairing trust material.");
                }
                Log.i(TAG, "Android NSD resolved the fixture and accepted the endpoint after pinned TLS verification.");
            } finally {
                context.stopService(trusted);
            }
        } finally {
            proxy.close();
        }
    }

    private static Intent startDnsSdFixture(Context context, String name, InetAddress host,
                                            int port, String fingerprint) {
        Intent intent = new Intent().setComponent(new ComponentName(
                "app.omaapp.launcherfixture",
                "app.omaapp.launcherfixture.NsdFixtureService"));
        intent.putExtra("serviceName", name);
        intent.putExtra("serviceHost", host.getHostAddress());
        intent.putExtra("servicePort", port);
        intent.putExtra("fingerprint", fingerprint);
        if (context.startService(intent) == null) {
            throw new IllegalStateException("The CI DNS-SD service fixture could not be started.");
        }
        return intent;
    }

    /** Transparent TLS tunnel from an emulator-local NSD endpoint to the CI plugin fixture. */
    private static final class TestTlsProxy implements AutoCloseable {
        private final InetAddress localAddress;
        private final int targetPort;
        private final ServerSocket server;
        private volatile boolean closed;
        private volatile Socket clientSocket;
        private volatile Socket upstreamSocket;
        private Thread acceptThread;

        TestTlsProxy(int targetPort) throws Exception {
            this.targetPort = targetPort;
            localAddress = findLocalSiteIpv4();
            server = new ServerSocket();
            server.setReuseAddress(false);
            server.bind(new InetSocketAddress(localAddress, 0), 2);
            server.setSoTimeout(1_000);
        }

        InetAddress getLocalAddress() {
            return localAddress;
        }

        int getPort() {
            return server.getLocalPort();
        }

        void start() {
            acceptThread = new Thread(this::acceptConnections, "OmaApp CI TLS forwarder");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void acceptConnections() {
            while (!closed) {
                try {
                    Socket client = server.accept();
                    clientSocket = client;
                    Thread connection = new Thread(() -> forward(client),
                            "OmaApp CI TLS forwarder connection");
                    connection.setDaemon(true);
                    connection.start();
                } catch (java.net.SocketTimeoutException ignored) {
                    // Recheck the close flag while the listener remains idle.
                } catch (IOException error) {
                    if (!closed) {
                        Log.w(TAG, "The DNS-SD TLS fixture listener stopped.", error);
                    }
                    return;
                }
            }
        }

        private void forward(Socket client) {
            try (Socket accepted = client) {
                Socket upstream = new Socket();
                upstreamSocket = upstream;
                upstream.connect(new InetSocketAddress("10.0.2.2", targetPort), 5_000);
                upstream.setSoTimeout(30_000);
                accepted.setSoTimeout(30_000);
                Thread request = new Thread(() -> copyTls(accepted, upstream),
                        "OmaApp CI TLS request tunnel");
                Thread response = new Thread(() -> copyTls(upstream, accepted),
                        "OmaApp CI TLS response tunnel");
                request.setDaemon(true);
                response.setDaemon(true);
                request.start();
                response.start();
                request.join(30_000);
                response.join(30_000);
            } catch (IOException error) {
                if (!closed) {
                    Log.w(TAG, "The DNS-SD TLS fixture connection failed.", error);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                Socket upstream = upstreamSocket;
                if (upstream != null) {
                    try {
                        upstream.close();
                    } catch (IOException ignored) {
                    }
                    upstreamSocket = null;
                }
                clientSocket = null;
            }
        }

        private static void copyTls(Socket source, Socket destination) {
            try {
                InputStream input = source.getInputStream();
                OutputStream output = destination.getOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
                destination.shutdownOutput();
            } catch (IOException ignored) {
                // Closing either side ends the transparent tunnel after the HTTPS response.
            }
        }

        @Override
        public void close() {
            closed = true;
            try {
                server.close();
            } catch (IOException ignored) {
            }
            Socket client = clientSocket;
            if (client != null) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
            Socket upstream = upstreamSocket;
            if (upstream != null) {
                try {
                    upstream.close();
                } catch (IOException ignored) {
                }
            }
            if (acceptThread != null) {
                try {
                    acceptThread.join(2_000L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private static InetAddress findLocalSiteIpv4() throws Exception {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                throw new SocketException("Android returned no network interfaces for DNS-SD.");
            }
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address;
                    }
                }
            }
            throw new SocketException("Android returned no site-local IPv4 interface for DNS-SD.");
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void saveAppWidgetScreenshot(Activity activity, UiAutomation automation)
            throws Exception {
        Bitmap bitmap = automation.takeScreenshot();
        if (bitmap == null) {
            throw new IllegalStateException("Android could not capture the live AppWidgetHostView.");
        }
        File directory = activity.getExternalFilesDir(null);
        if (directory == null) {
            directory = activity.getFilesDir();
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Android could not create the AppWidgetHostView screenshot directory.");
        }
        File output = new File(directory, "omaapp-appwidget-host-poc.png");
        try (FileOutputStream stream = new FileOutputStream(output, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IllegalStateException("Android could not save the AppWidgetHostView screenshot.");
            }
            stream.getFD().sync();
        } finally {
            bitmap.recycle();
        }
    }

    private static boolean clickAccessibleControl(UiAutomation automation, String label) {
        ArrayList<AccessibilityNodeInfo> pending = new ArrayList<>();
        AccessibilityNodeInfo activeRoot = automation.getRootInActiveWindow();
        if (activeRoot != null) {
            pending.add(activeRoot);
        }
        List<AccessibilityWindowInfo> wi…5124 tokens truncated…ursor.reset(preferences);

        EventRefreshQueue queue = new EventRefreshQueue();
        if (!queue.enqueue("features") || queue.takePending() != EventRefreshQueue.FEATURES) {
            throw new IllegalStateException("The event queue did not start a feature refresh.");
        }
        if (queue.enqueue("theme")) {
            throw new IllegalStateException("An active event worker was scheduled twice.");
        }
        if (queue.takePending() != EventRefreshQueue.THEME || queue.takePending() != 0) {
            throw new IllegalStateException("The event queue dropped a theme update during a feature refresh.");
        }
        if (!queue.enqueue("theme") || queue.enqueue("features")
                || queue.takePending() != (EventRefreshQueue.THEME | EventRefreshQueue.FEATURES)
                || queue.takePending() != 0) {
            throw new IllegalStateException("The event queue did not coalesce both pending refresh kinds.");
        }
        Log.i(TAG, "Event refresh queue retained overlapping theme and feature updates.");
    }

    private void runConnectivityHeartbeatSmoke(Context context) throws Exception {
        // The persistent event listener can refresh last_contact while this test
        // deliberately ages it. Stop that worker for the boundary assertion,
        // then resume event-driven updates for the rest of the pairing smoke.
        PairingBridge.setRealtimeSync(context, false);
        SystemClock.sleep(2_100L);
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PairingBridge.PREFERENCES, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        try {
            preferences.edit().putString("phase", "connected")
                    .putLong(PairingBridge.LAST_CONTACT_KEY, now - 20_000L).apply();
            if (!"connected".equals(new JSONObject(PairingBridge.stateJson(context)).optString("phase"))) {
                throw new IllegalStateException("A healthy connection expired before the event refresh interval.");
            }

            preferences.edit().putLong(PairingBridge.LAST_CONTACT_KEY, now - 60_000L).apply();
            if (!"offline".equals(new JSONObject(PairingBridge.stateJson(context)).optString("phase"))) {
                throw new IllegalStateException("A stale computer connection was incorrectly left online.");
            }
        } finally {
            PairingBridge.setRealtimeSync(context, true);
        }

        reportStage("connectivity_heartbeat_waiting");
        long deadline = SystemClock.elapsedRealtime() + 15_000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            JSONObject state = new JSONObject(PairingBridge.stateJson(context));
            long lastContact = preferences.getLong(PairingBridge.LAST_CONTACT_KEY, 0L);
            if ("connected".equals(state.optString("phase")) && lastContact >= now) {
                Log.i(TAG, "A successful event response refreshed the connected state.");
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new IllegalStateException("The plugin event heartbeat did not restore connected state.");
    }

    private JSONObject awaitTheme(Context context, String expectedTheme,
                                  long timeoutMillis, boolean requestRefresh) throws Exception {
        if (expectedTheme.isEmpty() || expectedTheme.length() > 80) {
            throw new IllegalArgumentException("An expected theme name is required.");
        }
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        long nextProgress = 0;
        JSONObject lastState = new JSONObject();
        while (SystemClock.elapsedRealtime() < deadline) {
            if (requestRefresh) {
                PairingBridge.refresh(context);
            }
            lastState = new JSONObject(PairingBridge.stateJson(context));
            if ("error".equals(lastState.optString("phase"))) {
                throw new IllegalStateException(lastState.optString("lastError", "pairing failed"));
            }
            JSONObject snapshot = lastState.optJSONObject("snapshot");
            JSONObject theme = snapshot == null ? null : snapshot.optJSONObject("theme");
            if ("connected".equals(lastState.optString("phase")) && theme != null
                    && expectedTheme.equals(theme.optString("name"))) {
                validateSnapshot(lastState, snapshot, theme);
                return lastState;
            }
            if (SystemClock.elapsedRealtime() >= nextProgress) {
                reportProgress(context, "waiting_for_" + expectedTheme);
                nextProgress = SystemClock.elapsedRealtime() + 5_000L;
            }
            SystemClock.sleep(250L);
        }
        throw new IllegalStateException("Timed out waiting for theme " + expectedTheme
                + "; last phase=" + lastState.optString("phase"));
    }

    private static void awaitPluginRestartReconnect(Context context, String expectedTheme)
            throws Exception {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);

        long deadline = SystemClock.elapsedRealtime() + 90_000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            JSONObject state = new JSONObject(PairingBridge.stateJson(context));
            JSONObject snapshot = state.optJSONObject("snapshot");
            JSONObject theme = snapshot == null ? null : snapshot.optJSONObject("theme");
            if ("connected".equals(state.optString("phase"))
                    && theme != null && expectedTheme.equals(theme.optString("name"))) {
                if (!state.optBoolean("hasPairedDevice")) {
                    throw new IllegalStateException("The plugin restart cleared the saved device token.");
                }
                Log.i(TAG, "The saved device reconnected after the Omarchy plugin restarted.");
                return;
            }
            SystemClock.sleep(250L);
        }
        throw new IllegalStateException("The saved device did not reconnect and receive the post-restart theme.");
    }

    private void reportProgress(Context context, String stage) throws Exception {
        JSONObject state = new JSONObject(PairingBridge.stateJson(context));
        JSONObject snapshot = state.optJSONObject("snapshot");
        JSONObject theme = snapshot == null ? null : snapshot.optJSONObject("theme");
        Bundle status = new Bundle();
        status.putString("stage", stage);
        status.putString("phase", state.optString("phase"));
        status.putString("theme", theme == null ? "" : theme.optString("name"));
        status.putString("hasPendingRequest", state.optString("hasPendingRequest"));
        status.putString("hasPairedDevice", state.optString("hasPairedDevice"));
        status.putString("lastError", state.optString("lastError"));
        Log.i(TAG, stage + ": phase=" + state.optString("phase")
                + ", theme=" + (theme == null ? "" : theme.optString("name"))
                + ", pending=" + state.optString("hasPendingRequest")
                + ", paired=" + state.optString("hasPairedDevice")
                + ", error=" + state.optString("lastError"));
        sendStatus(1, status);
    }

    private static void validateSnapshot(JSONObject state, JSONObject snapshot, JSONObject theme)
            throws Exception {
        JSONObject colors = theme.optJSONObject("colors");
        JSONObject shell = theme.optJSONObject("shell");
        JSONObject spacing = shell == null ? null : shell.optJSONObject("spacing");
        JSONObject font = shell == null ? null : shell.optJSONObject("font");
        JSONObject geometry = snapshot.optJSONObject("hyprland");
        if (colors == null || !"#a7c080".equals(colors.optString("accent"))
                || spacing == null || spacing.optInt("row-gap", -1) != 7
                || font == null || font.optInt("base-size", -1) != 14
                || font.optInt("body", -1) != 13 || font.optInt("caption", -1) != 11
                || spacing.optInt("panel-gap", -1) != 17
                || geometry == null || geometry.optInt("rounding", -1) != 13
                || Math.abs(geometry.optDouble("roundingPower", -1) - 4.0) > 0.001
                || geometry.optInt("borderWidth", -1) != 2
                || geometry.optInt("gapsIn", -1) != 5 || geometry.optInt("gapsOut", -1) != 10
                || !geometry.optString("activeBorder").contains("37deg")
                || geometry.optString("activeBorder").split("rgba\\(").length != 4) {
            throw new IllegalStateException("Theme colors, spacing, or Hyprland geometry did not sync.");
        }
        String wallpaperPath = state.optString("wallpaperPath", "");
        File wallpaper = new File(wallpaperPath);
        if (!wallpaper.isFile() || wallpaper.length() == 0) {
            throw new IllegalStateException("The paired wallpaper was not cached on Android.");
        }
    }

    private static void awaitRevocation(Context context) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + REVOCATION_TIMEOUT_MILLIS;
        long nextAttempt = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            long now = SystemClock.elapsedRealtime();
            if (now >= nextAttempt) {
                PairingBridge.revoke(context);
                nextAttempt = now + 1_000L;
            }
            JSONObject state = new JSONObject(PairingBridge.stateJson(context));
            if ("unpaired".equals(state.optString("phase"))
                    && !state.optBoolean("hasPairedDevice")
                    && state.optString("wallpaperPath", "").isEmpty()) {
                return;
            }
            SystemClock.sleep(250L);
        }
        throw new IllegalStateException("Timed out waiting for pairing revocation.");
    }

    private void runFeatureSmoke(Context context, boolean clipboardStress) throws Exception {
        JSONObject credentials = PairingStore.readCredential(context);
        if (credentials == null || !credentials.has("token")) {
            throw new IllegalStateException("Feature smoke requires a paired device token.");
        }

        reportStage("feature_permissions_waiting");
        JSONObject enabled = awaitFeatures(context, credentials, null, true, 30_000L);
        JSONObject permissions = enabled.getJSONObject("permissions");
        String[] permissionNames = {
            "clipboard", "files.send", "files.receive", "media.control", "system.read",
            "workspace.control", "commands"
        };
        for (String permission : permissionNames) {
            if (!permissions.optBoolean(permission)) {
                throw new IllegalStateException("The host did not grant " + permission + ".");
            }
        }

        reportStage("system_status_waiting");
        JSONObject system = requireJson(credentials, "/v1/system", "GET", null, 200);
        long memoryTotal = system.optLong("memoryTotalBytes", -1);
        long memoryAvailable = system.optLong("memoryAvailableBytes", -1);
        JSONArray loadAverage = system.optJSONArray("loadAverage");
        if (system.optLong("capturedAt", 0) <= 0 || memoryTotal <= 0
                || memoryAvailable < 0 || memoryAvailable > memoryTotal
                || !system.has("battery") || loadAverage == null || loadAverage.length() != 3
                || loadAverage.optDouble(0, -1) < 0) {
            throw new IllegalStateException("The computer status response is incomplete.");
        }
        reportStage("workspace_status_waiting");
        JSONObject workspaces = requireJson(credentials, "/v1/workspaces", "GET", null, 200);
        JSONArray workspaceList = workspaces.optJSONArray("workspaces");
        if (workspaceList == null || workspaceList.length() < 2
                || !hasFocusedWorkspace(workspaceList, 1)) {
            throw new IllegalStateException("The Hyprland workspace response is invalid.");
        }
        reportStage("media_status_waiting");
        JSONObject media = requireJson(credentials, "/v1/media", "GET", null, 200);
        JSONArray players = media.optJSONArray("players");
        JSONObject firstPlayer = players == null ? null : players.optJSONObject(0);
        if (players == null || players.length() != 1 || firstPlayer == null
                || !"mpv".equals(firstPlayer.optString("player"))
                || !"Before next".equals(firstPlayer.optString("title"))) {
            throw new IllegalStateException("The MPRIS response is invalid.");
        }

        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            throw new IllegalStateException("Android clipboard service is unavailable.");
        }
        // The plugin exposes the actual desktop clipboard. Verify its initial value
        // reaches Android before replacing that value with the phone clipboard.
        String largeQuotes = clipboardStress ? repeated('"', 65_536) : "from computer";
        setClipboardOnMainThread(clipboard, "");
        PairingBridge.pullClipboard(context);
        awaitClipboard(clipboard, largeQuotes, 15_000L);
        PersistableBundle clipboardExtras = clipboard.getPrimaryClip().getDescription().getExtras();
        if (clipboardExtras == null || !clipboardExtras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)) {
            throw new IllegalStateException("Incoming clipboard text was not marked sensitive.");
        }

        String phoneClipboard = clipboardStress
                ? repeated("a\n", 30_000) : "OmaApp CI phone clipboard";
        setClipboardOnMainThread(clipboard, phoneClipboard);
        PairingBridge.pushClipboard(context);
        awaitFeatureMessage(context, "Phone clipboard sent to the computer.", 15_000L);
        JSONObject computerClipboard = requireJson(credentials, "/v1/clipboard", "GET", null, 200);
        if (!phoneClipboard.equals(computerClipboard.optString("text"))) {
            throw new IllegalStateException("Phone clipboard text did not reach the computer.");
        }

        PairingBridge.activateWorkspace(context, 2);
        awaitAction(context);
        JSONObject switched = requireJson(credentials, "/v1/workspaces", "GET", null, 200);
        if (!hasFocusedWorkspace(switched.optJSONArray("workspaces"), 2)) {
            throw new IllegalStateException("The selected Hyprland workspace did not become active.");
        }
        PairingBridge.mediaAction(context, "mpv", "next");
        awaitAction(context);
        JSONObject advanced = requireJson(credentials, "/v1/media", "GET", null, 200);
        JSONArray advancedPlayers = advanced.optJSONArray("players");
        JSONObject advancedPlayer = advancedPlayers == null ? null : advancedPlayers.optJSONObject(0);
        if (advancedPlayers == null || advancedPlayers.length() != 1
                || advancedPlayer == null
                || !"After next".equals(advancedPlayer.optString("title"))) {
            throw new IllegalStateException("MPRIS did not report the updated track after next.");
        }
        PairingBridge.executeCommand(context, "ci-safe");
        awaitAction(context);

        transferFiles(credentials);

        PairingBridge.mediaAction(context, "mpv;id", "next");
        awaitFeatureMessage(context, "This media action is not allowed.", 2_000L);
        PairingBridge.executeCommand(context, "../etc/passwd");
        awaitFeatureMessage(context, "This computer command is not configured.", 2_000L);

        Set<String> disabledPermissions = new HashSet<>();
        for (String permission : permissionNames) {
            String safePermission = permission.replace('.', '_');
            reportStage("feature_disable_requested_" + safePermission);
            JSONObject disabled = awaitFeatures(context, credentials, permission, false, 30_000L);
            JSONObject remaining = disabled.getJSONObject("permissions");
            disabledPermissions.add(permission);
            for (String other : permissionNames) {
                boolean shouldRemainEnabled = !disabledPermissions.contains(other);
                if (remaining.optBoolean(other) != shouldRemainEnabled) {
                    throw new IllegalStateException("Changing " + permission
                            + " left " + other + " in an unexpected permission state.");
                }
            }
            boolean post = "files.receive".equals(permission) || "commands".equals(permission);
            JSONObject body = "files.receive".equals(permission) ? new JSONObject()
                    : ("commands".equals(permission)
                            ? new JSONObject().put("commandId", "ci-safe") : null);
            String path = deniedPath(permission);
            PairingBridge.HttpResult denied = PairingBridge.executeJson(credentials, path,
                    post ? "POST" : "GET", body, true);
            if (denied.status != 403
                    || !"permission_required".equals(denied.body.optString("error"))) {
                throw new IllegalStateException("Disabled permission did not reject " + path + ".");
            }
            if ("files.send".equals(permission)) {
                uploadFile(credentials, "receive-remains-enabled.txt",
                        "the other file permission remains enabled".getBytes(StandardCharsets.UTF_8));
            } else if ("files.receive".equals(permission)) {
                requireJson(credentials, "/v1/files", "GET", null, 200);
            }

            reportStage("feature_enable_requested_" + safePermission);
            JSONObject restored = awaitFeatures(context, credentials, permission, true, 30_000L);
            disabledPermissions.remove(permission);
            JSONObject restoredPermissions = restored.getJSONObject("permissions");
            for (String other : permissionNames) {
                boolean shouldRemainEnabled = !disabledPermissions.contains(other);
                if (restoredPermissions.optBoolean(other) != shouldRemainEnabled) {
                    throw new IllegalStateException("Restoring " + permission
                            + " changed the state of " + other + ".");
                }
            }
        }
        reportStage("features_passed");
    }

    private static boolean hasFocusedWorkspace(JSONArray workspaces, int expectedId)
            throws Exception {
        if (workspaces == null) {
            return false;
        }
        for (int index = 0; index < workspaces.length(); ++index) {
            JSONObject workspace = workspaces.optJSONObject(index);
            if (workspace != null && workspace.optInt("id", -1) == expectedId
                    && workspace.optBoolean("focused")) {
                return true;
            }
        }
        return false;
    }

    private void runLauncherShortcutSmoke(Context context) throws Exception {
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        if (shortcuts == null) {
            throw new IllegalStateException("Android dynamic shortcuts are not available.");
        }
        List<ShortcutInfo> dynamic = shortcuts.getDynamicShortcuts();
        if (dynamic.isEmpty()) {
            throw new IllegalStateException("Pinning an app did not publish a dynamic launcher shortcut.");
        }
        boolean resolvesToInstalledLauncherActivity = false;
        for (ShortcutInfo shortcut : dynamic) {
            if (shortcut.getIntent() == null || shortcut.getIntent().getComponent() == null
                    || shortcut.getShortLabel() == null || shortcut.getShortLabel().length() == 0) {
                throw new IllegalStateException("A dynamic launcher shortcut is missing its target or label.");
            }
            if (context.getPackageManager().resolveActivity(
                    shortcut.getIntent(), 0) != null) {
                resolvesToInstalledLauncherActivity = true;
            }
        }
        if (!resolvesToInstalledLauncherActivity) {
            throw new IllegalStateException("No dynamic shortcut resolves to an installed app activity.");
        }
        reportStage("launcher_shortcuts_passed");
    }

    private static JSONObject awaitFeatures(Context context, JSONObject credentials,
                                            String permission, boolean expected, long timeoutMillis)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        JSONObject last = new JSONObject();
        while (SystemClock.elapsedRealtime() < deadline) {
            PairingBridge.HttpResult result = PairingBridge.executeJson(
                    credentials, "/v1/features", "GET", null, true);
            if (result.status != 200) {
                throw new IllegalStateException("Could not read computer feature permissions.");
            }
            last = result.body;
            JSONObject permissions = last.optJSONObject("permissions");
            if (permissions != null && (permission == null
                    ? allPermissionsEqual(permissions, expected)
                    : permissions.optBoolean(permission) == expected)) {
                return last;
            }
            SystemClock.sleep(250L);
        }
        throw new IllegalStateException("Timed out waiting for host feature permission update: "
                + permission + "; last state=" + last.optString("permissions"));
    }

    private static boolean allPermissionsEqual(JSONObject permissions, boolean expected) {
        String[] names = {"clipboard", "files.send", "files.receive", "media.control",
                "system.read", "workspace.control", "commands"};
        for (String name : names) {
            if (permissions.optBoolean(name) != expected) {
                return false;
            }
        }
        return true;
    }

    private static JSONObject requireJson(JSONObject credentials, String path, String method,
                                           JSONObject body, int expectedStatus) throws Exception {
        PairingBridge.HttpResult result = PairingBridge.executeJson(
                credentials, path, method, body, true);
        if (result.status != expectedStatus) {
            throw new IllegalStateException(path + " returned HTTP " + result.status + ".");
        }
        return result.body;
    }

    private static void transferFiles(JSONObject credentials) throws Exception {
        uploadFile(credentials, "from-phone.txt",
                "from OmaApp Android CI".getBytes(StandardCharsets.UTF_8));

        JSONObject fileList = requireJson(credentials, "/v1/files", "GET", null, 200);
        JSONArray files = fileList.optJSONArray("files");
        boolean foundComputerFile = false;
        if (files != null) {
            for (int index = 0; index < files.length(); ++index) {
                JSONObject file = files.optJSONObject(index);
                if (file != null && "from-computer.txt".equals(file.optString("name"))) {
                    foundComputerFile = true;
                    break;
                }
            }
        }
        if (!foundComputerFile) {
            throw new IllegalStateException("The computer file listing omitted its shared file.");
        }
        HttpsURLConnection download = PairingBridge.openConnection(
                credentials, "/v1/files/from-computer.txt", "GET", true);
        try {
            if (download.getResponseCode() != 200) {
                throw new IllegalStateException("Android could not download the shared computer file.");
            }
            try (InputStream input = download.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) {
                        throw new IllegalStateException("The shared computer test file is too large.");
                    }
                    output.write(buffer, 0, count);
                }
                if (!"from Omarchy computer CI".equals(
                        new String(output.toByteArray(), StandardCharsets.UTF_8))) {
                    throw new IllegalStateException("The downloaded computer file did not match.");
                }
            }
        } finally {
            download.disconnect();
        }
    }

    private static void uploadFile(JSONObject credentials, String name, byte[] content)
            throws Exception {
        HttpsURLConnection upload = PairingBridge.openConnection(
                credentials, "/v1/files", "POST", true);
        try {
            upload.setDoOutput(true);
            upload.setFixedLengthStreamingMode(content.length);
            upload.setRequestProperty("Content-Type", "application/octet-stream");
            upload.setRequestProperty("X-OmaApp-File-Name", name);
            try (OutputStream output = upload.getOutputStream()) {
                output.write(content);
            }
            if (upload.getResponseCode() != 201) {
                throw new IllegalStateException("Android file upload was rejected.");
            }
        } finally {
            upload.disconnect();
        }
    }

    private static String deniedPath(String permission) {
        switch (permission) {
            case "clipboard": return "/v1/clipboard";
            case "files.send": return "/v1/files";
            case "media.control": return "/v1/media";
            case "system.read": return "/v1/system";
            case "workspace.control": return "/v1/workspaces";
            case "commands": return "/v1/commands/execute";
            case "files.receive": return "/v1/files";
            default: throw new IllegalArgumentException("Unknown feature permission: " + permission);
        }
    }

    private static void setClipboardOnMainThread(ClipboardManager clipboard, String text)
            throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            clipboard.setPrimaryClip(ClipData.newPlainText("OmaApp CI", text));
            completed.countDown();
        });
        if (!completed.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out setting the Android clipboard.");
        }
    }

    private static String repeated(char value, int count) {
        char[] characters = new char[count];
        java.util.Arrays.fill(characters, value);
        return new String(characters);
    }

    private static String repeated(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; ++index) {
            result.append(value);
        }
        return result.toString();
    }

    private static void awaitClipboard(ClipboardManager clipboard, String expected,
                                       long timeoutMillis) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        while (SystemClock.elapsedRealtime() < deadline) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0
                    && expected.contentEquals(clip.getItemAt(0).getText())) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new IllegalStateException("The computer clipboard text did not reach Android.");
    }

    private static void awaitFeatureMessage(Context context, String expected, long timeoutMillis)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        String lastMessage = "";
        while (SystemClock.elapsedRealtime() < deadline) {
            String message = context.getSharedPreferences(PairingBridge.PREFERENCES, Context.MODE_PRIVATE)
                    .getString("feature_message", "");
            lastMessage = message;
            if (expected.equals(message)) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new IllegalStateException("Timed out waiting for Android feature result: " + expected
                + "; last message: " + lastMessage);
    }

    private static void awaitAction(Context context) throws Exception {
        awaitFeatureMessage(context, "Sending command to the computer…", 2_000L);
        awaitFeatureMessage(context, "Computer action completed.", 15_000L);
    }

    private void reportStage(String stage) {
        Bundle status = new Bundle();
        status.putString("stage", stage);
        Log.i(TAG, "Feature smoke stage: " + stage);
        sendStatus(1, status);
    }

    private void report(String stage, JSONObject state) throws Exception {
        JSONObject snapshot = state.getJSONObject("snapshot");
        JSONObject theme = snapshot.getJSONObject("theme");
        JSONObject geometry = snapshot.getJSONObject("hyprland");
        Bundle status = new Bundle();
        status.putString("stage", stage);
        status.putString("theme", theme.optString("name"));
        status.putString("rounding", geometry.optString("rounding"));
        status.putString("roundingPower", geometry.optString("roundingPower"));
        status.putString("borderWidth", geometry.optString("borderWidth"));
        status.putString("activeBorder", geometry.optString("activeBorder"));
        sendStatus(1, status);
    }
}
