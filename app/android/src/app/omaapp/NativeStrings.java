package app.omaapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accesses the Qt Linguist catalog so native Android activities use the same language. */
public final class NativeStrings {
    private static final Pattern SENDING_FEATURE_FILE =
            Pattern.compile("^Sending (.+) to the computer…$");
    private static final Pattern SENT_FEATURE_FILE =
            Pattern.compile("^Sent (.+) to Downloads/OmaApp\\.$");
    private static final Pattern REJECTED_FEATURE_FILE =
            Pattern.compile("^Computer rejected the file \\(HTTP ([0-9]{3})\\)\\.$");
    private static final Pattern FAILED_FEATURE_REQUEST =
            Pattern.compile("^Computer feature request failed \\(HTTP ([0-9]{3})\\)\\.$");
    private static final Pattern SENDING_FILE = Pattern.compile("^Sending (.+)…$");
    private static final Pattern SAVING_FILE =
            Pattern.compile("^Saving (.+) to the selected location…$");
    private static final Pattern REJECTED_FILE_REQUEST =
            Pattern.compile("^Computer rejected the file request \\(HTTP ([0-9]{3})\\)\\.$");
    private static final Pattern SAVED_FILE =
            Pattern.compile("^Saved (.+) to the selected phone location\\.$");

    private NativeStrings() {
    }

    private static native String translate(String source);

    public static String get(String source) {
        String result = translate(source);
        return result == null ? source : result;
    }

    public static String format(String source, Object... arguments) {
        String result = get(source);
        for (int index = 0; index < arguments.length; index++) {
            result = result.replace("%" + (index + 1), String.valueOf(arguments[index]));
        }
        return result;
    }

    public static String featureMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        String translated = get(message);
        if (!translated.equals(message)) {
            return translated;
        }
        Matcher match = SENDING_FEATURE_FILE.matcher(message);
        if (match.matches()) {
            return format("Sending %1 to the computer…", match.group(1));
        }
        match = SENT_FEATURE_FILE.matcher(message);
        if (match.matches()) {
            return format("Sent %1 to Downloads/OmaApp.", match.group(1));
        }
        match = REJECTED_FEATURE_FILE.matcher(message);
        if (match.matches()) {
            return format("Computer rejected the file (HTTP %1).", match.group(1));
        }
        match = FAILED_FEATURE_REQUEST.matcher(message);
        if (match.matches()) {
            return format("Computer feature request failed (HTTP %1).", match.group(1));
        }
        return get("Computer feature request failed. Check the local network and plugin permissions.");
    }

    public static String fileTransferError(String message) {
        if (message == null || message.isEmpty()) {
            return get("Computer file transfer failed. Check pairing and the network.");
        }
        String translated = get(message);
        if (!translated.equals(message)) {
            return translated;
        }
        Matcher match = REJECTED_FILE_REQUEST.matcher(message);
        if (match.matches()) {
            return format("Computer rejected the file request (HTTP %1).", match.group(1));
        }
        return get("Computer file transfer failed. Check pairing and the network.");
    }

    public static String fileTransferMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        String translated = get(message);
        if (!translated.equals(message)) {
            return translated;
        }
        Matcher match = SENDING_FILE.matcher(message);
        if (match.matches()) {
            return format("Sending %1…", match.group(1));
        }
        match = SAVING_FILE.matcher(message);
        if (match.matches()) {
            return format("Saving %1 to the selected location…", match.group(1));
        }
        match = SAVED_FILE.matcher(message);
        if (match.matches()) {
            return format("Saved %1 to the selected phone location.", match.group(1));
        }
        return fileTransferError(message);
    }
}
