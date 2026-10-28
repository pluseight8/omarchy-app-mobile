package app.omaapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public final class FileTransferActivity extends Activity {
    private static final int PICK_UPLOAD = 6101;
    private static final int PICK_DOWNLOAD = 6102;
    private static final long MAX_TRANSFER_BYTES = 100L * 1024L * 1024L;
    private static final String STATE_DOWNLOAD_NAME = "pending_download_name";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable featureMessageRefresh = new Runnable() {
        @Override
        public void run() {
            if (status != null && !isFinishing()) {
                String message = getSharedPreferences(PairingBridge.PREFERENCES, MODE_PRIVATE)
                        .getString("feature_message", "");
                if (!message.isEmpty()) {
                    status.setText(NativeStrings.featureMessage(message));
                }
                handler.postDelayed(this, 800L);
            }
        }
    };
    private TextView status;
    private Button sendButton;
    private Button receiveButton;
    private String downloadName;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        downloadName = restorePendingDownloadName(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(margin, margin, margin, margin);

        TextView heading = new TextView(this);
        String hostName = getSharedPreferences(PairingBridge.PREFERENCES, MODE_PRIVATE)
                .getString("host_name", "Omarchy computer");
        heading.setText(NativeStrings.format("Files · %1", hostName));
        heading.setTextSize(22);
        layout.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText(NativeStrings.get(
                "Files move through your pinned local connection. The computer saves uploads in Downloads/OmaApp."));
        status.setTextSize(14);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = margin / 2;
        layout.addView(status, statusParams);

        sendButton = new Button(this);
        sendButton.setText(NativeStrings.get("Send a file to computer"));
        sendButton.setOnClickListener(view -> pickUpload());
        layout.addView(sendButton, buttonParams(margin));

        receiveButton = new Button(this);
        receiveButton.setText(NativeStrings.get("Save a computer file to phone"));
        receiveButton.setOnClickListener(view -> loadRemoteFiles());
        layout.addView(receiveButton, buttonParams(margin / 3));

        JSONObject features;
        try {
            features = new JSONObject(getSharedPreferences(
                    PairingBridge.PREFERENCES, MODE_PRIVATE).getString("features_cache", "{}"));
        } catch (JSONException error) {
            features = new JSONObject();
        }
        JSONObject permissions = features.optJSONObject("permissions");
        sendButton.setEnabled(permissions != null && permissions.optBoolean("files.receive", false));
        receiveButton.setEnabled(permissions != null && permissions.optBoolean("files.send", false));
        if (!sendButton.isEnabled() && !receiveButton.isEnabled()) {
            status.setText(NativeStrings.get(
                    "Enable Send files to phone or Receive files from phone in the Omarchy plugin."));
        }

        Button close = new Button(this);
        close.setText(NativeStrings.get("Close"));
        close.setOnClickListener(view -> finish());
        layout.addView(close, buttonParams(margin / 3));
        setContentView(layout);
    }

    private LinearLayout.LayoutParams buttonParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private void pickUpload() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_UPLOAD);
    }

    private void loadRemoteFiles() {
        status.setText(NativeStrings.get("Loading files shared from the computer…"));
        JSONObject credentials = PairingStore.readCredential(this);
        if (credentials == null || !credentials.has("token")) {
            status.setText(NativeStrings.get("Pair OmaApp with a computer first."));
            return;
        }
        network.execute(() -> {
            try {
                PairingBridge.HttpResult result = PairingBridge.executeJson(
                        credentials, "/v1/files", "GET", null, true);
                if (result.status != 200) {
                    throw new IllegalStateException(PairingBridge.featureError(result));
                }
                JSONArray files = result.body.optJSONArray("files");
                ArrayList<String> names = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                if (files != null) {
                    for (int index = 0; index < files.length(); ++index) {
                        JSONObject file = files.optJSONObject(index);
                        if (file == null) {
                            continue;
                        }
                        String name = file.optString("name", "");
                        long size = file.optLong("size", -1);
                        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 ._()@+-]{0,159}")
                                || size < 0 || size > MAX_TRANSFER_BYTES) {
                            continue;
                        }
                        names.add(name);
                        labels.add(name + "  ·  " + formatSize(size));
                    }
                }
                runOnUiThread(() -> showRemoteFileChooser(names, labels));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(
                        NativeStrings.fileTransferError(error.getMessage())));
            }
        });
    }

    private void showRemoteFileChooser(ArrayList<String> names, ArrayList<String> labels) {
        if (names.isEmpty()) {
            status.setText(NativeStrings.get(
                    "No files are shared from Downloads/OmaApp on this computer."));
            return;
        }
        status.setText(NativeStrings.get("Choose a file to save to the phone."));
        new AlertDialog.Builder(this)
                .setTitle(NativeStrings.get("Computer files"))
                .setItems(labels.toArray(new String[0]), (dialog, index) -> {
                    downloadName = names.get(index);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_TITLE, downloadName);
                    startActivityForResult(intent, PICK_DOWNLOAD);
                })
                .setNegativeButton(NativeStrings.get("Cancel"), null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_DOWNLOAD) {
            String selectedName = downloadName;
            downloadName = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && selectedName != null) {
                receiveFile(data.getData(), selectedName);
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == PICK_UPLOAD) {
            sendFile(uri);
        }
    }

    static String restorePendingDownloadName(Bundle state) {
        return state == null ? null : state.getString(STATE_DOWNLOAD_NAME);
    }

    static void savePendingDownloadName(Bundle state, String name) {
        if (name == null) {
            state.remove(STATE_DOWNLOAD_NAME);
        } else {
            state.putString(STATE_DOWNLOAD_NAME, name);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        savePendingDownloadName(outState, downloadName);
        super.onSaveInstanceState(outState);
    }

    private void sendFile(Uri uri) {
        String displayName = null;
        long size = -1;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
                if (!cursor.isNull(1)) {
                    size = cursor.getLong(1);
                }
            }
        } catch (RuntimeException ignored) {
            // Some document providers omit metadata; those files need a provider with a known size.
        }
        if (displayName == null || size < 1 || size > MAX_TRANSFER_BYTES) {
            status.setText(NativeStrings.get("Choose a named file between 1 byte and 100 MiB."));
            return;
        }
        status.setText(NativeStrings.format("Sending %1…", displayName));
        PairingBridge.uploadFile(this, uri, displayName, size);
    }

    private void receiveFile(Uri destination, String name) {
        JSONObject credentials = PairingStore.readCredential(this);
        if (credentials == null || !credentials.has("token")) {
            status.setText(NativeStrings.get("Pair OmaApp with a computer first."));
            return;
        }
        status.setText(NativeStrings.format("Saving %1 to the selected location…", name));
        network.execute(() -> {
            HttpsURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
                connection = PairingBridge.openConnection(credentials, "/v1/files/" + encoded,
                        "GET", true);
                int code = connection.getResponseCode();
                if (code != 200) {
                    throw new IllegalStateException(NativeStrings.format(
                            "Computer rejected the file request (HTTP %1).", code));
                }
                try (InputStream input = connection.getInputStream();
                     OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) {
                        throw new IllegalStateException(
                                NativeStrings.get("Android could not open the selected destination."));
                    }
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_TRANSFER_BYTES) {
                            throw new IllegalArgumentException("The downloaded file exceeds 100 MiB.");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                runOnUiThread(() -> status.setText(
                        NativeStrings.format("Saved %1 to the selected phone location.", name)));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(
                        NativeStrings.fileTransferError(error.getMessage())));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KiB";
        }
        return (bytes / (1024 * 1024)) + " MiB";
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(featureMessageRefresh);
        network.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(featureMessageRefresh);
        handler.post(featureMessageRefresh);
    }
}
