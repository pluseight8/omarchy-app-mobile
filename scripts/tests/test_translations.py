import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from scripts.ci import check_translations


class TranslationCatalogTests(unittest.TestCase):
    def test_all_qml_strings_are_translated_without_placeholder_drift(self):
        self.assertEqual(check_translations.validate(), [])

    def test_language_heading_and_description_have_accessible_names(self):
        qml = (Path(__file__).resolve().parents[2] / "app/qml/Main.qml").read_text(
            encoding="utf-8"
        )
        start = qml.index('text: qsTr("Language")')
        end = qml.index("RowLayout {", start)
        language_controls = qml[start:end]
        self.assertEqual(language_controls.count("Accessible.name: text"), 3)

    def test_language_choice_surface_does_not_self_bind_gradient_end(self):
        qml = (Path(__file__).resolve().parents[2] / "app/qml/Main.qml").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("borderEnd: borderStart", qml)
        self.assertIn(
            "borderEnd: parent.checked ? Theme.accent : Theme.outline",
            qml,
        )

    def test_accessibility_scale_reaches_every_shared_theme_font_size(self):
        root = Path(__file__).resolve().parents[2]
        theme = (root / "app/qml/Theme.qml").read_text(encoding="utf-8")
        launcher = (root / "app/native/LauncherModel.h").read_text(encoding="utf-8")
        bridge = (root / "app/android/src/app/omaapp/LauncherBridge.java").read_text(
            encoding="utf-8"
        )
        qml = (root / "app/qml/Main.qml").read_text(encoding="utf-8")
        smoke = (root / "scripts/ci/android_translation_smoke.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("property real systemFontScale: 1", theme)
        self.assertIn("* systemFontScale", theme)
        self.assertIn("Q_PROPERTY(qreal systemFontScale READ systemFontScale", launcher)
        self.assertIn("getConfiguration().fontScale", bridge)
        self.assertIn("Math.min(4.0f, scale)", bridge)
        self.assertIn("qBound<qreal>(0.5, reportedFontScale, 4.0)", (
            root / "app/native/LauncherModel.cpp"
        ).read_text(encoding="utf-8"))
        self.assertIn("Theme.systemFontScale = launcher.systemFontScale", qml)
        self.assertIn("Android accessibility text scale: %1×", qml)
        self.assertIn("settings put system font_scale 1.4", smoke)
        self.assertIn("settings-large-font.png", smoke)

    def test_dialogs_with_wrapping_content_have_explicit_mobile_width(self):
        qml = (Path(__file__).resolve().parents[2] / "app/qml/Main.qml").read_text(
            encoding="utf-8"
        )
        for dialog_id in ("forgetDialog", "deleteFolderDialog"):
            with self.subTest(dialog=dialog_id):
                start = qml.index(f"id: {dialog_id}")
                end = qml.find("\n    Dialog {", start)
                block = qml[start : end if end >= 0 else len(qml)]
                self.assertIn("width: Math.min(window.width - 32, 420)", block)

    def test_android_smoke_checks_repeated_locale_switching_and_qml_loops(self):
        smoke = (
            Path(__file__).resolve().parents[2]
            / "scripts/ci/android_translation_smoke.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("tap_scrolling_label 'Russian' down", smoke)
        self.assertIn("tap_scrolling_label 'Английский' down", smoke)
        visibility_helper = smoke.split("ensure_visible_settings_label() {", 1)[1].split(
            "\n}", 1
        )[0]
        self.assertIn("android_accessibility_assert_visible.py", visibility_helper)
        self.assertIn("adb shell input swipe 32 720 32 300 650", visibility_helper)
        self.assertEqual(smoke.count("ensure_visible_settings_label 'Язык'"), 2)
        self.assertIn("ensure_visible_settings_label 'Language'", smoke)
        self.assertIn("adb logcat -c", smoke)
        self.assertIn("adb logcat -d -v threadtime -t 30000", smoke)
        self.assertIn("trap restore_android_font_scale EXIT", smoke)
        self.assertIn("grep -F 'Binding loop detected'", smoke)
        first_ui_capture = smoke.index("dump_ui\npython3 scripts/ci/android_accessibility_assert_checked.py")
        russian_selection = smoke.index("tap_scrolling_label 'Russian'\n")
        native_russian_check = smoke.index("nativeQtTranslations true")
        english_selection = smoke.index("tap_scrolling_label 'Английский' down")
        native_english_check = smoke.index("nativeEnglishStrings true")
        live_pid_capture = smoke.index("live_locale_process=")
        self.assertLess(first_ui_capture, russian_selection)
        self.assertLess(russian_selection, native_russian_check)
        self.assertLess(native_russian_check, english_selection)
        self.assertLess(english_selection, native_english_check)
        self.assertLess(native_english_check, live_pid_capture)
        self.assertIn("restart_app_for_persistence", smoke[native_russian_check:native_english_check])
        self.assertIn("restart_app_for_persistence", smoke[native_english_check:live_pid_capture])
        live_switch_stage = smoke.split("# Exercise rapid locale changes", 1)[1].split(
            "# Haptics", 1
        )[0]
        self.assertNotIn("am instrument", live_switch_stage)
        self.assertIn("locale_process_after_switches", live_switch_stage)
        self.assertIn(
            '"$locale_process_after_switches" != "$live_locale_process"',
            live_switch_stage,
        )

    def test_native_android_messages_survive_both_english_and_russian(self):
        root = Path(__file__).resolve().parents[2]
        strings = (root / "app/android/src/app/omaapp/NativeStrings.java").read_text(
            encoding="utf-8"
        )
        instrumentation = (
            root / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        smoke = (root / "scripts/ci/android_translation_smoke.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("isKnownFeatureMessage(message)", strings)
        self.assertIn("isKnownFileTransferError(message)", strings)
        self.assertIn("FAILED_FEATURE_REQUEST.matcher(message)", strings)
        self.assertIn('testArguments.getString("nativeEnglishStrings")', instrumentation)
        self.assertIn("Computer action completed.", instrumentation)
        self.assertIn("Enable this feature for this phone in the Omarchy plugin permissions.", instrumentation)
        self.assertIn("NativeStrings.fileTransferError(permission)", instrumentation)
        self.assertIn("nativeEnglishStrings true", smoke)
        self.assertIn("stage=native_english_strings_passed", smoke)

    def test_native_catalog_checks_execute_jni_on_the_application_main_thread(self):
        instrumentation = (
            Path(__file__).resolve().parents[2]
            / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn("private String nativeStringsOnApplicationThread(", instrumentation)
        self.assertIn("runOnMainSync(() ->", instrumentation)
        self.assertRegex(
            instrumentation,
            r"nativeStringsOnApplicationThread\(\s*\(\) -> NativeStrings\.get\(",
        )
        self.assertRegex(
            instrumentation,
            r"nativeStringsOnApplicationThread\(\s*\(\) -> NativeStrings\.featureMessage\(",
        )
        self.assertRegex(
            instrumentation,
            r"nativeStringsOnApplicationThread\(\s*\(\) -> NativeStrings\.fileTransferError\(",
        )

    def test_switching_language_refreshes_owl_metadata_and_transient_messages(self):
        qml = (Path(__file__).resolve().parents[2] / "app/qml/Main.qml").read_text(
            encoding="utf-8"
        )
        connection = qml.split("function onLanguageChanged() {", 1)[1].split("\n        }", 1)[0]
        self.assertIn("owl.refreshLocalizedText()", connection)
        self.assertIn('window.widgetMessage = ""', connection)
        self.assertIn('window.layoutMessage = ""', connection)

        widget_store = qml.split('model: owl.widgetStore', 1)[1].split(
            'HyprlandSurface {', 1
        )[0]
        self.assertIn("delegate: ColumnLayout {", widget_store)
        self.assertEqual(widget_store.count("Accessible.name: text"), 2)
        self.assertIn("wrapMode: Text.WordWrap", widget_store)
        self.assertNotIn("elide: Text.ElideRight", widget_store)

        backup = qml.split('text: qsTr("Layout backup")', 1)[1].split(
            "visible: window.layoutMessage", 1
        )[0]
        self.assertIn("ColumnLayout {", backup)
        self.assertNotIn("RowLayout {", backup)

        smoke = (Path(__file__).resolve().parents[2] / "scripts/ci/android_translation_smoke.sh").read_text(
            encoding="utf-8"
        )
        for label in (
            "Часы Omarchy",
            "Локальные часы и дата на основе текстовых и компоновочных узлов OWL.",
            "Демонстрация Canvas",
            "Небольшой живой холст с фигурами OWL, выражениями и цветами Omarchy.",
            "Скопировать резервную копию раскладки в буфер обмена",
            "Восстановить раскладку главного экрана из JSON-копии",
        ):
            with self.subTest(label=label):
                self.assertIn(label, smoke)

    def test_russian_bottom_navigation_uses_compact_visual_labels_and_full_accessible_names(self):
        qml = (Path(__file__).resolve().parents[2] / "app/qml/Main.qml").read_text(
            encoding="utf-8"
        )
        navigation = qml.rsplit('model: [', 1)[1].split("            }", 1)[0]
        self.assertIn('appTranslations.language === "ru"', navigation)
        self.assertIn("modelData.compactLabel", navigation)
        self.assertIn("Accessible.name: modelData.page === 4", navigation)
        self.assertIn('qsTr("Settings tab") : modelData.label', navigation)
        for label in (
            "Apps compact", "Devices compact", "Home compact",
            "Settings compact", "Style compact",
        ):
            with self.subTest(label=label):
                self.assertIn(f'compactLabel: qsTr("{label}")', navigation)

    def test_rejects_unfinished_catalog_entries(self):
        with TemporaryDirectory() as directory:
            catalog = Path(directory) / "unfinished.ts"
            catalog.write_text(
                '<TS version="2.1" language="ru_RU"><context><name>Main</name>'
                '<message><source>OmaApp</source><translation type="unfinished">OmaApp</translation>'
                '</message></context></TS>',
                encoding="utf-8",
            )
            errors = check_translations.validate(catalog)
        self.assertEqual(errors, ["Unfinished Main translation: OmaApp"])

    def test_reports_missing_source_strings(self):
        with TemporaryDirectory() as directory:
            catalog = Path(directory) / "missing.ts"
            catalog.write_text(
                '<TS version="2.1" language="ru_RU"><context><name>Main</name>'
                '<message><source>Settings</source><translation>Настройки</translation>'
                '</message></context></TS>',
                encoding="utf-8",
            )
            errors = check_translations.validate(catalog)
        self.assertIn("Missing Main translation: ANDROID", errors)


if __name__ == "__main__":
    unittest.main()
