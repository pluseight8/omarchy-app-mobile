from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class HapticFeedbackTests(unittest.TestCase):
    def test_android_feedback_uses_opt_in_and_the_system_respecting_view_api(self):
        implementation = (ROOT / "app/native/LauncherModel.cpp").read_text(encoding="utf-8")
        method = implementation.split("void LauncherModel::performHapticFeedback()", 1)[1]
        method = method.split("\nQString LauncherModel::exportLayout()", 1)[0]
        self.assertLess(
            method.index("if (!m_hapticFeedbackEnabled)"),
            method.index("#ifdef Q_OS_ANDROID"),
        )
        self.assertIn("QAndroidApplication::isActivityContext()", method)
        self.assertIn("QAndroidApplication::runOnAndroidMainThread", method)
        self.assertIn('"android/view/HapticFeedbackConstants", "KEYBOARD_TAP"', method)
        self.assertIn('"performHapticFeedback", "(I)Z"', method)
        self.assertNotIn("Vibrator", method)

        manifest = (ROOT / "app/android/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("android.permission.VIBRATE", manifest)

    def test_haptics_are_opt_in_persistent_and_noted_in_layout_backups(self):
        model = (ROOT / "app/native/LauncherModel.cpp").read_text(encoding="utf-8")
        self.assertIn('settings.value(QStringLiteral("launcher/hapticFeedbackEnabled"),', model)
        self.assertIn('{QStringLiteral("hapticFeedbackEnabled"), m_hapticFeedbackEnabled}', model)
        self.assertIn('settings.setValue(QStringLiteral("launcher/hapticFeedbackEnabled"),', model)
        self.assertRegex(model, re.compile(r"hapticFeedbackValue\.isUndefined\(\)\s*\? false"))

        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        self.assertIn('text: qsTr("Enable haptic feedback")', qml)
        self.assertIn("checked: launcher.hapticFeedbackEnabled", qml)
        self.assertIn("launcher.setHapticFeedbackEnabled(checked)", qml)
        self.assertIn("launcher.performHapticFeedback()", qml)

    def test_android_translation_smoke_dispatches_and_restores_feedback_preference(self):
        smoke = (ROOT / "scripts/ci/android_translation_smoke.sh").read_text(encoding="utf-8")
        self.assertIn("tap_scrolling_label 'Enable haptic feedback' up", smoke)
        self.assertIn("find_scrolling_label 'Enable haptic feedback' down", smoke)
        self.assertIn("android_accessibility_assert_checked.py", smoke)
        self.assertIn('settings-haptics-enabled.png', smoke)
        self.assertIn("OmaApp: Android haptic feedback request dispatched", smoke)
        self.assertIn("adb shell am force-stop app.omaapp", smoke)

        scrolling_helper = smoke.split("find_scrolling_label() {", 1)[1].split("\n}", 1)[0]
        self.assertIn("dump_ui > /dev/null", scrolling_helper)
        self.assertIn('printf \'%s\\n\' "$target"', scrolling_helper)

        haptics = smoke.split("# Haptics are explicitly opt-in", 1)[1]
        after_restart = haptics.split("grep -F 'OmaApp: Android haptic feedback request dispatched'", 1)[1]
        after_restart = after_restart.split("# Returning from Android Settings", 1)[0]
        self.assertIn("find_scrolling_label 'Enable haptic feedback' down", after_restart)
        self.assertNotIn("tap_scrolling_label 'Enable haptic feedback'", after_restart)


if __name__ == "__main__":
    unittest.main()
