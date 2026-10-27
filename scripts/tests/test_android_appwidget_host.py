from pathlib import Path
import xml.etree.ElementTree as ET
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidAppWidgetHostTests(unittest.TestCase):
    def test_widget_frame_cleanup_cannot_read_destroyed_model_data(self):
        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        smoke = (ROOT / "scripts/ci/android_appwidget_host_smoke.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("id: androidWidgetDelegate", qml)
        self.assertIn("property int lastValidNativeWidgetId: 0", qml)
        self.assertIn("const widgetId = androidWidgetDelegate.nativeWidgetId", qml)
        self.assertIn("if (!launcher.platformSupported || widgetId <= 0)", qml)
        self.assertIn("Component.onDestruction: {", qml)
        self.assertIn("androidWidgetDelegate.lastValidNativeWidgetId", qml)
        self.assertIn("Timer {\n                                                    interval: 0", qml)
        self.assertNotIn("Qt.callLater(function() {\n                                                    nativeWidgetSlot.syncNativeWidgetFrame()",
                         qml)
        self.assertNotIn("launcher.setAndroidWidgetFrame(modelData.id", qml)
        self.assertIn('adb logcat -c', smoke)
        self.assertIn('adb logcat -b all -v threadtime > "$artifacts/appwidget-host-logcat.txt" &', smoke)
        self.assertIn("stop_logcat_capture", smoke)
        self.assertIn('appwidget-host-logcat.txt', smoke)
        self.assertIn("TypeError|ReferenceError", smoke)

    def test_layout_backup_restore_uses_a_recoverable_cross_store_journal(self):
        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        model = (ROOT / "app/native/LauncherModel.cpp").read_text(encoding="utf-8")
        owl = (ROOT / "app/native/OwlModel.cpp").read_text(encoding="utf-8")
        inspect_start = qml.index("function inspectLayoutBackup(json)")
        inspect_end = qml.index("function applyLayoutBackup", inspect_start)
        inspect = qml[inspect_start:inspect_end]
        self.assertLess(
            inspect.index("json.length > 2 * 1024 * 1024"), inspect.index("JSON.parse(json)")
        )

        restore_start = qml.index("function restoreLayoutBackup(json)")
        restore_end = qml.index("function recoverLayoutBackupRestore", restore_start)
        restore = qml[restore_start:restore_end]
        self.assertLess(
            restore.index("beginLayoutRestoreJournal"),
            restore.index("applyLayoutBackup(json, true)"),
        )
        self.assertLess(
            restore.index("applyLayoutBackup(json, true)"),
            restore.index("commitLayoutRestoreJournal"),
        )
        self.assertIn("rollbackLayoutBackup(rollbackBackup)", restore)
        self.assertIn('phase === "committed"', qml)
        self.assertIn('phase !== "applying"', qml)
        self.assertIn("layoutRestoreJournalBackup()", qml)
        self.assertIn('"launcher/layoutRestoreJournalBackup"', model)
        self.assertIn('"launcher/layoutRestoreJournalPhase"', model)

        import_start = owl.index("bool OwlModel::importWidgetBackup")
        import_end = owl.index("bool OwlModel::isCustomId", import_start)
        self.assertIn("if (!saveStore(installed, custom))", owl[import_start:import_end])

    def test_backup_recovery_marshals_android_view_mutations_to_the_main_thread(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        smoke = (ROOT / "scripts/ci/android_appwidget_host_smoke.sh").read_text(
            encoding="utf-8"
        )
        clear_start = host.index("private static void clearWidgetViewsForBackupRestore()")
        clear_end = host.index("private static boolean hasWidgetBackupRestoreJournal", clear_start)
        clear_views = host[clear_start:clear_end]
        self.assertIn("Looper.myLooper() != Looper.getMainLooper()", clear_views)
        self.assertIn("activity.runOnUiThread", clear_views)
        self.assertIn("activityRef.get() == activity", clear_views)

        restore_start = host.index("private static void restoreWidgets(Context context)")
        restore_end = host.index("private static boolean hostOwnsId", restore_start)
        restore_views = host[restore_start:restore_end]
        self.assertIn("Looper.myLooper() != Looper.getMainLooper()", restore_views)
        self.assertIn("activity.runOnUiThread", restore_views)
        self.assertIn("activityRef.get() == activity", restore_views)

        self.assertIn("android_widget_host_geometry_restore_passed", instrumentation)
        self.assertIn('awaitAccessibilityText(automation, "CI_WIDGET_UPDATED", 30_000L, context)',
                      instrumentation)
        self.assertIn("CalledFromWrongThreadException", smoke)

    def test_android_widget_backup_prepa