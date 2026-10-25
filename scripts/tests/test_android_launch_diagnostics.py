import unittest
from pathlib import Path

from scripts.ci.classify_android_launch import classify


class AndroidLaunchDiagnosticsTests(unittest.TestCase):
    def test_classifies_emulator_boot_timeout(self):
        self.assertEqual(classify("0", "123", ""), "emulator_boot_incomplete")

    def test_classifies_missing_app_process(self):
        self.assertEqual(classify("1", "", ""), "app_process_not_running")

    def test_classifies_android_crash_or_anr(self):
        self.assertEqual(
            classify("1", "123", "FATAL EXCEPTION: main"),
            "android_process_crash_or_anr",
        )

    def test_classifies_qml_load_failure(self):
        self.assertEqual(
            classify("1", "123", "QQmlApplicationEngine failed to load component"),
            "qml_component_load_failure",
        )

    def test_distinguishes_qt_graphics_startup_from_ui_readiness(self):
        self.assertEqual(
            classify("1", "123", "OmaApp startup: launcher refresh entered"),
            "qt_graphics_initialization_not_observed",
        )
        self.assertEqual(
            classify(
                "1", "123", "OmaApp initialized Qt Quick graphics API: Vulkan"
            ),
            "qt_graphics_initialized_ui_readiness_failed",
        )

    def test_ci_timeout_collects_android_and_qt_stage_evidence(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        for artifact in (
            "home-readiness-activities.txt",
            "home-readiness-windows.txt",
            "home-readiness-all-logcat.txt",
            "home-readiness-classification.txt",
        ):
            with self.subTest(artifact=artifact):
                self.assertIn(artifact, workflow)


if __name__ == "__main__":
    unittest.main()
