from __future__ import annotations

import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))
from check_visual_regression import (  # noqa: E402
    compare_images,
    decode_png,
    verify_style_surface_rendered,
)


def png_file(path: Path, pixels: tuple[tuple[tuple[int, int, int, int], ...], ...]) -> None:
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        raw.extend(channel for pixel in row for channel in pixel)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    )


class VisualRegressionTests(unittest.TestCase):
    def test_decodes_rgba_png_rows(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "screen.png"
            png_file(path, (
                ((0, 0, 0, 255), (255, 255, 255, 255)),
                ((1, 2, 3, 255), (4, 5, 6, 255)),
            ))

            width, height, channels, rows = decode_png(path)

        self.assertEqual((width, height, channels), (2, 2, 4))
        self.assertEqual(rows[1], bytes((1, 2, 3, 255, 4, 5, 6, 255)))

    def test_identical_images_have_no_regression(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "actual.png"
            baseline = Path(directory) / "baseline.png"
            pixels = (((10, 20, 30, 255), (40, 50, 60, 255)),) * 2
            png_file(actual, pixels)
            png_file(baseline, pixels)

            changed, fraction = compare_images(actual, baseline)

        self.assertEqual(changed, 0)
        self.assertEqual(fraction, 0)

    def test_higher_density_capture_is_compared_in_logical_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "actual.png"
            baseline = Path(directory) / "baseline.png"
            dark = (10, 20, 30, 255)
            bright = (40, 50, 60, 255)
            png_file(baseline, ((dark, bright), (bright, dark)))
            png_file(actual, (
                (dark, dark, bright, bright),
                (dark, dark, bright, bright),
                (bright, bright, dark, dark),
                (bright, bright, dark, dark),
            ))

            changed, fraction = compare_images(actual, baseline)

        self.assertEqual(changed, 0)
        self.assertEqual(fraction, 0)

    def test_system_bars_are_masked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "actual.png"
            baseline = Path(directory) / "baseline.png"
            dark = (0, 0, 0, 255)
            bright = (255, 255, 255, 255)
            png_file(actual, ((bright, bright),) + ((dark, dark),) * 97 + ((bright, bright),) * 2)
            png_file(baseline, ((dark, dark),) * 100)

            changed, fraction = compare_images(actual, baseline)

        self.assertEqual(changed, 0)
        self.assertEqual(fraction, 0)

    def test_content_change_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "actual.png"
            baseline = Path(directory) / "baseline.png"
            dark = (0, 0, 0, 255)
            bright = (255, 255, 255, 255)
            rows = [(dark, dark)] * 100
            rows[50] = (bright, dark)
            png_file(actual, tuple(rows))
            png_file(baseline, ((dark, dark),) * 100)

            changed, fraction = compare_images(actual, baseline)

        self.assertEqual(changed, 1)
        self.assertGreater(fraction, 0)

    def test_home_clock_and_date_are_masked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "home.png"
            baseline = Path(directory) / "baseline.png"
            dark = (0, 0, 0, 255)
            bright = (255, 255, 255, 255)
            rows = [[dark] * 412 for _ in range(892)]
            baseline_rows = tuple(tuple(row) for row in rows)
            rows[220][100] = bright
            png_file(actual, tuple(tuple(row) for row in rows))
            png_file(baseline, baseline_rows)

            changed, fraction = compare_images(actual, baseline)

        self.assertEqual(changed, 0)
        self.assertEqual(fraction, 0)

    def test_home_changes_outside_clock_area_are_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "home.png"
            baseline = Path(directory) / "baseline.png"
            dark = (0, 0, 0, 255)
            bright = (255, 255, 255, 255)
            rows = [[dark] * 412 for _ in range(892)]
            baseline_rows = tuple(tuple(row) for row in rows)
            rows[300][100] = bright
            png_file(actual, tuple(tuple(row) for row in rows))
            png_file(baseline, baseline_rows)

            changed, fraction = compare_images(actual, baseline)

        self.assertEqual(changed, 1)
        self.assertGreater(fraction, 0)

    def test_style_probe_requires_visible_surface_fill_and_border(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style.png"
            rows = [[(16, 19, 21, 255)] * 412 for _ in range(892)]
            for y in range(200, 355):
                for x in range(22, 390):
                    rows[y][x] = (12, 14, 16, 255)
            for x in range(22, 390):
                rows[199][x] = (202, 204, 204, 255)
            png_file(image, tuple(tuple(row) for row in rows))

            verify_style_surface_rendered(image)

    def test_style_probe_rejects_transparent_shader_surface(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style.png"
            rows = [[(16, 19, 21, 255)] * 412 for _ in range(892)]
            png_file(image, tuple(tuple(row) for row in rows))

            with self.assertRaisesRegex(ValueError, "interior is missing"):
                verify_style_surface_rendered(image)

    def test_style_probe_supports_high_density_capture(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style.png"
            background = (16, 19, 21, 255)
            fill = (12, 14, 16, 255)
            border = (202, 204, 204, 255)
            rows = [[background] * 618 for _ in range(1338)]
            for y in range(300, 532):
                for x in range(33, 585):
                    rows[y][x] = fill
            for x in range(33, 585):
                rows[299][x] = border
            png_file(image, tuple(tuple(row) for row in rows))

            verify_style_surface_rendered(image)


if __name__ == "__main__":
    unittest.main()
