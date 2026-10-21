#!/usr/bin/env python3
"""Exercise Android's TLS-pinned client against the installed Omarchy plugin service."""

from __future__ import annotations

import base64
import json
import queue
import struct
import subprocess
import sys
import tempfile
import threading
import time
import zlib
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "omarchy-plugin"))
import service  # noqa: E402


def _png_chunk(kind: bytes, payload: bytes) -> bytes:
    contents = kind + payload
    return struct.pack(">I", len(payload)) + contents + struct.pack(">I", zlib.crc32(contents) & 0xFFFFFFFF)


def _one_pixel_png() -> bytes:
    header = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
    pixel = zlib.compress(b"\x00\x20\x40\x60\xff")
    return b"\x89PNG\r\n\x1a\n" + _png_chunk(b"IHDR", header) + \
            _png_chunk(b"IDAT", pixel) + _png_chunk(b"IEND", b"")


def main() -> int:
    theme = {"name": "CI_Everforest"}
    theme_lock = threading.Lock()
    theme_reads = 0
    wallpaper_path: Path | None = None

    def snapshot():
        nonlocal theme_reads
        with theme_lock:
            theme_reads += 1
            name = theme["name"]
        return ({
            "protocolVersion": service.PROTOCOL_VERSION,
            "hostName": "CI Omarchy",
            "theme": {
                "name": name,
                "colors": {
                    "background": "#1e2326",
                    "foreground": "#d3c6aa",
                    "accent": "#a7c080",
                },
                "shell": {"spacing": {"scale": 1.0, "row-gap": 7, "panel-gap": 17}},
            },
            "hyprland": {
                "rounding": 13,
                "roundingPower": 4.0,
                "borderWidth": 2,
                "activeBorder": "rgba(33ccffee) rgba(00ff99ee) 45deg",
                "activeBorderAngle": 45,
                "inactiveBorder": "rgba(595959aa) 0deg",
                "gapsIn": 5,
                "gapsOut": 10,
            },
            "wallpaper": {
                "id": "a" * 32,
                "name": "ci-wallpaper.png",
                "size": wallpaper_path.stat().st_size,
                "mimeType": "image/png",
            },
            "capturedAt": int(time.time()),
        }, wallpaper_path)

    def qr_payload(state: service.ServiceState) -> str:
        state.begin_pairing()
        payload = {
            "schema": service.PROTOCOL_VERSION,
            "url": f"https://10.0.2.2:{state.port}",
            "fingerprint": state.fingerprint,
            "pairSecret": state.pair_secret,
            "expiresAt": state.pair_expires,
            "hostName": "CI Omarchy",
        }
        return json.dumps(payload, separators=(",", ":"))

    def run_android_instrumentation(state: service.ServiceState, qr: str, *, expected_theme: str,
                                   updated_theme: str = "", revoke: bool = False) -> str:
        encoded_qr = base64.b64encode(qr.encode("utf-8")).decode("ascii")
        command = [
            "adb", "shell", "am", "instrument", "-w",
            "-e", "qr64", encoded_qr,
            "-e", "expectedTheme", expected_theme,
        ]
        if updated_theme:
            command.extend(["-e", "updatedTheme", updated_theme])
        if revoke:
            command.extend(["-e", "revoke", "true"])
        command.append("app.omaapp/app.omaapp.PairingTestInstrumentation")
        listed = subprocess.run(
            ["adb", "shell", "pm", "list", "instrumentation"],
            check=True, capture_output=True, text=True, timeout=15,
        )
        print("Installed Android instrumentation: " + listed.stdout.strip(), flush=True)
        print("Starting Android pairing instrumentation.", flush=True)
        process = subprocess.Popen(
            command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1,
        )
        output_queue: queue.Queue[str | None] = queue.Queue()

        def read_output() -> None:
            assert process.stdout is not None
            for line in process.stdout:
                output_queue.put(line)
            output_queue.put(None)

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()
        output: list[str] = []
        updated = False
        # Two serial theme waits may each take 60 seconds; leave room for the
        # approval, reconnect, and Android instrumentation startup overhead.
        deadline = time.monotonic() + 150
        next_diagnostic = time.monotonic() + 10
        while process.poll() is None and time.monotonic() < deadline:
            try:
                line = output_queue.get(timeout=0.1)
            except queue.Empty:
                line = ""
            if line:
                output.append(line)
                print(line.rstrip())
                if updated_theme and not updated and "INSTRUMENTATION_STATUS: stage=paired" in line:
                    with theme_lock:
                        theme["name"] = updated_theme
                    updated = True

            with state.lock:
                pending_ids = [
                    request_id for request_id, request in state.pending.items()
                    if request.get("status") == "pending"
                ]
            for request_id in pending_ids:
                if state.decide_pair_request(request_id, approve=True):
                    print("Approved the Android pairing request through the plugin host flow.", flush=True)

            if time.monotonic() >= next_diagnostic:
                with state.lock:
                    pending_count = sum(
                        request.get("status") == "pending"
                        for request in state.pending.values()
                    )
                    device_count = len(state.devices)
                print(
                    "Waiting for Android pairing: "
                    f"pending={pending_count}, devices={device_count}, theme_reads={theme_reads}.",
                    flush=True,
                )
                next_diagnostic = time.monotonic() + 10

        if process.poll() is None:
            print("Android pairing instrumentation exceeded its 150-second deadline.", flush=True)
            logs = subprocess.run(
                ["adb", "logcat", "-d", "-t", "1000"],
                check=False, capture_output=True, text=True, timeout=15,
            ).stdout.splitlines()
            relevant = [
                line for line in logs
                if "OmaAppPairingSmoke" in line
                or "AndroidRuntime" in line
                or "app.omaapp" in line
            ]
            if relevant:
                print("Android pairing logcat diagnostics:", flush=True)
                for line in relevant[-80:]:
                    print(line, flush=True)
            process.kill()
        return_code = process.wait(timeout=5)
        reader.join(timeout=1)
        while True:
            try:
                line = output_queue.get_nowait()
            except queue.Empty:
                break
            if line:
                output.append(line)
                print(line.rstrip())
        combined = "".join(output)
        if return_code != 0:
            raise RuntimeError(f"Android pairing instrumentation exited {return_code}:\n{combined}")
        if "INSTRUMENTATION_STATUS: stage=failed" in combined:
            raise RuntimeError(f"Android pairing instrumentation reported failure:\n{combined}")
        if f"INSTRUMENTATION_STATUS: stage=paired" not in combined:
            raise RuntimeError(f"Android client did not report a paired theme:\n{combined}")
        if updated_theme and "INSTRUMENTATION_STATUS: stage=theme_updated" not in combined:
            raise RuntimeError(f"Android client did not receive a live theme update:\n{combined}")
        if revoke and "INSTRUMENTATION_STATUS: stage=revoked" not in combined:
            raise RuntimeError(f"Android client did not report revocation:\n{combined}")
        return combined

    with tempfile.TemporaryDirectory(prefix="omaapp-pairing-smoke-") as temporary:
        wallpaper_path = Path(temporary) / "ci-wallpaper.png"
        wallpaper_path.write_bytes(_one_pixel_png())
        state = service.ServiceState(Path(temporary), "0.0.0.0", 0)
        try:
            with patch.object(service, "_advertised_ipv4", return_value="10.0.2.2"), \
                    patch.object(service, "_theme_snapshot", side_effect=snapshot), \
                    patch.object(
                        service, "_emit",
                        side_effect=lambda event: print(
                            "Pairing service event: " + json.dumps(event, sort_keys=True),
                            flush=True,
                        ),
                    ):
                first_qr = qr_payload(state)
                run_android_instrumentation(
                    state, first_qr, expected_theme="CI_Everforest", updated_theme="CI_Night",
                )
                if len(state.devices) != 1 or not state.pending:
                    raise RuntimeError("The plugin did not retain the approved Android device.")

                second_qr = qr_payload(state)
                run_android_instrumentation(
                    state, second_qr, expected_theme="CI_Night", revoke=True,
                )
                if state.devices:
                    raise RuntimeError("The Android client did not revoke its saved device token.")
                if theme_reads < 2:
                    raise RuntimeError("The client did not fetch an updated theme snapshot.")
        finally:
            state.stop_server()
    print("Android TLS pairing, live theme update, reconnect, and revocation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
