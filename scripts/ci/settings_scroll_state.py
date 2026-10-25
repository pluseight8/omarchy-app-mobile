#!/usr/bin/env python3
"""Tell the Android visual smoke how to restore the Settings page to its top."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


TARGET_DESCRIPTION = "Create an OWL widget"
TOP_MIN_Y = 190
TOP_MAX_Y = 210
BOUNDS_PATTERN = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def scroll_state(xml_text: str) -> str:
    root = ET.fromstring(xml_text)
    for node in root.iter("node"):
        if node.attrib.get("content-desc") != TARGET_DESCRIPTION:
            continue
        match = BOUNDS_PATTERN.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            return "down"
        _, top, _, bottom = (int(value) for value in match.groups())
        center_y = (top + bottom) // 2
        if center_y < TOP_MIN_Y:
            return "down"
        if center_y > TOP_MAX_Y:
            return "up"
        return "ready"
    # The button is above the viewport when a long Settings page was left
    # scrolled down; moving the content down brings the top back into view.
    return "down"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: settings_scroll_state.py ACCESSIBILITY_XML", file=sys.stderr)
        return 2
    print(scroll_state(Path(sys.argv[1]).read_text(encoding="utf-8")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
