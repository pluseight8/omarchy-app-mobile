package app.omaapp;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hosts user-selected Android widgets over their reserved Qt Quick Home slots. */
public final class AndroidWidgetHost {
    private static final String TAG = "OmaAppAppWidget";
    private static final String PREFS = "android_widget_host";
    private static final String WIDGETS_KEY = "widgets";
    private static final String PENDING_ID_KEY = "pending_widget_id";
    private static final String PENDING_ID_STATE = "omaapp.pending_widget_id";
    private static final int HOST_ID = 0x4f4d41;
    private static final int REQUEST_PICK = 0x4f40;
    private static final int REQUEST_CONFIGURE = 0x4f41;
    private static final int MAX_WIDGETS = 8;
    private static final float MAX_COORDINATE_DP = 10000f;
    private static final float MAX_SIZE_DP = 3000f;

    private static WeakReference<OmaAppActivity> activityRef = new WeakReference<>(null);
    private static AppWidgetHost host;
    private static FrameLayout overlay;
    private static boolean listening;
    private static int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final Map<Integer, AppWidgetHostView> views = new HashMap<>();
    private static final Map<Integer, FrameLayout> clips = new HashMap<>();
    private static final Map<Integer, WidgetFrame> frames = new HashMap<>();
    private static final Map<Integer, String> lastSizeOptions = new HashMap<>();

    private AndroidWidgetHost() {
    }

    static OmaAppActivity currentActivityForInstrumentation() {
        return activityRef.get();
    }

