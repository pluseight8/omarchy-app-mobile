#!/usr/bin/env python3
"""Capture a valid Android UI hierarchy, retrying transient empty roots."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Callable, Sequence


REMOTE_PATH = re.compile(r"^/sdcard/[A-Za-z0-9_./-]+$")
Runner = Callable[..., subprocess.CompletedProcess[str]]


def dump_accessibility_xml(
    device_path: str,
    output_path: Path,
    *,
    attempts: int = 4,
    delay_seconds: float = 1.0,
    run: Runner = subprocess.run,
    sleep: Callable[[float], None] = time.sleep,
) -> Path:
    """Dump and pull an XML hierarchy, retrying when Android returns no root."""
    if not REMOTE_PATH.fullmatch(device_path):
        raise ValueError("device path must be a simple file path under /sdcard")
    if attempts < 1:
        raise ValueError("attempts must be at least one")
    if delay_seconds < 0:
        raise ValueError("delay_seconds must not be negative")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    errors: list[str] = []
    for attempt in range(1, attempts + 1):
        output_path.unlink(missing_ok=True)
        try:
            cleanup = run(
                ["adb", "shell", "rm", "-f", device_path],
                check=False, capture_output=True, text=True, timeout=10,
            )
            if cleanup.returncode != 0:
                raise RuntimeError("could not clear stale device hierarchy")

            dumped = run(
                ["adb", "shell", "uiautomator", "dump", "--compressed", device_path],
                check=False, capture_output=True, text=True, timeout=20,
            )
            if dumped.returncode != 0:
                detail = (dumped.stderr or dumped.stdout).strip()
                raise RuntimeError(f"uiautomator dump failed: {detail[:300]}")

            present = run(
                ["adb", "shell", "test", "-s", device_path],
                check=False, capture_output=True, text=True, timeout=10,
            )
            if present.returncode != 0:
                detail = (dumped.stderr or dumped.stdout).strip()
                suffix = f" ({detail[:240]})" if detail else ""
                raise RuntimeError("uiautomator returned no hierarchy root" + suffix)

            pulled = run(
                ["adb", "pull", device_path, str(output_path)],
                check=False, capture_output=True, text=True, timeout=20,
            )
            if pulled.returncode != 0 or not output_path.is_file() or output_path.stat().st_size == 0:
                detail = (pulled.stderr or pulled.stdout).strip()
                raise RuntimeError(f"adb pull failed: {detail[:300]}")

            root = ET.parse(output_path).getroot()
            if root.tag != "hierarchy":
                raise RuntimeError(f"unexpected UI hierarchy root element: {root.tag}")
            if len(root) == 0:
                raise RuntimeError("uiautomator returned an empty hierarchy")
            return output_path
        except (OSError, ET.ParseError, subprocess.SubprocessError, RuntimeError) as error:
            errors.append(f"attempt {attempt}/{attempts}: {error}")
            if attempt < attempts:
                sleep(delay_seconds)

    raise RuntimeError("Could not capture Android UI hierarchy; " + "; ".join(errors))


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("device_path")
    parser.add_argument("output_path", type=Path)
    parser.add_argument("--attempts", type=int, default=4)
    parser.add_argument("--delay-seconds", type=float, default=1.0)
    args = parser.parse_args(argv)
    try:
        result = dump_accessibility_xml(
            args.device_path, args.output_path,
            attempts=args.attempts, delay_seconds=args.delay_seconds,
        )
    except (OSError, RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1
    print(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
