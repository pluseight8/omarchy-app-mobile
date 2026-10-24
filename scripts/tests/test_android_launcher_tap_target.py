from pathlib import Path
import tempfile
import unittest

from scripts.ci.android_launcher_tap_target import find_target

ROOT = Path(__file__).resolve().parents[2]


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

    def test_prefers_system_settings_over_apps_that_require_first_run_sign_in(self):
        xml = """<hierarchy>
            <node package="app.omaapp" checked="true" content-desc="Apps" />
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Launch Drive" bounds="[22,489][105,584]" />
            <node package="app.omaapp" clickable="true" enabled="true"
                content-desc="Launch Settings" bounds="[114,489][197,584]" />
        </hierarchy>"""
        self.assertEqual(self.find(xml), (155, 536))

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

    def test_launcher_smoke_reselects_apps_after_home_folder_restart(self):
        smoke = (ROOT / "scripts/ci/android_launcher_home_smoke.sh").read_text(
            encoding="utf-8"
        )
        reselect = smoke.index(
            'select_apps_when_needed "$smoke_artifacts/window-after-restart.xml"'
        )
        launch_target = smoke.index(
            'tap_target="$(python3 scripts/ci/android_launcher_tap_target.py '
            '"$smoke_artifacts/window-after-restart.xml")"'
        )
        self.assertLess(reselect, launch_target)

    def test_home_reselection_retries_an_empty_accessibility_snapshot(self):
        smoke = (ROOT / "scripts/ci/android_launcher_home_smoke.sh").read_text(
            encoding="utf-8"
        )
        reselect = smoke.split("select_home_when_needed() {", 1)[1].split("\n}", 1)[0]

        self.assertIn("for attempt in 1 2 3 4 5", reselect)
        self.assertIn("2>/dev/null)\"; then", reselect)
        self.assertIn(
            'scripts/ci/android_accessibility_dump.py "$device_xml" "$accessibility_xml"',
            reselect,
        )
        self.assertIn("android_accessibility_assert_checked.py \"$accessibility_xml\" 'Home'", reselect)


if __name__ == "__main__":
    unittest.main()
