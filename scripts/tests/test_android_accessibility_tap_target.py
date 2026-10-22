from pathlib import Path
import tempfile
import unittest

from scripts.ci.android_accessibility_tap_target import find_home_folder_target, find_target
from scripts.ci.android_accessibility_assert_checked import is_checked


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

    def test_finds_home_folder_and_ignores_hidden_apps_drawer_copy(self):
        xml = """<hierarchy>
            <node package="app.omaapp" checked="true" content-desc="Home" />
            <node package="app.omaapp" content-desc="Open newfolder folder, 1 apps"
                clickable="true" enabled="true"
                resource-id="QQuickListView.QQuickItem_QML_27.Button_QMLTYPE_4"
                bounds="[22,600][105,668]" />
            <node package="app.omaapp" content-desc="Open newfolder folder, 1 apps"
                clickable="true" enabled="true"
                resource-id="QQuickColumnLayout.Button_QMLTYPE_4"
                bounds="[0,0][0,0]" />
        </hierarchy>"""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            self.assertEqual(
                find_home_folder_target(path, "Open newfolder folder, 1 apps"),
                (63, 634),
            )

    def test_rejects_apps_drawer_folder_while_home_is_selected(self):
        xml = """<hierarchy>
            <node package="app.omaapp" checked="true" content-desc="Home" />
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Open newfolder folder, 1 apps"
                resource-id="QQuickColumnLayout.Button_QMLTYPE_4"
                bounds="[22,338][105,405]" />
        </hierarchy>"""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "no visible Home folder"):
                find_home_folder_target(path, "Open newfolder folder, 1 apps")

    def test_prefers_exact_control_name_over_matching_part_of_long_name(self):
        xml = """<hierarchy>
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Swipe between Home, Apps, Devices, Style, and Settings"
                bounds="[40,733][371,772]" />
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Apps" bounds="[94,822][151,851]" />
        </hierarchy>"""
        self.assertEqual(self.find(xml, "Apps"), (122, 836))

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

    def test_finds_a_checked_launcher_setting(self):
        xml = """<hierarchy>
            <node package="app.omaapp" checked="false" content-desc="Use 4 app columns" />
            <node package="app.omaapp" checked="true" content-desc="Use 5 app columns" />
            <node package="com.android.systemui" checked="true" content-desc="Use 5 app columns" />
        </hierarchy>"""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            self.assertTrue(is_checked(path, "Use 5 app columns"))
            self.assertFalse(is_checked(path, "Use 3 app columns"))


if __name__ == "__main__":
    unittest.main()
