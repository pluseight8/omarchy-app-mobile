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
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hosts user-selected Android widgets over their reserved Qt Quick Home slots. */
public final class AndroidWidgetHost {
    private static final String TAG = "OmaAppAppWidget";
    private static final String PREFS = "android_widget_host";
    private static final String WIDGETS_KEY = "widgets";
    private static final int WIDGETS_SCHEMA_VERSION = 3;
    private static final int BACKUP_SCHEMA_VERSION = 2;
    private static final int MAX_BACKUP_CHARS = 2 * 1024 * 1024;
    private static final String PENDING_ID_KEY = "pending_widget_id";
    private static final String PENDING_RECORD_ID_KEY = "pending_widget_record_id";
    private static final String PENDING_PAGE_KEY = "pending_widget_page";
    private static final String RESTORE_STATE_KEY = "widget_backup_restore_state";
    private static final String RESTORE_PREVIOUS_KEY = "widget_backup_restore_previous";
    private static final String PENDING_ID_STATE = "omaapp.pending_widget_id";
    private static final String PENDING_RECORD_ID_STATE = "omaapp.pending_widget_record_id";
    private static final String PENDING_PAGE_STATE = "omaapp.pending_widget_page";
    private static final int HOST_ID = 0x4f4d41;
    private static final int REQUEST_PICK = 0x4f40;
    private static final int REQUEST_CONFIGURE = 0x4f41;
    private static final int MAX_WIDGETS = 8;
    private static final int PLACEMENT_GRID_DP = 24;
    private static final int WIDGET_CHROME_HEIGHT_DP = 256;
    private static final int MIN_WIDGET_CARD_WIDTH_DP = 280;
    private static final float MAX_COORDINATE_DP = 20000f;
    private static final float MAX_SIZE_DP = 3000f;

    private static WeakReference<OmaAppActivity> activityRef = new WeakReference<>(null);
    private static OmaAppWidgetHost host;
    private static FrameLayout overlay;
    private static boolean listening;
    private static int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static String pendingRecordId = "";
    private static int pendingPage;
    private static final Map<Integer, AppWidgetHostView> views = new HashMap<>();
    private static final Map<Integer, FrameLayout> clips = new HashMap<>();
    private static final Map<Integer, TextView> resizeHandles = new HashMap<>();
    private static final Map<Integer, WidgetFrame> frames = new HashMap<>();
    private static final Map<Integer, String> lastSizeOptions = new HashMap<>();

    private AndroidWidgetHost() {
    }

    static OmaAppActivity currentActivityForInstrumentation() {
        return activityRef.get();
    }

    static View resizeHandleForInstrumentation(int widgetId) {
        return resizeHandles.get(widgetId);
    }

