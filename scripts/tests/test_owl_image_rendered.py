from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))
from check_owl_image_rendered import count_embedded_image_clusters  # noqa: E402


class OwlImageRenderedTests(unittest.TestCase):
    def test_counts_two_separate_image_render_paths(self) -> None:
        rows = [bytearray((20, 20, 20, 255) * 24) for _ in range(24)]
        badge = (165, 85, 85, 255)
        for y in (5, 6):
            for x in (5, 6, 7):
                offset = x * 4
                rows[y][offset:offset + 4] = bytes(badge)
                rows[y + 12][(x + 12) * 4:(x + 12) * 4 + 4] = bytes(badge)

        regions, count = count_embedded_image_clusters(24, 24, 4, [bytes(row) for row in rows])

        self.assertEqual(regions, 2)
        self.assertGreaterEqual(count, 4)

    def test_rejects_matching_wallpaper_pixels_that_are_far_apart(self) -> None:
        rows = [bytearray((20, 20, 20, 255) * 24) for _ in range(24)]
        for x, y in ((1, 1), (22, 1), (1, 22), (22, 22)):
            offset = x * 4
            rows[y][offset:offset + 4] = bytes((165, 85, 85, 255))

        regions, count = count_embedded_image_clusters(24, 24, 4, [bytes(row) for row in rows])

        self.assertEqual(regions, 0)
        self.assertEqual(count, 1)

    def test_ignores_transparent_pixels(self) -> None:
        rows = [bytearray((20, 20, 20, 255) * 24) for _ in range(24)]
        for y in (10, 11):
            for x in (10, 11):
                offset = x * 4
                rows[y][offset:offset + 4] = bytes((165, 85, 85, 0))

        regions, count = count_embedded_image_clusters(24, 24, 4, [bytes(row) for row in rows])

        self.assertEqual(regions, 0)
        self.assertEqual(count, 0)


if __name__ == "__main__":
    unittest.main()