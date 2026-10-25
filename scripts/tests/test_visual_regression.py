from __future__ import annotations

import struct
import sys
import tempfile
import unittest
import zlib
import math
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))
from check_visual_regression import (  # noqa: E402
    compare_images,
    compare_image_variants,
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


def style_probe_image(path: Path, *, scale: float = 1.0, angle: float | None = None,
                      square_corners: bool = False, missing_top_border: bool = False,
                      bounds: tuple[float, float, float, float, float] =
                      (22.0, 199.0, 368.0, 156.0, 24.0)) -> None:
    width = round(412 * scale)
    height = round(892 * scale)
    background = (16, 19, 21, 255)
    fill = (12, 14, 16, 255)
    rows = [[background] * width for _ in range(height)]
    left, top, card_width, card_height, radius = bounds
    right, bottom = left + card_width, top + card_height
    sine = math.sin(math.radians(angle or 45.0))

    for y in range(height):
        logical_y = (y + 0.5) / scale
        if logical_y < top or logical_y >= bottom:
            continue
        for x in range(width):
            logical_x = (x + 0.5) / scale
            if logical_x < left or logical_x >= right:
                continue
            if not square_corners:
                nearest_x = min(max(logical_x, left + radius), right - radius)
                nearest_y = min(max(logical_y, top + radius), bottom - radius)
                if ((logical_x - nearest_x) ** 2 + (logical_y - nearest_y) ** 2
                        > radius ** 2):
                    continue
            edge_distance = min(logical_x - left, right - logical_x,
                                logical_y - top, bottom - logical_y)
            if edge_distance < 2.0:
                if missing_top_border and logical_y < top + 2.0:
                    rows[y][x] = background
                    continue
                if angle is None:
                    color = (202, 204, 204, 255)
                else:
                    u = (logical_x - left) / card_width
                    v = (logical_y - top) / card_height
                    progress = v * sine + u * (1.0 - sine)
                    tone = round(40 + 200 * progress)
                    color = (tone, tone, tone, 255)
                rows[y][x] = color
            else:
                rows[y][x] = fill
    png_file(path, tuple(tuple(row) for row in rows))


class VisualRegressionTests(unittest.TestCase):
    def test_android_ci_uses_renderer_specific_compact_home_baselines(self) -> None:
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")

        self.assertIn('if [[ -f "$baseline_dir/home-compact-viewport.png" ]]', workflow)
        self.assertIn(
            'home_baseline_args+=(--home-alternate-baseline "$baseline_dir/home-compact-viewport.png")',
            workflow,
        )
        self.assertNotIn(
            "--home-alternate-baseline tests/visual_baselines/android/home-compact-viewport.png",
            workflow,
        )
        self.assertTrue(
            (root / "tests/visual_baselines/android/opengl/home-compact-viewport.png").is_file()
        )
        self.assertFalse(
            (root / "tests/visual_baselines/android/home-compact-viewport.png").exists(),
            "a shared compact screenshot can hide a renderer-specific Home regression",
        )

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

    def test_selects_closest_reviewed_home_layout_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "actual.png"
            standard = Path(directory) / "standard.png"
            inset = Path(directory) / "inset.png"
            dark = (10, 20, 30, 255)
            bright = (220, 230, 240, 255)
            standard_pixels = [[dark] * 412 for _ in range(892)]
            inset_pixels = [[dark] * 412 for _ in range(892)]
            actual_pixels = [[dark] * 412 for _ in range(892)]
            standard_pixels[500][100] = bright
            inset_pixels[600][100] = bright
            actual_pixels[600][100] = bright
            png_file(actual, tuple(tuple(row) for row in actual_pixels))
            png_file(standard, tuple(tuple(row) for row in standard_pixels))
            png_file(inset, tuple(tuple(row) for row in inset_pixels))

            baseline, changed, fraction = compare_image_variants(actual, [standard, inset])

        self.assertEqual(baseline.name, "inset.png")
        self.assertEqual(changed, 0)
        self.assertEqual(fraction, 0)

    def test_variant_comparison_requires_a_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            actual = Path(directory) / "actual.png"
            png_file(actual, (((0, 0, 0, 255),),))
            with self.assertRaisesRegex(ValueError, "at least one visual baseline"):
                compare_image_variants(actual, [])

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
            style_probe_image(image)

            verify_style_surface_rendered(image)

    def test_style_probe_finds_compact_and_synced_theme_layouts(self) -> None:
        layouts = (
            (22.0, 199.0, 368.0, 156.0, 24.0),
            (26.0, 232.0, 360.0, 156.0, 13.0),
        )
        with tempfile.TemporaryDirectory() as directory:
            for index, bounds in enumerate(layouts):
                image = Path(directory) / f"style-layout-{index}.png"
                style_probe_image(image, bounds=bounds)

                verify_style_surface_rendered(image)

    def test_style_probe_rejects_unrounded_corner_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style-square.png"
            style_probe_image(image, square_corners=True)

            with self.assertRaisesRegex(ValueError, "rounded clipping is missing"):
                verify_style_surface_rendered(image)

    def test_paired_style_probe_checks_arbitrary_gradient_direction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style-gradient.png"
            style_probe_image(image, angle=37.0, bounds=(26.0, 232.0, 360.0, 156.0, 13.0))

            verify_style_surface_rendered(image, require_gradient=True, gradient_angle=37.0)

    def test_paired_style_probe_rejects_a_flat_gradient(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style-flat.png"
            style_probe_image(image)

            with self.assertRaisesRegex(ValueError, "gradient is missing"):
                verify_style_surface_rendered(image, require_gradient=True, gradient_angle=37.0)

    def test_style_probe_rejects_a_missing_straight_border_edge(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style-missing-top-border.png"
            style_probe_image(image, missing_top_border=True)

            with self.assertRaisesRegex(ValueError, "preview border is missing"):
                verify_style_surface_rendered(image)

    def test_style_probe_rejects_transparent_surface(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style.png"
            rows = [[(16, 19, 21, 255)] * 412 for _ in range(892)]
            png_file(image, tuple(tuple(row) for row in rows))

            with self.assertRaisesRegex(ValueError, "preview border is missing"):
                verify_style_surface_rendered(image)

    def test_style_probe_supports_high_density_capture(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "style.png"
            style_probe_image(image, scale=1.5)

            verify_style_surface_rendered(image)


if __name__ == "__main__":
    unittest.main()
