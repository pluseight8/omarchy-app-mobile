from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidAppWidgetHostTests(unittest.TestCase):
    def test_product_host_uses_the_system_picker_and_releases_cancelled_ids(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("AppWidgetManager.ACTION_APPWIDGET_PICK", host)
        self.assertIn("host.allocateAppWidgetId()", host)
        self.assertIn("REQUEST_PICK", host)
        self.assertIn("Activity.RESULT_OK", host)
        self.assertIn("info.configure", host)
        self.assertIn("new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)", host)
        self.assertIn("configuredId == widgetId) {", host)
        self.assertIn("resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID", host)
        self.assertIn("deletePendingWidget(activity)", host)
        self.assertIn("host.deleteAppWidgetId(widgetId)", host)
        self.assertIn("PENDING_ID_KEY", host)
        self.assertIn("MAX_WIDGETS = 8", host)

    def test_product_host_persists_and_recovers_only_matching_provider_ids(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        activity = (ROOT / "app/android/src/app/omaapp/OmaAppActivity.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('putString(WIDGETS_KEY, array.toString()).commit()', host)
        self.assertIn("AppWidgetHost(context.getApplicationContext(), HOST_ID)", host)
        self.assertIn("host.getAppWidgetIds()", host)
        self.assertIn("info.provider.equals(savedProvider)", host)
        self.assertIn("cleanupInterruptedPick(context)", host)
        self.assertIn("releaseActivityViews()", host)
        self.assertIn("host.clearViewsForActivityDetach()", host)
        self.assertIn("saveInstanceState(outState)", activity)
        self.assertIn("restoreInstanceState(savedInstanceState)", activity)
        self.assertIn("AndroidWidgetHost.onStart(this)", activity)
        self.assertIn("AndroidWidgetHost.onStop(this)", activity)
        self.assertIn("AndroidWidgetHost.onActivityResult(this", activity)
        self.assertIn("public static boolean removeWidget(Context context, int widgetId)", host)

    def test_corrupt_widget_state_does_not_get_treated_as_an_empty_host(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("Saved Android widget state is unreadable; leaving it untouched.", host)
        self.assertIn("if (widgets == null) {\n            // Do not interpret corrupt preferences", host)
        self.assertIn("widgets == null || widgets.size() >= MAX_WIDGETS", host)
        self.assertIn("savedWidgets == null || findWidget(savedWidgets, widgetId) == null", host)

    def test_qt_activity_overlay_maps_and_clips_live_qml_slot_geometry(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        model = (ROOT / "app/native/LauncherModel.cpp").read_text(encoding="utf-8")
        header = (ROOT / "app/native/LauncherModel.h").read_text(encoding="utf-8")
        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        self.assertIn("AppWidgetHostView", host)
        self.assertIn("contentRoot.addView(overlay", host)
        self.assertIn("clip.setClipBounds", host)
        self.assertIn("updateAppWidgetOptions", host)
        self.assertIn("Q_PROPERTY(QVariantList androidWidgets", header)
        self.assertIn("refreshAndroidWidgets()", model)
        self.assertIn("Java_app_omaapp_OmaAppActivity_notifyNativeAndroidWidgetsChanged", model)
        self.assertIn("setAndroidWidgetFrame", model)
        self.assertIn('qsTr("Android widgets")', qml)
        self.assertIn('Accessible.name: qsTr("Add Android widget")', qml)
        self.assertIn("homePageFlickable.mapToItem(null, 0, 0)", qml)
        self.assertIn("homePageView.mapToItem(null, 0, 0)", qml)
        self.assertIn("onContentYChanged", qml)
        self.assertIn("modelData.page === homePagePage.pageIndex", qml)

    def test_emulator_fixture_is_real_interactive_remoteviews_and_test_only(self):
        manifest = (ROOT / "app/android/AndroidManifest.xml").read_text(encoding="utf-8")
        fixture_manifest = (
            ROOT / "scripts/ci/android/widget_fixture/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        provider = (
            ROOT
            / "scripts/ci/android/widget_fixture/java/app/omaapp/widgetfixture/CiInteractiveWidgetProvider.java"
        ).read_text(encoding="utf-8")
        configure = (
            ROOT
            / "scripts/ci/android/widget_fixture/java/app/omaapp/widgetfixture/CiWidgetConfigureActivity.java"
        ).read_text(encoding="utf-8")
        provider_info = (
            ROOT / "scripts/ci/android/widget_fixture/res/xml/ci_interactive_widget_info.xml"
        ).read_text(encoding="utf-8")
        self.assertNotIn("android.appwidget.provider", manifest)
        self.assertIn('package="app.omaapp.widgetfixture"', fixture_manifest)
        self.assertIn('android:name=".CiWidgetConfigureActivity"', fixture_manifest)
        self.assertIn("android.appwidget.provider", fixture_manifest)
        self.assertIn("android.permission.BIND_APPWIDGET", fixture_manifest)
        self.assertIn("android:configure=\"app.omaapp.widgetfixture.CiWidgetConfigureActivity\"", provider_info)
        self.assertIn("Activity.RESULT_OK", configure)
        self.assertIn("AppWidgetManager.EXTRA_APPWIDGET_ID", configure)
        self.assertIn('clickAccessibleText(automation, "CI_WIDGET_CONFIGURE")', (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8"))
        self.assertIn("new RemoteViews", provider)
        self.assertIn("setOnClickPendingIntent", provider)
        self.assertIn("CI_WIDGET_UPDATED", (ROOT / "scripts/ci/android/widget_fixture/res/values/strings.xml").read_text(encoding="utf-8"))

    def test_emulator_ci_binds_clicks_restores_and_releases_a_real_provider(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        smoke = (ROOT / "scripts/ci/android_appwidget_host_smoke.sh").read_text(
            encoding="utf-8"
        )
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        for mode, stage in (
            ("setup", "android_widget_host_picker_bind_touch_passed"),
            ("restore", "android_widget_host_process_restore_passed"),
            ("remove", "android_widget_host_id_release_passed"),
        ):
            self.assertIn(f'run_stage {mode} {stage}', smoke)
            self.assertIn(stage, instrumentation)
        self.assertIn("AppWidgetManager.ACTION_APPWIDGET_PICK", host)
        self.assertIn("CI_WIDGET_ACTION", instrumentation)
        self.assertIn("CI_WIDGET_UPDATED", instrumentation)
        self.assertIn("CI_WIDGET_UPDATED_TWICE", instrumentation)
        self.assertIn("recreateOmaAppActivity", instrumentation)
        self.assertIn("currentActivityForInstrumentation", instrumentation)
        self.assertIn("adb shell am force-stop app.omaapp", smoke)
        self.assertIn("build_android_appwidget_fixture.sh", workflow)
        self.assertIn("android_appwidget_host_smoke.sh", workflow)


if __name__ == "__main__":
    unittest.main()
