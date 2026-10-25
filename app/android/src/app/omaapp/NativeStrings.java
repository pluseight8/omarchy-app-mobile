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
        if (!translated.equals(message) || isKnownFeatureMessage(message)) {
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
        if (!translated.equals(message) || isKnownFileTransferError(message)) {
            return translated;
        }
        Matcher featureFailure = FAILED_FEATURE_REQUEST.matcher(message);
        if (featureFailure.matches()) {
            return format("Computer feature request failed (HTTP %1).", featureFailure.group(1));
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

    private static boolean isKnownFeatureMessage(String message) {
        switch (message) {
            case "There is no text in the phone clipboard.":
            case "Clipboard text is empty or exceeds 64 KiB.":
            case "Could not read phone clipboard text.":
            case "Pair OmaApp with a computer first.":
            case "Reading computer clipboard…":
            case "Android clipboard is unavailable.":
            case "Computer text copied to the phone clipboard.":
            case "This media action is not allowed.":
            case "This computer command is not configured.":
            case "The selected file name or size is not supported.":
            case "The computer clipboard is unavailable in this session.":
            case "Enable this feature for this phone in the Omarchy plugin permissions.":
            case "Computer feature request failed. Check the local network and plugin permissions.":
            case "Sending command to the computer…":
            case "Phone clipboard sent to the computer.":
            case "Computer action completed.":
                return true;
            default:
                return false;
        }
    }

    private static boolean isKnownFileTransferError(String message) {
        switch (message) {
            case "Pair OmaApp with a computer first.":
            case "The selected file name or size is not supported.":
            case "Android could not open this file.":
            case "The selected file changed while sending.":
            case "The computer clipboard is unavailable in this session.":
            case "Enable this feature for this phone in the Omarchy plugin permissions.":
            case "Computer file transfer failed. Check pairing and the network.":
                return true;
            default:
                return false;
        }
    }
}
