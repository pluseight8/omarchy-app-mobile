from pathlib import Path
import tempfile
import unittest

from scripts.ci.android_accessibility_tap_target import find_target


class AndroidAccessibilityTapTargetTest(unittest.TestCase):
    def find(self, xml: str, target: str) -> tuple[int, int]:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            return find_target(path, target)

    def test_finds_accessible_name_for_clickable_omaapp_control(self):
        xml = """<hierarchy><node package="app.omaapp" clickable="true"
            enabled="true" content-desc="Create folder" bounds="[12,20][92,68]" />
        </hierarchy>"""
        self.assertEqual(self.find(xml, "Create folder"), (52, 44))

    def test_ignores_other_packages_disabled_and_non_clickable_nodes(self):
        xml = """<hierarchy>
            <node package="com.android.systemui" clickable="true" enabled="true"
                content-desc="Create folder" bounds="[0,0][40,40]" />
            <node package="app.omaapp" clickable="false" enabled="true"
                content-desc="Create folder" bounds="[0,0][40,40]" />
            <node package="app.omaapp" clickable="true" enabled="false"
                content-desc="Create folder" bounds="[0,0][40,40]" />
        </hierarchy>"""
        with self.assertRaisesRegex(ValueError, "no clickable OmaApp control"):
            self.find(xml, "Create folder")

    def test_finds_visible_button_text(self):
        xml = """<hierarchy><node package="app.omaapp" clickable="true"
            enabled="true" text="OK" bounds="[0,0][64,48]" /></hierarchy>"""
        self.assertEqual(self.find(xml, "OK"), (32, 24))

    def test_finds_focusable_field_with_composed_accessible_name(self):
        xml = """<hierarchy><node package="app.omaapp" clickable="false" focusable="true"
            enabled="true" content-desc="New folder name, Folder name"
            bounds="[28,416][383,455]" /></hierarchy>"""
        self.assertEqual(self.find(xml, "New folder name"), (205, 435))

    def test_preserves_commas_in_full_accessible_name(self):
        xml = """<hierarchy><node package="app.omaapp" clickable="true"
            enabled="true" content-desc="Open CI_Folder folder, 1 apps"
            bounds="[22,440][105,507]" /></hierarchy>"""
        self.assertEqual(
            self.find(xml, "Open CI_Folder folder, 1 apps"),
            (63, 473),
        )


if __name__ == "__main__":
    unittest.main()
