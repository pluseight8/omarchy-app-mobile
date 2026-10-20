package app.omaapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QrScannerActivity extends Activity implements LifecycleOwner {
    private static final int CAMERA_PERMISSION_REQUEST = 4102;
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
    private final AtomicBoolean resultAccepted = new AtomicBoolean(false);
    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private LifecycleCameraController controller;
    private TextView message;

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        PreviewView preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        frame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        message = new TextView(this);
        message.setText("Scan the temporary OmaApp pairing QR code");
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        message.setPadding(20, 16, 20, 16);
        message.setBackgroundColor(0xAA101315);
        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        frame.addView(message, messageParams);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(view -> finish());
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        buttonParams.bottomMargin = 28;
        frame.addView(cancel, buttonParams);
        setContentView(frame);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);
        analysisExecutor = Executors.newSingleThreadExecutor();

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera(preview);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void bindCamera(PreviewView preview) {
        controller = new LifecycleCameraController(this);
        controller.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        controller.setImageAnalysisAnalyzer(analysisExecutor, this::analyzeFrame);
        preview.setController(controller);
        controller.bindToLifecycle(this);
    }

    private void analyzeFrame(ImageProxy proxy) {
        if (resultAccepted.get()) {
            proxy.close();
            return;
        }
        android.media.Image image = proxy.getImage();
        if (image == null) {
            proxy.close();
            return;
        }

        InputImage input = InputImage.fromMediaImage(
                image, proxy.getImageInfo().getRotationDegrees());
        scanner.process(input)
                .addOnSuccessListener(analysisExecutor, results -> {
                    for (Barcode barcode : results) {
                        String raw = barcode.getRawValue();
                        if (raw != null && isOmaAppPairingPayload(raw)
                                && resultAccepted.compareAndSet(false, true)) {
                            if (PairingStore.writePendingScan(this, raw)) {
                                runOnUiThread(() -> {
                                    message.setText("QR scanned. Return to OmaApp to review the computer.");
                                    finish();
                                });
                            } else {
                                resultAccepted.set(false);
                                runOnUiThread(() -> message.setText(
                                        "Could not securely store the pairing code. Please scan again."));
                            }
                            break;
                        }
                    }
                })
                .addOnCompleteListener(analysisExecutor, task -> proxy.close());
    }

    private static boolean isOmaAppPairingPayload(String raw) {
        if (raw.length() > 2048) {
            return false;
        }
        try {
            JSONObject payload = new JSONObject(raw);
            return payload.optInt("schema", 0) == 1
                    && payload.optString("url").startsWith("https://")
                    && payload.optString("fingerprint").matches("[a-fA-F0-9]{64}")
                    && payload.optString("pairSecret").length() >= 32;
        } catch (JSONException error) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ViewGroup content = findViewById(android.R.id.content);
            FrameLayout frame = content.getChildCount() > 0
                    && content.getChildAt(0) instanceof FrameLayout
                    ? (FrameLayout) content.getChildAt(0) : null;
            PreviewView preview = frame == null ? null : findPreview(frame);
            if (preview != null) {
                bindCamera(preview);
            } else {
                finish();
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            message.setText("Camera permission is needed to scan the pairing QR code.");
        }
    }

    private static PreviewView findPreview(FrameLayout frame) {
        for (int index = 0; index < frame.getChildCount(); index++) {
            if (frame.getChildAt(index) instanceof PreviewView) {
                return (PreviewView) frame.getChildAt(index);
            }
        }
        return null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
    }

    @Override
    protected void onPause() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.clearImageAnalysisAnalyzer();
            controller.unbind();
        }
        if (scanner != null) {
            scanner.close();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
        }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }
}
