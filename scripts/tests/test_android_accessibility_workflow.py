from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidAccessibilityWorkflowTests(unittest.TestCase):
    def test_emulator_enables_qt_accessibility_before_launching_home(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        enable = workflow.index("adb shell settings put secure accessibility_enabled 1")
        verify = workflow.index(
            'test "$(adb shell settings get secure accessibility_enabled | tr -d',
            enable,
        )
        launch = workflow.index(
            "adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME",
            verify,
        )

        self.assertLess(enable, verify)
        self.assertLess(verify, launch)


if __name__ == "__main__":
    unittest.main()
