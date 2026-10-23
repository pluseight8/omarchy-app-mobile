#!/usr/bin/env python3
"""Check Android launcher screenshots against deterministic PNG baselines."""

from __future__ import annotations

import argparse
import math
import struct
import sys
import zlib
from pathlib import Path


SIGNATURE = b"\x89PNG\r\n\x1a\n"
SCREENS = ("home", "apps", "devices", "style", "settings")
HOME_DYNAMIC_REGIONS = ((34, 184, 250, 266),)  # Local time and date.


def decode_png(path: Path) -> tuple[int, int, int, list[bytes]]:
    data = path.read_bytes()
    if not data.startswith(SIGNATURE):
        raise ValueError(f"{path} is not a PNG file")
    offset = len(SIGNATURE)
    compressed = bytearray()
    width = height = bit_depth = color_type = None
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        chunk = data[offset + 8:offset + 8 + length]
        if len(chunk) != length:
            raise ValueError(f"{path} contains a truncated PNG chunk")
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", chunk
            )
            if bit_depth != 8 or color_type not in (2, 6) or compression or filtering or interlace:
                raise ValueError(f"{path} uses an unsupported PNG encoding")
        elif kind == b"IDAT":
            compressed.extend(chunk)
        elif kind == b"IEND":
            break
        offset += length + 12
    if width is None or height is None:
        raise ValueError(f"{path} has no PNG dimensions")

    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(compressed)
    if len(raw) != height * (stride + 1):
        raise ValueError(f"{path} has an invalid decoded image size")

    rows: list[bytes] = []
    previous = bytearray(stride)
    cursor = 0
    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        scanline = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        for index in range(stride):
            left = scanline[index - channels] if index >= channels else 0
            up = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                scanline[index] = (scanline[index] + left) & 0xFF
            elif filter_type == 2:
                scanline[index] = (scanline[index] + up) & 0xFF
            elif filter_type == 3:
                scanline[index] = (scanline[index] + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                estimate = left + up - upper_left
                left_distance = abs(estimate - left)
                up_distance = abs(estimate - up)
                upper_left_distance = abs(estimate - upper_left)
                predictor = left if left_distance <= up_distance and left_distance <= upper_left_distance \
                    else up if up_distance <= upper_left_distance else upper_left
                scanline[index] = (scanline[index] + predictor) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"{path} uses unknown PNG filter {filter_type}")
        rows.append(bytes(scanline))
        previous = scanline
    return width, height, channels, rows


