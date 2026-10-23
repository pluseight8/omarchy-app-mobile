from pathlib import Path
import tempfile
import unittest

from scripts.ci.android_accessibility_assert_visible import is_visible


class AndroidAccessibilityVisibleTests(unittest.TestCase):
    def check(self, bounds: str, package: str = "app.omaapp") -> bool:
        xml = f'''<hierarchy><node package="{package}" text="No apps on this page"
            bounds="{bounds}" /></hierarchy>'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "window.xml"
            path.write_text(xml, encoding="utf-8")
            return is_visible(path, "No apps on this page")

    def test_accepts_a_nonempty_visible_node_bounds(self) -> None:
        self.assertTrue(self.check("[22,768][389,805]"))

    def test_rejects_zero_sized_unlaid_out_node(self) -> None:
        self.assertFalse(self.check("[0,0][0,0]"))

    def test_rejects_matching_content_from_another_package(self) -> None:
        self.assertFalse(self.check("[22,768][389,805]", package="com.android.systemui"))


if __name__ == "__main__":
    unittest.main()
