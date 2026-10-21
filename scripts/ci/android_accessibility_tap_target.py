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
        actionable = (node.attrib.get("clickable") == "true"
                      or node.attrib.get("focusable") == "true")
        labels = set()
        for attribute in ("content-desc", "text"):
            value = node.attrib.get(attribute, "").strip()
            if value:
                # Keep the full accessible name intact: commas can be part of
                # a label, as in "Open <folder>, 1 apps". Qt may also combine
                # multiple accessibility names into one comma-separated value.
                labels.add(value)
                labels.update(label.strip() for label in value.split(","))
        if (node.attrib.get("package") != "app.omaapp"
                or not actionable
                or node.attrib.get("enabled") == "false"
                or target not in labels):
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
