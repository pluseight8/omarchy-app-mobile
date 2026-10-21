package app.omaapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.File;
import java.io.FileOutputStream;

public final class LauncherBridge {
    private static final int MAX_DYNAMIC_SHORTCUTS = 4;

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

    public static String iconUrl(Context context, String packageName, String activityName) {
        if (context == null || packageName == null || activityName == null
                || !packageName.matches("[A-Za-z0-9_.]{1,200}")
                || activityName.length() > 255 || !activityName.startsWith(packageName)) {
            return "";
        }

        PackageManager packageManager = context.getPackageManager();
        ComponentName component = new ComponentName(packageName, activityName);
        final ActivityInfo activity;
        final Drawable icon;
        try {
            activity = packageManager.getActivityInfo(component, 0);
            icon = packageManager.getActivityIcon(component);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return "";
        }

        File cacheDirectory = new File(context.getCacheDir(), "launcher-icons");
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            return "";
        }
        long updatedAt = activity.applicationInfo == null || activity.applicationInfo.sourceDir == null
                ? 0 : new File(activity.applicationInfo.sourceDir).lastModified();
        String cacheKey = activity.packageName + "/" + activity.name + "/" + updatedAt;
        String fileName = Integer.toUnsignedString(cacheKey.hashCode(), 16) + ".png";
        File iconFile = new File(cacheDirectory, fileName);
        if (!iconFile.isFile()) {
            File temporary = new File(cacheDirectory, fileName + ".tmp");
            Bitmap bitmap = null;
            try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    return "";
                }
                output.getFD().sync();
            } catch (Exception error) {
                temporary.delete();
                return "";
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            if (!temporary.renameTo(iconFile)) {
                temporary.delete();
                return "";
            }
        }
        return Uri.fromFile(iconFile).toString();
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

    /** Publishes pinned launcher apps as long-press shortcuts for OmaApp. */
    public static int syncDynamicShortcuts(Context context, String favoritesJson) {
        if (context == null || favoritesJson == null || favoritesJson.length() > 64 * 1024) {
            return 0;
        }
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null) {
            return 0;
        }

        final JSONArray favorites;
        try {
            favorites = new JSONArray(favoritesJson);
        } catch (JSONException error) {
            return 0;
        }
        int limit = Math.min(MAX_DYNAMIC_SHORTCUTS,
                Math.max(0, shortcutManager.getMaxShortcutCountPerActivity()));
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        for (int index = 0; index < favorites.length() && shortcuts.size() < limit; ++index) {
            JSONObject favorite = favorites.optJSONObject(index);
            if (favorite == null) {
                continue;
            }
            String packageName = favorite.optString("packageName", "");
            String activityName = favorite.optString("activityName", "");
            String label = favorite.optString("name", "").trim();
            if (!packageName.matches("[A-Za-z0-9_.]{1,200}")
                    || activityName.length() > 255 || !activityName.startsWith(packageName)
                    || label.isEmpty() || label.length() > 160) {
                continue;
            }

            ComponentName component = new ComponentName(packageName, activityName);
            final ActivityInfo activity;
            final Drawable activityIcon;
            try {
                activity = packageManager.getActivityInfo(component, 0);
                activityIcon = packageManager.getActivityIcon(component);
            } catch (PackageManager.NameNotFoundException | RuntimeException error) {
                continue;
            }
            if (activity.applicationInfo == null || !activity.exported) {
                continue;
            }

            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(component);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context,
                    shortcutId(packageName, activityName));
            builder.setShortLabel(shortLabel(label));
            builder.setLongLabel(label);
            builder.setIntent(launch);
            builder.setRank(shortcuts.size());
            if (activityIcon != null) {
                builder.setIcon(shortcutIcon(activityIcon));
            }
            shortcuts.add(builder.build());
        }

        try {
            return shortcutManager.setDynamicShortcuts(shortcuts) ? shortcuts.size() : 0;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return 0;
        }
    }

    private static String shortLabel(String value) {
        int end = value.offsetByCodePoints(0, Math.min(25, value.codePointCount(0, value.length())));
        return value.substring(0, end);
    }

    private static String shortcutId(String packageName, String activityName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((packageName + "/" + activityName)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("app-");
            for (int index = 0; index < 12; ++index) {
                result.append(String.format(Locale.ROOT, "%02x", hash[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available.", error);
        }
    }

    private static Icon shortcutIcon(Drawable drawable) {
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return Icon.createWithAdaptiveBitmap(bitmap);
    }
}
