from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidAppWidgetPocTests(unittest.TestCase):
    def test_real_widget_host_proof_is_debug_only(self):
        provider = (ROOT / "scripts/ci/android/CiAppWidgetProvider.java").read_text(
            encoding="utf-8"
        )
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        prepare = (ROOT / "scripts/ci/prepare_android_pairing_smoke.py").read_text(
            encoding="utf-8"
        )
        manifest = (ROOT / "app/android/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        for api in (
            "AppWidgetHost",
            "allocateAppWidgetId",
            "bindAppWidgetIdIfAllowed",
            "ACTION_APPWIDGET_BIND",
            "AppWidgetHostView",
            "createView(",
            "sendPointerSync",
            "deleteAppWidgetId",
        ):
            self.assertIn(api, instrumentation)
        self.assertIn("RemoteViews", provider)
        self.assertIn("Widget tap received by Android provider", provider)
        self.assertIn("scripts/ci/android/CiAppWidgetProvider.java", prepare)
        self.assertIn("The CI AppWidget provider must not be part of the product manifest.", prepare)
        self.assertNotIn("CiAppWidgetProvider", manifest)
        self.assertIn("appWidgetHost true", workflow)
        self.assertIn("new Instrumentation.ActivityMonitor(", instrumentation)
        self.assertIn("waitForActivityWithTimeout(15_000L)", instrumentation)
        self.assertNotIn("startActivitySync(", instrumentation)
        self.assertIn("private static boolean awaitLatchlessView(", instrumentation)
        self.assertIn("private static boolean clickWidgetBindConfirmation(", instrumentation)
        self.assertIn(
            "private static void runOwlRuntimeSmoke() throws Exception", instrumentation
        )
        self.assertNotIn("tokens truncated", instrumentation)
        self.assertIn("node.getContentDescription()", instrumentation)
        self.assertIn("nodeText != null && label.contentEquals(nodeText)", instrumentation)
        self.assertIn(
            "nodeDescription != null && label.contentEquals(nodeDescription)", instrumentation
        )
        self.assertIn("label.contentEquals(nodeDescription)", instrumentation)
        self.assertIn(
            'awaitAccessibleControlClick(automation, "Home", 8_000L)', instrumentation
        )
        self.assertIn(
            "private static boolean awaitAccessibilityText(UiAutomation automation",
            instrumentation,
        )
        self.assertIn("accessibilityTreeContains(automation, text)", instrumentation)
        self.assertIn(
            "private static boolean accessibilityTreeContains(UiAutomation automation",
            instrumentation,
        )
        self.assertIn("nodeText.toString().contains(expected)", instrumentation)
        self.assertIn("nodeDescription.toString().contains(expected)", instrumentation)
        self.assertIn("MAX_ACCESSIBILITY_NODES = 512", instrumentation)
        self.assertIn("text.length() > 256", instrumentation)
        self.assertIn('reportAppWidgetStage("activity_starting")', instrumentation)
        self.assertIn('reportAppWidgetStage("touch_received")', instrumentation)
        confirmation = instrumentation[
            instrumentation.index("private static boolean clickWidgetBindConfirmation") :
            instrumentation.index("private static void runOwlRuntimeSmoke")
        ]
        accessible_click = instrumentation[
            instrumentation.index("private static boolean clickAccessibleControl") :
            instrumentation.index("private static boolean awaitAccessibleControlClick")
        ]
        for label in (
            '"Allow"',
            '"Add"',
            '"Add widget"',
            '"Create"',
            '"Create widget"',
            '"Разрешить"',
            '"Добавить"',
            '"Создать"',
            '"Создать виджет"',
        ):
            self.assertIn(label, confirmation)
        self.assertNotIn("Always allow", confirmation)
        self.assertIn("clickAccessibleControl(automation, label)", confirmation)
        self.assertIn(
            "nodeDescription != null && label.contentEquals(nodeDescription)",
            accessible_click,
        )
        self.assertIn("parent.isEnabled()", accessible_click)
        self.assertIn("parent.isClickable()", accessible_click)
        self.assertIn("depth < 8", accessible_click)
        self.assertIn("node.recycle()", accessible_click)
        self.assertIn("automation.getWindows()", accessible_click)
        self.assertIn("window.getRoot()", accessible_click)
        self.assertIn(
            "serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS",
            instrumentation,
        )
        self.assertIn("automation.setServiceInfo(serviceInfo)", instrumentation)
        self.assertIn("describeInteractiveWindows(automation)", instrumentation)
        self.assertIn(
            "private static String describeInteractiveWindows(UiAutomation automation)",
            instrumentation,
        )
        self.assertIn("MAX_ACCESSIBILITY_WINDOWS = 16", instrumentation)
        self.assertIn("MAX_APPWIDGET_DIAGNOSTIC_CHARS = 1_799", instrumentation)
        self.assertIn("window.getBoundsInScreen(bounds)", instrumentation)
        self.assertIn("window.recycle()", instrumentation)
        self.assertIn("node.recycle()", instrumentation)
        window_diagnostics = instrumentation[
            instrumentation.index("private static String describeInteractiveWindows") :
            instrumentation.index("private static boolean awaitAccessibleControlClick")
        ]
        self.assertNotIn("node.getText()", window_diagnostics)
        self.assertNotIn("node.getContentDescription()", window_diagnostics)
        self.assertNotIn("findAccessibilityNodeInfosByText", confirmation)
        self.assertIn(
            "timeout 90s adb shell am instrument -w -e appWidgetHost true", workflow
        )
        self.assertNotIn("CiAppWidgetProvider", release)


if __name__ == "__main__":
    unittest.main()
