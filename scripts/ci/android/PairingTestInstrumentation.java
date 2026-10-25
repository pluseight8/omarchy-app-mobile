package app.omaapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
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
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
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
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
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
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.lang.reflect.Method;
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
    private static final int MAX_ACCESSIBILITY_NODES = 512;
    private static final int MAX_ACCESSIBILITY_WINDOWS = 16;
    private static final int MAX_APPWIDGET_DIAGNOSTIC_CHARS = 1_799;
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
            String widgetHostMode = testArguments.getString("androidWidgetHost", "");
            if ("setup".equals(widgetHostMode)) {
                runAndroidWidgetHostSetup(context);
                result.putString("stage", "android_widget_host_picker_bind_touch_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("restore".equals(widgetHostMode)) {
                runAndroidWidgetHostRestore(context);
                result.putString("stage", "android_widget_host_process_restore_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("remove".equals(widgetHostMode)) {
                runAndroidWidgetHostRemove(context);
                result.putString("stage", "android_widget_host_id_release_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (Boolean.parseBoolean(testArguments.getString("nativeQtTranslations"))) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(
                        context.getPackageName());
                if (launch == null) {
                    throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
                waitForIdleSync();
                String translated = "Computer files";
                for (int attempt = 0; attempt < 40 && "Computer files".equals(translated); ++attempt) {
                    translated = NativeStrings.get("Computer files");
                    if ("Computer files".equals(translated)) {
                        SystemClock.sleep(250L);
                    }
                }
                if (!"Файлы компьютера".equals(translated)) {
                    throw new IllegalStateException("Native Android UI did not use the Russian Qt catalog: "
                            + translated);
                }
                if (!"Действие на компьютере выполнено.".equals(
                        NativeStrings.featureMessage("Computer action completed."))) {
                    throw new IllegalStateException("Russian UI lost a recognized feature success message.");
                }
                if (!"Разрешите эту функцию для телефона в настройках плагина Omarchy.".equals(
                        NativeStrings.fileTransferError(
                                "Enable this feature for this phone in the Omarchy plugin permissions."))) {
                    throw new IllegalStateException("Russian file UI lost the plugin permission guidance.");
                }
                result.putString("stage", "native_qt_translations_passed");
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (Boolean.parseBoolean(testArguments.getString("nativeEnglishStrings"))) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(
                        context.getPackageName());
                if (launch == null) {
                    throw new IllegalStateException("OmaApp does not expose its normal launcher activity.");
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
                SystemClock.sleep(2_000L);
                String success = "Computer action completed.";
                String permission = "Enable this feature for this phone in the Omarchy plugin permissions.";
                if (!success.equals(NativeStrings.featureMessage(success))
                        || !permission.equals(NativeStrings.featureMessage(permission))
                        || !permission.equals(NativeStrings.fileTransferError(permission))
                        || !"Computer feature request failed (HTTP 403).".equals(
                                NativeStrings.fileTransferError("Computer feature request failed (HTTP 403)."))
                        || !"Computer file transfer failed. Check pairing and the network.".equals(
                                NativeStrings.fileTransferError("unrecognized server detail"))) {
                    throw new IllegalStateException("English Android UI did not preserve known native messages or contain unknown errors.");
                }
                result.putString("stage", "native_english_strings_passed");
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
                JSONObject reconnected = runDnsSdReconnectSmoke(context,
                        Integer.parseInt(dnsSdPort),
                        Integer.parseInt(testArguments.getString("dnsSdWrongCertPort", "0")),
                        testArguments.getString("dnsSdFingerprint", ""),
                        testArguments.getString("expectedTheme", ""));
                report("dns_sd_reconnected", reconnected);
                result.putString("stage", "dns_sd_reconnected");
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
    private JSONObject runDnsSdReconnectSmoke(Context context, int port, int wrongCertPort,
                                               String fingerprint, String expectedTheme) throws Exception {
        if (!PairingBridge.shouldThrottleDiscoveredRefresh(10_500L, 10_000L, false)
                || PairingBridge.shouldThrottleDiscoveredRefresh(10_500L, 10_000L, true)) {
            throw new IllegalStateException(
                    "A queued DNS-SD endpoint must bypass duplicate-announcement throttling.");
        }
        if (port < 1 || port > 65535 || wrongCertPort < 1 || wrongCertPort > 65535
                || wrongCertPort == port || fingerprint == null
                || !fingerprint.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("Bounded CI endpoints for the trusted and wrong TLS identities are required.");
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
            startDnsSdFixture(context,
                    "OmaApp-" + fingerprint.substring(0, 12),
                    host, discoveryPort, "0".repeat(64));
            try {
                SystemClock.sleep(3_000L);
                JSONObject stillSaved = PairingStore.readCredential(context);
                if (stillSaved == null || !originalUrl.equals(stillSaved.optString("serverUrl", ""))) {
                    throw new IllegalStateException("An untrusted DNS-SD fingerprint changed the saved endpoint.");
                }
            } finally {
                stopDnsSdFixture(context);
                SystemClock.sleep(500L);
            }

            // A spoof can copy both the saved service-name hint and TXT fingerprint. It still
            // must not move the endpoint unless the actual TLS certificate is the QR-pinned one.
            TestTlsProxy wrongCertificateProxy = new TestTlsProxy(wrongCertPort);
            try {
                wrongCertificateProxy.start();
                startDnsSdFixture(context,
                        "OmaApp-" + fingerprint.substring(0, 12),
                        wrongCertificateProxy.getLocalAddress(), wrongCertificateProxy.getPort(),
                        fingerprint);
                long deadline = SystemClock.uptimeMillis() + 6_000L;
                while (SystemClock.uptimeMillis() < deadline
                        && (wrongCertificateProxy.getForwardedConnectionCount() == 0
                        || wrongCertificateProxy.getActiveConnectionCount() != 0)) {
                    SystemClock.sleep(200L);
                }
                if (wrongCertificateProxy.getForwardedConnectionCount() == 0) {
                    throw new IllegalStateException("Android did not attempt TLS against the spoofed DNS-SD endpoint.");
                }
                if (wrongCertificateProxy.getActiveConnectionCount() != 0) {
                    throw new IllegalStateException("TLS against the spoofed DNS-SD endpoint did not finish within its bounded window.");
                }
                long unchangedDeadline = SystemClock.uptimeMillis() + 2_000L;
                do {
                    JSONObject stillSaved = PairingStore.readCredential(context);
                    if (stillSaved == null || !originalUrl.equals(
                            stillSaved.optString("serverUrl", ""))) {
                        throw new IllegalStateException("A matching DNS-SD hint with a wrong TLS certificate changed the saved endpoint.");
                    }
                    SystemClock.sleep(200L);
                } while (SystemClock.uptimeMillis() < unchangedDeadline);
            } finally {
                stopDnsSdFixture(context);
                wrongCertificateProxy.close();
            }
            // Keep the valid endpoint outside PairingBridge's 10-second duplicate reconnect window.
            SystemClock.sleep(10_500L);

            startDnsSdFixture(context,
                    "OmaApp-" + fingerprint.substring(0, 12),
                    host, discoveryPort, fingerprint);
            try {
                long deadline = SystemClock.uptimeMillis() + 25_000L;
                JSONObject updated = PairingStore.readCredential(context);
                while (SystemClock.uptimeMillis() < deadline
                        && (updated == null
                        || originalUrl.equals(updated.optString("serverUrl", ""))
                        || !isPrivateTlsEndpoint(updated.optString("serverUrl", ""), discoveryPort))) {
                    SystemClock.sleep(200L);
                    updated = PairingStore.readCredential(context);
                }
                if (updated == null
                        || originalUrl.equals(updated.optString("serverUrl", ""))
                        || !isPrivateTlsEndpoint(updated.optString("serverUrl", ""), discoveryPort)) {
                    throw new IllegalStateException("Android NSD did not reconnect to the TLS-pinned endpoint.");
                }
                if (!token.equals(updated.optString("token", ""))
                        || !pinnedFingerprint.equalsIgnoreCase(updated.optString("fingerprint", ""))) {
                    throw new IllegalStateException("DNS-SD reconnect changed saved pairing trust material.");
                }
                Log.i(TAG, "Android NSD resolved the fixture and accepted the endpoint after pinned TLS verification.");
                // Keep the ephemeral NSD fixture and TLS forwarder alive until the
                // saved device has applied the post-restart theme snapshot.
                return awaitTheme(context, expectedTheme, 30_000L, false);
            } finally {
                stopDnsSdFixture(context);
            }
        } finally {
            proxy.close();
        }
    }

    private static boolean isPrivateTlsEndpoint(String value, int expectedPort) {
        try {
            URI endpoint = URI.create(value);
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || (endpoint.getPath() != null && !endpoint.getPath().isEmpty())
                    || endpoint.getPort() != expectedPort || endpoint.getHost() == null) {
                return false;
            }
            InetAddress address = InetAddress.getByName(endpoint.getHost());
            return address instanceof Inet4Address && address.isSiteLocalAddress();
        } catch (IllegalArgumentException | IOException error) {
            return false;
        }
    }

    private static void startDnsSdFixture(Context context, String name, InetAddress host,
                                          int port, String fingerprint) {
        Intent serviceIntent = new Intent().setComponent(new ComponentName(
                "app.omaapp.launcherfixture",
                "app.omaapp.launcherfixture.NsdFixtureService"));
        serviceIntent.putExtra("serviceName", name);
        serviceIntent.putExtra("serviceHost", host.getHostAddress());
        serviceIntent.putExtra("servicePort", port);
        serviceIntent.putExtra("fingerprint", fingerprint);
        Intent fixtureActivity = new Intent().setComponent(new ComponentName(
                "app.omaapp.launcherfixture",
                "app.omaapp.launcherfixture.NsdFixtureActivity"));
        fixtureActivity.putExtras(serviceIntent);
        fixtureActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(fixtureActivity);
    }

    private static void stopDnsSdFixture(Context context) {
        Intent fixtureActivity = new Intent().setComponent(new ComponentName(
                "app.omaapp.launcherfixture",
                "app.omaapp.launcherfixture.NsdFixtureActivity"));
        fixtureActivity.setAction("app.omaapp.launcherfixture.STOP_NSD_FIXTURE");
        fixtureActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(fixtureActivity);
    }

    /** Transparent TLS tunnel from an emulator-local NSD endpoint to the CI plugin fixture. */
    private static final class TestTlsProxy implements AutoCloseable {
        private final InetAddress localAddress;
        private final int targetPort;
        private final ServerSocket server;
        private volatile boolean closed;
        private volatile Socket clientSocket;
        private volatile Socket upstreamSocket;
        private final java.util.concurrent.atomic.AtomicInteger forwardedConnectionCount =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger activeConnectionCount =
                new java.util.concurrent.atomic.AtomicInteger();
        private Thread acceptThread;

        TestTlsProxy(int targetPort) throws Exception {
            this.targetPort = targetPort;
            localAddress = findLocalSiteIpv4();
            server = new ServerSocket();
            server.setReuseAddress(false);
            // DNS-SD may resolve the emulator host through another local interface.
            // Advertise the selected site-local address, but accept on every interface.
            server.bind(new InetSocketAddress(0), 2);
            server.setSoTimeout(1_000);
        }

        InetAddress getLocalAddress() {
            return localAddress;
        }

        int getPort() {
            return server.getLocalPort();
        }

        int getForwardedConnectionCount() {
            return forwardedConnectionCount.get();
        }

        int getActiveConnectionCount() {
            return activeConnectionCount.get();
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
                    activeConnectionCount.incrementAndGet();
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
                forwardedConnectionCount.incrementAndGet();
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
                activeConnectionCount.decrementAndGet();
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
        saveAppWidgetScreenshot(activity, automation, "omaapp-appwidget-host-poc.png");
    }

    private static void saveAppWidgetScreenshot(Activity activity, UiAutomation automation,
                                                String fileName) throws Exception {
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
        File output = new File(directory, fileName);
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
        ArrayList<AccessibilityNodeInfo> pending = accessibilityRoots(automation);
        if (pending.isEmpty()) {
            return false;
        }
        boolean clicked = false;
        try {
            for (int index = 0; index < pending.size() && index < MAX_ACCESSIBILITY_NODES; index++) {
                AccessibilityNodeInfo node = pending.get(index);
                CharSequence nodeText = node.getText();
                CharSequence nodeDescription = node.getContentDescription();
                boolean matches = (nodeText != null && label.contentEquals(nodeText))
                        || (nodeDescription != null && label.contentEquals(nodeDescription));
                if (matches && node.isVisibleToUser() && node.isEnabled() && node.isClickable()
                        && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    clicked = true;
                    break;
                }
                if (matches) {
                    AccessibilityNodeInfo parent = node.getParent();
                    try {
                        for (int depth = 0; parent != null && depth < 8; depth++) {
                            if (parent.isVisibleToUser() && parent.isEnabled()
                                    && parent.isClickable()
                                    && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                clicked = true;
                                break;
                            }
                            AccessibilityNodeInfo ancestor = parent.getParent();
                            parent.recycle();
                            parent = ancestor;
                        }
                    } finally {
                        if (parent != null) {
                            parent.recycle();
                        }
                    }
                    if (clicked) {
                        break;
                    }
                }
                for (int child = 0; child < node.getChildCount()
                        && pending.size() < MAX_ACCESSIBILITY_NODES; child++) {
                    AccessibilityNodeInfo childNode = node.getChild(child);
                    if (childNode != null) {
                        pending.add(childNode);
                    }
                }
            }
        } finally {
            for (AccessibilityNodeInfo node : pending) {
                node.recycle();
            }
        }
        return clicked;
    }

    private static ArrayList<AccessibilityNodeInfo> accessibilityRoots(UiAutomation automation) {
        ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
        AccessibilityNodeInfo activeRoot = automation.getRootInActiveWindow();
        if (activeRoot != null) {
            roots.add(activeRoot);
        }
        List<AccessibilityWindowInfo> windows = automation.getWindows();
        if (windows != null) {
            try {
                int count = 0;
                for (AccessibilityWindowInfo window : windows) {
                    if (count++ >= MAX_ACCESSIBILITY_WINDOWS) {
                        break;
                    }
                    AccessibilityNodeInfo windowRoot = window.getRoot();
                    if (windowRoot != null && roots.size() < MAX_ACCESSIBILITY_WINDOWS + 1) {
                        roots.add(windowRoot);
                    } else if (windowRoot != null) {
                        windowRoot.recycle();
                    }
                }
            } finally {
                for (AccessibilityWindowInfo window : windows) {
                    window.recycle();
                }
            }
        }
        return roots;
    }

    private static boolean awaitAccessibilityText(UiAutomation automation, String text,
            long timeoutMillis) {
        if (text == null || text.isEmpty() || text.length() > 256 || timeoutMillis < 0) {
            return false;
        }
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
        do {
            if (accessibilityTreeContains(automation, text)) {
                return true;
            }
            if (SystemClock.uptimeMillis() >= deadline) {
                break;
            }
            SystemClock.sleep(250L);
        } while (true);
        return false;
    }

    private static boolean accessibilityTreeContains(UiAutomation automation, String expected) {
        ArrayList<AccessibilityNodeInfo> pending = accessibilityRoots(automation);
        if (pending.isEmpty()) {
            return false;
        }
        boolean found = false;
        try {
            for (int index = 0; index < pending.size() && index < MAX_ACCESSIBILITY_NODES; index++) {
                AccessibilityNodeInfo node = pending.get(index);
                CharSequence nodeText = node.getText();
                CharSequence nodeDescription = node.getContentDescription();
                if ((nodeText != null && nodeText.toString().contains(expected))
                        || (nodeDescription != null
                                && nodeDescription.toString().contains(expected))) {
                    found = true;
                    break;
                }
                for (int child = 0; child < node.getChildCount()
                        && pending.size() < MAX_ACCESSIBILITY_NODES; child++) {
                    AccessibilityNodeInfo childNode = node.getChild(child);
                    if (childNode != null) {
                        pending.add(childNode);
                    }
                }
            }
        } finally {
            for (AccessibilityNodeInfo node : pending) {
                node.recycle();
            }
        }
        return found;
    }

    private static String describeInteractiveWindows(UiAutomation automation) {
        StringBuilder summary = new StringBuilder("windows=");
        List<AccessibilityWindowInfo> windows = automation.getWindows();
        if (windows == null) {
            return "windows=unavailable";
        }
        try {
            int count = 0;
            for (AccessibilityWindowInfo window : windows) {
                if (count >= MAX_ACCESSIBILITY_WINDOWS) {
                    summary.append("; …");
                    break;
                }
                Rect bounds = new Rect();
                window.getBoundsInScreen(bounds);
                String entry = (count == 0 ? "" : "; ")
                        + "id=" + window.getId()
                        + ",type=" + window.getType()
                        + ",active=" + window.isActive()
                        + ",focused=" + window.isFocused()
                        + ",bounds=" + bounds.flattenToString();
                if (summary.length() + entry.length() > MAX_APPWIDGET_DIAGNOSTIC_CHARS) {
                    summary.append("…");
                    break;
                }
                summary.append(entry);
                count++;
            }
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                window.recycle();
            }
        }
        return summary.toString();
    }

    private static boolean awaitAccessibleControlClick(UiAutomation automation, String label,
            long timeoutMillis) {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            if (clickAccessibleControl(automation, label)) {
                return true;
            }
            SystemClock.sleep(250L);
        }
        return false;
    }

    private static boolean clickWidgetBindConfirmation(UiAutomation automation) {
        for (String label : new String[] {
                "Allow", "Add", "Add widget", "Create", "Create widget",
                "Разрешить", "Добавить", "Создать", "Создать виджет"}) {
            if (clickAccessibleControl(automation, label)) {
                return true;
            }
        }
        return false;
    }

    private static void runOwlRuntimeSmoke() throws Exception {
        String clockSource = "widget \"CI clock\" {\n"
                + "  meta { owl: 1 }\n"
                + "  source now = clock every 1s\n"
                + "  size: 2x1\n"
                + "  background: surface\n"
                + "  Column {\n"
                + "    Text { text: fmtTime(now, \"HH:mm\"); style: huge; color: accent }\n"
                + "    Canvas { width: 96; height: 32; circle(width / 2, height / 2, 6, fill: accent) }\n"
                + "  }\n"
                + "}\n";
        JSONObject theme = new JSONObject()
                .put("foreground", "#cacccc")
                .put("background", "#101315")
                .put("accent", "#a7c080")
                .put("surface", "#202326");
        JSONObject rendered = new JSONObject(OwlBridge.render(clockSource, 320.0, 156.0,
                theme.toString()));
        if (rendered.has("error") || !"CI clock".equals(rendered.optString("name"))) {
            throw new IllegalStateException("The embedded OWL clock did not compile and render: "
                    + rendered.optString("error"));
        }
        JSONArray nodes = rendered.optJSONArray("nodes");
        if (nodes == null || nodes.length() != 1
                || !"Column".equals(nodes.optJSONObject(0).optString("type"))) {
            throw new IllegalStateException("OWL layout nodes were not returned to the launcher.");
        }
        JSONArray children = nodes.optJSONObject(0).optJSONArray("children");
        if (children == null || children.length() != 2
                || !"Text".equals(children.optJSONObject(0).optString("type"))
                || !"Canvas".equals(children.optJSONObject(1).optString("type"))) {
            throw new IllegalStateException("OWL text and canvas nodes were not rendered.");
        }
        JSONArray drawing = children.optJSONObject(1).optJSONArray("children");
        if (drawing == null || drawing.length() != 1
                || !"circle".equals(drawing.optJSONObject(0).optString("op"))) {
            throw new IllegalStateException("OWL canvas drawing commands were not evaluated.");
        }
        JSONArray circleArgs = drawing.optJSONObject(0).optJSONArray("args");
        if (circleArgs == null || circleArgs.length() != 3
                || circleArgs.optDouble(0, Double.NaN) != 48.0
                || circleArgs.optDouble(1, Double.NaN) != 16.0
                || circleArgs.optDouble(2, Double.NaN) != 6.0) {
            throw new IllegalStateException("OWL canvas width and height were not evaluated in its local scope.");
        }
        JSONObject unsafe = new JSONObject(OwlBridge.render(
                "widget \"network\" { source data = http(\"https://example.invalid\") every 30s "
                        + "Text { text: data } }", 320.0, 156.0, theme.toString()));
        if (!unsafe.has("error")) {
            throw new IllegalStateException("The offline OWL runtime accepted a network source.");
        }
        JSONObject explosiveValue = new JSONObject(OwlBridge.render(
                "widget \"serialization budget\" { meta { owl: 11 } "
                        + "Text { text: reduce(range(30), (a, x) => [a, a], [1]) } }",
                320.0, 156.0, theme.toString()));
        if (!explosiveValue.optString("error").contains("serialization budget")) {
            throw new IllegalStateException("OWL recursive values escaped the bounded JSON serializer: "
                    + explosiveValue.optString("error"));
        }
        String boxSource = "widget \"CI box\" {\n"
                + "  meta { owl: 1 }\n"
                + "  size: 2x1\n"
                + "  Box { align: bottomEnd; width: 64; height: 32; Text { text: \"Overlay\" } }\n"
                + "}\n";
        JSONObject box = new JSONObject(OwlBridge.render(boxSource, 320.0, 156.0, theme.toString()));
        JSONArray boxNodes = box.optJSONArray("nodes");
        if (box.has("error") || boxNodes == null || boxNodes.length() != 1
                || !"Box".equals(boxNodes.optJSONObject(0).optString("type"))
                || !"bottomEnd".equals(boxNodes.optJSONObject(0).optJSONObject("props").optString("align"))) {
            throw new IllegalStateException("OWL Box alignment and overlay layout did not compile.");
        }

        String png = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAoElEQVQY04WPLQ+CUABFD34kOpVR3jS5Z6Mp+WUcP4Riohn4IQzyy2KjgSYdBkbVTFKnxZe56Wz37G4XRmIZyE7JAkgBBWggjjbJzTLl+/W56rymKu/4gUCFktl8upz8B1JdNAznJ4f1iqF5oPMGIDWCqo4tkecCEHkuVdkCKCNofyvIup4vkHU9fiAAtBFitZPY0mFfX7ClgwolQDz64gcxODgdIU6pCQAAAABJRU5ErkJggg==";
        String assetWidget = "widget \"CI asset\" {\n"
                + "  meta { owl: 11 }\n"
                + "  asset badge = base64\"" + png + "\"\n"
                + "  Row { Image { src: badge; width: 16; height: 16; fit: contain; label: \"badge\" } }\n"
                + "  Canvas { width: 32; height: 32; image(badge, 2, 3, 16, 16) }\n"
                + "}\n";
        JSONObject assetRender = new JSONObject(OwlBridge.render(assetWidget, 320.0, 156.0,
                theme.toString()));
        JSONArray assetNodes = assetRender.optJSONArray("nodes");
        if (assetRender.has("error") || assetNodes == null || assetNodes.length() != 2) {
            throw new IllegalStateException("The offline OWL image and canvas nodes did not render: "
                    + assetRender.optString("error"));
        }
        JSONObject imageNode = assetNodes.optJSONObject(0).optJSONArray("children").optJSONObject(0);
        String expectedImage = "data:image/png;base64," + png;
        if (!"Image".equals(imageNode.optString("type"))
                || !expectedImage.equals(imageNode.optJSONObject("props").optString("src"))) {
            throw new IllegalStateException("OWL Image did not receive its self-contained raster asset.");
        }
        JSONArray canvasCommands = assetNodes.optJSONObject(1).optJSONArray("children");
        JSONObject imageCommand = canvasCommands == null ? null : canvasCommands.optJSONObject(0);
        JSONArray imageArgs = imageCommand == null ? null : imageCommand.optJSONArray("args");
        if (imageCommand == null || !"image".equals(imageCommand.optString("op"))
                || imageArgs == null || imageArgs.length() != 5
                || !expectedImage.equals(imageArgs.optString(0))
                || imageArgs.optDouble(1, -1) != 2 || imageArgs.optDouble(2, -1) != 3
                || imageArgs.optDouble(3, -1) != 16 || imageArgs.optDouble(4, -1) != 16) {
            throw new IllegalStateException("OWL Canvas did not evaluate and bound its embedded image call.");
        }
        JSONObject remoteImage = new JSONObject(OwlBridge.render(
                "widget \"remote image\" {\n"
                        + "  state source = \"https://example.invalid/icon.png\"\n"
                        + "  Image { src: source }\n"
                        + "}\n",
                320.0, 156.0, theme.toString()));
        if (!remoteImage.optString("error").contains("self-contained OWL asset")) {
            throw new IllegalStateException("The offline OWL Image path accepted a remote URL: "
                    + remoteImage.optString("error"));
        }
        JSONObject remoteCanvasImage = new JSONObject(OwlBridge.render(
                "widget \"remote canvas image\" {\n"
                        + "  state source = \"https://example.invalid/icon.png\"\n"
                        + "  Canvas { width: 32; height: 32; image(source, 2, 3, 16, 16) }\n"
                        + "}\n",
                320.0, 156.0, theme.toString()));
        if (!remoteCanvasImage.optString("error").contains("self-contained OWL asset")) {
            throw new IllegalStateException("The offline OWL Canvas path accepted a remote URL: "
                    + remoteCanvasImage.optString("error"));
        }
        JSONObject largeImage = new JSONObject(OwlBridge.render(
                assetWidget.replace("image(badge, 2, 3, 16, 16)", "image(badge, 2, 3, 5000, 16)"),
                320.0, 156.0, theme.toString()));
        if (!largeImage.has("error")) {
            throw new IllegalStateException("The offline OWL canvas accepted an unbounded image size.");
        }
        JSONObject portableWidgets = new JSONObject(OwlBridge.render(
                "widget \"CI visuals\" {\n"
                        + "  Flow { Text { text: \"offline\" } }\n"
                        + "  Grid { columns: 2; Text { text: \"a\" }; Text { text: \"b\" } }\n"
                        + "  Ring { value: 3; max: 4; width: 32; height: 32 }\n"
                        + "  Sparkline { values: [1, 3, 2]; area: true; width: 64; height: 24 }\n"
                        + "  Spacer { width: 8; height: 12 }\n"
                        + "}\n", 320.0, 156.0, theme.toString()));
        JSONArray visualNodes = portableWidgets.optJSONArray("nodes");
        String[] visualTypes = {"Flow", "Grid", "Ring", "Sparkline", "Spacer"};
        if (portableWidgets.has("error") || visualNodes == null
                || visualNodes.length() != visualTypes.length) {
            throw new IllegalStateException("Portable OWL visual nodes did not render: "
                    + portableWidgets.optString("error"));
        }
        for (int index = 0; index < visualTypes.length; ++index) {
            if (!visualTypes[index].equals(visualNodes.optJSONObject(index).optString("type"))) {
                throw new IllegalStateException("Offline OWL visual node order changed at " + index + ".");
            }
        }
        JSONObject nonImageAsset = new JSONObject(OwlBridge.render(
                "widget \"bad asset\" { asset text = base64\"SGVsbG8=\"; Image { src: text } }",
                320.0, 156.0, theme.toString()));
        if (!nonImageAsset.has("error")) {
            throw new IllegalStateException("The offline OWL image renderer accepted a non-image asset.");
        }
        Log.i(TAG, "Embedded OWL compiler, offline clock, canvas/image assets, Flow/Grid/Ring/Sparkline/Spacer, and network rejection passed.");
    }

    private static boolean awaitLatchlessView(AtomicReference<AppWidgetHostView> view,
            AtomicReference<String> error, long timeoutMillis) {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            if (view.get() != null || !error.get().isEmpty()) {
                return view.get() != null;
            }
            SystemClock.sleep(50L);
        }
        return false;
    }

    private static void runFileTransferStateSmoke() {
        Bundle savedState = new Bundle();
        FileTransferActivity.savePendingDownloadName(savedState, "fixture-report.txt");
        String restoredName = FileTransferActivity.restorePendingDownloadName(savedState);
        if (!"fixture-report.txt".equals(restoredName)) {
            throw new IllegalStateException(
                    "A pending download name did not survive Android Activity state restoration.");
        }
        FileTransferActivity.savePendingDownloadName(savedState, null);
        if (FileTransferActivity.restorePendingDownloadName(savedState) != null) {
            throw new IllegalStateException("A cleared pending download name remained in saved state.");
        }
        Log.i(TAG, "File picker lifecycle state passed.");
    }

    private static void runEventRefreshQueueSmoke(Context context) throws Exception {
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PairingBridge.PREFERENCES, Context.MODE_PRIVATE);
        preferences.edit().remove(EventRevisionCursor.KEY).commit();
        if (EventRevisionCursor.current(preferences) != 0L
                || !EventRevisionCursor.acceptEvent(preferences,
                new JSONObject().put("revision", 12L).put("kind", "theme"))
                || EventRevisionCursor.current(preferences) != 12L
                || EventRevisionCursor.acceptEvent(preferences,
                new JSONObject().put("revision", 9L).put("kind", "features"))
                || EventRevisionCursor.current(preferences) != 12L) {
            throw new IllegalStateException("The event cursor failed its monotonic event-only update.");
        }
        EventRevisionCursor.reset(preferences);

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

    private void runAndroidWidgetHostSetup(Context context) throws Exception {
        reportStage("android_widget_host_setup_started");
        verifyWidgetGridCollisionRules();
        ComponentName expectedProvider = new ComponentName(
                "app.omaapp.widgetfixture",
                "app.omaapp.widgetfixture.CiInteractiveWidgetProvider");
        try {
            context.getPackageManager().getApplicationInfo(expectedProvider.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("The CI Android widget provider is hidden from OmaApp; "
                    + "check its package-visibility declaration.", error);
        }
        reportStage("android_widget_host_provider_visible");
        Intent launch = new Intent(context, OmaAppActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
        reportStage("android_widget_host_home_launch_requested");
        UiAutomation automation = getUiAutomation();
        awaitAccessibilityText(automation, "Add Android widget", 15_000L, context);
        reportStage("android_widget_host_home_ready");
        Activity activity = AndroidWidgetHost.currentActivityForInstrumentation();
        if (!(activity instanceof OmaAppActivity)) {
            throw new IllegalStateException("Could not open OmaApp Home for widget hosting.");
        }
        if (!clickAccessibleText(automation, "Add Android widget")) {
            throw new IllegalStateException("Could not open the Android widget picker from OmaApp Home.");
        }
        reportStage("android_widget_host_picker_opened");

        String providerLabel = "OmaApp CI Interactive Widget";
        long bindDeadline = SystemClock.elapsedRealtime() + 90_000L;
        boolean providerChoiceClicked = false;
        boolean configurationClicked = false;
        boolean widgetRecordSaved = hasWidgetRecord(context, expectedProvider);
        while (SystemClock.elapsedRealtime() < bindDeadline
                && (!configurationClicked || !widgetRecordSaved)) {
            if (!providerChoiceClicked && clickAccessibleText(automation, providerLabel)) {
                providerChoiceClicked = true;
                reportStage("android_widget_host_provider_selected");
            } else if (providerChoiceClicked) {
                if (!configurationClicked && clickAccessibleText(automation, "CI_WIDGET_CONFIGURE")) {
                    configurationClicked = true;
                    reportStage("android_widget_host_configured");
                } else {
                    clickAccessibleText(automation, "Add to home screen", "Add to Home screen",
                            "Add widget", "Add", "Place widget");
                }
            }
            if (!providerChoiceClicked) {
                scrollSystemPicker(automation, context);
            }
            SystemClock.sleep(500L);
            widgetRecordSaved = hasWidgetRecord(context, expectedProvider);
        }
        if (!providerChoiceClicked || !configurationClicked
                || !hasWidgetRecord(context, expectedProvider)) {
            throw new IllegalStateException("Android's system picker did not finish binding/configuring "
                    + "the selected CI provider (selected=" + providerChoiceClicked
                    + ", configured=" + configurationClicked + ", recordSaved=" + widgetRecordSaved
                    + "). UI: " + accessibilityTree(automation));
        }
        awaitAccessibilityText(automation, "CI_WIDGET_READY", 30_000L, context);
        if (!clickAccessibleText(automation, "CI_WIDGET_ACTION")) {
            throw new IllegalStateException("The real RemoteViews action was not accessible in OmaApp Home.");
        }
        awaitAccessibilityText(automation, "CI_WIDGET_UPDATED", 15_000L, context);
        if (!hasWidgetRecord(context, expectedProvider)) {
            throw new IllegalStateException("The bound provider disappeared after its click action.");
        }

        JSONObject originalWidget = requireWidgetRecord(context, expectedProvider);
        verifyWidgetSchemaTwoMigration(context, originalWidget);
        originalWidget = requireWidgetRecord(context, expectedProvider);
        String widgetRecordId = originalWidget.getString("recordId");
        int widgetId = originalWidget.getInt("id");
        int resizedWidth = Math.max(originalWidget.getInt("minWidthDp"), 320);
        int resizedHeight = Math.max(originalWidget.getInt("minHeightDp"), 224);
        AtomicReference<String> layoutError = new AtomicReference<>("");
        runOnMainSync(() -> {
            try {
                if (!AndroidWidgetHost.placeWidgetRecord(context, widgetRecordId, 1,
                            24, 48, 2)
                        || !AndroidWidgetHost.resizeWidgetRecord(context, widgetRecordId,
                                resizedWidth, resizedHeight)
                        || !AndroidWidgetHost.placeWidgetRecord(context, widgetRecordId, 0,
                                48, 72, 2)
                        || AndroidWidgetHost.placeWidgetRecord(context, widgetRecordId, 0,
                                25, 72, 2)
                        || AndroidWidgetHost.placeWidgetRecord(context, widgetRecordId, 0,
                                48, -24, 2)) {
                    throw new IllegalStateException("The AppWidgetHost rejected grid/size updates or accepted invalid coordinates.");
                }
            } catch (Exception error) {
                layoutError.set(error.getMessage());
            }
        });
        if (!layoutError.get().isEmpty()) {
            throw new IllegalStateException(layoutError.get());
        }
        JSONObject resizedWidget = requireWidgetRecord(context, expectedProvider);
        if (resizedWidget.getInt("id") != widgetId
                || !widgetRecordId.equals(resizedWidget.getString("recordId"))
                || resizedWidget.getInt("page") != 0 || resizedWidget.getInt("order") != 0
                || resizedWidget.getInt("xDp") != 48 || resizedWidget.getInt("yDp") != 72
                || resizedWidget.getInt("widthDp") != resizedWidth
                || resizedWidget.getInt("heightDp") != resizedHeight) {
            throw new IllegalStateException("The Android widget placement/size did not round-trip: "
                    + resizedWidget);
        }
        awaitWidgetOptions(context, widgetId, resizedWidth, resizedHeight, 10_000L);
        awaitAccessibilityText(automation, "Move Android widget down", 20_000L, context);
        if (!clickAccessibleText(automation, "Move Android widget down")) {
            throw new IllegalStateException("The Android widget grid movement control was not accessible.");
        }
        if (!awaitWidgetPosition(context, expectedProvider, 0, 48, 96, 10_000L)) {
            throw new IllegalStateException("The Home screen could not save an accessible grid move.");
        }
        awaitAccessibilityText(automation, "Undo Android widget layout change", 15_000L, context);
        if (!clickAccessibleText(automation, "Undo Android widget layout change")
                || !awaitWidgetPosition(context, expectedProvider, 0, 48, 72, 10_000L)) {
            throw new IllegalStateException("The Home screen could not undo the Android widget grid move.");
        }
        reportStage("android_widget_host_qml_controls_passed");
        int handleResizedWidth = Math.min(1200, resizedWidth + 24);
        int handleResizedHeight = Math.min(1200, resizedHeight + 24);
        if (!dragResizeHandle(context, widgetId, 24, 24)
                || !awaitWidgetDimensions(context, expectedProvider, handleResizedWidth,
                        handleResizedHeight, 10_000L)) {
            throw new IllegalStateException("The native Android resize handle did not persist its drag.");
        }
        awaitWidgetOptions(context, widgetId, handleResizedWidth, handleResizedHeight, 10_000L);
        reportStage("android_widget_host_resize_handle_passed");
        String widgetBackup = AndroidWidgetHost.exportWidgetBackup(context);
        JSONObject portableBackup = new JSONObject(widgetBackup);
        JSONArray portableWidgets = portableBackup.optJSONArray("widgets");
        if (portableBackup.optInt("schemaVersion", -1) != 2 || portableWidgets == null
                || portableWidgets.length() != 1 || widgetBackup.contains("\"id\"")) {
            throw new IllegalStateException("Android widget backup included a binding ID or lost its placement: "
                    + widgetBackup);
        }
        if (!context.getSharedPreferences("ci_appwidget_backup", Context.MODE_PRIVATE).edit()
                .putString("json", widgetBackup).commit()) {
            throw new IllegalStateException("The test could not retain the exported widget backup.");
        }
        saveAppWidgetScreenshot(activity, automation, "omaapp-appwidget-home-layout.png");
        reportStage("android_widget_host_layout_roundtrip_passed");

        // QtActivity owns a process-wide native Qt engine; forcing Activity.recreate() tears
        // down that engine and is not a supported way to test Android lifecycle restoration.
        // The next instrumentation stage force-stops this process and verifies a cold restore.
    }

    private static void verifyWidgetGridCollisionRules() throws Exception {
        JSONObject first = new JSONObject().put("page", 0).put("xDp", 0).put("yDp", 0)
                .put("widthDp", 320).put("heightDp", 240);
        JSONObject overlaps = new JSONObject().put("page", 0).put("xDp", 24).put("yDp", 24)
                .put("widthDp", 320).put("heightDp", 240);
        JSONObject touchesChromeBoundary = new JSONObject().put("page", 0).put("xDp", 0)
                .put("yDp", 496).put("widthDp", 320).put("heightDp", 240);
        Method conflictCheck = AndroidWidgetHost.class.getDeclaredMethod(
                "hasPlacementConflict", List.class, JSONObject.class, JSONObject.class);
        conflictCheck.setAccessible(true);
        List<JSONObject> occupied = Collections.singletonList(first);
        if (!((Boolean) conflictCheck.invoke(null, occupied, overlaps, null))
                || ((Boolean) conflictCheck.invoke(null, occupied, touchesChromeBoundary, null))) {
            throw new IllegalStateException("The widget grid did not reject overlaps or allowed cards to collide.");
        }
    }

    private void verifyWidgetSchemaTwoMigration(Context context, JSONObject current)
            throws Exception {
        JSONObject legacyWidget = new JSONObject()
                .put("recordId", current.getString("recordId"))
                .put("id", current.getInt("id"))
                .put("provider", current.getString("provider"))
                .put("label", current.getString("label"))
                .put("page", 0)
                .put("order", 0)
                .put("minWidthDp", current.getInt("minWidthDp"))
                .put("minHeightDp", current.getInt("minHeightDp"))
                .put("widthDp", current.getInt("widthDp"))
                .put("heightDp", current.getInt("heightDp"));
        JSONObject legacyRoot = new JSONObject()
                .put("schemaVersion", 2)
                .put("widgets", new JSONArray().put(legacyWidget));
        if (!context.getSharedPreferences("android_widget_host", Context.MODE_PRIVATE).edit()
                .putString("widgets", legacyRoot.toString()).commit()) {
            throw new IllegalStateException("Could not seed the prior widget layout schema.");
        }

        JSONObject migrated = requireWidgetRecord(context,
                ComponentName.unflattenFromString(current.getString("provider")));
        JSONObject stored = new JSONObject(context.getSharedPreferences("android_widget_host",
                Context.MODE_PRIVATE).getString("widgets", "{}"));
        if (stored.optInt("schemaVersion", -1) != 3
                || migrated.optInt("id", -1) != current.optInt("id", -2)
                || migrated.optInt("xDp", -1) != 0 || migrated.optInt("yDp", -1) != 0
                || migrated.optInt("widthDp", -1) != current.optInt("widthDp", -2)
                || migrated.optInt("heightDp", -1) != current.optInt("heightDp", -2)) {
            throw new IllegalStateException("The previous Android widget schema did not migrate safely: "
                    + migrated + " stored=" + stored);
        }
        reportStage("android_widget_host_schema_migration_passed");
    }

    private void runAndroidWidgetHostRestore(Context context) throws Exception {
        reportStage("android_widget_host_restore_started");
        ComponentName expectedProvider = new ComponentName(
                "app.omaapp.widgetfixture",
                "app.omaapp.widgetfixture.CiInteractiveWidgetProvider");
        Activity activity = null;
        long activityDeadline = SystemClock.elapsedRealtime() + 15_000L;
        while (SystemClock.elapsedRealtime() < activityDeadline) {
            activity = AndroidWidgetHost.currentActivityForInstrumentation();
            if (activity instanceof OmaAppActivity && !activity.isFinishing()) {
                break;
            }
            SystemClock.sleep(100L);
        }
        if (!(activity instanceof OmaAppActivity)) {
            throw new IllegalStateException("OmaApp did not attach its cold-started QtActivity after process restart.");
        }
        reportStage("android_widget_host_restore_activity_ready");
        if (!hasWidgetRecord(context, expectedProvider)) {
            throw new IllegalStateException("The Android widget ID/provider record did not survive process death.");
        }
        String exportedBackup = context.getSharedPreferences("ci_appwidget_backup", Context.MODE_PRIVATE)
                .getString("json", "");
        JSONArray backupWidgets = new JSONObject(exportedBackup).optJSONArray("widgets");
        JSONObject expectedPlacement = backupWidgets == null ? null : backupWidgets.optJSONObject(0);
        JSONObject restoredPlacement = requireWidgetRecord(context, expectedProvider);
        if (expectedPlacement == null
                || restoredPlacement.optInt("page", -1) != expectedPlacement.optInt("page", -2)
                || restoredPlacement.optInt("order", -1) != expectedPlacement.optInt("order", -2)
                || restoredPlacement.optInt("xDp", -1) != expectedPlacement.optInt("xDp", -2)
                || restoredPlacement.optInt("yDp", -1) != expectedPlacement.optInt("yDp", -2)
                || restoredPlacement.optInt("widthDp", -1) != expectedPlacement.optInt("widthDp", -2)
                || restoredPlacement.optInt("heightDp", -1) != expectedPlacement.optInt("heightDp", -2)
                || !"ready".equals(restoredPlacement.optString("state"))) {
            throw new IllegalStateException("The widget page/order/size did not survive process death: "
                    + restoredPlacement);
        }
        awaitWidgetOptions(context, restoredPlacement.getInt("id"),
                restoredPlacement.getInt("widthDp"), restoredPlacement.getInt("heightDp"),
                10_000L);
        reportStage("android_widget_host_geometry_restore_passed");
        UiAutomation automation = getUiAutomation();
        awaitAccessibilityText(automation, "CI_WIDGET_UPDATED", 30_000L, context);
        if (!clickAccessibleText(automation, "CI_WIDGET_ACTION")) {
            throw new IllegalStateException("The restored Android widget stopped receiving touch after process death.");
        }
        awaitAccessibilityText(automation, "CI_WIDGET_UPDATED_TWICE", 15_000L, context);
        if (!hasWidgetRecord(context, expectedProvider)) {
            throw new IllegalStateException("The restored widget provider disappeared after its click action.");
        }
    }

    private void runAndroidWidgetHostRemove(Context context) throws Exception {
        reportStage("android_widget_host_remove_started");
        Intent launch = new Intent(context, OmaAppActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
        UiAutomation automation = getUiAutomation();
        Activity activity = null;
        long activityDeadline = SystemClock.elapsedRealtime() + 15_000L;
        while (SystemClock.elapsedRealtime() < activityDeadline) {
            activity = AndroidWidgetHost.currentActivityForInstrumentation();
            if (activity instanceof OmaAppActivity && !activity.isFinishing()) {
                break;
            }
            SystemClock.sleep(100L);
        }
        if (!(activity instanceof OmaAppActivity) || activity.isFinishing()) {
            throw new IllegalStateException("OmaApp Home did not attach before widget removal.");
        }
        awaitAccessibilityText(automation, "Add Android widget", 15_000L, context);

        ComponentName expectedProvider = new ComponentName(
                "app.omaapp.widgetfixture",
                "app.omaapp.widgetfixture.CiInteractiveWidgetProvider");
        JSONArray widgets = new JSONArray(AndroidWidgetHost.listWidgets(context));
        int widgetId = -1;
        for (int index = 0; index < widgets.length(); ++index) {
            JSONObject item = widgets.optJSONObject(index);
            if (item != null && expectedProvider.flattenToString().equals(item.optString("provider"))) {
                widgetId = item.optInt("id", -1);
                break;
            }
        }
        if (widgetId <= 0) {
            throw new IllegalStateException("No persisted CI widget ID was found before removal: "
                    + widgets);
        }
        if (!clickAccessibleText(automation, "Remove Android widget ")) {
            awaitAccessibilityText(automation, "Remove Android widget ", 20_000L, context);
            if (!clickAccessibleText(automation, "Remove Android widget ")) {
                throw new IllegalStateException("The Android widget removal control was not accessible.");
            }
        }
        awaitAccessibilityText(automation, "Remove this Android widget?", 10_000L, context);
        if (!clickAccessibleText(automation, "OK")) {
            throw new IllegalStateException("The Android widget removal confirmation was not accessible.");
        }
        long removalDeadline = SystemClock.elapsedRealtime() + 5_000L;
        while (SystemClock.elapsedRealtime() < removalDeadline
                && (AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) != null
                || hasWidgetRecord(context, expectedProvider))) {
            SystemClock.sleep(100L);
        }
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) != null
                || hasWidgetRecord(context, expectedProvider)) {
            throw new IllegalStateException("Widget ID " + widgetId
                    + " or its saved placement remains after removal.");
        }

        String backup = AndroidWidgetHost.exportWidgetBackup(context);
        // The removal smoke also covers the empty-list backup below; recreate a real
        // placement from provider metadata to verify the system-approved rebinding path.
        if (backup == null || backup.isEmpty()) {
            throw new IllegalStateException("The widget host could not export a versioned backup.");
        }
        JSONObject portableBackup = new JSONObject(backup);
        JSONArray backupWidgets = portableBackup.optJSONArray("widgets");
        if (portableBackup.optInt("schemaVersion", -1) != 2
                || backupWidgets == null || backupWidgets.length() != 0) {
            throw new IllegalStateException("A removed widget remained in the empty portable backup.");
        }
        // Use the layout captured before removal so the restore stage creates a placeholder
        // without importing the old Android widget ID or its provider grant.
        String widgetBackup = context.getSharedPreferences("ci_appwidget_backup", Context.MODE_PRIVATE)
                .getString("json", "");
        if (widgetBackup.isEmpty()) {
            throw new IllegalStateException("The setup stage did not preserve its real widget backup.");
        }
        AtomicReference<Boolean> backupRestored = new AtomicReference<>(false);
        runOnMainSync(() -> backupRestored.set(
                AndroidWidgetHost.restoreWidgetBackup(context, widgetBackup)));
        if (!backupRestored.get()) {
            throw new IllegalStateException("Android widget backup did not restore its placeholder.");
        }
        JSONObject placeholder = requireWidgetRecord(context, expectedProvider);
        String recordId = placeholder.getString("recordId");
        if (placeholder.getInt("id") != 0
                || !"needs_rebind".equals(placeholder.getString("state"))) {
            throw new IllegalStateException("Portable restore imported a binding ID instead of a placeholder: "
                    + placeholder);
        }
        reportStage("android_widget_host_backup_placeholder_passed");
        awaitAccessibilityText(automation, "Rebind", 15_000L, context);
        saveAppWidgetScreenshot(activity, automation, "omaapp-appwidget-rebind-placeholder.png");
        if (!clickAccessibleText(automation, "Rebind")) {
            throw new IllegalStateException("The restored widget placeholder did not expose Rebind.");
        }
        String providerLabel = "OmaApp CI Interactive Widget";
        long rebindDeadline = SystemClock.elapsedRealtime() + 90_000L;
        boolean providerChoiceClicked = false;
        boolean configurationClicked = false;
        while (SystemClock.elapsedRealtime() < rebindDeadline
                && (!configurationClicked || !hasBoundWidgetRecord(context, expectedProvider))) {
            if (!providerChoiceClicked && clickAccessibleText(automation, providerLabel)) {
                providerChoiceClicked = true;
            } else if (providerChoiceClicked) {
                if (!configurationClicked && clickAccessibleText(automation, "CI_WIDGET_CONFIGURE")) {
                    configurationClicked = true;
                } else {
                    clickAccessibleText(automation, "Add to home screen", "Add to Home screen",
                            "Add widget", "Add", "Place widget");
                }
            }
            if (!providerChoiceClicked) {
                scrollSystemPicker(automation, context);
            }
            SystemClock.sleep(500L);
        }
        if (!providerChoiceClicked || !configurationClicked
                || !hasBoundWidgetRecord(context, expectedProvider)) {
            throw new IllegalStateException("The Android system picker did not complete placeholder rebinding. UI: "
                    + accessibilityTree(automation));
        }
        JSONObject rebound = requireWidgetRecord(context, expectedProvider);
        int reboundId = rebound.getInt("id");
        if (!recordId.equals(rebound.getString("recordId")) || reboundId == widgetId
                || rebound.getInt("page") != placeholder.getInt("page")
                || rebound.getInt("xDp") != placeholder.getInt("xDp")
                || rebound.getInt("yDp") != placeholder.getInt("yDp")
                || rebound.getInt("widthDp") != placeholder.getInt("widthDp")
                || rebound.getInt("heightDp") != placeholder.getInt("heightDp")) {
            throw new IllegalStateException("The system rebind did not preserve placement with a fresh ID: "
                    + rebound);
        }
        awaitAccessibilityText(automation, "CI_WIDGET_READY", 15_000L, context);
        if (!clickAccessibleText(automation, "CI_WIDGET_ACTION")) {
            throw new IllegalStateException("The rebound RemoteViews action was not interactive.");
        }
        awaitAccessibilityText(automation, "CI_WIDGET_UPDATED", 15_000L, context);
        AtomicReference<Boolean> reboundRemoved = new AtomicReference<>(false);
        runOnMainSync(() -> reboundRemoved.set(AndroidWidgetHost.removeWidget(context, reboundId)));
        if (!reboundRemoved.get() || hasWidgetRecord(context, expectedProvider)
                || AppWidgetManager.getInstance(context).getAppWidgetInfo(reboundId) != null) {
            throw new IllegalStateException("The rebound fixture widget was not released cleanly.");
        }
        reportStage("android_widget_host_id_release_passed");
    }

    private static JSONObject requireWidgetRecord(Context context, ComponentName provider)
            throws Exception {
        JSONArray widgets = new JSONArray(AndroidWidgetHost.listWidgets(context));
        for (int index = 0; index < widgets.length(); ++index) {
            JSONObject item = widgets.optJSONObject(index);
            if (item != null && provider.flattenToString().equals(item.optString("provider"))) {
                return item;
            }
        }
        throw new IllegalStateException("No saved Android widget record exists for " + provider + ".");
    }

    private static boolean hasBoundWidgetRecord(Context context, ComponentName provider) {
        try {
            JSONObject widget = requireWidgetRecord(context, provider);
            int id = widget.optInt("id", 0);
            AppWidgetProviderInfo info = id > 0
                    ? AppWidgetManager.getInstance(context).getAppWidgetInfo(id) : null;
            return info != null && provider.equals(info.provider);
        } catch (Exception error) {
            return false;
        }
    }

    private static void awaitWidgetOptions(Context context, int widgetId, int width, int height,
                                           long timeoutMillis) throws Exception {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        Bundle options;
        do {
            options = manager.getAppWidgetOptions(widgetId);
            if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1) == width
                    && options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1) == height) {
                return;
            }
            SystemClock.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new IllegalStateException("AppWidget size options did not reach " + width + "x" + height
                + " dp: " + options);
    }

    private boolean dragResizeHandle(Context context, int widgetId, int deltaWidthDp,
                                     int deltaHeightDp) {
        AtomicReference<int[]> location = new AtomicReference<>();
        runOnMainSync(() -> {
            View handle = AndroidWidgetHost.resizeHandleForInstrumentation(widgetId);
            if (handle == null || handle.getVisibility() != View.VISIBLE
                    || handle.getWidth() <= 0 || handle.getHeight() <= 0) {
                return;
            }
            int[] point = new int[2];
            handle.getLocationOnScreen(point);
            location.set(new int[]{point[0], point[1], handle.getWidth(), handle.getHeight()});
        });
        int[] bounds = location.get();
        if (bounds == null) {
            return false;
        }
        float density = context.getResources().getDisplayMetrics().density;
        int startX = bounds[0] + bounds[2] / 2;
        int startY = bounds[1] + bounds[3] / 2;
        float endX = startX + deltaWidthDp * density;
        float endY = startY + deltaHeightDp * density;
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, startX, startY, 0);
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 80,
                MotionEvent.ACTION_MOVE, endX, endY, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 160,
                MotionEvent.ACTION_UP, endX, endY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            UiAutomation automation = getUiAutomation();
            return automation.injectInputEvent(down, true)
                    && automation.injectInputEvent(move, true)
                    && automation.injectInputEvent(up, true);
        } finally {
            down.recycle();
            move.recycle();
            up.recycle();
        }
    }

    private static boolean awaitWidgetDimensions(Context context, ComponentName provider,
                                                int width, int height, long timeoutMillis)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        do {
            JSONObject widget = requireWidgetRecord(context, provider);
            if (widget.optInt("widthDp", -1) == width
                    && widget.optInt("heightDp", -1) == height) {
                return true;
            }
            SystemClock.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        return false;
    }

    private static boolean awaitWidgetPosition(Context context, ComponentName provider,
                                               int page, int xDp, int yDp, long timeoutMillis)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        do {
            JSONObject widget = requireWidgetRecord(context, provider);
            if (widget.optInt("page", -1) == page && widget.optInt("xDp", -1) == xDp
                    && widget.optInt("yDp", -1) == yDp) {
                return true;
            }
            SystemClock.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        return false;
    }

    private static boolean hasWidgetRecord(Context context, ComponentName expectedProvider) {
        try {
            JSONArray widgets = new JSONArray(AndroidWidgetHost.listWidgets(context));
            for (int index = 0; index < widgets.length(); ++index) {
                JSONObject item = widgets.optJSONObject(index);
                if (item != null && expectedProvider.flattenToString()
                        .equals(item.optString("provider"))) {
                    int widgetId = item.optInt("id", -1);
                    AppWidgetProviderInfo info = AppWidgetManager.getInstance(context)
                            .getAppWidgetInfo(widgetId);
                    return widgetId > 0 && info != null && expectedProvider.equals(info.provider);
                }
            }
        } catch (Exception error) {
            Log.e(TAG, "Could not read the saved widget host state.", error);
        }
        return false;
    }

    private static void awaitAccessibilityText(UiAutomation automation, String text,
                                               long timeoutMillis, Context context) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        int swipes = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (containsAccessibleText(automation, text)) {
                return;
            }
            if (swipes++ % 3 == 2) {
                scrollHome(automation, context);
            }
            SystemClock.sleep(250L);
        }
        throw new IllegalStateException("Timed out waiting for Android widget text " + text
                + "; visible accessibility tree: " + accessibilityTree(automation));
    }

    private static boolean clickAccessibleText(UiAutomation automation, String... labels) {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        try {
            return clickAccessibleNode(automation, root, labels, 0);
        } finally {
            root.recycle();
        }
    }

    private static boolean clickAccessibleNode(UiAutomation automation, AccessibilityNodeInfo node,
                                               String[] labels, int depth) {
        if (node == null || depth > 24) {
            return false;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        boolean matches = false;
        for (String label : labels) {
            matches |= (text != null && text.toString().contains(label))
                    || (description != null && description.toString().contains(label));
        }
        if (matches) {
            AccessibilityNodeInfo candidate = AccessibilityNodeInfo.obtain(node);
            try {
                for (int parentDepth = 0; candidate != null && parentDepth < 5; ++parentDepth) {
                    if (candidate.isClickable()
                            && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true;
                    }
                    if (candidate.isClickable() && injectAccessibleTap(automation, candidate)) {
                        return true;
                    }
                    AccessibilityNodeInfo parent = candidate.getParent();
                    candidate.recycle();
                    candidate = parent;
                }
            } finally {
                if (candidate != null) {
                    candidate.recycle();
                }
            }
        }
        for (int index = 0; index < node.getChildCount(); ++index) {
            AccessibilityNodeInfo child = node.getChild(index);
            try {
                if (clickAccessibleNode(automation, child, labels, depth + 1)) {
                    return true;
                }
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private static boolean injectAccessibleTap(UiAutomation automation,
                                               AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }
        int x = bounds.centerX();
        int y = bounds.centerY();
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 60,
                MotionEvent.ACTION_UP, x, y, 0);
        try {
            return automation.injectInputEvent(down, true)
                    && automation.injectInputEvent(up, true);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static boolean containsAccessibleText(UiAutomation automation, String expected) {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        try {
            return containsAccessibleNode(root, expected);
        } finally {
            root.recycle();
        }
    }

    private static boolean containsAccessibleNode(AccessibilityNodeInfo root, String expected) {
        if (root == null) {
            return false;
        }
        CharSequence text = root.getText();
        CharSequence description = root.getContentDescription();
        if ((text != null && text.toString().contains(expected))
                || (description != null && description.toString().contains(expected))) {
            return true;
        }
        for (int index = 0; index < root.getChildCount(); ++index) {
            AccessibilityNodeInfo child = root.getChild(index);
            try {
                if (containsAccessibleNode(child, expected)) {
                    return true;
                }
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private static String accessibilityTree(UiAutomation automation) {
        StringBuilder result = new StringBuilder();
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        try {
            appendAccessibilityTree(result, root, 0);
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
        return result.toString();
    }

    private static void appendAccessibilityTree(StringBuilder output, AccessibilityNodeInfo node,
                                                int depth) {
        if (node == null || depth > 20 || output.length() > 12_000) {
            return;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (text != null || description != null) {
            output.append(" ".repeat(Math.min(depth, 10)))
                    .append(text == null ? "" : text).append("|")
                    .append(description == null ? "" : description).append("\n");
        }
        for (int index = 0; index < node.getChildCount(); ++index) {
            AccessibilityNodeInfo child = node.getChild(index);
            try {
                appendAccessibilityTree(output, child, depth + 1);
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
    }

    private static void scrollSystemPicker(UiAutomation automation, Context context) {
        if (containsAccessibleText(automation, "OmaApp CI Interactive Widget")) {
            return;
        }
        executeSwipe(automation, context, 0.78f, 0.28f);
    }

    private static void scrollHome(UiAutomation automation, Context context) {
        executeSwipe(automation, context, 0.84f, 0.38f);
    }

    private static void executeSwipe(UiAutomation automation, Context context,
                                     float fromRatio, float toRatio) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        String command = "input swipe " + (width / 2) + " " + Math.round(height * fromRatio)
                + " " + (width / 2) + " " + Math.round(height * toRatio) + " 250";
        try {
            automation.executeShellCommand(command).close();
        } catch (Exception error) {
            Log.w(TAG, "Could not scroll the Android widget accessibility surface.", error);
        }
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
