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
import android.os.ParcelFileDescriptor;
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
                result.putInt("processId", android.os.Process.myPid());
                sendStatus(2, result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("restore".equals(widgetHostMode)) {
                runAndroidWidgetHostRestore(context);
                result.putString("stage", "android_widget_host_process_restore_passed");
                result.putInt("processId", android.os.Process.myPid());
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
            if (Boolean.parseBoolean(testArguments.getString("nativeQtTransl