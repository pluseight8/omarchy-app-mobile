from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

from scripts.ci import prepare_android_pairing_smoke


ROOT = Path(__file__).resolve().parents[2]


class AndroidAccessibilityWorkflowTests(unittest.TestCase):
    def test_android_home_readiness_failure_saves_screen_and_pid_logcat(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

        self.assertIn('"${output_xml%.xml}-failed.png"', workflow)
        self.assertIn('"$smoke_artifacts/home-readiness-logcat.txt"', workflow)
        self.assertIn('timeout 10s adb logcat -d -v threadtime --pid="$readiness_pid"', workflow)

    def test_emulator_enables_qt_accessibility_before_launching_home(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        service_discovery = workflow.index(
            "adb shell cmd package query-services --components --user 0 -a android.accessibilityservice.AccessibilityService"
        )
        enable_restricted_settings = workflow.index(
            "adb shell cmd appops set --user 0 app.omaapp ACCESS_RESTRICTED_SETTINGS allow"
        )
        enable_loop = workflow.rfind("for attempt in {1..20}; do", 0, enable_restricted_settings)
        enable_service = workflow.index(
            'adb shell settings put secure enabled_accessibility_services "$accessibility_service"'
        )
        verify_service = workflow.index(
            'enabled_accessibility_services="$(adb shell settings get secure enabled_accessibility_services',
            enable_service,
        )
        enable = workflow.index("adb shell settings put secure accessibility_enabled 1")
        verify = workflow.index(
            'accessibility_enabled="$(adb shell settings get secure accessibility_enabled',
            enable,
        )
        membership_check = workflow.index(
            '[[ ":${enabled_accessibility_services}:" == *":${accessibility_service}:"*',
            verify_service,
        )
        launch = workflow.index(
            "adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME",
            verify,
        )
        enable_loop_end = workflow.index("done", enable_restricted_settings)
        enable_loop_text = workflow[enable_loop:enable_loop_end]

        self.assertLess(service_discovery, enable_service)
        self.assertLess(service_discovery, enable_restricted_settings)
        self.assertLess(enable_restricted_settings, enable_service)
        self.assertLess(enable_service, enable)
        self.assertLess(enable, verify_service)
        self.assertLess(verify_service, membership_check)
        self.assertLess(membership_check, launch)
        self.assertLess(verify, membership_check)
        self.assertLess(verify, launch)
        self.assertIn("for attempt in {1..20}; do", enable_loop_text)
        self.assertIn("ACCESS_RESTRICTED_SETTINGS allow", enable_loop_text)
        self.assertIn("adb shell settings put secure enabled_accessibility_services", enable_loop_text)
        self.assertIn("adb shell settings put secure accessibility_enabled 1", enable_loop_text)
        self.assertIn("adb shell settings get secure enabled_accessibility_services", enable_loop_text)
        self.assertIn("Package restriction initialization can clear the secure setting", enable_loop_text)
        self.assertIn("grep -Fq 'CiAccessibilityService'", workflow[service_discovery:enable_service])
        self.assertIn("accessibility_service='app.omaapp/.CiAccessibilityService'", workflow)
        self.assertIn(
            "adb shell cmd appops get --user 0 app.omaapp ACCESS_RESTRICTED_SETTINGS",
            workflow[enable_loop_end:launch],
        )
        self.assertIn("injected into this throwaway emulator APK", workflow)
        self.assertIn("adb shell dumpsys accessibility", workflow[membership_check:launch])
        self.assertNotIn(
            'test "$(adb shell settings get secure enabled_accessibility_services',
            workflow,
        )

    def test_accessibility_service_is_injected_only_into_debug_smoke_manifest(self):
        product_manifest = (ROOT / "app/android/AndroidManifest.xml").read_text(encoding="utf-8")
        prepare = (ROOT / "scripts/ci/prepare_android_pairing_smoke.py").read_text(
            encoding="utf-8"
        )
        service = (ROOT / "scripts/ci/android/CiAccessibilityService.java").read_text(
            encoding="utf-8"
        )
        service_xml = (
            ROOT / "scripts/ci/android/res/xml/omaapp_ci_accessibility_service.xml"
        ).read_text(encoding="utf-8")

        self.assertNotIn("CiAccessibilityService", product_manifest)
        self.assertIn("CiAccessibilityService", prepare)
        self.assertIn("ACCESSIBILITY_SERVICE_DESTINATION", prepare)
        self.assertIn("canRetrieveWindowContent=\"true\"", service_xml)
        self.assertIn("extends AccessibilityService", service)

    def test_debug_manifest_generator_adds_accessibility_service_and_metadata(self):
        source_manifest = ROOT / "app/android/AndroidManifest.xml"
        namespace = {"android": "http://schemas.android.com/apk/res/android"}
        with tempfile.TemporaryDirectory() as temporary:
            temporary_root = Path(temporary)
            manifest = temporary_root / "AndroidManifest.xml"
            shutil.copyfile(source_manifest, manifest)
            app_source = temporary_root / "source/app.omaapp"
            app_source.mkdir(parents=True)

            with patch.multiple(
                prepare_android_pairing_smoke,
                MANIFEST=manifest,
                SOURCE=ROOT / "scripts/ci/android/PairingTestInstrumentation.java",
                DESTINATION=app_source / "PairingTestInstrumentation.java",
                WIDGET_PROVIDER_SOURCE=ROOT / "scripts/ci/android/CiAppWidgetProvider.java",
                WIDGET_PROVIDER_DESTINATION=app_source / "CiAppWidgetProvider.java",
                ACCESSIBILITY_SERVICE_SOURCE=ROOT / "scripts/ci/android/CiAccessibilityService.java",
                ACCESSIBILITY_SERVICE_DESTINATION=app_source / "CiAccessibilityService.java",
                WIDGET_RESOURCES=ROOT / "scripts/ci/android/res",
                WIDGET_RESOURCE_DESTINATION=temporary_root / "res",
            ):
                prepare_android_pairing_smoke.main()

            manifest_root = ET.parse(manifest).getroot()
            service = manifest_root.find(
                ".//service[@android:name='app.omaapp.CiAccessibilityService']",
                namespace,
            )
            self.assertIsNotNone(service)
            self.assertEqual(
                service.attrib[f"{{{namespace['android']}}}permission"],
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
            )
            metadata = service.find("meta-data", namespace)
            self.assertEqual(
                metadata.attrib[f"{{{namespace['android']}}}resource"],
                "@xml/omaapp_ci_accessibility_service",
            )
            self.assertTrue((app_source / "CiAccessibilityService.java").is_file())
            self.assertTrue(
                (temporary_root / "res/xml/omaapp_ci_accessibility_service.xml").is_file()
            )


if __name__ == "__main__":
    unittest.main()
