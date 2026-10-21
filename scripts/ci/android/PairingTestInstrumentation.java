package app.omaapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;

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
            report("paired", connected);

            String updatedTheme = testArguments.getString("updatedTheme", "");
            if (!updatedTheme.isEmpty()) {
                JSONObject updated = awaitTheme(context, updatedTheme, PAIRING_TIMEOUT_MILLIS, true);
                report("theme_updated", updated);
            }

            if (testArguments.getBoolean("revoke", false)) {
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
        JSONObject geometry = snapshot.optJSONObject("hyprland");
        if (colors == null || !"#a7c080".equals(colors.optString("accent"))
                || spacing == null || spacing.optInt("row-gap", -1) != 7
                || spacing.optInt("panel-gap", -1) != 17
                || geometry == null || geometry.optInt("rounding", -1) != 13
                || Math.abs(geometry.optDouble("roundingPower", -1) - 4.0) > 0.001
                || geometry.optInt("borderWidth", -1) != 2
                || geometry.optInt("gapsIn", -1) != 5 || geometry.optInt("gapsOut", -1) != 10
                || !geometry.optString("activeBorder").contains("45deg")) {
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