    static void restoreInstanceState(Bundle state) {
        if (state != null) {
            int restoredId = state.getInt(PENDING_ID_STATE, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (restoredId > 0) {
                pendingWidgetId = restoredId;
            }
            pendingRecordId = state.getString(PENDING_RECORD_ID_STATE, "");
            pendingPage = Math.max(0, state.getInt(PENDING_PAGE_STATE, 0));
        }
    }

    static void saveInstanceState(Bundle state) {
        if (state != null && pendingWidgetId > 0) {
            state.putInt(PENDING_ID_STATE, pendingWidgetId);
            state.putString(PENDING_RECORD_ID_STATE, pendingRecordId);
            state.putInt(PENDING_PAGE_STATE, pendingPage);
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
                overlay.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setEmpty();
                    }
                });
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
            }
            // Qt can restore its own view order after the widget picker Activity returns.
            // Reassert the native overlay's z-order on every Activity attach, not only when
            // the FrameLayout parent changes, so RemoteViews remain touchable after resume.
            float highestSiblingZ = 0f;
            for (int index = 0; index < contentRoot.getChildCount(); ++index) {
                View sibling = contentRoot.getChildAt(index);
                if (sibling != overlay) {
                    highestSiblingZ = Math.max(highestSiblingZ, sibling.getZ());
                }
            }
            overlay.setTranslationZ(0f);
            overlay.setElevation(highestSiblingZ
                    + 16.0f * activity.getResources().getDisplayMetrics().density);
            overlay.bringToFront();
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
        resizeHandles.clear();
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
        return requestWidgetPicker(0);
    }

    public static boolean requestWidgetPicker(int page) {
        return requestWidgetPickerForRecord(page, "");
    }

    /** Rebinds a restored placeholder through Android's system picker and user confirmation. */
    public static boolean requestWidgetRebind(String recordId) {
        OmaAppActivity activity = activityRef.get();
        if (activity == null || recordId == null || recordId.isEmpty()) {
            return false;
        }
        List<JSONObject> widgets = readWidgets(activity);
        JSONObject placeholder = findWidgetByRecordId(widgets, recordId);
        if (placeholder == null || isWidgetBound(activity, placeholder)) {
            return false;
        }
        return requestWidgetPickerForRecord(placeholder.optInt("page", 0), recordId);
    }

    private static boolean requestWidgetPickerForRecord(int page, String recordId) {
        OmaAppActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing()
                || pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                || page < 0 || page >= 5) {
            return false;
        }
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            AtomicBoolean accepted = new AtomicBoolean(false);
            CountDownLatch completed = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                try {
                    accepted.set(requestWidgetPickerForRecord(page, recordId));
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
        if (host == null || widgets == null
                || (recordId.isEmpty() && widgets.size() >= MAX_WIDGETS)
                || (!recordId.isEmpty() && findWidgetByRecordId(widgets, recordId) == null)) {
            return false;
        }
        Log.i(TAG, "Launching system widget picker for page " + page + " ("
                + (recordId.isEmpty() ? "new widget" : "placeholder rebind") + ").");
        final int widgetId;
        try {
            widgetId = host.allocateAppWidgetId();
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not allocate an Android widget ID.", error);
            return false;
        }
        pendingWidgetId = widgetId;
        pendingRecordId = recordId;
        pendingPage = page;
        boolean pendingSaved = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PENDING_ID_KEY, widgetId)
                .putString(PENDING_RECORD_ID_KEY, recordId)
                .putInt(PENDING_PAGE_KEY, page)
                .commit();
        if (!pendingSaved) {
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            pendingRecordId = "";
            pendingPage = 0;
            try {
                host.deleteAppWidgetId(widgetId);
            } catch (RuntimeException error) {
                // Startup recovery releases IDs that could not be tracked durably.
                Log.e(TAG, "Could not release an Android widget ID after its pending state failed to persist.",
                        error);
            }
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
        if (widgets == null || findWidget(widgets, widgetId) != null
                || (pendingRecordId.isEmpty() && widgets.size() >= MAX_WIDGETS)) {
            deletePendingWidget(activity);
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        JSONObject previous = pendingRecordId.isEmpty() ? null
                : findWidgetByRecordId(widgets, pendingRecordId);
        if (!pendingRecordId.isEmpty() && (previous == null || isWidgetBound(activity, previous)
                || !previous.optString("provider", "").equals(info.provider.flattenToString()))) {
            Log.w(TAG, "The selected provider does not match the saved widget placeholder.");
            deletePendingWidget(activity);
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        int page = previous == null ? pendingPage : previous.optInt("page", pendingPage);
        int order = previous == null ? countWidgetsOnPage(widgets, page)
                : previous.optInt("order", 0);
        int xDp = previous == null ? 0 : previous.optInt("xDp", 0);
        int yDp = previous == null ? nextWidgetY(widgets, page)
                : previous.optInt("yDp", nextWidgetY(widgets, page));
        JSONObject item = widgetRecord(activity, widgetId, info,
                previous == null ? UUID.randomUUID().toString()
                        : previous.optString("recordId", UUID.randomUUID().toString()),
                page, order, xDp, yDp);
        if (item == null) {
            deletePendingWidget(activity);
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        if (previous == null) {
            widgets.add(item);
        } else {
            int previousIndex = widgets.indexOf(previous);
            item = preserveWidgetGeometry(previous, item);
            if (hasPlacementConflict(widgets, item, previous)) {
                try {
                    item.put("xDp", 0);
                    item.put("yDp", nextWidgetY(widgets, page));
                } catch (JSONException ignored) {
                    deletePendingWidget(activity);
                    return;
                }
            }
            widgets.set(previousIndex, item);
        }
        if (!writeWidgets(activity, widgets)) {
            deletePendingWidget(activity);
            Log.e(TAG, "Could not persist the selected Android widget; the allocated ID was released.");
            OmaAppActivity.notifyAndroidWidgetsChanged();
            return;
        }
        if (previous != null) {
            int previousId = previous.optInt("id", 0);
            if (previousId > 0 && previousId != widgetId && host != null) {
                try {
                    host.deleteAppWidgetId(previousId);
                } catch (RuntimeException error) {
                    // The new record is durable. Cold-start recovery removes this orphan ID.
                    Log.w(TAG, "Could not release the replaced Android widget ID.", error);
                }
            }
        }
        clearPendingWidget(activity);
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        pendingRecordId = "";
        pendingPage = 0;
        ensureOverlay(activity);
        createWidgetView(activity, widgetId, info);
        OmaAppActivity.notifyAndroidWidgetsChanged();
    }

    public static String listWidgets(Context context) {
        if (context == null) {
            return "[]";
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            // Keep unreadable state distinct from a genuinely empty widget list so the
            // launcher can surface a recoverable, non-destructive error to the user.
            return "{\"status\":\"storage_error\"}";
        }
        JSONArray result = new JSONArray();
        for (JSONObject widget : widgets) {
            JSONObject visible = copyJsonObject(widget);
            try {
                visible.put("state", isWidgetBound(context, widget) ? "ready" : "needs_rebind");
                result.put(visible);
            } catch (JSONException ignored) {
                // The object contains only bounded JSON values.
            }
        }
        return result.toString();
    }

    public static boolean moveWidget(Context context, int widgetId, int page, int order,
                                     int pageCount) {
        if (context == null || widgetId <= 0) {
            return false;
        }
        JSONObject widget = findWidget(readWidgets(context), widgetId);
        return widget != null && moveWidgetRecord(context, widget.optString("recordId", ""),
                page, order, pageCount);
    }

    public static boolean moveWidgetRecord(Context context, String recordId, int page, int order,
                                           int pageCount) {
        if (context == null || recordId == null || recordId.isEmpty()
                || page < 0 || page >= pageCount
                || pageCount < 1 || pageCount > 5 || order < 0 || order >= MAX_WIDGETS) {
            return false;
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            return false;
        }
        JSONObject moving = findWidgetByRecordId(widgets, recordId);
        if (moving == null) {
            return false;
        }
        List<JSONObject> reordered = deepCopyWidgets(widgets);
        JSONObject moved = findWidgetByRecordId(reordered, recordId);
        reordered.remove(moved);
        int targetCount = countWidgetsOnPage(reordered, page);
        int targetOrder = Math.min(order, targetCount);
        try {
            moved.put("page", page);
            moved.put("order", targetOrder);
            if (moving.optInt("page", 0) != page
                    && hasPlacementConflict(reordered, moved, null)) {
                moved.put("xDp", 0);
                moved.put("yDp", nextWidgetY(reordered, page));
            }
        } catch (JSONException ignored) {
            return false;
        }
        if (moved.optInt("xDp", -1) + Math.max(MIN_WIDGET_CARD_WIDTH_DP,
                    moved.optInt("widthDp", 0)) > 3000
                || moved.optInt("yDp", -1) + moved.optInt("heightDp", 0)
                    + WIDGET_CHROME_HEIGHT_DP > 20000
                || hasPlacementConflict(reordered, moved, null)) {
            return false;
        }
        int insertAt = reordered.size();
        for (int index = 0; index < reordered.size(); ++index) {
            JSONObject item = reordered.get(index);
            if (item.optInt("page", 0) == page && item.optInt("order", 0) >= targetOrder) {
                insertAt = index;
                break;
            }
        }
        reordered.add(insertAt, moved);
        normalizePageOrders(reordered);
        if (!writeWidgets(context, reordered)) {
            return false;
        }
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    /** Places a widget on the Home grid after validating bounds and overlap. */
    public static boolean placeWidgetRecord(Context context, String recordId, int page,
                                            int xDp, int yDp, int pageCount) {
        if (context == null || recordId == null || recordId.isEmpty()
                || page < 0 || pageCount < 1 || page >= pageCount || pageCount > 5
                || xDp < 0 || xDp > 3000 || yDp < 0 || yDp > 20000
                || xDp % PLACEMENT_GRID_DP != 0 || yDp % PLACEMENT_GRID_DP != 0) {
            return false;
        }
        List<JSONObject> widgets = readWidgets(context);
        JSONObject current = findWidgetByRecordId(widgets, recordId);
        if (current == null) {
            return false;
        }
        List<JSONObject> updated = deepCopyWidgets(widgets);
        JSONObject target = findWidgetByRecordId(updated, recordId);
        int oldPage = target.optInt("page", 0);
        try {
            target.put("page", page);
            target.put("xDp", xDp);
            target.put("yDp", yDp);
            if (oldPage != page) {
                target.put("order", countWidgetsOnPage(updated, page) - 1);
            }
        } catch (JSONException ignored) {
            return false;
        }
        if (xDp + Math.max(MIN_WIDGET_CARD_WIDTH_DP, target.optInt("widthDp", 1)) > 3000
                || yDp + target.optInt("heightDp", 1) + WIDGET_CHROME_HEIGHT_DP > 20000
                || hasPlacementConflict(updated, target, target)) {
            return false;
        }
        normalizePageOrders(updated);
        if (!writeWidgets(context, updated)) {
            return false;
        }
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    public static boolean resizeWidget(Context context, int widgetId, int widthDp, int heightDp) {
        if (context == null || widgetId <= 0) {
            return false;
        }
        JSONObject widget = findWidget(readWidgets(context), widgetId);
        return widget != null && resizeWidgetRecord(context, widget.optString("recordId", ""),
                widthDp, heightDp);
    }

    public static boolean resizeWidgetRecord(Context context, String recordId, int widthDp,
                                             int heightDp) {
        if (context == null || recordId == null || recordId.isEmpty()
                || widthDp < 1 || widthDp > 1200
                || heightDp < 1 || heightDp > 1200) {
            return false;
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            return false;
        }
        JSONObject widget = findWidgetByRecordId(widgets, recordId);
        if (widget == null
                || widthDp < widget.optInt("minWidthDp", 1)
                || heightDp < widget.optInt("minHeightDp", 1)) {
            return false;
        }
        List<JSONObject> updated = deepCopyWidgets(widgets);
        JSONObject target = findWidgetByRecordId(updated, recordId);
        try {
            target.put("widthDp", widthDp);
            target.put("heightDp", heightDp);
        } catch (JSONException ignored) {
            return false;
        }
        if (target.optInt("xDp", 0) + Math.max(MIN_WIDGET_CARD_WIDTH_DP, widthDp) > 3000
                || target.optInt("yDp", 0) + heightDp + WIDGET_CHROME_HEIGHT_DP > 20000
                || hasPlacementConflict(updated, target, target)) {
            return false;
        }
        if (!writeWidgets(context, updated)) {
            return false;
        }
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    public static boolean setHomePageCount(Context context, int pageCount) {
        if (context == null || pageCount < 1 || pageCount > 5) {
            return false;
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            return false;
        }
        List<JSONObject> updated = deepCopyWidgets(widgets);
        List<JSONObject> placed = new ArrayList<>();
        for (JSONObject widget : updated) {
            try {
                int page = Math.min(pageCount - 1, widget.optInt("page", 0));
                widget.put("page", page);
                if (hasPlacementConflict(placed, widget, null)) {
                    widget.put("xDp", 0);
                    widget.put("yDp", nextWidgetY(placed, page));
                }
                placed.add(widget);
            } catch (JSONException ignored) {
                return false;
            }
        }
        normalizePageOrders(updated);
        if (!writeWidgets(context, updated)) {
            return false;
        }
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    /** Exports provider/layout metadata only. Android widget IDs and grants are never portable. */
    public static String exportWidgetBackup(Context context) {
        List<JSONObject> widgets = context == null ? null : readWidgets(context);
        if (widgets == null) {
            return "";
        }
        JSONObject backup = new JSONObject();
        JSONArray entries = new JSONArray();
        try {
            backup.put("schemaVersion", BACKUP_SCHEMA_VERSION);
            for (JSONObject widget : widgets) {
                JSONObject entry = new JSONObject();
                entry.put("provider", widget.optString("provider", ""));
                entry.put("label", widget.optString("label", ""));
                entry.put("page", widget.optInt("page", 0));
                entry.put("order", widget.optInt("order", 0));
                entry.put("xDp", widget.optInt("xDp", 0));
                entry.put("yDp", widget.optInt("yDp", 0));
                entry.put("minWidthDp", widget.optInt("minWidthDp", 160));
                entry.put("minHeightDp", widget.optInt("minHeightDp", 120));
                entry.put("widthDp", widget.optInt("widthDp", 400));
                entry.put("heightDp", widget.optInt("heightDp", 240));
                entries.put(entry);
            }
            backup.put("widgets", entries);
        } catch (JSONException ignored) {
            return "";
        }
        return backup.toString();
    }

    public static boolean canRestoreWidgetBackup(String json) {
        return parseWidgetBackup(json) != null;
    }

    /** Restores placeholders only; a provider must be chosen again in Android's system picker. */
    public static boolean restoreWidgetBackup(Context context, String json) {
        return prepareWidgetBackupRestore(context, json)
                && commitWidgetBackupRestore(context);
    }

    /** Writes placeholders but keeps existing Android IDs until the layout transaction commits. */
    public static boolean prepareWidgetBackupRestore(Context context, String json) {
        if (context == null || pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                || json == null || json.length() > MAX_BACKUP_CHARS) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.contains(RESTORE_STATE_KEY) || preferences.contains(RESTORE_PREVIOUS_KEY)) {
            return false;
        }
        List<JSONObject> placeholders = parseWidgetBackup(json);
        List<JSONObject> oldWidgets = readWidgets(context);
        if (placeholders == null || oldWidgets == null) {
            return false;
        }
        String previous = serializeStoredWidgetRecords(oldWidgets);
        if (previous.isEmpty() || !preferences.edit()
                .putString(RESTORE_PREVIOUS_KEY, previous)
                .putString(RESTORE_STATE_KEY, "prepared")
                .commit()) {
            return false;
        }
        if (!writeWidgets(context, placeholders)) {
            if (!rollbackWidgetBackupRestore(context)) {
                Log.e(TAG, "Could not roll back a failed Android widget backup preparation.");
            }
            return false;
        }
        clearWidgetViewsForBackupRestore();
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    /** Finalizes a prepared restore after the layout journal has reached its commit point. */
    public static boolean commitWidgetBackupRestore(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!preferences.contains(RESTORE_STATE_KEY) && !preferences.contains(RESTORE_PREVIOUS_KEY)) {
            return true;
        }
        String state = preferences.getString(RESTORE_STATE_KEY, "");
        if ("prepared".equals(state)) {
            if (!preferences.edit().putString(RESTORE_STATE_KEY, "committing").commit()) {
                return false;
            }
        } else if (!"committing".equals(state)) {
            return false;
        }
        List<JSONObject> previous = parseStoredWidgetRecords(
                preferences.getString(RESTORE_PREVIOUS_KEY, ""));
        if (previous == null) {
            return false;
        }
        ensureHost(context);
        if (host == null) {
            return false;
        }
        Set<Integer> allocated = new HashSet<>();
        for (int id : host.getAppWidgetIds()) {
            allocated.add(id);
        }
        for (JSONObject widget : previous) {
            int id = widget.optInt("id", 0);
            if (id <= 0 || !allocated.contains(id)) {
                continue;
            }
            try {
                host.deleteAppWidgetId(id);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not release a replaced Android widget ID; recovery will retry.", error);
                return false;
            }
        }
        if (!clearWidgetBackupRestoreJournal(preferences)) {
            Log.w(TAG, "The Android widget restore committed; journal cleanup will retry.");
            return false;
        }
        clearWidgetViewsForBackupRestore();
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    /** Completes the persisted choice when startup finds an Android journal without a Qt journal. */
    public static boolean recoverWidgetBackupRestore(Context context) {
        if (context == null) {
            Log.e(TAG, "Could not recover Android widget backup state without an application context.");
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            String state = preferences.getString(RESTORE_STATE_KEY, "");
            boolean hasPrevious = preferences.contains(RESTORE_PREVIOUS_KEY);
            if (state.isEmpty() && !hasPrevious) {
                Log.i(TAG, "No interrupted Android widget backup restore needs recovery.");
                return true;
            }
            if ("prepared".equals(state)) {
                boolean recovered = rollbackWidgetBackupRestore(context);
                if (!recovered) {
                    Log.e(TAG, "Could not roll back the prepared Android widget restore journal.");
                }
                return recovered;
            }
            if ("committing".equals(state)) {
                boolean recovered = commitWidgetBackupRestore(context);
                if (!recovered) {
                    Log.e(TAG, "Could not complete the committed Android widget restore journal.");
                }
                return recovered;
            }
            Log.e(TAG, "Unrecognized Android widget restore journal state; preserving it. "
                    + "state=" + state + ", hasPrevious=" + hasPrevious);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not read Android widget restore journal state; preserving it.", error);
        }
        return false;
    }

    /** Restores the exact previous host records without discarding their system widget IDs. */
    public static boolean rollbackWidgetBackupRestore(Context context) {
        if (context == null) {
            Log.e(TAG, "Could not roll back an Android widget restore without an application context.");
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!preferences.contains(RESTORE_STATE_KEY) && !preferences.contains(RESTORE_PREVIOUS_KEY)) {
            return true;
        }
        String state = preferences.getString(RESTORE_STATE_KEY, "");
        if (!"prepared".equals(state)) {
            Log.e(TAG, "Cannot roll back Android widget restore journal in state: " + state);
            return false;
        }
        List<JSONObject> previous = parseStoredWidgetRecords(
                preferences.getString(RESTORE_PREVIOUS_KEY, ""));
        if (previous == null) {
            Log.e(TAG, "The previous Android widget records in the restore journal are invalid.");
            return false;
        }
        if (!writeWidgets(context, previous)) {
            Log.e(TAG, "Could not restore the previous Android widget records.");
            return false;
        }
        if (!clearWidgetBackupRestoreJournal(preferences)) {
            Log.e(TAG, "Could not clear the rolled-back Android widget restore journal.");
            return false;
        }
        clearWidgetViewsForBackupRestore();
        restoreWidgets(context);
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
    }

    private static void clearWidgetViewsForBackupRestore() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            OmaAppActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (activityRef.get() == activity && !activity.isFinishing()) {
                    clearWidgetViewsForBackupRestore();
                }
            });
            return;
        }
        for (FrameLayout clip : new ArrayList<>(clips.values())) {
            if (clip.getParent() instanceof ViewGroup) {
                ((ViewGroup) clip.getParent()).removeView(clip);
            }
        }
        views.clear();
        clips.clear();
        resizeHandles.clear();
        frames.clear();
        lastSizeOptions.clear();
    }

    private static boolean hasWidgetBackupRestoreJournal(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.contains(RESTORE_STATE_KEY) || preferences.contains(RESTORE_PREVIOUS_KEY);
    }

    private static boolean clearWidgetBackupRestoreJournal(SharedPreferences preferences) {
        return preferences.edit().remove(RESTORE_STATE_KEY).remove(RESTORE_PREVIOUS_KEY).commit();
    }

    private static String serializeStoredWidgetRecords(List<JSONObject> widgets) {
        JSONArray array = new JSONArray();
        for (JSONObject widget : deepCopyWidgets(widgets)) {
            array.put(widget);
        }
        JSONObject root = new JSONObject();
        try {
            root.put("schemaVersion", WIDGETS_SCHEMA_VERSION);
            root.put("widgets", array);
        } catch (JSONException ignored) {
            return "";
        }
        return root.toString();
    }

    private static List<JSONObject> parseStoredWidgetRecords(String json) {
        if (json == null || json.length() > MAX_BACKUP_CHARS) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.length() != 2 || root.optInt("schemaVersion", -1) != WIDGETS_SCHEMA_VERSION) {
                return null;
            }
            JSONArray array = root.getJSONArray("widgets");
            if (array.length() > MAX_WIDGETS) {
                return null;
            }
            List<JSONObject> result = new ArrayList<>();
            Set<Integer> ids = new HashSet<>();
            Set<String> recordIds = new HashSet<>();
            for (int index = 0; index < array.length(); ++index) {
                JSONObject widget = array.optJSONObject(index);
                if (widget == null) {
                    return null;
                }
                int id = widget.optInt("id", -1);
                String recordId = widget.optString("recordId", "");
                String provider = widget.optString("provider", "");
                int page = widget.optInt("page", -1);
                int order = widget.optInt("order", -1);
                if (id < 0 || (id > 0 && !ids.add(id)) || recordId.isEmpty()
                        || recordId.length() > 64 || !recordIds.add(recordId)
                        || ComponentName.unflattenFromString(provider) == null
                        || page < 0 || page >= 5 || order < 0 || order >= MAX_WIDGETS) {
                    return null;
                }
                JSONObject copy = new JSONObject(widget.toString());
                if (!writeRecordHasValidGeometry(copy, result)) {
                    return null;
                }
                result.add(copy);
            }
            return result;
        } catch (JSONException | RuntimeException error) {
            Log.e(TAG, "The pending Android widget restore journal is invalid.", error);
            return null;
        }
    }

    private static boolean writeRecordHasValidGeometry(JSONObject widget, List<JSONObject> occupied) {
        int x = widget.optInt("xDp", -1);
        int y = widget.optInt("yDp", -1);
        int minWidth = widget.optInt("minWidthDp", -1);
        int minHeight = widget.optInt("minHeightDp", -1);
        int width = widget.optInt("widthDp", -1);
        int height = widget.optInt("heightDp", -1);
        return x >= 0 && x <= 3000 && x % PLACEMENT_GRID_DP == 0
                && y >= 0 && y <= 20000 && y % PLACEMENT_GRID_DP == 0
                && minWidth > 0 && minWidth <= 1200 && minHeight > 0 && minHeight <= 1200
                && width >= minWidth && width <= 1200 && height >= minHeight && height <= 1200
                && x + Math.max(MIN_WIDGET_CARD_WIDTH_DP, width) <= 3000
                && y + height + WIDGET_CHROME_HEIGHT_DP <= 20000
                && !hasPlacementConflict(occupied, widget, null);
    }

    public static boolean removeWidgetRecord(Context context, String recordId) {
        if (context == null || recordId == null || recordId.isEmpty()) {
            return false;
        }
        List<JSONObject> widgets = readWidgets(context);
        JSONObject widget = findWidgetByRecordId(widgets, recordId);
        if (widget == null) {
            return false;
        }
        int id = widget.optInt("id", 0);
        return id > 0 ? removeWidget(context, id) : removePlaceholder(context, recordId);
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
        List<JSONObject> original = deepCopyWidgets(widgets);
        List<JSONObject> updated = deepCopyWidgets(widgets);
        updated.removeIf(item -> item.optInt("id", -1) == widgetId);
        normalizePageOrders(updated);
        // Persist first. If SharedPreferences cannot commit, keep the Android
        // binding and its RemoteViews intact for the next recovery attempt.
        if (!writeWidgets(context, updated)) {
            return false;
        }
        ensureHost(context);
        if (host == null) {
            writeWidgets(context, original);
            return false;
        }
        try {
            host.deleteAppWidgetId(widgetId);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not release Android widget ID " + widgetId + ".", error);
            if (!writeWidgets(context, original)) {
                Log.e(TAG, "Could not roll back widget metadata after ID release failed.");
            }
            return false;
        }
        AppWidgetHostView view = views.remove(widgetId);
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        FrameLayout clip = clips.remove(widgetId);
        resizeHandles.remove(widgetId);
        if (clip != null && clip.getParent() instanceof ViewGroup) {
            ((ViewGroup) clip.getParent()).removeView(clip);
        }
        frames.remove(widgetId);
        lastSizeOptions.remove(widgetId);
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
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
        TextView resizeHandle = resizeHandles.get(view.getAppWidgetId());
        if (resizeHandle != null) {
            int handleSize = Math.round(48f * density);
            int handleMargin = Math.round(8f * density);
            int viewLeft = Math.round(view.getTranslationX());
            int viewTop = Math.round(view.getTranslationY());
            int visibleRight = Math.min(clipWidth, viewLeft + width);
            int visibleBottom = Math.min(clipHeight, viewTop + height);
            int visibleLeft = Math.max(0, viewLeft);
            int visibleTop = Math.max(0, viewTop);
            boolean handleFits = visible && visibleRight - visibleLeft >= handleSize + handleMargin
                    && visibleBottom - visibleTop >= handleSize + handleMargin;
            int handleLeft = visibleRight - handleSize - handleMargin;
            int handleTop = visibleBottom - handleSize - handleMargin;
            FrameLayout.LayoutParams handleParams =
                    (FrameLayout.LayoutParams) resizeHandle.getLayoutParams();
            if (handleParams == null) {
                handleParams = new FrameLayout.LayoutParams(handleSize, handleSize);
            }
            if (handleParams.width != handleSize || handleParams.height != handleSize
                    || handleParams.leftMargin != handleLeft || handleParams.topMargin != handleTop) {
                handleParams.width = handleSize;
                handleParams.height = handleSize;
                handleParams.leftMargin = handleLeft;
                handleParams.topMargin = handleTop;
                resizeHandle.setLayoutParams(handleParams);
            }
            resizeHandle.setVisibility(handleFits ? View.VISIBLE : View.GONE);
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            OmaAppActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (activityRef.get() == activity && !activity.isFinishing()) {
                    restoreWidgets(context);
                }
            });
            return;
        }
        if (host == null || overlay == null || overlay.getParent() == null) {
            return;
        }
        // A layout restore is between its durable prepare and commit/rollback stages.
        // Preserve old bound IDs until the QML recovery journal decides the outcome.
        if (hasWidgetBackupRestoreJournal(context)) {
            return;
        }
        List<JSONObject> widgets = readWidgets(context);
        if (widgets == null) {
            // Do not interpret corrupt preferences as an empty widget list and release users' IDs.
            return;
        }
        cleanupInterruptedPick(context);
        Set<Integer> known = new HashSet<>();
        Set<Integer> savedIds = new HashSet<>();
        for (JSONObject widget : widgets) {
            int id = widget.optInt("id", 0);
            if (id <= 0) {
                continue;
            }
            savedIds.add(id);
            AppWidgetProviderInfo info = id > 0 ? widgetManager(context).getAppWidgetInfo(id) : null;
            ComponentName savedProvider = ComponentName.unflattenFromString(
                    widget.optString("provider", ""));
            if (info == null || info.provider == null || !info.provider.equals(savedProvider)
                    || !hostOwnsId(id)) {
                // Provider packages can be temporarily unavailable during boot,
                // package replacement, or profile restoration. Keep the saved
                // placement so Home can show a rebind placeholder.
                continue;
            }
            known.add(id);
            createWidgetView(context, id, info);
        }
        for (int id : host.getAppWidgetIds()) {
            if (!savedIds.contains(id) && id != pendingWidgetId) {
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
                resizeHandles.remove(id);
                if (stale != null && stale.getParent() instanceof ViewGroup) {
                    ((ViewGroup) stale.getParent()).removeView(stale);
                }
                frames.remove(id);
                lastSizeOptions.remove(id);
            }
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
            view.setVisibility(View.GONE);
            clip.addView(view, new FrameLayout.LayoutParams(1, 1));
            clips.put(widgetId, clip);
            views.put(widgetId, view);
            TextView handle = createResizeHandle(context, widgetId);
            clip.addView(handle, new FrameLayout.LayoutParams(1, 1));
            resizeHandles.put(widgetId, handle);
            WidgetFrame frame = frames.get(widgetId);
            Activity activity = context instanceof Activity ? (Activity) context : activityRef.get();
            if (frame != null && activity != null) {
                applyFrame(activity, view, frame);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not create Android widget view for ID " + widgetId + ".", error);
        }
    }

    private static TextView createResizeHandle(Context context, int widgetId) {
        TextView handle = new TextView(context);
        handle.setText("◢");
        handle.setTextSize(15f);
        handle.setTextColor(0xffb8f27c);
        handle.setGravity(Gravity.CENTER);
        handle.setBackgroundColor(0xdd202020);
        handle.setElevation(8f * context.getResources().getDisplayMetrics().density);
        handle.setFocusable(true);
        handle.setClickable(true);
        handle.setContentDescription(NativeStrings.get("Resize Android widget"));
        handle.setOnClickListener(view -> { });
        final int[] startSize = {0, 0};
        final float[] startTouch = {0f, 0f};
        final String[] recordId = {""};
        handle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    JSONObject widget = findWidget(readWidgets(context), widgetId);
                    if (widget == null) {
                        return false;
                    }
                    recordId[0] = widget.optString("recordId", "");
                    startSize[0] = widget.optInt("widthDp", 0);
                    startSize[1] = widget.optInt("heightDp", 0);
                    startTouch[0] = event.getRawX();
                    startTouch[1] = event.getRawY();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP: {
                    float density = context.getResources().getDisplayMetrics().density;
                    int width = snapWidgetSize(startSize[0]
                            + Math.round((event.getRawX() - startTouch[0]) / density));
                    int height = snapWidgetSize(startSize[1]
                            + Math.round((event.getRawY() - startTouch[1]) / density));
                    JSONObject widget = findWidgetByRecordId(readWidgets(context), recordId[0]);
                    if (widget != null) {
                        width = Math.max(widget.optInt("minWidthDp", 1), width);
                        height = Math.max(widget.optInt("minHeightDp", 1), height);
                        if (!resizeWidgetRecord(context, recordId[0], width, height)) {
                            view.announceForAccessibility(NativeStrings.get(
                                    "Widget size was not changed."));
                        } else if (width != startSize[0] || height != startSize[1]) {
                            OmaAppActivity.notifyAndroidWidgetResizeFinished(recordId[0],
                                    startSize[0], startSize[1], width, height);
                        }
                    }
                    view.performClick();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        });
        return handle;
    }

    private static int snapWidgetSize(int sizeDp) {
        int bounded = Math.max(PLACEMENT_GRID_DP, Math.min(1200, sizeDp));
        return Math.max(PLACEMENT_GRID_DP,
                Math.round((float) bounded / PLACEMENT_GRID_DP) * PLACEMENT_GRID_DP);
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

    private static JSONObject widgetRecord(Context context, int id, AppWidgetProviderInfo info,
                                           String recordId, int page, int order, int xDp, int yDp) {
        if (info == null || info.provider == null || id <= 0 || recordId == null
                || recordId.isEmpty() || page < 0 || page >= 5
                || order < 0 || order >= MAX_WIDGETS) {
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
            int minWidth = boundedProviderSize(info.minWidth);
            int minHeight = boundedProviderSize(info.minHeight);
            JSONObject item = new JSONObject();
            item.put("recordId", recordId);
            item.put("id", id);
            item.put("provider", info.provider.flattenToString());
            item.put("label", label.substring(0, Math.min(160, label.length())));
            item.put("page", page);
            item.put("order", order);
            item.put("xDp", xDp);
            item.put("yDp", yDp);
            item.put("minWidthDp", minWidth);
            item.put("minHeightDp", minHeight);
            item.put("widthDp", Math.max(minWidth, Math.min(1200, 400)));
            item.put("heightDp", Math.max(minHeight, Math.min(1200, 240)));
            item.put("state", "ready");
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
        boolean legacyArray = false;
        int storedSchemaVersion = 1;
        try {
            String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(WIDGETS_KEY, "[]");
            legacyArray = stored.trim().startsWith("[");
            if (legacyArray) {
                // Migrate the original unversioned array shape after sanitizing it.
                array = new JSONArray(stored);
            } else {
                JSONObject root = new JSONObject(stored);
                storedSchemaVersion = root.optInt("schemaVersion", -1);
                if (storedSchemaVersion < 2 || storedSchemaVersion > WIDGETS_SCHEMA_VERSION) {
                    throw new JSONException("unsupported Android widget schema version");
                }
                array = root.getJSONArray("widgets");
            }
            if (array.length() > MAX_WIDGETS) {
                throw new JSONException("saved Android widget count exceeds the host limit");
            }
        } catch (JSONException | RuntimeException error) {
            Log.e(TAG, "Saved Android widget state is unreadable; leaving it untouched.", error);
            return null;
        }
        Set<Integer> ids = new HashSet<>();
        Set<String> recordIds = new HashSet<>();
        for (int index = 0; index < array.length() && result.size() < MAX_WIDGETS; ++index) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                Log.e(TAG, "Saved Android widget state contains a non-object entry; leaving it untouched.");
                return null;
            }
            int id = item.optInt("id", 0);
            String recordId = item.optString("recordId", "");
            String provider = item.optString("provider", "");
            String label = item.optString("label", "");
            int page = item.optInt("page", 0);
            int order = item.optInt("order", index);
            if (id < 0 || (id > 0 && !ids.add(id))
                    || provider.length() > 512 || ComponentName.unflattenFromString(provider) == null
                    || label.length() > 160 || page < 0 || page >= 5
                    || order < 0 || order >= MAX_WIDGETS) {
                Log.e(TAG, "Saved Android widget state contains an invalid entry; leaving it untouched.");
                return null;
            }
            if (recordId.isEmpty()) {
                // Keep identity stable if SharedPreferences is temporarily read-only.
                recordId = id > 0 ? "legacy-" + id : UUID.randomUUID().toString();
            }
            if (recordId.length() > 64 || !recordIds.add(recordId)) {
                Log.e(TAG,
                        "Saved Android widget state contains a duplicate or invalid record ID; leaving it untouched.");
                return null;
            }
            int minWidth = boundedProviderSize(item.optInt("minWidthDp", 160));
            int minHeight = boundedProviderSize(item.optInt("minHeightDp", 120));
            int width = Math.max(minWidth, Math.min(1200, item.optInt("widthDp", 400)));
            int height = Math.max(minHeight, Math.min(1200, item.optInt("heightDp", 240)));
            int xDp = storedSchemaVersion >= 3 ? item.optInt("xDp", -1) : 0;
            int yDp = storedSchemaVersion >= 3 ? item.optInt("yDp", -1)
                    : nextWidgetY(result, page);
            JSONObject sanitized = new JSONObject();
            try {
                sanitized.put("recordId", recordId);
                sanitized.put("id", id);
                sanitized.put("provider", provider);
                sanitized.put("label", safeLabel(label, provider));
                sanitized.put("page", page);
                sanitized.put("order", order);
                sanitized.put("xDp", xDp);
                sanitized.put("yDp", yDp);
                sanitized.put("minWidthDp", minWidth);
                sanitized.put("minHeightDp", minHeight);
                sanitized.put("widthDp", width);
                sanitized.put("heightDp", height);
                sanitized.put("state", id > 0 ? "ready" : "needs_rebind");
                if (xDp < 0 || xDp > 3000 || yDp < 0 || yDp > 20000
                        || (storedSchemaVersion >= 3
                                && (xDp % PLACEMENT_GRID_DP != 0
                                        || yDp % PLACEMENT_GRID_DP != 0))
                        || xDp + Math.max(MIN_WIDGET_CARD_WIDTH_DP, width) > 3000
                        || yDp + height + WIDGET_CHROME_HEIGHT_DP > 20000
                        || hasPlacementConflict(result, sanitized, null)) {
                    sanitized.put("xDp", 0);
                    sanitized.put("yDp", nextWidgetY(result, page));
                }
                result.add(sanitized);
            } catch (JSONException ignored) {
                // JSONObject.put of these bounded primitive values cannot fail.
            }
        }
        result.sort(Comparator.comparingInt((JSONObject item) -> item.optInt("page", 0))
                .thenComparingInt(item -> item.optInt("order", 0)));
        normalizePageOrders(result);
        if ((legacyArray || storedSchemaVersion < WIDGETS_SCHEMA_VERSION)
                && !writeWidgets(context, result)) {
            Log.w(TAG, "Legacy Android widget configuration remains unmigrated.");
        }
        return result;
    }

    private static JSONObject findWidget(List<JSONObject> widgets, int widgetId) {
        if (widgets == null) {
            return null;
        }
        for (JSONObject widget : widgets) {
            if (widget.optInt("id", -1) == widgetId) {
                return widget;
            }
        }
        return null;
    }

    private static JSONObject findWidgetByRecordId(List<JSONObject> widgets, String recordId) {
        if (widgets == null || recordId == null) {
            return null;
        }
        for (JSONObject widget : widgets) {
            if (recordId.equals(widget.optString("recordId", ""))) {
                return widget;
            }
        }
        return null;
    }

    private static boolean writeWidgets(Context context, List<JSONObject> widgets) {
        if (context == null || widgets == null || widgets.size() > MAX_WIDGETS) {
            return false;
        }
        JSONArray array = new JSONArray();
        List<JSONObject> normalized = deepCopyWidgets(widgets);
        normalizePageOrders(normalized);
        List<JSONObject> validated = new ArrayList<>();
        for (int index = 0; index < normalized.size() && index < MAX_WIDGETS; ++index) {
            JSONObject value = normalized.get(index);
            value.remove("state");
            int xDp = value.optInt("xDp", -1);
            int yDp = value.optInt("yDp", -1);
            int widthDp = value.optInt("widthDp", -1);
            int heightDp = value.optInt("heightDp", -1);
            if (xDp < 0 || xDp > 3000 || yDp < 0 || yDp > 20000
                    || xDp % PLACEMENT_GRID_DP != 0 || yDp % PLACEMENT_GRID_DP != 0
                    || widthDp < 1 || widthDp > 1200 || heightDp < 1 || heightDp > 1200
                    || xDp + Math.max(MIN_WIDGET_CARD_WIDTH_DP, widthDp) > 3000
                    || yDp + heightDp + WIDGET_CHROME_HEIGHT_DP > 20000
                    || hasPlacementConflict(validated, value, null)) {
                return false;
            }
            try {
                value.put("order", value.optInt("order", index));
            } catch (JSONException ignored) {
                continue;
            }
            array.put(value);
            validated.add(value);
        }
        JSONObject root = new JSONObject();
        try {
            root.put("schemaVersion", WIDGETS_SCHEMA_VERSION);
            root.put("widgets", array);
        } catch (JSONException ignored) {
            return false;
        }
        boolean saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(WIDGETS_KEY, root.toString()).commit();
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
                .remove(PENDING_ID_KEY)
                .remove(PENDING_RECORD_ID_KEY)
                .remove(PENDING_PAGE_KEY)
                .commit();
        pendingRecordId = "";
        pendingPage = 0;
    }

    private static List<JSONObject> parseWidgetBackup(String json) {
        if (json == null || json.isEmpty() || json.length() > 32_768) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            int backupVersion = root.optInt("schemaVersion", -1);
            if (root.length() != 2 || (backupVersion != 1
                    && backupVersion != BACKUP_SCHEMA_VERSION)) {
                return null;
            }
            JSONArray entries = root.optJSONArray("widgets");
            if (entries == null || entries.length() > MAX_WIDGETS) {
                return null;
            }
            List<JSONObject> placeholders = new ArrayList<>();
            Set<String> identityKeys = new HashSet<>();
            for (int index = 0; index < entries.length(); ++index) {
                JSONObject entry = entries.optJSONObject(index);
                if (entry == null || entry.length() != (backupVersion == 1 ? 8 : 10)) {
                    return null;
                }
                String provider = entry.optString("provider", "");
                String label = entry.optString("label", "");
                int page = entry.optInt("page", -1);
                int order = entry.optInt("order", -1);
                int xDp = backupVersion == 1 ? 0 : entry.optInt("xDp", -1);
                int yDp = backupVersion == 1 ? nextWidgetY(placeholders, page)
                        : entry.optInt("yDp", -1);
                int minWidth = entry.optInt("minWidthDp", -1);
                int minHeight = entry.optInt("minHeightDp", -1);
                int width = entry.optInt("widthDp", -1);
                int height = entry.optInt("heightDp", -1);
                ComponentName component = provider.length() <= 512
                        ? ComponentName.unflattenFromString(provider) : null;
                if (component == null || label.length() > 160 || page < 0 || page >= 5
                        || order < 0 || order >= MAX_WIDGETS
                        || minWidth < 1 || minWidth > 1200 || minHeight < 1 || minHeight > 1200
                        || width < minWidth || width > 1200
                        || height < minHeight || height > 1200
                        || xDp < 0 || xDp > 3000 || yDp < 0 || yDp > 20000
                        || (backupVersion >= 2 && (xDp % PLACEMENT_GRID_DP != 0
                                || yDp % PLACEMENT_GRID_DP != 0))
                        || xDp + Math.max(MIN_WIDGET_CARD_WIDTH_DP, width) > 3000
                        || yDp + height + WIDGET_CHROME_HEIGHT_DP > 20000) {
                    return null;
                }
                String identity = provider + "\u0000" + page + "\u0000" + order;
                if (!identityKeys.add(identity)) {
                    return null;
                }
                JSONObject placeholder = new JSONObject();
                placeholder.put("recordId", UUID.randomUUID().toString());
                placeholder.put("id", 0);
                placeholder.put("provider", provider);
                placeholder.put("label", safeLabel(label, provider));
                placeholder.put("page", page);
                placeholder.put("order", order);
                placeholder.put("xDp", xDp);
                placeholder.put("yDp", yDp);
                placeholder.put("minWidthDp", minWidth);
                placeholder.put("minHeightDp", minHeight);
                placeholder.put("widthDp", width);
                placeholder.put("heightDp", height);
                placeholder.put("state", "needs_rebind");
                if (hasPlacementConflict(placeholders, placeholder, null)) {
                    return null;
                }
                placeholders.add(placeholder);
            }
            placeholders.sort(Comparator.comparingInt((JSONObject item) -> item.optInt("page", 0))
                    .thenComparingInt(item -> item.optInt("order", 0)));
            normalizePageOrders(placeholders);
            return placeholders;
        } catch (JSONException | RuntimeException error) {
            return null;
        }
    }

    private static boolean isWidgetBound(Context context, JSONObject widget) {
        if (context == null || widget == null || widget.optInt("id", 0) <= 0) {
            return false;
        }
        ComponentName expected = ComponentName.unflattenFromString(
                widget.optString("provider", ""));
        if (expected == null) {
            return false;
        }
        int id = widget.optInt("id", 0);
        try {
            AppWidgetManager manager = widgetManager(context);
            AppWidgetProviderInfo info = manager == null ? null : manager.getAppWidgetInfo(id);
            return info != null && expected.equals(info.provider) && hostOwnsId(id);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not verify the Android widget binding.", error);
            return false;
        }
    }

    private static JSONObject copyJsonObject(JSONObject value) {
        if (value == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value.toString());
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    private static List<JSONObject> deepCopyWidgets(List<JSONObject> widgets) {
        List<JSONObject> copies = new ArrayList<>();
        if (widgets != null) {
            for (JSONObject widget : widgets) {
                copies.add(copyJsonObject(widget));
            }
        }
        return copies;
    }

    private static void normalizePageOrders(List<JSONObject> widgets) {
        if (widgets == null) {
            return;
        }
        widgets.sort(Comparator.comparingInt((JSONObject item) -> item.optInt("page", 0))
                .thenComparingInt(item -> item.optInt("order", 0)));
        Map<Integer, Integer> nextOrder = new HashMap<>();
        for (JSONObject widget : widgets) {
            int page = Math.max(0, Math.min(4, widget.optInt("page", 0)));
            int order = nextOrder.containsKey(page) ? nextOrder.get(page) : 0;
            try {
                widget.put("page", page);
                widget.put("order", order);
            } catch (JSONException ignored) {
                // Bounded primitive values cannot fail to encode.
            }
            nextOrder.put(page, order + 1);
        }
    }

    private static int countWidgetsOnPage(List<JSONObject> widgets, int page) {
        int count = 0;
        if (widgets != null) {
            for (JSONObject widget : widgets) {
                if (widget.optInt("page", 0) == page) {
                    ++count;
                }
            }
        }
        return count;
    }

    private static int nextWidgetY(List<JSONObject> widgets, int page) {
        int bottom = 0;
        if (widgets != null) {
            for (JSONObject widget : widgets) {
                if (widget.optInt("page", -1) == page) {
                    bottom = Math.max(bottom, widget.optInt("yDp", 0)
                            + widget.optInt("heightDp", 240) + WIDGET_CHROME_HEIGHT_DP);
                }
            }
        }
        int snapped = ((bottom + PLACEMENT_GRID_DP - 1) / PLACEMENT_GRID_DP)
                * PLACEMENT_GRID_DP;
        return Math.min(20000 - WIDGET_CHROME_HEIGHT_DP - 120, snapped);
    }

    private static boolean hasPlacementConflict(List<JSONObject> widgets, JSONObject candidate,
                                                JSONObject ignored) {
        if (widgets == null || candidate == null) {
            return false;
        }
        int page = candidate.optInt("page", -1);
        int left = candidate.optInt("xDp", -1);
        int top = candidate.optInt("yDp", -1);
        int right = left + Math.max(MIN_WIDGET_CARD_WIDTH_DP,
                candidate.optInt("widthDp", 0));
        int bottom = top + candidate.optInt("heightDp", 0) + WIDGET_CHROME_HEIGHT_DP;
        for (JSONObject other : widgets) {
            if (other == ignored || other == candidate
                    || other.optInt("page", -2) != page) {
                continue;
            }
            int otherLeft = other.optInt("xDp", -1);
            int otherTop = other.optInt("yDp", -1);
            int otherRight = otherLeft + Math.max(MIN_WIDGET_CARD_WIDTH_DP,
                    other.optInt("widthDp", 0));
            int otherBottom = otherTop + other.optInt("heightDp", 0)
                    + WIDGET_CHROME_HEIGHT_DP;
            if (left < otherRight && right > otherLeft
                    && top < otherBottom && bottom > otherTop) {
                return true;
            }
        }
        return false;
    }

    private static int boundedProviderSize(int size) {
        return Math.max(1, Math.min(1200, size));
    }

    private static String safeLabel(String label, String provider) {
        String value = label == null ? "" : label.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.isEmpty()) {
            ComponentName component = ComponentName.unflattenFromString(provider == null ? "" : provider);
            value = component == null ? "Android widget" : component.getPackageName();
        }
        return value.substring(0, Math.min(160, value.length()));
    }

    private static JSONObject preserveWidgetGeometry(JSONObject previous, JSONObject updated) {
        if (previous == null || updated == null) {
            return updated;
        }
        try {
            updated.put("page", previous.optInt("page", 0));
            updated.put("order", previous.optInt("order", 0));
            updated.put("xDp", previous.optInt("xDp", 0));
            updated.put("yDp", previous.optInt("yDp", 0));
            updated.put("widthDp", Math.max(updated.optInt("minWidthDp", 1),
                    Math.min(1200, previous.optInt("widthDp", 400))));
            updated.put("heightDp", Math.max(updated.optInt("minHeightDp", 1),
                    Math.min(1200, previous.optInt("heightDp", 240))));
        } catch (JSONException ignored) {
            // These values are bounded primitives.
        }
        return updated;
    }

    private static boolean removePlaceholder(Context context, String recordId) {
        List<JSONObject> widgets = readWidgets(context);
        JSONObject placeholder = findWidgetByRecordId(widgets, recordId);
        if (placeholder == null || placeholder.optInt("id", 0) > 0) {
            return false;
        }
        List<JSONObject> updated = deepCopyWidgets(widgets);
        updated.remove(findWidgetByRecordId(updated, recordId));
        normalizePageOrders(updated);
        if (!writeWidgets(context, updated)) {
            return false;
        }
        OmaAppActivity.notifyAndroidWidgetsChanged();
        return true;
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

        @Override
        protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                                                 AppWidgetProviderInfo appWidget) {
            return new OmaAppWidgetHostView(context);
        }

        void clearViewsForActivityDetach() {
            clearViews();
        }
    }

    /**
     * Keeps blank parts of a RemoteViews card from trapping Home gestures. A DOWN is
     * delivered to the provider only when it hits a clickable/scrollable descendant;
     * Android then retains that target for the complete gesture as usual.
     */
    private static final class OmaAppWidgetHostView extends AppWidgetHostView {
        private boolean touchStartedInRemoteViews;

        OmaAppWidgetHostView(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touchStartedInRemoteViews = hasInteractiveTargetAt(
                        this, event.getX(), event.getY());
                if (!touchStartedInRemoteViews) {
                    return false;
                }
            } else if (!touchStartedInRemoteViews) {
                return false;
            }

            boolean handled = super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchStartedInRemoteViews = false;
            }
            return handled;
        }

        private static boolean hasInteractiveTargetAt(View view, float x, float y) {
            if (view.getVisibility() != View.VISIBLE || !view.isEnabled()
                    || view.getAlpha() <= 0f || x < 0f || y < 0f
                    || x >= view.getWidth() || y >= view.getHeight()) {
                return false;
            }
            if (view.isClickable() || view.isLongClickable() || view.isContextClickable()
                    || view.isScrollContainer() || view.canScrollHorizontally(-1)
                    || view.canScrollHorizontally(1) || view.canScrollVertically(-1)
                    || view.canScrollVertically(1)) {
                return true;
            }
            if (!(view instanceof ViewGroup)) {
                return false;
            }

            ViewGroup group = (ViewGroup) view;
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                View child = group.getChildAt(index);
                Rect childBounds = new Rect();
                child.getHitRect(childBounds);
                if (!childBounds.contains((int) x, (int) y)) {
                    continue;
                }
                float childX = x - child.getLeft() + group.getScrollX();
                float childY = y - child.getTop() + group.getScrollY();
                if (hasInteractiveTargetAt(child, childX, childY)) {
                    return true;
                }
            }
            return false;
        }
    }
}
