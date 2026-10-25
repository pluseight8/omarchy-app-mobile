#!/usr/bin/env python3
"""Classify the observed stage when Android HOME UI readiness times out."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def classify(boot_completed: str, app_pid: str, logcat: str) -> str:
    if boot_completed.strip() != "1":
        return "emulator_boot_incomplete"
    if re.search(r"FATAL EXCEPTION|Fatal signal|ANR in app\.omaapp", logcat, re.IGNORECASE):
        return "android_process_crash_or_anr"
    if not app_pid.strip():
        return "app_process_not_running"
    if re.search(
        r"QQmlApplicationEngine failed to load component|module .* is not installed",
        logcat,
        re.IGNORECASE,
    ):
        return "qml_component_load_failure"

    qml_load_started = "OmaApp startup: loading QML module" in logcat
    qml_load_returned = "OmaApp startup: QML module load returned" in logcat
    event_loop_entered = "OmaApp startup: entering event loop" in logcat
    graphics_marker = "OmaApp initialized Qt Quick graphics API:" in logcat
    rhi_started = re.search(
        r"Creating QRhi with backend|Created QRhi |Created OpenGL context",
        logcat,
    ) is not None

    # The diagnostic signal is connected immediately after QML construction.
    # A runner can hang while the component tree is still being instantiated,
    # even though the renderer thread has already started creating its RHI.
    if qml_load_started and not qml_load_returned:
        return (
            "qml_component_creation_stalled_after_qt_rhi_start"
            if rhi_started
            else "qml_component_creation_stalled_before_qt_rhi_start"
        )
    if qml_load_returned and not event_loop_entered:
        return "qt_setup_stalled_before_event_loop"
    if event_loop_entered and rhi_started and not graphics_marker:
        return "qt_rhi_started_graphics_diagnostic_missing"
    if not graphics_marker:
        return "qt_graphics_initialization_not_observed"
    return "qt_graphics_initialized_ui_readiness_failed"


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: classify_android_launch.py BOOT_COMPLETED APP_PID LOGCAT_FILE", file=sys.stderr)
        return 2
    boot_completed, app_pid, log_path = sys.argv[1:]
    logcat = Path(log_path).read_text(encoding="utf-8", errors="replace")
    print(classify(boot_completed, app_pid, logcat))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
