#!/usr/bin/env python3
"""Fail when localized Android navigation text paints outside its button cells."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Sequence

from check_visual_regression import decode_png


BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
INK_THRESHOLD = 96
MAX_SPILL_PIXELS = 2


def check_navigation_layout(
    screenshot: Path, hierarchy: Path, labels: Sequence[str]
) -> list[str]:
    """Return layout failures for the visible bottom navigation row."""
    if len(labels) != 5 or len(set(labels)) != len(labels):
        return ["expected five unique navigation labels"]

    try:
        width, height, channels, rows = decode_png(screenshot)
        root = ET.parse(hierarchy).getroot()
    except (OSError, ET.ParseError, ValueError) as error:
        return [f"could not read navigation screenshot or UI hierarchy: {error}"]

    hierarchy_node = next(root.iter("node"), None)
    hierarchy_bounds = (BOUNDS.fullmatch(hierarchy_node.attrib.get("bounds", ""))
                        if hierarchy_node is not None else None)
    if hierarchy_bounds is None:
        return ["Android hierarchy has no viewport bounds"]
    _left, _top, logical_width, logical_height = map(int, hierarchy_bounds.groups())
    if logical_width <= 0 or logical_height <= 0:
        return ["Android hierarchy has invalid viewport bounds"]

    scale_x = width / logical_width
    scale_y = height / logical_height
    if abs(scale_x - scale_y) > 0.02:
        return ["screenshot and Android UI hierarchy have different aspect ratios"]

    nav_bounds: list[tuple[int, int, int, int]] = []
    errors: list[str] = []
    for label in labels:
        matches = [
            node for node in root.iter("node")
            if node.attrib.get("package") == "app.omaapp"
            and node.attrib.get("clickable") == "true"
            and node.attrib.get("content-desc", "").strip() == label
        ]
        if len(matches) != 1:
            errors.append(f"expected one clickable navigation control named {label!r}")
            continue
        match = BOUNDS.fullmatch(matches[0].attrib.get("bounds", ""))
        if match is None:
            errors.append(f"navigation control {label!r} has no usable bounds")
            continue
        left, top, right, bottom = map(int, match.groups())
        if right <= left or bottom <= top:
            errors.append(f"navigation control {label!r} has empty bounds")
            continue
        nav_bounds.append((
            round(left * scale_x), round(top * scale_y),
            round(right * scale_x), round(bottom * scale_y),
        ))

    if errors:
        return errors
    if len(nav_bounds) != 5:
        return ["did not resolve five navigation buttons"]
    nav_bounds.sort(key=lambda bounds: bounds[0])
    if any(left < 0 or right > width or top < 0 or bottom > height
           for left, top, right, bottom in nav_bounds):
        return ["navigation button bounds exceed the screenshot"]

    def is_text_ink(x: int, y: int) -> bool:
        pixel = rows[y][x * channels:x * channels + 3]
        return min(pixel) >= INK_THRESHOLD and max(pixel) - min(pixel) <= 80

    # Keep a two-pixel inset from the control edges so rounded surface outlines
    # do not count as text. Any remaining neutral foreground in the gaps or
    # outside the row comes from text that escaped its own cell.
    _first_left, row_top, _first_right, row_bottom = nav_bounds[0]
    row_top = max(top for _left, top, _right, _bottom in nav_bounds) + 2
    row_bottom = min(bottom for _left, _top, _right, bottom in nav_bounds) - 2
    if row_bottom <= row_top:
        return ["navigation button cells do not share a visible text row"]
    regions = [(0, nav_bounds[0][0] - 2)]
    regions.extend(
        (left_bounds[2] + 2, right_bounds[0] - 2)
        for left_bounds, right_bounds in zip(nav_bounds, nav_bounds[1:])
    )
    regions.append((nav_bounds[-1][2] + 2, width))

    for index, (left, right) in enumerate(regions):
        left = max(0, left)
        right = min(width, right)
        spill = sum(
            1
            for y in range(row_top, row_bottom)
            for x in range(left, right)
            if is_text_ink(x, y)
        )
        if spill > MAX_SPILL_PIXELS:
            errors.append(
                f"navigation label ink spills outside button gap {index + 1}: "
                f"{spill} pixels"
            )
    return errors


def main(argv: Sequence[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 7:
        print(
            "usage: check_android_navigation_layout.py SCREENSHOT.png UI.xml "
            "LABEL1 LABEL2 LABEL3 LABEL4 LABEL5",
            file=sys.stderr,
        )
        return 2
    errors = check_navigation_layout(Path(args[0]), Path(args[1]), args[2:])
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("localized navigation labels stay within their button cells")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
