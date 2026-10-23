package app.omaapp;

import android.content.SharedPreferences;

import org.json.JSONObject;

/** Persists a cursor only after the event long-poll returns an event to the client. */
final class EventRevisionCursor {
    static final String KEY = "event_revision";

    private EventRevisionCursor() {}

    static long current(SharedPreferences preferences) {
        return Math.max(0L, preferences.getLong(KEY, 0L));
    }

    static boolean acceptEvent(SharedPreferences preferences, JSONObject event) {
        long previous = current(preferences);
        long next = event.optLong("revision", previous);
        if (next <= previous || next < 0L) {
            return false;
        }
        preferences.edit().putLong(KEY, next).apply();
        return true;
    }

    static void reset(SharedPreferences preferences) {
        preferences.edit().remove(KEY).apply();
    }
}
