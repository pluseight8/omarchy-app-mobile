#!/usr/bin/env python3
"""Add the CI-only Android instrumentation runner to the emulator debug build."""

from pathlib import Path
import shutil


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
DESTINATION = ROOT / "app/android/src/app/omaapp/PairingTestInstrumentation.java"
WIDGET_PROVIDER_SOURCE = ROOT / "scripts/ci/android/CiAppWidgetProvider.java"
WIDGET_PROVIDER_DESTINATION = ROOT / "app/android/src/app/omaapp/CiAppWidgetProvider.java"
WIDGET_RESOURCES = ROOT / "scripts/ci/android/res"
WIDGET_RESOURCE_DESTINATION = ROOT / "app/android/res"
MANIFEST = ROOT / "app/android/AndroidManifest.xml"
MARKER = "    <supports-screens\n"
APPLICATION_MARKER = '        <activity\n            android:name="app.omaapp.QrScannerActivity"'
INSTRUMENTATION = '''    <instrumentation
        android:name="app.omaapp.PairingTestInstrumentation"
        android:targetPackage="app.omaapp"
        android:label="OmaApp pairing smoke test" />

'''
WIDGET_PROVIDER = '''    <receiver
        android:name="app.omaapp.CiAppWidgetProvider"
        android:exported="true"
        android:label="OmaApp CI widget"
        android:permission="android.permission.BIND_APPWIDGET">
        <intent-filter>
            <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
        </intent-filter>
        <meta-data
            android:name="android.appwidget.provider"
            android:resource="@xml/omaapp_ci_widget_info" />
    </receiver>

'''


def main() -> None:
    manifest = MANIFEST.read_text(encoding="utf-8")
    if "app.omaapp.PairingTestInstrumentation" in manifest:
        raise SystemExit("The CI instrumentation runner must not be part of the product manifest.")
    if "app.omaapp.CiAppWidgetProvider" in manifest:
        raise SystemExit("The CI AppWidget provider must not be part of the product manifest.")
    if MARKER not in manifest:
        raise SystemExit("Could not find the Android manifest insertion point.")
    if APPLICATION_MARKER not in manifest:
        raise SystemExit("Could not find the Android application component insertion point.")
    shutil.copyfile(SOURCE, DESTINATION)
    shutil.copyfile(WIDGET_PROVIDER_SOURCE, WIDGET_PROVIDER_DESTINATION)
    shutil.copytree(WIDGET_RESOURCES, WIDGET_RESOURCE_DESTINATION, dirs_exist_ok=True)
    manifest = manifest.replace(APPLICATION_MARKER, WIDGET_PROVIDER + APPLICATION_MARKER, 1)
    MANIFEST.write_text(
        manifest.replace(MARKER, INSTRUMENTATION + MARKER, 1),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
