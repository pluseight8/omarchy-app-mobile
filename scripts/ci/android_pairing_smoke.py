#!/usr/bin/env python3
"""Exercise Android's TLS-pinned client against the installed Omarchy plugin service."""

from __future__ import annotations

import base64
import json
import os
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

from android_accessibility_dump import dump_accessibility_xml
from check_visual_regression import verify_style_surface_rendered


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
                "shell": {
                    "spacing": {"scale": 1.0, "row-gap": 7, "panel-gap": 17},
                    "font": {"base-size": 14, "body": 13, "caption": 11},
                    "popups": {"background": "#263238", "background-alpha": 0.85},
                },
            },
            "hyprland": {
                "rounding": 13,
                "roundingPower": 4.0,
                "borderWidth": 2,
                "activeBorder": "rgba(33ccffee) rgba(00ff99ee) rgba(ffcc88ee) 37deg",
                "activeBorderAngle": 37,
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
                                   updated_theme: str = "", revoke: bool = False,
                                   feature_smoke: bool = False,
                                   assert_reconnect_theme: str = "") -> str:
        command = [
            "adb", "shell", "am", "instrument", "-w",
        ]
        if assert_reconnect_theme:
            command.extend(["-e", "assertReconnect", assert_reconnect_theme])
        else:
            encoded_qr = base64.b64encode(qr.encode("utf-8")).decode("ascii")
            command.extend(["-e", "qr64", encoded_qr, "-e", "expectedTheme", expected_theme])
        if updated_theme:
            command.extend(["-e", "updatedTheme", updated_theme])
        if revoke:
            command.extend(["-e", "revoke", "true"])
        if feature_smoke:
            command.extend(["-e", "featureSmoke", "true"])
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
        # Leave room for theme refresh, feature permissions, binary transfers, and revocation.
        deadline = time.monotonic() + 240
        next_diagnostic = time.monotonic() + 10
        granted_features = False
        disabled_features: set[str] = set()
        restored_features: set[str] = set()
        feature_permissions = tuple(service.DEVICE_PERMISSIONS)
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
                    state.event_hub.publish("theme")
                if "INSTRUMENTATION_STATUS: stage=connectivity_heartbeat_waiting" in line:
                    state.event_hub.publish("features")
                if (feature_smoke and not granted_features
                        and "INSTRUMENTATION_STATUS: stage=feature_permissions_waiting" in line):
                    if len(state.devices) != 1:
                        raise RuntimeError("The Android test device was not paired before feature grants.")
                    device_id = next(iter(state.devices))
                    for permission in feature_permissions:
                        if not state.set_permission(device_id, permission, True):
                            raise RuntimeError(f"Could not grant Android test permission {permission}.")
                    granted_features = True
                    print("Granted separate Omarchy permissions for Android feature smoke.", flush=True)
                if feature_smoke:
                    for permission in feature_permissions:
                        stage_permission = permission.replace(".", "_")
                        disable_stage = (
                            f"INSTRUMENTATION_STATUS: stage=feature_disable_requested_{stage_permission}"
                        )
                        enable_stage = (
                            f"INSTRUMENTATION_STATUS: stage=feature_enable_requested_{stage_permission}"
                        )
                        if disable_stage in line and permission not in disabled_features:
                            if len(state.devices) != 1:
                                raise RuntimeError("The Android test device disappeared before revoking a grant.")
                            device_id = next(iter(state.devices))
                            if not state.set_permission(device_id, permission, False):
                                raise RuntimeError(f"Could not disable Android test permission {permission}.")
                            disabled_features.add(permission)
                            print(f"Disabled only the {permission} permission for Android test.", flush=True)
                        if enable_stage in line and permission not in restored_features:
                            if len(state.devices) != 1:
                                raise RuntimeError("The Android test device disappeared before restoring a grant.")
                            device_id = next(iter(state.devices))
                            if not state.set_permission(device_id, permission, True):
                                raise RuntimeError(f"Could not restore Android test permission {permission}.")
                            restored_features.add(permission)
                            print(f"Restored only the {permission} permission for Android test.", flush=True)

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
            print("Android pairing instrumentation exceeded its 240-second deadline.", flush=True)
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
            for label, command in (
                ("Android process list", ["adb", "shell", "ps", "-A"]),
                ("Android instrumentation state", [
                    "adb", "shell", "dumpsys", "activity", "instrumentation",
                ]),
            ):
                result = subprocess.run(
                    command, check=False, capture_output=True, text=True, timeout=15,
                )
                print(label + ":", flush=True)
                selected = [
                    line for line in result.stdout.splitlines()
                    if "app.omaapp" in line or "Instrumentation" in line
                    or "instrumentation" in line
                ]
                for line in selected[-80:]:
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
            if assert_reconnect_theme and "INSTRUMENTATION_STATUS: stage=plugin_restart_reconnected" in combined:
                return combined
            raise RuntimeError(f"Android client did not report a paired theme:\n{combined}")
        if updated_theme and "INSTRUMENTATION_STATUS: stage=theme_updated" not in combined:
            raise RuntimeError(f"Android client did not receive a live theme update:\n{combined}")
        if revoke and "INSTRUMENTATION_STATUS: stage=revoked" not in combined:
            raise RuntimeError(f"Android client did not report revocation:\n{combined}")
        if feature_smoke:
            if (not granted_features or disabled_features != set(feature_permissions)
                    or restored_features != set(feature_permissions)):
                raise RuntimeError("Android feature permission transitions did not complete.")
            if "INSTRUMENTATION_STATUS: stage=features_passed" not in combined:
                raise RuntimeError(f"Android client did not finish feature smoke:\n{combined}")
        return combined

    def capture_synced_theme_render() -> None:
        """Capture synced QML surfaces at normal and high Android density."""
        paired_artifacts = ROOT / "ci-artifacts/android-smoke/paired-theme"
        paired_artifacts.mkdir(parents=True, exist_ok=True)
        def dump_ui(name: str, output_dir: Path) -> Path:
            device_path = f"/sdcard/omaapp-{name}.xml"
            local_path = output_dir / f"{name}.xml"
            return dump_accessibility_xml(device_path, local_path)

        def render_style(label: str, output_dir: Path, expected_width: int, expected_height: int) -> None:
            output_dir.mkdir(parents=True, exist_ok=True)
            subprocess.run(["adb", "shell", "am", "force-stop", "app.omaapp"],
                           check=True, timeout=15)
            subprocess.run(
                ["adb", "shell", "am", "start", "-W", "-n",
                 "app.omaapp/app.omaapp.OmaAppActivity"],
                check=True, capture_output=True, text=True, timeout=30,
            )
            time.sleep(8)

            navigation = dump_ui(f"{label}-navigation", output_dir)
            target = subprocess.run(
                [sys.executable, str(ROOT / "scripts/ci/android_accessibility_tap_target.py"),
                 str(navigation), "Style"],
                check=True, capture_output=True, text=True, timeout=15,
            ).stdout.strip()
            x, y = target.split()
            subprocess.run(["adb", "shell", "input", "tap", x, y],
                           check=True, timeout=15)
            time.sleep(2)

            style_ui = dump_ui(f"{label}-style", output_dir)
            style_text = style_ui.read_text(encoding="utf-8")
            shape_samples = all(
                f'content-desc="Rounding power {power}"' in style_text
                for power in ("1.0", "2.0", "4.0")
            )
            if ("Power 4.0" not in style_text or "37°" not in style_text
                    or "3 stops" not in style_text or "14 px base font" not in style_text
                    or not shape_samples):
                raise RuntimeError(
                    f"The {label} synced style screen did not expose rounding_power=4, the 37 degree "
                    "angle, all three gradient stops, the synced font base size, and the power 1/2/4 render samples."
                )
            screenshot = subprocess.run(
                ["adb", "exec-out", "screencap", "-p"],
                check=True, capture_output=True, timeout=20,
            ).stdout
            image_path = output_dir / "style.png"
            image_path.write_bytes(screenshot)
            if len(screenshot) < 1024:
                raise RuntimeError(f"Android returned an empty {label} synced-theme screenshot.")
            verify_style_surface_rendered(
                image_path, require_gradient=True, gradient_angle=37.0
            )

            width = subprocess.run(
                ["adb", "shell", "wm", "size"], check=True, capture_output=True,
                text=True, timeout=15,
            ).stdout
            if f"{expected_width}x{expected_height}" not in width:
                raise RuntimeError(f"Android display size is wrong for {label}: {width.strip()}")

            pid = subprocess.run(
                ["adb", "shell", "pidof", "app.omaapp"],
                check=True, capture_output=True, text=True, timeout=15,
            ).stdout.strip().split()
            if not pid:
                raise RuntimeError(f"OmaApp exited before the {label} theme could be rendered.")
            logcat = subprocess.run(
                ["adb", "logcat", "-d", "-v", "threadtime", f"--pid={pid[0]}", "-t", "10000"],
                check=True, capture_output=True, text=True, timeout=20,
            ).stdout
            (output_dir / "style-logcat.txt").write_text(logcat, encoding="utf-8")

        try:
            render_style("density-160", paired_artifacts, 412, 892)
            high_dpi_artifacts = paired_artifacts / "density-240"
            high_dpi_artifacts.mkdir(parents=True, exist_ok=True)
            subprocess.run(["adb", "shell", "wm", "size", "618x1338"],
                           check=True, timeout=15)
            subprocess.run(["adb", "shell", "wm", "density", "240"],
                           check=True, timeout=15)
            render_style("density-240", high_dpi_artifacts, 618, 1338)
        finally:
            subprocess.run(["adb", "shell", "wm", "size", "reset"],
                           check=False, capture_output=True, text=True, timeout=15)
            subprocess.run(["adb", "shell", "wm", "density", "reset"],
                           check=False, capture_output=True, text=True, timeout=15)

    with tempfile.TemporaryDirectory(prefix="omaapp-pairing-smoke-") as temporary:
        wallpaper_path = Path(temporary) / "ci-wallpaper.png"
        wallpaper_path.write_bytes(_one_pixel_png())
        downloads_directory = Path(temporary) / "downloads"
        shared_files = downloads_directory / "OmaApp"
        shared_files.mkdir(parents=True)
        (shared_files / "from-computer.txt").write_text(
            "from Omarchy computer CI", encoding="utf-8")
        fixture_bin = Path(temporary) / "bin"
        fixture_bin.mkdir()
        fixture_state = Path(temporary) / "fixture-state"
        fixture_state.mkdir()
        clipboard_path = fixture_state / "clipboard.txt"
        clipboard_path.write_text("from computer", encoding="utf-8")
        workspace_path = fixture_state / "workspaces.json"
        workspace_path.write_text(json.dumps([
            {"id": 1, "name": "1", "monitor": "CI", "focused": True, "windows": 2},
            {"id": 2, "name": "2", "monitor": "CI", "focused": False, "windows": 1},
        ]), encoding="utf-8")
        media_path = fixture_state / "media.json"
        media_path.write_text(json.dumps({
            "status": "Playing", "artist": "CI Artist", "title": "Before next",
            "album": "OmaApp", "actions": [],
        }), encoding="utf-8")

        def write_cli(name: str, source: str) -> None:
            executable = fixture_bin / name
            executable.write_text("#!/usr/bin/env python3\n" + source, encoding="utf-8")
            executable.chmod(0o755)

        write_cli("wl-paste", f"""
import pathlib, sys
if sys.argv[1:] != ["--no-newline", "--type", "text/plain;charset=utf-8"]:
    raise SystemExit(2)
sys.stdout.buffer.write(pathlib.Path({str(clipboard_path)!r}).read_bytes())
""")
        write_cli("wl-copy", f"""
import pathlib, sys
if sys.argv[1:] != ["--type", "text/plain;charset=utf-8"]:
    raise SystemExit(2)
pathlib.Path({str(clipboard_path)!r}).write_bytes(sys.stdin.buffer.read())
""")
        write_cli("hyprctl", f"""
import json, pathlib, sys
state = pathlib.Path({str(workspace_path)!r})
workspaces = json.loads(state.read_text(encoding="utf-8"))
if sys.argv[1:] == ["-j", "workspaces"]:
    print(json.dumps(workspaces))
elif len(sys.argv) == 4 and sys.argv[1:3] == ["dispatch", "workspace"]:
    target = int(sys.argv[3])
    if not any(item["id"] == target for item in workspaces):
        raise SystemExit(1)
    for item in workspaces:
        item["focused"] = item["id"] == target
    state.write_text(json.dumps(workspaces), encoding="utf-8")
else:
    raise SystemExit(2)
""")
        write_cli("playerctl", f"""
import json, pathlib, sys
state = pathlib.Path({str(media_path)!r})
media = json.loads(state.read_text(encoding="utf-8"))
if sys.argv[1:] == ["-l"]:
    print("mpv")
elif len(sys.argv) == 6 and sys.argv[1:3] == ["-p", "mpv"] and sys.argv[3:5] == ["metadata", "--format"]:
    template = sys.argv[5]
    if template != "{{{{status}}}}\\n{{{{artist}}}}\\n{{{{title}}}}\\n{{{{album}}}}":
        raise SystemExit(2)
    print("\\n".join(media[key] for key in ("status", "artist", "title", "album")))
elif len(sys.argv) == 4 and sys.argv[1:3] == ["-p", "mpv"]:
    action = sys.argv[3]
    if action not in {{"play-pause", "play", "pause", "next", "previous", "stop"}}:
        raise SystemExit(2)
    media["actions"].append(action)
    if action == "next":
        media["title"] = "After next"
    state.write_text(json.dumps(media), encoding="utf-8")
else:
    raise SystemExit(2)
""")
        config_root = Path(temporary) / "config"
        command_directory = config_root / "omaapp"
        command_directory.mkdir(parents=True)
        marker_path = Path(temporary) / "safe-command-ran.txt"
        command_script = Path(temporary) / "safe-command.py"
        command_script.write_text(
            "import pathlib, sys\n"
            "pathlib.Path(sys.argv[1]).write_text('approved', encoding='utf-8')\n",
            encoding="utf-8",
        )
        (command_directory / "commands.toml").write_text(
            "[[command]]\n"
            'id = "ci-safe"\n'
            'label = "CI approved command"\n'
            f"argv = [{json.dumps(sys.executable)}, {json.dumps(str(command_script))}, "
            f"{json.dumps(str(marker_path))}]\n",
            encoding="utf-8",
        )
        state = service.ServiceState(Path(temporary), "0.0.0.0", 0)
        try:
            with patch.dict(os.environ, {
                    "XDG_DOWNLOAD_DIR": str(downloads_directory),
                    "XDG_CONFIG_HOME": str(config_root),
                    "PATH": str(fixture_bin) + os.pathsep + os.environ.get("PATH", ""),
                }), \
                    patch.object(service, "_advertised_ipv4", return_value="10.0.2.2"), \
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
                    feature_smoke=True,
                )
                if clipboard_path.read_text(encoding="utf-8") != "OmaApp CI phone clipboard":
                    raise RuntimeError("Android did not send clipboard text to the computer.")
                if marker_path.read_text(encoding="utf-8") != "approved":
                    raise RuntimeError("Android did not execute the configured safe command.")
                workspace_state = json.loads(workspace_path.read_text(encoding="utf-8"))
                if not any(item["id"] == 2 and item["focused"] for item in workspace_state):
                    raise RuntimeError("Android did not activate the configured Hyprland workspace.")
                media_state = json.loads(media_path.read_text(encoding="utf-8"))
                if media_state["title"] != "After next" or "next" not in media_state["actions"]:
                    raise RuntimeError("Android MPRIS next action did not advance the test player.")
                if not (shared_files / "from-phone.txt").is_file():
                    raise RuntimeError("The Omarchy plugin did not save the Android-uploaded file.")
                if (shared_files / "from-phone.txt").read_text(encoding="utf-8") != "from OmaApp Android CI":
                    raise RuntimeError("The Omarchy plugin changed the uploaded Android file contents.")
                if len(state.devices) != 1 or not state.pending:
                    raise RuntimeError("The plugin did not retain the approved Android device.")

                capture_synced_theme_render()

                old_fingerprint = state.fingerprint
                old_port = state.port
                state.stop_server()
                theme["name"] = "CI_AfterRestart"
                restarted_state = service.ServiceState(Path(temporary), "0.0.0.0", old_port)
                restarted_state.start_server()
                if len(restarted_state.devices) != 1:
                    raise RuntimeError("The plugin restart did not reload the saved Android trust token.")
                if restarted_state.fingerprint.lower() != old_fingerprint.lower():
                    raise RuntimeError("The plugin restart changed its persisted TLS certificate fingerprint.")
                state = restarted_state
                state.event_hub.publish("theme")
                run_android_instrumentation(
                    state, "", expected_theme="CI_AfterRestart",
                    assert_reconnect_theme="CI_AfterRestart",
                )

                second_qr = qr_payload(state)
                run_android_instrumentation(
                    state, second_qr, expected_theme="CI_AfterRestart", revoke=True,
                )
                if state.devices:
                    raise RuntimeError("The Android client did not revoke its saved device token.")
                if theme_reads < 2:
                    raise RuntimeError("The client did not fetch an updated theme snapshot.")
        finally:
            state.stop_server()
    print("Android pairing, live theme update, clipboard, file transfer, MPRIS, system status, "
          "Hyprland workspace control, safe commands, independent permissions, reconnect, "
          "and revocation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
