#!/usr/bin/env python3
"""Find a clickable OmaApp control by its visible or accessible label."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def find_target(xml_path: Path, target: str) -> tuple[int, int]:
    root = ET.parse(xml_path).getroot()
    for node in root.iter("node"):
        if (node.attrib.get("package") != "app.omaapp"
                or node.attrib.get("clickable") != "true"
                or node.attrib.get("enabled") == "false"
                or target not in (node.attrib.get("content-desc", ""), node.attrib.get("text", ""))):
            continue

        match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            continue
        left, top, right, bottom = (int(value) for value in match.groups())
        if right <= left or bottom <= top:
            continue
        return (left + right) // 2, (top + bottom) // 2

    raise ValueError(f"no clickable OmaApp control matches {target!r}")


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: android_accessibility_tap_target.py UI_DUMP.xml LABEL", file=sys.stderr)
        return 2

    try:
        x, y = find_target(Path(sys.argv[1]), sys.argv[2])
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"cannot find Android UI target: {error}", file=sys.stderr)
        return 1

    print(x, y)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