    static void restoreInstanceState(Bundle state) {
        if (state != null) {
            int restoredId = state.getInt(PENDING_ID_STATE, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (restoredId > 0) {
                pendingWidgetId = restoredId;
            }
        }
    }

    static void saveInstanceState(Bundle state) {
        if (state != null && pendingWidgetId > 0) {
            state.putInt(PENDING_ID_STATE, pendingWidgetId);
        }
    }

    static void attach(OmaAppActivity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        OmaAppActivity previousActivity = activityRef.get();
        if (previousActivity != null && previousActivity != activity) {
            onStop(previousActivity);
            releaseActivityViews();
        }
        activityRef = new WeakReference<>(activity);
        ensureHost(activity);
        activity.getWindow().getDecorView().post(() -> {
            if (activityRef.get() != activity || activity.isFinishing()) {
                return;
            }
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout)) {
                Log.e(TAG, "Qt Activity content root is not a FrameLayout; widgets remain detached.");
                return;
            }
            FrameLayout contentRoot = (FrameLayout) content;
            if (overlay == null || overlay.getContext() != activity) {
                detachOverlay();
                overlay = new FrameLayout(activity);
                overlay.setBackgroundColor(Color.TRANSPARENT);
                overlay.setClipChildren(true);
                overlay.setClipToPadding(true);
                overlay.setClickable(false);
                overlay.setFocusable(false);
                overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
            if (overlay.getParent() != contentRoot) {
                if (overlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
                contentRoot.addView(overlay, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                overlay.bringToFront();
            }
            restoreWidgets(activity);
        });
    }

    static void onStart(OmaAppActivity activity) {
        ensureHost(activity);
        if (host != null && !listening) {
            try {
                host.startListening();
                listening = true;
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not start Android widget listening.", error);
            }
        }
        attach(activity);
    }

    static void onStop(OmaAppActivity activity) {
        if (activityRef.get() != activity) {
            return;
        }
        if (host != null && listening) {
            try {
                host.stopListening();
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop Android widget listening cleanly.", error);
            }
            listening = false;
        }
    }

    static void detach(OmaAppActivity activity) {
        if (activityRef.get() != activity) {
            return;
        }
        onStop(activity);
        releaseActivityViews();
        activityRef.clear();
    }

    private static void releaseActivityViews() {
        detachOverlay();
        if (host != null) {
            host.clearViewsForActivityDetach();
        }
        views.clear();
        clips.clear();
        frames.clear();
        lastSizeOptions.clear();
    }

    private static void detachOverlay() {
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        overlay = null;
    }

    private static void ensureHost(Context context) {
        if (host == null && context != null) {
            host = new OmaAppWidgetHost(context.getApplicationContext(), HOST_ID);
        }
    }

    /** Starts Android's system widget picker, which performs the user's provider selection/bind. */
    public static boolean requestWidgetPicker() {
        OmaAppActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            return false;
        }
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            AtomicBoolean accepted = new AtomicBoolean(false);
            CountDownLatch completed = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                try {
                    accepted.set(requestWidgetPicker());
                } finally {
                    completed.countDown();
                }
            });
            try {
                return completed.await(5, TimeUnit.SECONDS) && accepted.get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        ensureHost(activity);
        List<JSONObject> widgets = readWidgets(activity);
        if (host == null || widgets == null || widgets.size() >= MAX_WIDGETS) {
            return false;
        }
        final int widgetId;
        try {
            widgetId = host.allocateAppWidgetId();
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not allocate an Android widget ID.", error);
            return false;
        }
        pendingWidgetId = widgetId;
        if (!activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PENDING_ID_KEY, widgetId).commit()) {
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            host.deleteAppWidgetId(widgetId);
            return false;
        }
        Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        try {
            activity.startActivityForResult(pick, REQUEST_PICK);
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not launch Android's widget picker.", error);
            deletePendingWidget(activity);
            return false;
        }
    }

    static boolean onActivityResult(OmaAppActivity activity, int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == REQUEST_PICK) {
            int returnedId = data == null
                    ? AppWidgetManager.INVALID_APPWIDGET_ID
                    : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID);
            if (resultCode != Activity.RESULT_OK || returnedId != pendingWidgetId) {
                deletePendingWidget(activity);
                OmaAppActivity.notifyAndroidWidgetsChanged();
                return true;
            }
            AppWidgetProviderInfo info = widgetManager(activity).getAppWidgetInfo(returnedId);
            if (info == null || info.provider == null) {
                deletePendingWidget(activity);
                OmaAppActivity.notifyAndroidWidgetsChanged();
                return true;
            }
            if (info.configure != null) {
                Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, returnedId);
                try {
                    activity.startActivityForResult(configure, REQUEST_CONFIGURE);
                    return true;
                } catch (RuntimeException error) {
                    Log.e(TAG, "Could not launch the widget configuration activity.", error);
                    deletePendingWidget(activity);
                    OmaAppActivity.notifyAndroidWidgetsChanged();
                    return true;
                }
            }
            finishWidgetSelection(activity, returnedId, info);
            return true;
        }
        if (requestCode == REQUEST_CONFIGURE) {
            int widgetId = pendingWidgetId;
            int configuredId = data == null ? AppWidgetManager.INVALID_APPWIDGET_ID
                    : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID);
            if (resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && configuredId == widgetId) {
                AppWidgetProviderInfo info = widgetManager(activity).getAppWidgetInfo(widgetId);
                if (info != null && info.provider != null) {
                    finishWidgetSelection(activity, widgetId, info);
                    return true;
                }
            }
            deletePendingWidget(activity);
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return true;
        }
        return false;
    }

    private static void finishWidgetSelection(OmaAppActivity activity, int widgetId,
                                              AppWidgetProviderInfo info) {
        List<JSONObject> widgets = readWidgets(activity);
        if (widgets == null || widgets.size() >= MAX_WIDGETS || findWidget(widgets, widgetId) != null) {
            deletePendingWidget(activity);
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        JSONObject item = widgetRecord(activity, widgetId, info, widgets.size());
        if (item == null) {
            deletePendingWidget(activity);
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        widgets.add(item);
        if (!writeWidgets(activity, widgets)) {
            deletePendingWidget(activity);
            Log.e(TAG, "Could not persist the selected Android widget; the allocated ID was released.");
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        clearPendingWidget(activity);
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        ensureOverlay(activity);
        createWidgetView(activity, widgetId, info);
        OmaAppActivity.notifyAndroidWidgetsChanged();
    }

    public static String listWidgets(Context context) {
        if (context == null) {
            return "[]";
        }
        List<JSONObject> widgets = readWidgets(context);
        return widgets == null ? "[]" : new JSONArray(widgets).toString();
    }

    public static boolean removeWidget(Context context, int widgetId) {
        if (context == null || widgetId <= 0) {
            return false;
        }
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            OmaAppActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                return false;
            }
            AtomicBoolean removed = new AtomicBoolean(false);
            CountDownLatch completed = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                try {
                    removed.set(removeWidget(context, widgetId));
                } finally {
                    completed.countDown();
                }
            });
            try {
                return completed.await(5, TimeUnit.SECONDS) && removed.get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            return false;
        }
        boolean known = findWidget(widgets, widgetId) != null;
        if (!known) {
            return false;
        }
        ensureHost(context);
        if (host != null) {
            try {
                host.deleteAppWidgetId(widgetId);
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not release Android widget ID " + widgetId + ".", error);
                return false;
            }
        }
        widgets.removeIf(item -> item.optInt("id", -1) == widgetId);
        boolean stateSaved = writeWidgets(context, widgets);
        AppWidgetHostView view = views.remove(widgetId);
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        FrameLayout clip = clips.remove(widgetId);
        if (clip != null && clip.getParent() instanceof ViewGroup) {
            ((ViewGroup) clip.getParent()).removeView(clip);
        }
        frames.remove(widgetId);
        lastSizeOptions.remove(widgetId);
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return stateSaved;
    }

    /** Updates the native view in density-independent coordinates supplied by Qt Quick. */
    public static void updateFrame(Context context, int widgetId, float xDp, float yDp,
                                   float widthDp, float heightDp, float clipXdp, float clipYdp,
                                   float clipWidthDp, float clipHeightDp, boolean visible) {
        OmaAppActivity activity = activityRef.get();
        if (context == null || activity == null || widgetId <= 0
                || !Float.isFinite(xDp) || !Float.isFinite(yDp)
                || !Float.isFinite(widthDp) || !Float.isFinite(heightDp)
                || !Float.isFinite(clipXdp) || !Float.isFinite(clipYdp)
                || !Float.isFinite(clipWidthDp) || !Float.isFinite(clipHeightDp)
                || Math.abs(xDp) > MAX_COORDINATE_DP || Math.abs(yDp) > MAX_COORDINATE_DP
                || Math.abs(clipXdp) > MAX_COORDINATE_DP || Math.abs(clipYdp) > MAX_COORDINATE_DP
                || widthDp < 0f || heightDp < 0f
                || widthDp > MAX_SIZE_DP || heightDp > MAX_SIZE_DP
                || clipWidthDp < 0f || clipHeightDp < 0f
                || clipWidthDp > MAX_SIZE_DP || clipHeightDp > MAX_SIZE_DP) {
            return;
        }
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            activity.runOnUiThread(() -> updateFrame(context, widgetId, xDp, yDp, widthDp,
                    heightDp, clipXdp, clipYdp, clipWidthDp, clipHeightDp, visible));
            return;
        }
        boolean hasView = views.containsKey(widgetId);
        if (!hasView) {
            List<JSONObject> savedWidgets = readWidgets(context);
            if (savedWidgets == null || findWidget(savedWidgets, widgetId) == null) {
                return;
            }
        }
        WidgetFrame frame = new WidgetFrame(xDp, yDp, widthDp, heightDp,
                clipXdp, clipYdp, clipWidthDp, clipHeightDp, visible);
        frames.put(widgetId, frame);
        if (!hasView) {
            return;
        }
        AppWidgetHostView view = views.get(widgetId);
        if (view == null) {
            return;
        }
        Runnable update = () -> applyFrame(activity, view, frame);
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            update.run();
        } else {
            activity.runOnUiThread(update);
        }
    }

    private static void applyFrame(Activity activity, AppWidgetHostView view, WidgetFrame frame) {
        FrameLayout clip = clips.get(view.getAppWidgetId());
        if (overlay == null || clip == null || view.getParent() != clip || activity.isFinishing()) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        float leftDp = Math.max(frame.xDp, frame.clipXdp);
        float topDp = Math.max(frame.yDp, frame.clipYdp);
        float rightDp = Math.min(frame.xDp + frame.widthDp, frame.clipXdp + frame.clipWidthDp);
        float bottomDp = Math.min(frame.yDp + frame.heightDp, frame.clipYdp + frame.clipHeightDp);
        int left = Math.round(leftDp * density);
        int top = Math.round(topDp * density);
        int clipWidth = Math.max(0, Math.round((rightDp - leftDp) * density));
        int clipHeight = Math.max(0, Math.round((bottomDp - topDp) * density));
        int width = Math.round(frame.widthDp * density);
        int height = Math.round(frame.heightDp * density);
        boolean visible = frame.visible && clipWidth > 0 && clipHeight > 0 && width > 0 && height > 0;
        clip.setVisibility(visible ? View.VISIBLE : View.GONE);
        clip.setClipBounds(new Rect(0, 0, clipWidth, clipHeight));
        FrameLayout.LayoutParams clipParams = (FrameLayout.LayoutParams) clip.getLayoutParams();
        if (clipParams == null) {
            clipParams = new FrameLayout.LayoutParams(clipWidth, clipHeight);
        }
        if (clipParams.leftMargin != left || clipParams.topMargin != top
                || clipParams.width != clipWidth || clipParams.height != clipHeight) {
            clipParams.width = clipWidth;
            clipParams.height = clipHeight;
            clipParams.leftMargin = left;
            clipParams.topMargin = top;
            clip.setLayoutParams(clipParams);
        }
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        view.setTranslationX(Math.round((frame.xDp - leftDp) * density));
        view.setTranslationY(Math.round((frame.yDp - topDp) * density));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(width, height);
        }
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            view.setLayoutParams(params);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            String sizeKey = Math.round(frame.widthDp) + "x" + Math.round(frame.heightDp);
            if (sizeKey.equals(lastSizeOptions.get(view.getAppWidgetId()))) {
                return;
            }
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Math.max(1, frame.widthDp > 0
                    ? Math.round(frame.widthDp) : 1));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, Math.max(1, frame.widthDp > 0
                    ? Math.round(frame.widthDp) : 1));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Math.max(1, frame.heightDp > 0
                    ? Math.round(frame.heightDp) : 1));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, Math.max(1, frame.heightDp > 0
                    ? Math.round(frame.heightDp) : 1));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            try {
                widgetManager(activity).updateAppWidgetOptions(view.getAppWidgetId(), options);
                lastSizeOptions.put(view.getAppWidgetId(), sizeKey);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not update provider size options.", error);
            }
        }
    }

    private static void ensureOverlay(OmaAppActivity activity) {
        if (activityRef.get() == null || activityRef.get() != activity) {
            return;
        }
        attach(activity);
    }

    private static void restoreWidgets(Context context) {
        if (host == null || overlay == null || overlay.getParent() == null) {
            return;
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            // Do not interpret corrupt preferences as an empty widget list and release users' IDs.
            return;
        }
        cleanupInterruptedPick(context);
        Set<Integer> known = new HashSet<>();
        for (JSONObject widget : widgets) {
            int id = widget.optInt("id", -1);
            AppWidgetProviderInfo info = id > 0 ? widgetManager(context).getAppWidgetInfo(id) : null;
            ComponentName savedProvider = ComponentName.unflattenFromString(
                    widget.optString("provider", ""));
            if (info == null || info.provider == null || !info.provider.equals(savedProvider)
                    || !hostOwnsId(id)) {
                if (id > 0) {
                    try {
                        host.deleteAppWidgetId(id);
                    } catch (RuntimeException ignored) {
                        Log.w(TAG, "Could not release an orphaned widget ID.");
                    }
                }
                continue;
            }
            known.add(id);
            createWidgetView(context, id, info);
        }
        for (int id : host.getAppWidgetIds()) {
            if (!known.contains(id) && id != pendingWidgetId) {
                try {
                    host.deleteAppWidgetId(id);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Could not release an untracked Android widget ID.", error);
                }
            }
        }
        for (Integer id : new ArrayList<>(views.keySet())) {
            if (!known.contains(id)) {
                views.remove(id);
                FrameLayout stale = clips.remove(id);
                if (stale != null && stale.getParent() instanceof ViewGroup) {
                    ((ViewGroup) stale.getParent()).removeView(stale);
                }
                frames.remove(id);
                lastSizeOptions.remove(id);
            }
        }
        if (known.size() != widgets.size()) {
            List<JSONObject> valid = new ArrayList<>();
            for (JSONObject widget : widgets) {
                if (known.contains(widget.optInt("id", -1))) {
                    valid.add(widget);
                }
            }
            writeWidgets(context, valid);
            OmaAppActivity.notifyAndroidWidgetsChanged();
        }
        applySavedFrames(context);
    }

    private static boolean hostOwnsId(int widgetId) {
        if (host == null) {
            return false;
        }
        for (int id : host.getAppWidgetIds()) {
            if (id == widgetId) {
                return true;
            }
        }
        return false;
    }

    private static void createWidgetView(Context context, int widgetId, AppWidgetProviderInfo info) {
        if (overlay == null || views.containsKey(widgetId)) {
            return;
        }
        try {
            FrameLayout clip = new FrameLayout(context);
            clip.setClipChildren(true);
            clip.setClipToPadding(true);
            clip.setClickable(false);
            clip.setFocusable(false);
            clip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            clip.setVisibility(View.GONE);
            overlay.addView(clip, new FrameLayout.LayoutParams(1, 1));
            AppWidgetHostView view = host.createView(context, widgetId, info);
            view.setAppWidget(widgetId, info);
            view.setVisibility(View.GONE);
            clip.addView(view, new FrameLayout.LayoutParams(1, 1));
            clips.put(widgetId, clip);
            views.put(widgetId, view);
            WidgetFrame frame = frames.get(widgetId);
            Activity activity = context instanceof Activity ? (Activity) context : activityRef.get();
            if (frame != null && activity != null) {
                applyFrame(activity, view, frame);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not create Android widget view for ID " + widgetId + ".", error);
        }
    }

    private static void applySavedFrames(Context context) {
        for (Map.Entry<Integer, WidgetFrame> entry : frames.entrySet()) {
            AppWidgetHostView view = views.get(entry.getKey());
            OmaAppActivity activity = activityRef.get();
            if (view != null && activity != null) {
                applyFrame(activity, view, entry.getValue());
            }
        }
    }

    private static JSONObject widgetRecord(Context context, int id,
                                           AppWidgetProviderInfo info, int order) {
        if (info == null || info.provider == null || id <= 0 || order < 0 || order >= MAX_WIDGETS) {
            return null;
        }
        try {
            CharSequence loadedLabel = info.loadLabel(context.getPackageManager());
            String label = loadedLabel == null ? "" : loadedLabel.toString();
            if (label.length() > 512) {
                label = label.substring(0, 512);
            }
            label = label.replaceAll("[\\p{Cntrl}]", " ").trim();
            if (label.isEmpty() || label.length() > 160) {
                label = info.provider.getPackageName();
            }
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("provider", info.provider.flattenToString());
            item.put("label", label.substring(0, Math.min(160, label.length())));
            item.put("page", 0);
            item.put("order", order);
            item.put("minWidthDp", Math.max(1, info.minWidth));
            item.put("minHeightDp", Math.max(1, info.minHeight));
            return item;
        } catch (JSONException | RuntimeException error) {
            Log.e(TAG, "Could not describe the selected Android widget.", error);
            return null;
        }
    }

    private static AppWidgetManager widgetManager(Context context) {
        return context.getSystemService(AppWidgetManager.class);
    }

    private static List<JSONObject> readWidgets(Context context) {
        List<JSONObject> result = new ArrayList<>();
        JSONArray array;
        try {
            array = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(WIDGETS_KEY, "[]"));
        } catch (JSONException | RuntimeException error) {
            Log.e(TAG, "Saved Android widget state is unreadable; leaving it untouched.", error);
            return null;
        }
        Set<Integer> ids = new HashSet<>();
        for (int index = 0; index < array.length() && result.size() < MAX_WIDGETS; ++index) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                continue;
            }
            int id = item.optInt("id", -1);
            String provider = item.optString("provider", "");
            String label = item.optString("label", "");
            if (id <= 0 || !ids.add(id)
                    || provider.length() > 512 || ComponentName.unflattenFromString(provider) == null
                    || label.length() > 160) {
                continue;
            }
            JSONObject sanitized = new JSONObject();
            try {
                sanitized.put("id", id);
                sanitized.put("provider", provider);
                sanitized.put("label", label);
                sanitized.put("page", 0);
                sanitized.put("order", result.size());
                sanitized.put("minWidthDp", Math.max(1, Math.min(1200, item.optInt("minWidthDp", 160))));
                sanitized.put("minHeightDp", Math.max(1, Math.min(1200, item.optInt("minHeightDp", 120))));
                result.add(sanitized);
            } catch (JSONException ignored) {
                // JSONObject.put of these bounded primitive values cannot fail.
            }
        }
        return result;
    }

    private static JSONObject findWidget(List<JSONObject> widgets, int widgetId) {
        for (JSONObject widget : widgets) {
            if (widget.optInt("id", -1) == widgetId) {
                return widget;
            }
        }
        return null;
    }

    private static boolean writeWidgets(Context context, List<JSONObject> widgets) {
        JSONArray array = new JSONArray();
        for (int index = 0; index < widgets.size() && index < MAX_WIDGETS; ++index) {
            JSONObject value = widgets.get(index);
            value.remove("order");
            try {
                value.put("order", index);
            } catch (JSONException ignored) {
                continue;
            }
            array.put(value);
        }
        boolean saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(WIDGETS_KEY, array.toString()).commit();
        if (!saved) {
            Log.e(TAG, "Could not persist Android widget IDs and Home order.");
        }
        return saved;
    }

    private static void cleanupInterruptedPick(Context context) {
        int interrupted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PENDING_ID_KEY, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (interrupted == pendingWidgetId) {
            return;
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            return;
        }
        if (interrupted != AppWidgetManager.INVALID_APPWIDGET_ID
                && findWidget(widgets, interrupted) == null) {
            ensureHost(context);
            if (host != null) {
                try {
                    host.deleteAppWidgetId(interrupted);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Could not release an interrupted widget selection.", error);
                }
            }
            clearPendingWidget(context);
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        }
    }

    private static void deletePendingWidget(Context context) {
        int id = pendingWidgetId;
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(PENDING_ID_KEY, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ensureHost(context);
            if (host != null) {
                try {
                    host.deleteAppWidgetId(id);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Could not release a cancelled widget ID.", error);
                }
            }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        clearPendingWidget(context);
    }

    private static void clearPendingWidget(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(PENDING_ID_KEY).commit();
    }

    private static final class WidgetFrame {
        final float xDp;
        final float yDp;
        final float widthDp;
        final float heightDp;
        final float clipXdp;
        final float clipYdp;
        final float clipWidthDp;
        final float clipHeightDp;
        final boolean visible;

        WidgetFrame(float xDp, float yDp, float widthDp, float heightDp,
                    float clipXdp, float clipYdp, float clipWidthDp, float clipHeightDp,
                    boolean visible) {
            this.xDp = xDp;
            this.yDp = yDp;
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.clipXdp = clipXdp;
            this.clipYdp = clipYdp;
            this.clipWidthDp = clipWidthDp;
            this.clipHeightDp = clipHeightDp;
            this.visible = visible;
        }
    }

    private static final class OmaAppWidgetHost extends AppWidgetHost {
        OmaAppWidgetHost(Context context, int hostId) {
            super(context, hostId);
        }

        void clearViewsForActivityDetach() {
            clearViews();
        }
    }
}
