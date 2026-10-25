from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidLauncherHomeSmokeTests(unittest.TestCase):
    def test_tab_selection_refreshes_accessibility_tree_before_pin_target(self):
        smoke = (ROOT / "scripts/ci/android_launcher_home_smoke.sh").read_text(
            encoding="utf-8"
        )
        helper_start = smoke.index("select_tab_when_needed() {")
        helper_end = smoke.index("\n}\n", helper_start) + 3
        helper = smoke[helper_start:helper_end]

        self.assertIn('"$accessibility_xml" "$tab_label"', helper)
        self.assertIn(
            'android_accessibility_tap_target.py "$accessibility_xml" "$tab_label"',
            helper,
        )
        self.assertIn(
            'android_accessibility_dump.py "$device_xml" "$accessibility_xml"',
            helper,
        )
        self.assertIn("for attempt in 1 2 3 4 5", helper)

        navigation_start = smoke.index(
            'apps_navigation_xml="$smoke_artifacts/window-apps-before-pin-navigation.xml"'
        )
        pin_target_start = smoke.index(
            'pin_target="$(python3 scripts/ci/android_launcher_tap_target.py'
        )
        navigation = smoke[navigation_start:pin_target_start]
        self.assertIn("select_tab_when_needed 'Apps'", navigation)
        self.assertIn('window-apps-before-pin.xml" \'Apps\'', navigation)
        self.assertLess(
            navigation.index("android_accessibility_assert_checked.py"),
            navigation.index(
                'adb exec-out screencap -p > "$smoke_artifacts/launcher-before-pin.png"'
            ),
        )
        self.assertNotIn(
            'apps_target="$(python3 scripts/ci/android_accessibility_tap_target.py',
            navigation,
        )

    def test_restart_icon_cache_assertion_waits_until_apps_are_visible(self):
        smoke = (ROOT / "scripts/ci/android_launcher_home_smoke.sh").read_text(
            encoding="utf-8"
        )
        restart_start = smoke.index('restarted_pid="$(adb shell pidof app.omaapp')
        home_restore_start = smoke.index("scroll_to_home_folder", restart_start)
        restart = smoke[restart_start:home_restore_start]

        apps_selection = restart.index("select_tab_when_needed 'Apps'")
        bounded_wait = restart.index("for attempt in {1..10}")
        icon_assertion = restart.rindex("OmaApp: cached launcher icon for")
        home_selection = restart.index("select_tab_when_needed 'Home'")
        self.assertLess(apps_selection, bounded_wait)
        self.assertLess(bounded_wait, icon_assertion)
        self.assertLess(icon_assertion, home_selection)
        self.assertIn("sleep 1", restart[bounded_wait:icon_assertion])


if __name__ == "__main__":
    unittest.main()
