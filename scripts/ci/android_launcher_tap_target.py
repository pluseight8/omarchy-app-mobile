#!/usr/bin/env python3
"""Find the first accessible app-launch button in an Android UIAutomator dump."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def find_target(xml_path: Path) -> tuple[int, int]:
    root = ET.parse(xml_path).getroot()
    nodes = list(root.iter("node"))
    apps_selected = any(
        node.attrib.get("package") == "app.omaapp"
        and node.attrib.get("checked") == "true"
        and "Apps" in {node.attrib.get("content-desc", "").strip(),
                       node.attrib.get("text", "").strip()}
        for node in nodes
    )
    if not apps_selected:
        raise ValueError("Apps drawer is not selected")

    fallback_target = None
    for node in nodes:
        accessible_name = node.attrib.get("content-desc", "")
        if (node.attrib.get("package") != "app.omaapp"
                or node.attrib.get("clickable") != "true"
                or not accessible_name.startswith("Launch ")):
            continue

        match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            continue

        left, top, right, bottom = (int(value) for value in match.groups())
        if right <= left or bottom <= top:
            continue

        target = ((left + right) // 2, (top + bottom) // 2)
        # Prefer the Android Settings app in emulator smoke. Other preloaded
        # apps such as Drive can launch account-setup UI on a fresh AVD, which
        # makes a Home return test depend on Google sign-in state.
        if accessible_name == "Launch Settings":
            return target
        if fallback_target is None:
            fallback_target = target

    if fallback_target is not None:
        return fallback_target

    raise ValueError("no clickable app-launch node found in Android UI dump")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: android_launcher_tap_target.py UI_DUMP.xml", file=sys.stderr)
        return 2

    try:
        x, y = find_target(Path(sys.argv[1]))
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"cannot find Android launcher app target: {error}", file=sys.stderr)
        return 1
    print(x, y)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
