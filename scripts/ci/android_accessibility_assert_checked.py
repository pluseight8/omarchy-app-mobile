#!/usr/bin/env python3
"""Assert that an Android UIAutomator dump exposes a checked labeled control."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def is_checked(xml_path: Path, target: str) -> bool:
    root = ET.parse(xml_path).getroot()
    for node in root.iter("node"):
        if node.attrib.get("package") != "app.omaapp" or node.attrib.get("checked") != "true":
            continue
        labels = set()
        for attribute in ("content-desc", "text"):
            value = node.attrib.get(attribute, "").strip()
            if value:
                labels.add(value)
        if target in labels:
            return True
    return False


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: android_accessibility_assert_checked.py UI_DUMP.xml LABEL", file=sys.stderr)
        return 2
    try:
        if not is_checked(Path(sys.argv[1]), sys.argv[2]):
            raise ValueError(f"no checked OmaApp control matches {sys.argv[2]!r}")
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Android accessibility state check failed: {error}", file=sys.stderr)
        return 1
    print(f"Checked OmaApp control: {sys.argv[2]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
