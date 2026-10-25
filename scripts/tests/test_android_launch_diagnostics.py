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
                "1",
                "123",
                "OmaApp startup: loading QML module\n"
                "Creating QRhi with backend OpenGL\n",
            ),
            "qml_component_creation_stalled_after_qt_rhi_start",
        )
        self.assertEqual(
            classify(
                "1",
                "123",
                "OmaApp startup: loading QML module\n"
                "OmaApp startup: QML module load returned 1 root objects\n"
                "OmaApp startup: entering event loop\n"
                "Created QRhi 0x123 for window 0x456\n",
            ),
            "qt_rhi_started_graphics_diagnostic_missing",
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
            "home-readiness-thread-dump.txt",
            "home-readiness-thread-list.txt",
            "home-readiness-classification.txt",
        ):
            with self.subTest(artifact=artifact):
                self.assertIn(artifact, workflow)


if __name__ == "__main__":
    unittest.main()
