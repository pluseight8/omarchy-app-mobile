Warning: truncated output (original token count: 34985)
Total output lines: 2691

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
import java.lang.reflect.Field;
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
                String translated = null;
                for (int attempt = 0; attempt < 40
                        && !"Файлы компьютера".equals(translated); ++attempt) {
                    translated = nativeStringsOnApplicationThread(
                            () -> NativeStrings.get("Computer files"));
                    if (!"Файлы компьютера".equals(translated)) {
                        SystemClock.sleep(250L);
                    }
                }
                if (!"Файлы компьютера".equals(translated)) {
                    throw new IllegalStateException("Native Android UI did not use the Russian Qt catalog: "
                            + translated);
                }
                if (!"Действие на компьютере выполнено.".equals(
                        nativeStringsOnApplicationThread(
                                () -> NativeStrings.featureMessage("Computer action completed.")))) {
                    throw new IllegalStateException("Russian UI lost a recognized feature success message.");
                }
                if (!"Разрешите эту функцию для телефона в настройках плагина Omarchy.".equals(
                        nativeStringsOnApplicationThread(() -> NativeStrings.fileTransferError(
                                "Enable this feature for this phone in the Omarchy plugin permissions.")))) {
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
                if (!success.equals(nativeStringsOnApplicationThread(
                            () -> NativeStrings.featureMessage(success)))
                        || !permission.equals(nativeStringsOnApplicationThread(
                            () -> NativeStrings.featureMessage(permission)))
                        || !permission.equals(nativeStringsOnApplicationThread(
                            () -> NativeStrings.fileTransferError(permission)))
                        || !"Computer feature request failed (HTTP 403).".equals(
                            nativeStringsOnApplicationThread(() -> NativeStrings.fileTransferError(
                                "Computer feature request failed (HTTP 403).")))
                        || !"Computer file transfer failed. Check pairing and the network.".equals(
                            nativeStringsOnApplicationThread(() -> NativeStrings.fileTransferError(
                                "unrecognized server detail")))) {
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
        context.…22985 tokens truncated…ts.length(); ++index) {
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

    private static String widgetBindingDiagnostics(Context context, ComponentName expectedProvider) {
        try {
            JSONObject widget = requireWidgetRecord(context, expectedProvider);
            int widgetId = widget.optInt("id", AppWidgetManager.INVALID_APPWIDGET_ID);
            AppWidgetProviderInfo providerInfo = widgetId > 0
                    ? AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) : null;
            Field hostField = AndroidWidgetHost.class.getDeclaredField("host");
            hostField.setAccessible(true);
            AppWidgetHost host = (AppWidgetHost) hostField.get(null);
            int[] hostWidgetIds = host == null ? new int[0] : host.getAppWidgetIds();
            Method hostOwnsId = AndroidWidgetHost.class.getDeclaredMethod("hostOwnsId", int.class);
            hostOwnsId.setAccessible(true);
            boolean owned = (Boolean) hostOwnsId.invoke(null, widgetId);
            Field listeningField = AndroidWidgetHost.class.getDeclaredField("listening");
            listeningField.setAccessible(true);
            return "record=" + widget + ", managerProvider="
                    + (providerInfo == null ? "null" : providerInfo.provider.flattenToString())
                    + ", hostIds=" + java.util.Arrays.toString(hostWidgetIds)
                    + ", hostOwnsId=" + owned + ", listening=" + listeningField.getBoolean(null);
        } catch (Exception error) {
            return "could not inspect binding state: " + error;
        }
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
        // A bound AppWidgetHostView occupies the center of the page and owns
        // its RemoteViews touch stream. The fixture leaves a right gutter wide
        // enough for a vertical Home scroll without touching the hosted view.
        executeSwipe(automation, context, 0.84f, 0.38f, 0.92f);
    }

    private static void scrollHomeToTop(UiAutomation automation, Context context) {
        for (int attempt = 0; attempt < 4; attempt++) {
            executeSwipe(automation, context, 0.38f, 0.84f, 0.92f);
            SystemClock.sleep(200L);
        }
    }

    private static void executeSwipe(UiAutomation automation, Context context,
                                     float fromRatio, float toRatio) {
        executeSwipe(automation, context, fromRatio, toRatio, 0.5f);
    }

    private static void executeSwipe(UiAutomation automation, Context context,
                                     float fromRatio, float toRatio, float xRatio) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        int x = Math.max(1, Math.min(width - 2, Math.round(width * xRatio)));
        String command = "input swipe " + x + " " + Math.round(height * fromRatio)
                + " " + x + " " + Math.round(height * toRatio) + " 250";
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

    private String nativeStringsOnApplicationThread(NativeStringsCall call) {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<UnsatisfiedLinkError> libraryError = new AtomicReference<>();
        runOnMainSync(() -> {
            try {
                result.set(call.invoke());
            } catch (UnsatisfiedLinkError error) {
                libraryError.set(error);
            }
        });
        return libraryError.get() == null ? result.get() : null;
    }

    @FunctionalInterface
    private interface NativeStringsCall {
        String invoke();
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
