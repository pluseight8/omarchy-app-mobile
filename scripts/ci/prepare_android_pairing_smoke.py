#!/usr/bin/env python3
"""Add the CI-only Android instrumentation runner to the emulator debug build."""

from pathlib import Path
import shutil


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
DESTINATION = ROOT / "app/android/src/app/omaapp/PairingTestInstrumentation.java"
WIDGET_PROVIDER_SOURCE = ROOT / "scripts/ci/android/CiAppWidgetProvider.java"
WIDGET_PROVIDER_DESTINATION = ROOT / "app/android/src/app/omaapp/CiAppWidgetProvider.java"
ACCESSIBILITY_SERVICE_SOURCE = ROOT / "scripts/ci/android/CiAccessibilityService.java"
ACCESSIBILITY_SERVICE_DESTINATION = ROOT / "app/android/src/app/omaapp/CiAccessibilityService.java"
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
FIXTURE_QUERY = '        <package android:name="app.omaapp.launcherfixture" />\n'
WIDGET_PROVIDER = '''    <receiver
        android:name="app.omaapp.CiAppWidgetProvider"
        android:exported="true"
        android:label="OmaApp CI widget">
        <intent-filter>
            <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
        </intent-filter>
        <meta-data
            android:name="android.appwidget.provider"
            android:resource="@xml/omaapp_ci_widget_info" />
    </receiver>

'''
ACCESSIBILITY_SERVICE = '''        <service
            android:name="app.omaapp.CiAccessibilityService"
            android:exported="true"
            android:label="OmaApp CI accessibility test service"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/omaapp_ci_accessibility_service" />
        </service>

'''


def main() -> None:
    manifest = MANIFEST.read_text(encoding="utf-8")
    if "app.omaapp.PairingTestInstrumentation" in manifest:
        raise SystemExit("The CI instrumentation runner must not be part of the product manifest.")
    if "app.omaapp.CiAppWidgetProvider" in manifest:
        raise SystemExit("The CI AppWidget provider must not be part of the product manifest.")
    if "app.omaapp.CiAccessibilityService" in manifest:
        raise SystemExit("The CI accessibility service must not be part of the product manifest.")
    if MARKER not in manifest:
        raise SystemExit("Could not find the Android manifest insertion point.")
    if APPLICATION_MARKER not in manifest:
        raise SystemExit("Could not find the Android application component insertion point.")
    if FIXTURE_QUERY not in manifest:
        manifest = manifest.replace("    <queries>\n", "    <queries>\n" + FIXTURE_QUERY, 1)
    shutil.copyfile(SOURCE, DESTINATION)
    shutil.copyfile(WIDGET_PROVIDER_SOURCE, WIDGET_PROVIDER_DESTINATION)
    shutil.copyfile(ACCESSIBILITY_SERVICE_SOURCE, ACCESSIBILITY_SERVICE_DESTINATION)
    shutil.copytree(WIDGET_RESOURCES, WIDGET_RESOURCE_DESTINATION, dirs_exist_ok=True)
    manifest = manifest.replace(
        APPLICATION_MARKER,
        WIDGET_PROVIDER + ACCESSIBILITY_SERVICE + APPLICATION_MARKER,
        1,
    )
    MANIFEST.write_text(
        manifest.replace(MARKER, INSTRUMENTATION + MARKER, 1),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
