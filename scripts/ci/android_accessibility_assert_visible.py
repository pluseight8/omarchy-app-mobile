#!/usr/bin/env python3
"""Assert that a labeled OmaApp accessibility node has laid-out bounds."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def is_visible(xml_path: Path, target: str) -> bool:
    root = ET.parse(xml_path).getroot()
    for node in root.iter("node"):
        if node.attrib.get("package") != "app.omaapp":
            continue
        if target not in (node.attrib.get("content-desc", "").strip(),
                          node.attrib.get("text", "").strip()):
            continue
        match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            continue
        left, top, right, bottom = (int(value) for value in match.groups())
        if right > left and bottom > top:
            return True
    return False


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: android_accessibility_assert_visible.py UI_DUMP.xml LABEL", file=sys.stderr)
        return 2
    try:
        if not is_visible(Path(sys.argv[1]), sys.argv[2]):
            raise ValueError(f"no visible OmaApp node matches {sys.argv[2]!r}")
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Android accessibility visibility check failed: {error}", file=sys.stderr)
        return 1
    print(f"Visible OmaApp content: {sys.argv[2]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
