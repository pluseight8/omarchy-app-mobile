#!/usr/bin/env python3
"""Find the first accessible app-launch button in an Android UIAutomator dump."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: android_launcher_tap_target.py UI_DUMP.xml", file=sys.stderr)
        return 2

    try:
        root = ET.parse(Path(sys.argv[1])).getroot()
    except (OSError, ET.ParseError) as error:
        print(f"cannot read Android UI dump: {error}", file=sys.stderr)
        return 1

    for node in root.iter("node"):
        accessible_name = node.attrib.get("content-desc", "")
        if node.attrib.get("clickable") != "true" or not accessible_name.startswith("Launch "):
            continue

        match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            continue

        left, top, right, bottom = (int(value) for value in match.groups())
        if right <= left or bottom <= top:
            continue

        print((left + right) // 2, (top + bottom) // 2)
        return 0

    print("no clickable app-launch node found in Android UI dump", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
