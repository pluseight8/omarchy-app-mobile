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
    nodes = list(root.iter("node"))
    # An exact control name must win over a word that happens to appear in a
    # longer accessible name, such as the Apps tab vs. the Settings gesture
    # option "Swipe between Home, Apps, Devices, Style, and Settings".
    for allow_components in (False, True):
        for node in nodes:
            actionable = (node.attrib.get("clickable") == "true"
                          or node.attrib.get("focusable") == "true")
            if (node.attrib.get("package") != "app.omaapp"
                    or not actionable
                    or node.attrib.get("enabled") == "false"):
                continue

            labels = set()
            for attribute in ("content-desc", "text"):
                value = node.attrib.get(attribute, "").strip()
                if value:
                    # Commas can be part of a complete name, as in
                    # "Open <folder>, 1 apps". Component matching is a
                    # fallback for combined names emitted by Qt accessibility.
                    labels.add(value)
                    if allow_components:
                        labels.update(label.strip() for label in value.split(","))
            if target not in labels:
                continue

            match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
            if match is None:
                continue
            left, top, right, bottom = (int(value) for value in match.groups())
            if right <= left or bottom <= top:
                continue
            return (left + right) // 2, (top + bottom) // 2

    raise ValueError(f"no clickable OmaApp control matches {target!r}")


def find_home_folder_target(xml_path: Path, target: str) -> tuple[int, int]:
    root = ET.parse(xml_path).getroot()
    nodes = list(root.iter("node"))
    home_selected = any(
        node.attrib.get("package") == "app.omaapp"
        and node.attrib.get("checked") == "true"
        and node.attrib.get("content-desc", "").strip() == "Home"
        for node in nodes
    )
    if not home_selected:
        raise ValueError("Home is not the selected launcher page")

    # Both the Home page and Apps drawer expose the folder label. Select the
    # Home copy by its QML page container instead of accepting an offscreen
    # or partially visible Apps drawer node during a swipe transition.
    for node in nodes:
        resource_id = node.attrib.get("resource-id", "")
        if (node.attrib.get("package") != "app.omaapp"
                or node.attrib.get("content-desc", "").strip() != target
                or node.attrib.get("clickable") != "true"
                or node.attrib.get("enabled") == "false"
                or "QQuickListView" not in resource_id
                or "QQuickColumnLayout" in resource_id):
            continue
        match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if match is None:
            continue
        left, top, right, bottom = (int(value) for value in match.groups())
        if right > left and bottom > top:
            return (left + right) // 2, (top + bottom) // 2

    raise ValueError(f"no visible Home folder matches {target!r}")


def main() -> int:
    if len(sys.argv) not in (3, 4) or (len(sys.argv) == 4 and sys.argv[1] != "--home-folder"):
        print("usage: android_accessibility_tap_target.py UI_DUMP.xml LABEL", file=sys.stderr)
        return 2

    try:
        if len(sys.argv) == 4:
            x, y = find_home_folder_target(Path(sys.argv[2]), sys.argv[3])
        else:
            x, y = find_target(Path(sys.argv[1]), sys.argv[2])
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"cannot find Android UI target: {error}", file=sys.stderr)
        return 1

    print(x, y)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
