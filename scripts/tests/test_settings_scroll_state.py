import unittest
from pathlib import Path

from scripts.ci.settings_scroll_state import scroll_state


def hierarchy(button_bounds: str | None) -> str:
    if button_bounds is None:
        return '<hierarchy><node content-desc="Settings" bounds="[323,826][389,851]" /></hierarchy>'
    return (
        "<hierarchy><node content-desc=\"Create an OWL widget\" "
        f"bounds=\"{button_bounds}\" /></hierarchy>"
    )


class SettingsScrollStateTests(unittest.TestCase):
    def test_accepts_the_baseline_top_position(self):
        self.assertEqual(scroll_state(hierarchy("[290,181][389,220]")), "ready")

    def test_scrolls_down_when_settings_header_is_above_viewport(self):
        self.assertEqual(scroll_state(hierarchy("[290,91][389,130]")), "down")

    def test_scrolls_up_when_anchor_is_below_baseline_position(self):
        self.assertEqual(scroll_state(hierarchy("[290,280][389,320]")), "up")

    def test_missing_anchor_moves_toward_the_top(self):
        self.assertEqual(scroll_state(hierarchy(None)), "down")

    def test_android_visual_smoke_checks_the_anchor_before_its_settings_capture(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        settings_block = workflow.split('if [[ "$screen" == "settings" ]]; then', 1)[1]
        settings_block = settings_block.split('if [[ "$screen" == "apps" ]]; then', 1)[0]
        self.assertIn("settings_scroll_state.py", settings_block)
        self.assertIn("for attempt in {1..8}", settings_block)
        self.assertIn('[[ "$settings_at_top" != true ]]', settings_block)
        self.assertIn('adb exec-out screencap -p > "$visual_artifacts/$screen.png"', settings_block)
        self.assertLess(
            settings_block.index("settings_scroll_state.py"),
            settings_block.index('adb exec-out screencap -p > "$visual_artifacts/$screen.png"'),
        )


if __name__ == "__main__":
    unittest.main()
