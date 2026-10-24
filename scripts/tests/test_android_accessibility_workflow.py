from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

from scripts.ci import prepare_android_pairing_smoke


ROOT = Path(__file__).resolve().parents[2]


class AndroidAccessibilityWorkflowTests(unittest.TestCase):
    def test_emulator_enables_qt_accessibility_before_launching_home(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        enable_service = workflow.index(
            'adb shell settings put secure enabled_accessibility_services "$accessibility_service"'
        )
        verify_service = workflow.index(
            'test "$(adb shell settings get secure enabled_accessibility_services | tr -d',
            enable_service,
        )
        enable = workflow.index("adb shell settings put secure accessibility_enabled 1")
        verify = workflow.index(
            'test "$(adb shell settings get secure accessibility_enabled | tr -d',
            enable,
        )
        launch = workflow.index(
            "adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME",
            verify,
        )

        self.assertLess(enable_service, enable)
        self.assertLess(enable, verify_service)
        self.assertLess(enable, verify)
        self.assertLess(verify, launch)

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
