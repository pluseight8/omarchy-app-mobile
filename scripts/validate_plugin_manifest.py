#!/usr/bin/env python3
"""Check the manifest contract used by Omarchy third-party plugins."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path, PurePosixPath

ALLOWED_KINDS = {"bar-widget", "panel", "overlay", "menu", "service", "bar"}
ENTRY_KEYS = {
    "bar-widget": "barWidget",
    "panel": "panel",
    "overlay": "overlay",
    "menu": "menu",
    "service": "service",
    "bar": "bar",
}


def validate_plugin(root: Path) -> list[str]:
    errors: list[str] = []
    root = root.resolve()
    manifest_path = root / "manifest.json"

    if not root.is_dir():
        return [f"plugin directory does not exist: {root}"]
    if manifest_path.is_symlink() or not manifest_path.is_file():
        return ["manifest.json is missing or is a symbolic link"]

    for path in root.rglob("*"):
        if path.is_symlink():
            errors.append(f"symbolic links are not allowed: {path.relative_to(root)}")

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [f"could not read manifest.json: {error}"]

    if not isinstance(manifest, dict):
        return ["manifest root must be a JSON object"]

    if manifest.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")

    plugin_id = manifest.get("id")
    if not isinstance(plugin_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{1,127}", plugin_id):
        errors.append("id must be a namespaced identifier with 2-128 safe characters")
    elif plugin_id.startswith("omarchy."):
        errors.append("the omarchy. plugin namespace is reserved")

    for field in ("name", "version"):
        if not isinstance(manifest.get(field), str) or not manifest[field].strip():
            errors.append(f"{field} must be a non-empty string")

    kinds = manifest.get("kinds")
    if not isinstance(kinds, list) or not kinds or any(kind not in ALLOWED_KINDS for kind in kinds):
        errors.append("kinds must contain one or more supported plugin kinds")
        kinds = []
    elif len(set(kinds)) != len(kinds):
        errors.append("kinds must not contain duplicates")

    entry_points = manifest.get("entryPoints")
    if not isinstance(entry_points, dict):
        errors.append("entryPoints must be an object")
        entry_points = {}

    for kind in kinds:
        key = ENTRY_KEYS[kind]
        entry = entry_points.get(key)
        if not isinstance(entry, str) or not entry:
            errors.append(f"entryPoints.{key} is required for kind {kind}")
            continue

        relative = PurePosixPath(entry)
        if relative.is_absolute() or ".." in relative.parts or "\\" in entry:
            errors.append(f"entryPoints.{key} must be a safe relative path")
            continue

        target = (root / Path(*relative.parts)).resolve()
        try:
            target.relative_to(root)
        except ValueError:
            errors.append(f"entryPoints.{key} escapes the plugin directory")
            continue
        if not target.is_file():
            errors.append(f"entry point does not exist: {entry}")

    if "bar-widget" in kinds:
        widget = manifest.get("barWidget")
        if not isinstance(widget, dict):
            errors.append("barWidget metadata is required for bar-widget plugins")
        else:
            for field in ("displayName", "category"):
                if not isinstance(widget.get(field), str) or not widget[field].strip():
                    errors.append(f"barWidget.{field} must be a non-empty string")
            if not isinstance(widget.get("allowMultiple"), bool):
                errors.append("barWidget.allowMultiple must be a boolean")

    return errors


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {Path(argv[0]).name} PLUGIN_DIRECTORY", file=sys.stderr)
        return 2

    errors = validate_plugin(Path(argv[1]))
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"Valid Omarchy plugin manifest: {Path(argv[1])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