def compare_images(actual: Path, baseline: Path) -> tuple[int, float]:
    actual_width, actual_height, actual_channels, actual_rows = decode_png(actual)
    base_width, base_height, base_channels, base_rows = decode_png(baseline)
    if actual_channels != base_channels:
        raise ValueError(
            f"{actual.name}: screenshot has {actual_channels} channels, baseline has {base_channels}"
        )

    scaled = (actual_width, actual_height) != (base_width, base_height)
    if scaled and actual_width * base_height != actual_height * base_width:
        raise ValueError(
            f"{actual.name}: screenshot {actual_width}x{actual_height} does not share the baseline "
            f"aspect ratio {base_width}x{base_height}"
        )

    def sample_actual_pixel(x: int, y: int) -> tuple[int, ...]:
        if not scaled:
            start = x * actual_channels
            return tuple(actual_rows[y][start:start + actual_channels])

        # Average the physical-pixel rectangle for one logical baseline pixel.
        # This supports fractional density ratios such as 240/160 without a
        # Pillow dependency and makes DPI captures comparable in dp space.
        left = x * actual_width // base_width
        right = max(left + 1, (x + 1) * actual_width // base_width)
        top = y * actual_height // base_height
        bottom = max(top + 1, (y + 1) * actual_height // base_height)
        sums = [0] * actual_channels
        samples = 0
        for physical_y in range(top, bottom):
            row = actual_rows[physical_y]
            for physical_x in range(left, right):
                offset = physical_x * actual_channels
                for channel in range(actual_channels):
                    sums[channel] += row[offset + channel]
                samples += 1
        return tuple((value + samples // 2) // samples for value in sums)

    changed = total = 0
    bytes_per_pixel = actual_channels
    dynamic_regions = HOME_DYNAMIC_REGIONS if actual.stem == "home" else ()
    for y, base_row in enumerate(base_rows):
        # Mask Android's clock/status icons and gesture navigation hint in the
        # baseline's logical-pixel coordinate space.
        if y < 42 or y >= base_height - 32:
            continue
        for x in range(base_width):
            if any(left <= x < right and top <= y < bottom
                   for left, top, right, bottom in dynamic_regions):
                continue
            left = x * bytes_per_pixel
            actual_pixel = sample_actual_pixel(x, y)
            delta = max(abs(actual_pixel[channel] - base_row[left + channel])
                        for channel in range(min(3, bytes_per_pixel)))
            changed += delta > 16
            total += 1
    return changed, changed / max(1, total)


def compare_image_variants(actual: Path, baselines: list[Path]) -> tuple[Path, int, float]:
    """Compare against reviewed layout variants and return the closest match."""
    if not baselines:
        raise ValueError("at least one visual baseline is required")
    results = [(baseline, *compare_images(actual, baseline)) for baseline in baselines]
    return min(results, key=lambda result: result[2])


def verify_style_surface_rendered(
    image: Path, *, require_gradient: bool = False, gradient_angle: float = 45.0
) -> None:
    """Check that the GPU surface clips corners and renders its border."""
    width, height, channels, rows = decode_png(image)
    if width * 892 != height * 412 or width < 412 or height < 892:
        raise ValueError(
            f"style surface probe expects a 412x892 logical viewport, got {width}x{height}"
        )

    def rgb(point: tuple[int, int]) -> tuple[int, int, int]:
        x, y = point
        x = round((x + 0.5) * width / 412 - 0.5)
        y = round((y + 0.5) * height / 892 - 0.5)
        start = x * channels
        return tuple(rows[y][start:start + 3])

    # Theme tokens can move the preview vertically and change its inset. Find
    # its long top/bottom border edges first, then the two long side edges.
    # The old fixed (26, 232) probe sampled the fill when the local theme used
    # its smaller spacing, and missed the surface entirely on that layout.
    page_background = rgb((10, 180))

    def contrast(point: tuple[int, int], base: tuple[int, int, int]) -> int:
        return sum(abs(value - base_channel) for value, base_channel in zip(rgb(point), base))

    row_scores = {
        y: sum(contrast((x, y), page_background) > 48 for x in range(20, 392))
        for y in range(100, 410)
    }
    edge_threshold = 170
    edge_rows = [(y, score) for y, score in row_scores.items() if score >= edge_threshold]
    edge_pairs = [
        (min(top_score, bottom_score), top_score + bottom_score, top, bottom)
        for top, top_score in edge_rows
        for bottom, bottom_score in edge_rows
        if 120 <= bottom - top <= 190
    ]
    if not edge_pairs:
        raise ValueError("HyprlandSurface preview border is missing: no complete edge pair found")
    _, _, top, bottom = max(edge_pairs, key=lambda pair: (pair[0], pair[1], -pair[2]))

    mid_y = (top + bottom) // 2
    background = rgb((10, mid_y))

    def column_score(x: int) -> int:
        return sum(contrast((x, y), background) > 48 for y in range(top + 16, bottom - 16))

    left_scores = {x: column_score(x) for x in range(0, 100)}
    right_scores = {x: column_score(x) for x in range(312, 412)}
    left_max = max(left_scores.values())
    right_max = max(right_scores.values())
    if min(left_max, right_max) < 80:
        raise ValueError("HyprlandSurface preview border is missing: no complete side edges found")

    left_candidates = [x for x, score in left_scores.items() if score >= left_max - 1]
    right_candidates = [x for x, score in right_scores.items() if score >= right_max - 1]
    left = round(sum(left_candidates) / len(left_candidates))
    right = round(sum(right_candidates) / len(right_candidates))
    center_x = (left + right) // 2

    interior = rgb((center_x, top + 60))
    border = rgb((center_x, top))
    interior_contrast = sum(abs(a - b) for a, b in zip(interior, background))
    border_contrast = sum(abs(a - b) for a, b in zip(border, background))
    if interior_contrast < 8:
        raise ValueError(
            "HyprlandSurface preview interior is missing: its center matches the page background"
        )
    if border_contrast < 48:
        raise ValueError(
            "HyprlandSurface preview border is missing: its top edge matches the page background"
        )

    # Sampling the discovered outer corners catches square clipping and the
    # old vertex-mesh diagonal on either RHI without assuming a theme's spacing.
    corners = ((left, top), (right, top), (left, bottom), (right, bottom))
    for point in corners:
        corner = rgb(point)
        if sum(abs(channel - base) for channel, base in zip(corner, background)) > 24:
            raise ValueError(
                f"HyprlandSurface rounded clipping is missing at corner {point}: "
                "the corner is filled instead of transparent"
            )

    if require_gradient:
        # The fixture has a three-stop 37 degree gradient. Verify that its
        # horizontal top edge changes color and that two points on the same
        # gradient isocontour agree. This tests the continuous arbitrary-angle
        # shader path instead of only checking the QML labels.
        inset = max(28, round((right - left) * 0.08))
        left_top = rgb((left + inset, top))
        right_top = rgb((right - inset, top))
        if sum(abs(left - right) for left, right in zip(left_top, right_top)) < 48:
            raise ValueError(
                "HyprlandSurface gradient is missing: the 37 degree top edge has no color change"
            )

        radians = gradient_angle * 3.141592653589793 / 180.0
        sine = math.sin(radians)
        if sine <= 0.0:
            raise ValueError("The gradient angle must be between 0 and 360 degrees")
        left_f = float(left)
        top_f = float(top)
        card_width = float(right - left)
        card_height = float(bottom - top)
        top_x = center_x
        top_y = top
        top_u = (top_x + 0.5 - left_f) / card_width
        top_v = (top_y + 0.5 - top_f) / card_height
        left_u = (left + 0.5 - left_f) / card_width
        left_v = (top_u * (1.0 - sine) + top_v * sine
                  - left_u * (1.0 - sine)) / sine
        left_y = round(top_f + left_v * card_height - 0.5)
        top_mid = rgb((top_x, top_y))
        left_match = rgb((left, left_y))
        if sum(abs(left - right) for left, right in zip(top_mid, left_match)) > 30:
            raise ValueError(
                "HyprlandSurface gradient angle is inconsistent across the same color contour"
            )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("screenshots", type=Path)
    parser.add_argument("--baselines", type=Path,
                        default=Path("tests/visual_baselines/android"))
    parser.add_argument("--screens", nargs="+", choices=SCREENS, default=SCREENS,
                        help="which captured screens to compare")
    parser.add_argument("--verify-style-surface", action="store_true",
                        help="assert the Omarchy preview fill and border are actually rendered")
    parser.add_argument("--require-gradient", action="store_true",
                        help="also verify the synced fixture's arbitrary-angle gradient render")
    parser.add_argument("--gradient-angle", type=float, default=37.0,
                        help="gradient angle used by the paired-theme fixture (default: 37)")
    parser.add_argument("--expected-size", nargs=2, type=int, metavar=("WIDTH", "HEIGHT"),
                        default=(412, 892),
                        help="expected physical screenshot size (default: 412 892)")
    parser.add_argument("--record", action="store_true",
                        help="replace baseline images with the current screenshots")
    parser.add_argument("--home-alternate-baseline", type=Path,
                        help="additional reviewed Home screenshot for a compact Android viewport")
    parser.add_argument("--allow-missing-baseline", action="store_true",
                        help="allow capture-only CI while bootstrapping reviewed baseline images")
    parser.add_argument("--max-changed", type=float, default=0.015,
                        help="allowed changed-pixel fraction, excluding system bars")
    args = parser.parse_args()

    if not 0 <= args.max_changed <= 1:
        parser.error("--max-changed must be between 0 and 1")

    failures = []
    missing_baselines = []
    if args.verify_style_surface:
        try:
            verify_style_surface_rendered(
                args.screenshots / "style.png",
                require_gradient=args.require_gradient,
                gradient_angle=args.gradient_angle,
            )
            print("style surface probe: preview fill and border are visible")
        except (OSError, ValueError, zlib.error) as error:
            failures.append(str(error))

    for screen in args.screens:
        actual = args.screenshots / f"{screen}.png"
        baseline = args.baselines / f"{screen}.png"
        try:
            width, height, _channels, rows = decode_png(actual)
            expected_width, expected_height = args.expected_size
            if width != expected_width or height != expected_height:
                raise ValueError(
                    f"{screen}: expected {expected_width}x{expected_height} screenshot, got {width}x{height}"
                )
            if not any(any(pixel for pixel in row) for row in rows):
                raise ValueError(f"{screen}: screenshot is empty")
            if args.record:
                args.baselines.mkdir(parents=True, exist_ok=True)
                baseline.write_bytes(actual.read_bytes())
                print(f"Recorded {baseline}")
            elif not baseline.is_file():
                missing_baselines.append(screen)
            else:
                candidates = [baseline]
                if screen == "home" and args.home_alternate_baseline is not None:
                    if not args.home_alternate_baseline.is_file():
                        raise ValueError(
                            "missing alternate Home visual baseline: "
                            f"{args.home_alternate_baseline}"
                        )
                    candidates.append(args.home_alternate_baseline)
                closest, changed, fraction = compare_image_variants(actual, candidates)
                baseline_note = f", closest baseline: {closest.name}" if len(candidates) > 1 else ""
                print(f"{screen}: {changed} changed pixels ({fraction:.2%}{baseline_note})")
                if fraction > args.max_changed:
                    failures.append(f"{screen}: changed-pixel ratio {fraction:.2%} exceeds {args.max_changed:.2%}")
        except (OSError, ValueError, zlib.error) as error:
            failures.append(str(error))

    if missing_baselines and not args.record:
        print("Visual baselines not committed yet for: " + ", ".join(missing_baselines), file=sys.stderr)
        print("Capture the first reviewed CI screenshots, then record them with --record.", file=sys.stderr)
        if not args.allow_missing_baseline:
            failures.append("missing visual regression baseline")
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
