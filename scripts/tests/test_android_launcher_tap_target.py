from pathlib import Path
import tempfile
import unittest

from scripts.ci.android_launcher_tap_target import find_target


class AndroidLauncherTapTargetTests(unittest.TestCase):
    def find(self, xml: str) -> tuple[int, int]:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            return find_target(path)

    def test_finds_launcher_app_when_apps_drawer_is_selected(self):
        xml = """<hierarchy>
            <node package="app.omaapp" checked="false" content-desc="Home" />
            <node package="app.omaapp" checked="true" content-desc="Apps" />
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Launch Drive" bounds="[22,489][105,584]" />
        </hierarchy>"""
        self.assertEqual(self.find(xml), (63, 536))

    def test_rejects_hidden_apps_drawer_items_when_home_is_selected(self):
        xml = """<hierarchy>
            <node package="app.omaapp" checked="true" content-desc="Home" />
            <node package="app.omaapp" checked="false" content-desc="Apps" />
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Launch Drive" bounds="[22,489][105,584]" />
        </hierarchy>"""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Apps drawer is not selected"):
                find_target(path)


if __name__ == "__main__":
    unittest.main()
