from pathlib import Path
import xml.etree.ElementTree as ET
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidAppWidgetHostTests(unittest.TestCase):
    def test_widget_provider_visibility_is_scoped_to_widget_update_receivers(self):
        manifest_path = ROOT / "app/android/AndroidManifest.xml"
        manifest = ET.parse(manifest_path).getroot()
        android_name = "{http://schemas.android.com/apk/res/android}name"
        queries = manifest.find("queries")
        self.assertIsNotNone(queries)

        queried_actions = {
            action.get(android_name)
            for action in queries.findall("intent/action")
        }
        self.assertIn("android.appwidget.action.APPWIDGET_UPDATE", queried_actions)
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", {
            element.get(android_name) for element in manifest.findall("uses-permission")
        })

    def test_instrumentation_falls_back_to_a_touch_for_qt_accessibility_controls(self):
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn("candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)", instrumentation)
        self.assertIn("injectAccessibleTap(automation, candidate)", instrumentation)
        self.assertIn("node.getBoundsInScreen(bounds)", instrumentation)
        self.assertIn("automation.injectInputEvent(down, true)", instrumentation)
        self.assertIn("automation.injectInputEvent(up, true)", instrumentation)

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
        self.assertIn('putString(WIDGETS_KEY, root.toString()).commit()', host)
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
        self.assertIn('(recordId.isEmpty() && widgets.size() >= MAX_WIDGETS)', host)
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
        self.assertIn('Accessible.name: qsTr("Undo Android widget layout change")', qml)
        self.assertIn('id: removeAndroidWidgetDialog', qml)
        self.assertIn('title: qsTr("Remove this Android widget?")', qml)
        self.assertIn("homePageFlickable.mapToItem(null, 0, 0)", qml)
        self.assertIn("homePageView.mapToItem(null, 0, 0)", qml)
        self.assertIn("onContentYChanged", qml)
        self.assertIn("modelData.page === homePagePage.pageIndex", qml)

    def test_android_widget_grid_uses_page_local_width_and_offers_undo(self):
        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        native = (ROOT / "app/native/main.cpp").read_text(encoding="utf-8")
        start = qml.index("function placeAndroidWidgetTo(")
        end = qml.index("function moveAndroidWidgetTo(", start)
        place_function = qml[start:end]
        self.assertIn("availableWidthDp", place_function)
        self.assertIn("Number(availableWidthDp)", place_function)
        self.assertNotIn("androidWidgetCanvas.width", place_function)
        self.assertIn("DragHandler {", qml)
        self.assertIn("androidWidgetCanvas.width)", qml)
        self.assertIn("function undoAndroidWidgetLayout()", qml)
        self.assertIn('onTriggered: window.androidWidgetLayoutUndo = null', qml)
        self.assertIn("function updateAndroidWidgetDragPage(scenePosition)", qml)
        self.assertIn("androidWidgetPageDragTimer", qml)
        self.assertIn("centroid.scenePosition", qml)
        self.assertIn('QT_TRANSLATE_NOOP("Main", "Resize Android widget")', native)
        self.assertIn('QT_TRANSLATE_NOOP("Main", "Widget size was not changed.")', native)

    def test_accessible_widget_move_waits_for_the_refreshed_grid_position(self):
        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn(
            'Accessible.description: qsTr("Current Android widget position: %1, %2 dp")',
            qml,
        )
        self.assertIn(
            'awaitAccessibilityText(automation, "Current Android widget position: 0, 72 dp"',
            instrumentation,
        )
        self.assertIn('"; visible accessibility tree: "', instrumentation)

    def test_schema_migration_instrumentation_reports_from_instance_context(self):
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn("private void verifyWidgetSchemaTwoMigration(", instrumentation)
        self.assertNotIn("private static void verifyWidgetSchemaTwoMigration(", instrumentation)
        self.assertIn('reportStage("android_widget_host_schema_migration_passed")', instrumentation)

    def test_integration_smoke_checks_old_widget_schema_and_survives_slow_qml_startup(self):
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        smoke = (
            ROOT / "scripts/ci/android_translation_smoke.sh"
        ).read_text(encoding="utf-8")
        widget_smoke = (
            ROOT / "scripts/ci/android_appwidget_host_smoke.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("verifyWidgetSchemaTwoMigration(context, originalWidget)", instrumentation)
        self.assertIn('reportStage("android_widget_host_schema_migration_passed")', instrumentation)
        self.assertIn('reportStage("android_widget_host_qml_controls_passed")', instrumentation)
        self.assertIn("stored.optInt(\"schemaVersion\", -1) != 3", instrumentation)
        self.assertIn("for (int attempt = 0; attempt < 40", instrumentation)
        self.assertIn("restart_app_for_persistence\nensure_settings_tab 'Настройки'", smoke)
        self.assertIn("for attempt in {1..60}", smoke)
        self.assertIn("stage=android_widget_host_schema_migration_passed", widget_smoke)

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
        setup_start = instrumentation.index("private void runAndroidWidgetHostSetup")
        setup_end = instrumentation.index("private void runAndroidWidgetHostRestore", setup_start)
        widget_setup = instrumentation[setup_start:setup_end]
        self.assertIn('reportStage("android_widget_host_setup_started")', widget_setup)
        self.assertIn("context.startActivity(launch)", widget_setup)
        self.assertNotIn("startActivitySync", widget_setup)
        self.assertIn('reportStage("android_widget_host_picker_opened")', widget_setup)
        # InstalledProviders is package-visibility filtered for the host app;
        # validate the provider through the system picker and actual binding.
        self.assertNotIn("getInstalledProviders()", widget_setup)
        self.assertIn("clickAccessibleText(automation, providerLabel)", widget_setup)
        self.assertIn("&& (!configurationClicked || !widgetRecordSaved)", widget_setup)
        self.assertIn("if (!providerChoiceClicked) {\n                scrollSystemPicker", widget_setup)
        for mode, stage in (
            ("setup", "android_widget_host_picker_bind_touch_passed"),
            ("restore", "android_widget_host_process_restore_passed"),
            ("remove", "android_widget_host_id_release_passed"),
        ):
            self.assertIn(f'run_stage {mode} {stage}', smoke)
            self.assertIn(stage, instrumentation)
        self.assertIn('pid_before="$(adb shell pidof app.omaapp', smoke)
        self.assertIn('if [[ -n "$pid_before" ]]; then', smoke)
        self.assertIn('[[ "$pid_after_force_stop" != "$pid_before" ]]', smoke)
        self.assertIn('[[ -z "$pid_after_start" || "$pid_after_start" == "$pid_before" ]]', smoke)
        self.assertNotIn("OmaApp process was not running before", smoke)
        self.assertIn("AppWidgetManager.ACTION_APPWIDGET_PICK", host)
        self.assertIn("CI_WIDGET_ACTION", instrumentation)
        self.assertIn("CI_WIDGET_UPDATED", instrumentation)
        self.assertIn("CI_WIDGET_UPDATED_TWICE", instrumentation)
        self.assertIn('getApplicationInfo(expectedProvider.getPackageName(), 0)', instrumentation)
        self.assertIn('reportStage("android_widget_host_provider_visible")', instrumentation)
        self.assertIn("process death", instrumentation)
        self.assertNotIn("recreateOmaAppActivity", instrumentation)
        restore_start = instrumentation.index("private void runAndroidWidgetHostRestore")
        restore_end = instrumentation.index("private void runAndroidWidgetHostRemove", restore_start)
        widget_restore = instrumentation[restore_start:restore_end]
        self.assertIn('reportStage("android_widget_host_restore_started")', widget_restore)
        self.assertIn("AndroidWidgetHost.currentActivityForInstrumentation()", widget_restore)
        self.assertNotIn("startActivitySync", widget_restore)
        self.assertIn('clickAccessibleText(automation, "CI_WIDGET_ACTION")', widget_restore)
        self.assertIn('awaitAccessibilityText(automation, "CI_WIDGET_UPDATED_TWICE"', widget_restore)
        remove_start = instrumentation.index("private void runAndroidWidgetHostRemove")
        remove_stage = instrumentation[remove_start:]
        self.assertIn('reportStage("android_widget_host_remove_started")', remove_stage)
        self.assertIn("context.startActivity(launch)", remove_stage)
        self.assertIn("AndroidWidgetHost.currentActivityForInstrumentation()", remove_stage)
        self.assertIn('awaitAccessibilityText(automation, "Add Android widget"', remove_stage)
        self.assertIn("The Android widget removal confirmation was not accessible", remove_stage)
        self.assertIn("removalDeadline", remove_stage)
        self.assertIn("currentActivityForInstrumentation", instrumentation)
        self.assertIn("adb shell am force-stop app.omaapp", smoke)
        self.assertIn('clickAccessibleText(automation, "Remove Android widget ")', instrumentation)
        self.assertIn('awaitAccessibilityText(automation, "Remove this Android widget?"', instrumentation)
        self.assertIn('clickAccessibleText(automation, "OK")', instrumentation)
        self.assertIn("build_android_appwidget_fixture.sh", workflow)
        self.assertIn("android_appwidget_host_smoke.sh", workflow)

    def test_picker_smoke_reports_host_ownership_when_provider_metadata_exists(self):
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn('if (!"ready".equals(selectedWidget.optString("state")))', instrumentation)
        self.assertIn("widgetBindingDiagnostics(context, expectedProvider)", instrumentation)
        self.assertIn("host.getAppWidgetIds()", instrumentation)
        self.assertIn('", managerProvider="', instrumentation)
        self.assertIn('", hostOwnsId=" + owned', instrumentation)

    def test_widget_storage_is_versioned_and_migrates_legacy_arrays(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("WIDGETS_SCHEMA_VERSION = 3", host)
        self.assertIn("storedSchemaVersion < 2 || storedSchemaVersion > WIDGETS_SCHEMA_VERSION", host)
        self.assertIn('legacyArray = stored.trim().startsWith("[")', host)
        self.assertIn('recordId = id > 0 ? "legacy-" + id', host)
        self.assertIn('root.put("schemaVersion", WIDGETS_SCHEMA_VERSION)', host)
        self.assertIn('sanitized.put("widthDp", width)', host)
        self.assertIn('sanitized.put("heightDp", height)', host)
        self.assertIn('sanitized.put("page", page)', host)
        self.assertIn('sanitized.put("xDp", xDp)', host)
        self.assertIn('sanitized.put("yDp", yDp)', host)
        self.assertIn("PLACEMENT_GRID_DP = 24", host)
        self.assertIn("hasPlacementConflict", host)
        self.assertIn("nextWidgetY", host)
        self.assertIn("normalizePageOrders(result)", host)

    def test_removal_persists_before_android_releases_the_widget_id(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        remove_start = host.index("public static boolean removeWidget(Context context, int widgetId)")
        remove_end = host.index("/** Updates the native view", remove_start)
        remove_method = host[remove_start:remove_end]
        self.assertLess(remove_method.index("if (!writeWidgets(context, updated))"),
                        remove_method.index("host.deleteAppWidgetId(widgetId)"))
        self.assertIn("writeWidgets(context, original)", remove_method)

    def test_portable_widget_backup_never_restores_android_binding_ids(self):
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        export_start = host.index("public static String exportWidgetBackup")
        export_end = host.index("public static boolean canRestoreWidgetBackup", export_start)
        export_method = host[export_start:export_end]
        restore_start = host.index("private static List<JSONObject> parseWidgetBackup")
        restore_end = host.index("private static boolean isWidgetBound", restore_start)
        parser = host[restore_start:restore_end]
        self.assertIn("BACKUP_SCHEMA_VERSION = 2", host)
        self.assertNotIn('entry.put("id"', export_method)
        self.assertNotIn('entry.put("grant"', export_method)
        self.assertIn('entry.length() != (backupVersion == 1 ? 8 : 10)', parser)
        self.assertIn('entry.put("xDp"', export_method)
        self.assertIn('entry.put("yDp"', export_method)
        self.assertIn('placeholder.put("id", 0)', parser)
        self.assertIn('placeholder.put("state", "needs_rebind")', parser)
        self.assertIn('"The selected provider does not match the saved widget placeholder."', host)
        self.assertIn('"requestWidgetRebind"', (
            ROOT / "app/native/LauncherModel.cpp"
        ).read_text(encoding="utf-8"))

    def test_home_widget_controls_drive_per_page_move_resize_and_placeholder_rebind(self):
        model = (ROOT / "app/native/LauncherModel.cpp").read_text(encoding="utf-8")
        host = (ROOT / "app/android/src/app/omaapp/AndroidWidgetHost.java").read_text(
            encoding="utf-8"
        )
        header = (ROOT / "app/native/LauncherModel.h").read_text(encoding="utf-8")
        qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn("moveAndroidWidget(const QString &recordId, int page, int order)", header)
        self.assertIn("resizeAndroidWidget(const QString &recordId, int widthDp, int heightDp)", header)
        self.assertIn("placeAndroidWidget(const QString &recordId, int page, int xDp, int yDp)", header)
        self.assertIn('"moveWidgetRecord"', model)
        self.assertIn('"placeWidgetRecord"', model)
        self.assertIn(
            '"placeWidgetRecord",\n        "(Landroid/content/Context;Ljava/lang/String;IIII)Z"',
            model,
        )
        self.assertIn(
            "public static boolean placeWidgetRecord(Context context, String recordId, int page,\n"
            "                                            int xDp, int yDp, int pageCount)",
            host,
        )
        self.assertIn('"resizeWidgetRecord"', model)
        self.assertIn('launcher.addAndroidWidgetOnPage(\n                                            homePagePage.pageIndex)', qml)
        self.assertIn('modelData.page + 1, 7)', qml)
        self.assertIn('window.resizeAndroidWidgetTo(modelData.recordId', qml)
        self.assertIn("launcher.rebindAndroidWidget(", qml)
        self.assertIn('modelData.state !== "ready"', qml)
        self.assertIn("DropArea {", qml)
        self.assertIn("androidWidgetGridStep: 24", qml)
        self.assertIn('x: Number(modelData.xDp) || 0', qml)
        self.assertIn('y: Number(modelData.yDp) || 0', qml)
        self.assertIn('executeSwipe(automation, context, 0.84f, 0.38f, 0.05f)', instrumentation)
        self.assertIn('" + x + " " + Math.round(height * toRatio)', instrumentation)

    def test_android_widget_instrumentation_exercises_layout_and_safe_restore(self):
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        smoke = (ROOT / "scripts/ci/android_appwidget_host_smoke.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("android_widget_host_layout_roundtrip_passed", instrumentation)
        self.assertIn("android_widget_host_second_home_page_ready", instrumentation)
        self.assertIn("android_widget_host_second_page_placement_passed", instrumentation)
        self.assertIn("android_widget_host_cross_page_move_passed", instrumentation)
        self.assertIn('selectedWidget.optInt("page", -1) != 1', instrumentation)
        self.assertIn('"Move Android widget to previous page"', instrumentation)
        self.assertIn("android_widget_host_resize_handle_passed", instrumentation)
        self.assertIn("dragResizeHandle(context, widgetId, 24, 24)", instrumentation)
        self.assertIn("android_widget_host_geometry_restore_passed", instrumentation)
        self.assertIn("android_widget_host_backup_placeholder_passed", instrumentation)
        setup_start = instrumentation.index("private void ensureSecondHomePage")
        setup_end = instrumentation.index(
            "private static void verifyWidgetGridCollisionRules", setup_start
        )
        second_page_setup = instrumentation[setup_start:setup_end]
        self.assertIn('clickAccessibleControl(automation, "Settings")', second_page_setup)
        self.assertIn('clickAccessibleControl(automation, "Add a home page")', second_page_setup)
        self.assertIn('clickAccessibleControl(automation, "Home")', second_page_setup)
        self.assertNotIn('clickAccessibleText(automation, "Settings")', second_page_setup)
        widget_setup_start = instrumentation.index("private void runAndroidWidgetHostSetup")
        widget_setup_end = instrumentation.index("private void ensureSecondHomePage", widget_setup_start)
        widget_setup = instrumentation[widget_setup_start:widget_setup_end]
        self.assertIn('clickAccessibleControl(automation, "Add Android widget")', widget_setup)
        self.assertNotIn('clickAccessibleText(automation, "Add Android widget")', widget_setup)
        self.assertIn("exportWidgetBackup(context)", instrumentation)
        self.assertIn("restoreWidgetBackup(context, widgetBackup)", instrumentation)
        self.assertIn('"needs_rebind"', instrumentation)
        self.assertIn('clickAccessibleText(automation, "Rebind")', instrumentation)
        self.assertIn('"omaapp-appwidget-home-layout.png"', instrumentation)
        self.assertIn('"omaapp-appwidget-rebind-placeholder.png"', instrumentation)
        self.assertIn("widget-home-layout.png", smoke)
        self.assertIn("widget-rebind-placeholder.png", smoke)
        self.assertIn("android_widget_host_geometry_restore_passed", smoke)
        self.assertIn("android_widget_host_cross_page_move_passed", smoke)


if __name__ == "__main__":
    unittest.main()
