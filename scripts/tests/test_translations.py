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
        self.assertEqual(language_controls.count("Accessible.name: text"), 2)

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
        self.assertEqual(widget_store.count("Accessible.name: text"), 2)

        smoke = (Path(__file__).resolve().parents[2] / "scripts/ci/android_translation_smoke.sh").read_text(
            encoding="utf-8"
        )
        for label in (
            "Часы Omarchy",
            "Локальные часы и дата на основе текстовых и компоновочных узлов OWL.",
            "Демонстрация Canvas",
            "Небольшой живой холст с фигурами OWL, выражениями и цветами Omarchy.",
        ):
            with self.subTest(label=label):
                self.assertIn(label, smoke)

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
