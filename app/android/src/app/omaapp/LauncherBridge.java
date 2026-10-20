package app.omaapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LauncherBridge {
    private LauncherBridge() {}

    public static String listLauncherApps(Context context) {
        JSONArray result = new JSONArray();
        if (context == null) {
            return result.toString();
        }

        PackageManager packageManager = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = packageManager.queryIntentActivities(
                query, PackageManager.MATCH_DEFAULT_ONLY);

        ArrayList<JSONObject> entries = new ArrayList<>();
        for (ResolveInfo resolveInfo : activities) {
            ActivityInfo activity = resolveInfo.activityInfo;
            if (activity == null || activity.packageName == null || activity.name == null) {
                continue;
            }

            CharSequence label = resolveInfo.loadLabel(packageManager);
            String name = label == null ? "" : label.toString().trim();
            if (name.isEmpty() || name.length() > 160) {
                continue;
            }

            try {
                JSONObject item = new JSONObject();
                item.put("name", name);
                item.put("packageName", activity.packageName);
                item.put("activityName", activity.name);
                entries.add(item);
            } catch (JSONException ignored) {
                // Skip malformed platform metadata and keep the remaining entries.
            }
        }

        entries.sort(Comparator.comparing(
                item -> item.optString("name").toLowerCase(Locale.ROOT)));
        for (JSONObject item : entries) {
            result.put(item);
        }
        return result.toString();
    }

    public static boolean launchApp(Context context, String packageName, String activityName) {
        if (context == null
                || packageName == null
                || activityName == null
                || !packageName.matches("[A-Za-z0-9_.]{1,200}")
                || activityName.length() > 255
                || !activityName.startsWith(packageName)) {
            return false;
        }

        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setComponent(new ComponentName(packageName, activityName));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (context.getPackageManager().resolveActivity(
                    launch, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                return false;
            }
            context.startActivity(launch);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }
}
