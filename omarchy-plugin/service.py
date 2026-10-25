#!/usr/bin/env python3
"""Small, TLS-pinned OmaApp companion service owned by the Omarchy plugin."""

from __future__ import annotations

import argparse
import base64
import ctypes
import hashlib
import hmac
import http.server
import ipaddress
import json
import mimetypes
import os
import re
import select
import secrets
import signal
import socket
import ssl
import stat
import struct
import subprocess
import sys
import tempfile
import threading
import time
import tomllib
from collections import defaultdict, deque
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlsplit

from third_party.qrcodegen import QrCode


PROTOCOL_VERSION = 1
DEFAULT_PORT = 44892
PAIRING_TTL_SECONDS = 180
MAX_JSON_BYTES = 16 * 1024
MAX_WALLPAPER_BYTES = 30 * 1024 * 1024
MAX_CLIPBOARD_BYTES = 64 * 1024
MAX_CLIPBOARD_JSON_BYTES = MAX_CLIPBOARD_BYTES * 6 + 1024
MAX_TRANSFER_BYTES = 100 * 1024 * 1024
DEVICE_ID_CHARS = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_")
DEVICE_NAME_MAX = 80
DEVICE_PERMISSIONS = {
    "clipboard": False,
    "files.send": False,
    "files.receive": False,
    "media.control": False,
    "system.read": False,
    "workspace.control": False,
    "commands": False,
}
TRANSFER_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._()@+-]{0,159}\Z")
MPRIS_PLAYER = re.compile(r"[A-Za-z0-9_.-]{1,128}\Z")
COMMAND_ID = re.compile(r"[a-z][a-z0-9-]{0,47}\Z")
COLOR_KEYS = {
    "accent", "background", "dark_background", "darker_background",
    "lighter_background", "foreground", "dark_foreground", "light_foreground",
    "bright_foreground", "muted", "selection", "red", "blue", "green", "brown",
    "yellow", "orange", "magenta", "cyan", "bright_red", "bright_blue",
    "bright_green", "bright_yellow", "bright_orange", "bright_magenta",
    "bright_cyan", "active_border_color", "active_tab_background",
    "color0", "color1", "color2",
    "color3", "color4", "color5", "color6", "color7", "color8", "color9",
    "color10", "color11", "color12", "color13", "color14", "color15",
    "hyprland_active_border", "hyprland_inactive_border",
}
IMAGE_SUFFIXES = {".avif", ".bmp", ".jpeg", ".jpg", ".png", ".webp"}
COLOR_VALUE = re.compile(r"(?:#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?|rgba\([0-9a-fA-F]{8}\)|rgb\([0-9a-fA-F]{6}\))\Z")
GRADIENT_ANGLE = re.compile(r"(?:^|\s)(-?\d+(?:\.\d+)?)deg(?:\s|$)", re.IGNORECASE)
GRADIENT_COLOR = re.compile(r"(?:#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?|rgba\([0-9a-fA-F]{8}\)|rgb\([0-9a-fA-F]{6}\))")
HYPRLAND_NUMBER = re.compile(r"([+-]?(?:\d+(?:\.\d*)?|\.\d+))(?:px)?\Z", re.IGNORECASE)
SHELL_SPACING_KEYS = {
    "scale", "xxs", "xs", "sm", "md", "lg", "xl", "xxl", "xxxl", "huge",
    "control-gap", "control-padding-x", "control-padding-y", "input-padding-y",
    "control-height", "popup-row-height", "row-gap", "row-padding-x", "label-gap",
    "panel-gap", "panel-padding", "popup-padding", "dropdown-width",
    "searchable-dropdown-width", "number-field-width", "searchable-popup-min-height",
}
SHELL_TOML_KEYS = {
    "bar": {"background", "background-alpha", "text", "active", "scale-with-font", "size-horizontal", "size-vertical"},
    "hyprland": {"active-border", "active-border-foreground"},
    "controls": {
        "normal-color", "normal-fill-alpha", "normal-border", "normal-border-width", "normal-border-alpha",
        "hover-cursor-color", "hover-cursor-fill-alpha", "hover-cursor-border", "hover-cursor-border-width",
        "hover-cursor-border-alpha", "focus-color", "focus-fill-alpha", "focus-border", "focus-border-width",
        "focus-border-alpha", "selected-color", "selected-fill-alpha", "selected-border",
        "selected-border-width", "selected-border-alpha", "pressed-fill-alpha", "selection-fill-alpha",
    },
    "spacing": SHELL_SPACING_KEYS | {"scale-with-font"},
    "font": {
        "base-size", "caption", "body-small", "body", "subtitle", "title", "heading", "display",
        "display-large", "icon-small", "icon", "icon-large",
    },
    "popups": {"background", "background-alpha", "text", "border", "border-alpha", "border-width"},
    "tooltip": {"background", "background-alpha", "text", "border", "border-alpha", "border-width"},
    "notifications": {"background", "background-alpha", "text", "border", "border-alpha", "border-width", "countdown"},
    "launcher": {
        "background", "background-alpha", "text", "border", "border-alpha", "scrim", "scrim-alpha",
        "selected-background", "selected-background-alpha", "selected-text", "selected-border",
        "selected-border-alpha",
    },
    "menu": {
        "background", "background-alpha", "text", "border", "border-alpha", "scrim", "scrim-alpha",
        "selected-background", "selected-background-alpha", "selected-text", "selected-border",
        "selected-border-alpha",
    },
    "polkit": {"background", "background-alpha", "text", "text-error", "border", "border-error", "border-alpha", "scrim", "scrim-alpha", "accent"},
    "lock": {"background", "background-alpha", "text", "placeholder", "text-error", "border", "border-active", "border-error", "border-alpha", "selection", "selection-alpha"},
    "image-picker": {"scrim", "scrim-alpha", "text", "selected-border", "selected-border-alpha", "unselected-border", "unselected-border-alpha"},
}
SHELL_COLOR_KEYS = {
    "background", "text", "active", "accent", "border", "border-error", "border-active",
    "countdown", "placeholder", "selection", "scrim", "selected-background", "selected-text",
    "selected-border", "unselected-border", "normal-color", "normal-border", "hover-cursor-color",
    "hover-cursor-border", "focus-color", "focus-border", "selected-color", "selected-border",
    "active-border", "active-border-foreground", "text-error",
}
SHELL_BOOL_KEYS = {"scale-with-font"}
SHELL_COLOR_REFERENCE = re.compile(r"(?:colors|hyprland)\.[a-z][a-z0-9_-]{0,63}\Z")
SHELL_COLOR_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_-]{0,31}\Z")
MAX_PAIRED_DEVICES = 16
_EVENT_LOCK = threading.Lock()
PRIVATE_IPV4_NETWORKS = tuple(map(ipaddress.ip_network, ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")))


def _qr_svg_data(payload: str) -> str:
    qr = QrCode.encode_text(payload, QrCode.Ecc.MEDIUM)
    border = 4
    size = qr.get_size() + border * 2
    paths = []
    for y in range(qr.get_size()):
        x = 0
        while x < qr.get_size():
            if not qr.get_module(x, y):
                x += 1
                continue
            run_start = x
            while x < qr.get_size() and qr.get_module(x, y):
                x += 1
            run_length = x - run_start
            paths.append(
                f"M{run_start + border} {y + border}h{run_length}v1h-{run_length}z"
            )
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
        f'shape-rendering="crispEdges"><rect width="{size}" height="{size}" fill="#fff"/>'
        f'<path fill="#000" d="{"".join(paths)}"/></svg>'
    )
    return "data:image/svg+xml;base64," + base64.b64encode(svg.encode("utf-8")).decode("ascii")


def _emit(event: dict[str, Any]) -> None:
    with _EVENT_LOCK:
        sys.stdout.write(json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n")
        sys.stdout.flush()


def _state_directory() -> Path:
    root = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local/state"))
    return root / "omarchy" / "omaapp"


def _current_directory() -> Path:
    root = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local/state"))
    return root / "omarchy" / "current"


def _transfer_directory() -> Path:
    configured = os.environ.get("XDG_DOWNLOAD_DIR")
    download_directory = Path(configured).expanduser() if configured else Path.home() / "Downloads"
    return download_directory / "OmaApp"


class EventHub:
    """Coalesce local Omarchy and Hyprland notifications for paired clients."""

    def __init__(self) -> None:
        self._condition = threading.Condition()
        self._revision = 0
        self._events: deque[tuple[int, str]] = deque(maxlen=128)

    @property
    def revision(self) -> int:
        with self._condition:
            return self._revision

    def publish(self, kind: str) -> None:
        if kind not in {"theme", "features"}:
            return
        with self._condition:
            self._revision += 1
            self._events.append((self._revision, kind))
            self._condition.notify_all()

    def wait_after(self, revision: int, timeout: float = 25.0) -> dict[str, Any]:
        with self._condition:
            self._condition.wait_for(lambda: self._revision > revision, timeout=timeout)
            for event_revision, kind in self._events:
                if event_revision > revision:
                    return {"revision": event_revision, "kind": kind}
            return {"revision": self._revision, "kind": None}


def _run_command(arguments: list[str], *, timeout: float = 2.0,
                 input_data: bytes | None = None, output_limit: int = 64 * 1024) -> bytes | None:
    try:
        result = subprocess.run(
            arguments,
            check=False,
            capture_output=True,
            input=input_data,
            timeout=timeout,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0 or len(result.stdout) > output_limit:
        return None
    return result.stdout


def _safe_transfer_name(value: str) -> str | None:
    if value in {".", ".."} or not TRANSFER_NAME.fullmatch(value):
        return None
    return value


def _system_status() -> dict[str, Any]:
    memory: dict[str, int] = {}
    try:
        for line in Path("/proc/meminfo").read_text(encoding="ascii").splitlines():
            key, _, raw = line.partition(":")
            if key in {"MemTotal", "MemAvailable"}:
                value = raw.strip().split()[0]
                if value.isdigit():
                    memory[key] = int(value) * 1024
    except (OSError, UnicodeError, IndexError):
        pass

    battery = None
    for candidate in Path("/sys/class/power_supply").glob("*"):
        try:
            if (candidate / "type").read_text(encoding="ascii").strip() != "Battery":
                continue
            capacity = (candidate / "capacity").read_text(encoding="ascii").strip()
            status = (candidate / "status").read_text(encoding="ascii").strip()
            if capacity.isdigit() and 0 <= int(capacity) <= 100:
                battery = {"percent": int(capacity), "status": _safe_string(status, 24)}
            break
        except OSError:
            continue

    try:
        load = [round(float(value), 2) for value in os.getloadavg()]
        load_average = load if all(0 <= value < 1_000_000 for value in load) else None
    except (AttributeError, OSError):
        load_average = None
    return {
        "memoryTotalBytes": memory.get("MemTotal"),
        "memoryAvailableBytes": memory.get("MemAvailable"),
        "loadAverage": load_average,
        "battery": battery,
        "capturedAt": int(time.time()),
    }


def _hyprland_workspaces() -> list[dict[str, Any]]:
    raw = _run_command(["hyprctl", "-j", "workspaces"], output_limit=256 * 1024)
    if raw is None:
        return []
    try:
        value = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return []
    if not isinstance(value, list):
        return []
    result = []
    for workspace in value[:64]:
        if not isinstance(workspace, dict):
            continue
        workspace_id = workspace.get("id")
        if isinstance(workspace_id, bool) or not isinstance(workspace_id, int) or not 0 <= workspace_id <= 1000:
            continue
        result.append({
            "id": workspace_id,
            "name": _safe_string(workspace.get("name"), 80),
            "monitor": _safe_string(workspace.get("monitor"), 80),
            "focused": workspace.get("focused") is True,
            "windows": _safe_number(workspace.get("windows"), 0, 10000),
        })
    return result


def _mpris_players() -> list[dict[str, Any]]:
    output = _run_command(["playerctl", "-l"], output_limit=16 * 1024)
    if output is None:
        return []
    result = []
    for player in output.decode("utf-8", errors="replace").splitlines()[:16]:
        player = player.strip()
        if not MPRIS_PLAYER.fullmatch(player):
            continue
        metadata = _run_command(
            ["playerctl", "-p", player, "metadata", "--format",
             "{{status}}\n{{artist}}\n{{title}}\n{{album}}"],
            output_limit=2048,
        )
        if metadata is None:
            continue
        fields = metadata.decode("utf-8", errors="replace").splitlines()
        fields += [""] * (4 - len(fields))
        result.append({
            "player": player,
            "status": fields[0][:24],
            "artist": _safe_string(fields[1], 160),
            "title": _safe_string(fields[2], 160),
            "album": _safe_string(fields[3], 160),
        })
    return result


def _user_commands() -> list[dict[str, Any]]:
    config_root = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    path = config_root / "omaapp" / "commands.toml"
    try:
        if path.stat().st_size > 64 * 1024:
            return []
        with path.open("rb") as stream:
            data = tomllib.load(stream)
    except (OSError, tomllib.TOMLDecodeError):
        return []
    raw_commands = data.get("command")
    if not isinstance(raw_commands, list):
        return []
    result = []
    seen_ids: set[str] = set()
    for command in raw_commands[:16]:
        if not isinstance(command, dict):
            continue
        command_id = command.get("id")
        label = _safe_string(command.get("label"), 48)
        argv = command.get("argv")
        if not isinstance(command_id, str) or not COMMAND_ID.fullmatch(command_id) \
                or command_id in seen_ids or not label or not isinstance(argv, list) \
                or not 1 <= len(argv) <= 16:
            continue
        if any(not isinstance(argument, str) or len(argument) > 256 or not argument.isprintable()
               for argument in argv):
            continue
        executable = Path(argv[0])
        if not executable.is_absolute() or not executable.is_file() or not os.access(executable, os.X_OK):
            continue
        seen_ids.add(command_id)
        result.append({"id": command_id, "label": label, "argv": argv})
    return result


def _run_user_command(command_id: str) -> bool:
    if not COMMAND_ID.fullmatch(command_id):
        return False
    command = next((item for item in _user_commands() if item["id"] == command_id), None)
    if command is None:
        return False
    try:
        process = subprocess.Popen(
            command["argv"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            close_fds=True,
            start_new_session=True,
        )
        try:
            return process.wait(timeout=3.0) == 0
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=0.5)
            except (OSError, subprocess.TimeoutExpired):
                process.kill()
            return False
    except OSError:
        return False


def _private_directory(path: Path) -> None:
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) \
        | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
    descriptor = os.open(path, flags)
    try:
        os.fchmod(descriptor, 0o700)
    finally:
        os.close(descriptor)


def _atomic_json(path: Path, value: Any) -> None:
    _private_directory(path.parent)
    temporary = path.with_name(path.name + ".tmp-" + secrets.token_hex(6))
    try:
        with temporary.open("x", encoding="utf-8") as stream:
            os.chmod(temporary, 0o600)
            json.dump(value, stream, ensure_ascii=False, separators=(",", ":"))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        temporary.unlink(missing_ok=True)


def _load_json(path: Path, fallback: Any) -> Any:
    try:
        with path.open("r", encoding="utf-8") as stream:
            value = json.load(stream)
        return value
    except (OSError, ValueError, json.JSONDecodeError):
        return fallback


def _safe_string(value: Any, limit: int = 160) -> str | None:
    if not isinstance(value, str):
        return None
    value = "".join(char for char in value.strip() if char.isprintable())
    return value[:limit] or None


def _safe_number(value: Any, lower: float = 0, upper: float = 1000) -> int | float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    if not lower <= value <= upper:
        return None
    return value


def _safe_color_value(key: str, value: str) -> bool:
    if key not in {"hyprland_active_border", "hyprland_inactive_border"}:
        return COLOR_VALUE.fullmatch(value) is not None
    colors = GRADIENT_COLOR.findall(value)
    remainder = GRADIENT_COLOR.sub("", value)
    remainder = GRADIENT_ANGLE.sub("", remainder).strip()
    return 1 <= len(colors) <= 10 and not remainder


def _hyprland_value(option: str) -> Any:
    try:
        result = subprocess.run(
            ["hyprctl", "-j", "getoption", option],
            check=False,
            capture_output=True,
            text=True,
            timeout=0.5,
        )
        if result.returncode != 0 or len(result.stdout) > 8192:
            return None
        value = json.loads(result.stdout)
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError):
        return None
    if not isinstance(value, dict):
        return None
    for key in ("int", "float", "str", "custom", "gradient", "css"):
        candidate = value.get(key)
        if isinstance(candidate, (int, float)) and not isinstance(candidate, bool):
            return candidate
        if isinstance(candidate, str):
            return _safe_string(candidate, 256)
    return None


def _hyprland_number(option: str, lower: float = 0, upper: float = 1000) -> int | float | None:
    value = _hyprland_value(option)
    number = _safe_number(value, lower, upper)
    if number is not None:
        return number
    if not isinstance(value, str):
        return None
    parts = value.split()
    if not 1 <= len(parts) <= 4:
        return None
    values: list[float] = []
    for part in parts:
        match = HYPRLAND_NUMBER.fullmatch(part)
        if not match:
            return None
        try:
            candidate = float(match.group(1))
        except ValueError:
            return None
        if not lower <= candidate <= upper:
            return None
        values.append(candidate)
    result = sum(values) / len(values)
    return int(result) if result.is_integer() else result


def _normalize_hyprland_gradient(value: Any) -> str | None:
    if not isinstance(value, str) or len(value) > 256:
        return None
    colors = []
    angle = None
    for token in value.split():
        if re.fullmatch(r"-?\d+(?:\.\d+)?deg", token, re.IGNORECASE):
            try:
                degrees = float(token[:-3])
            except ValueError:
                return None
            if not -360 <= degrees <= 360:
                return None
            angle = f"{degrees:g}deg"
        elif re.fullmatch(r"rgba\([0-9a-fA-F]{8}\)|rgb\([0-9a-fA-F]{6}\)|#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?", token):
            colors.append(token)
        elif re.fullmatch(r"[0-9a-fA-F]{8}", token):
            # hyprctl's `gradient` field serializes packed colors as AARRGGBB;
            # the Qt client consumes the CSS-style RRGGBBAA spelling.
            alpha, red, green, blue = token[:2], token[2:4], token[4:6], token[6:8]
            colors.append(f"rgba({red}{green}{blue}{alpha})")
        elif re.fullmatch(r"[0-9a-fA-F]{6}", token):
            colors.append("#" + token)
        else:
            return None
    if not 1 <= len(colors) <= 10:
        return None
    return " ".join(colors + ([angle] if angle else []))


def _safe_shell_setting(key: str, value: Any) -> int | float | str | bool | None:
    if key in SHELL_BOOL_KEYS:
        return value if isinstance(value, bool) else None
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        upper = 1 if key.endswith("-alpha") else 1000
        return _safe_number(value, 0, upper)
    if not isinstance(value, str) or len(value) > 160 or not value.isprintable():
        return None
    value = value.strip()
    if key not in SHELL_COLOR_KEYS:
        return None
    if SHELL_COLOR_REFERENCE.fullmatch(value) or value in {"transparent", "none"}:
        return value
    if _safe_color_value("hyprland_active_border", value):
        return value
    if SHELL_COLOR_NAME.fullmatch(value):
        return value
    return None


def _hyprland_animations() -> dict[str, list[dict[str, Any]]]:
    raw = _run_command(["hyprctl", "-j", "animations"], timeout=1.0, output_limit=512 * 1024)
    if raw is None:
        return {"items": [], "beziers": []}
    try:
        value = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return {"items": [], "beziers": []}
    if not isinstance(value, list) or len(value) != 2:
        return {"items": [], "beziers": []}

    items: list[dict[str, Any]] = []
    if isinstance(value[0], list):
        for animation in value[0][:128]:
            if not isinstance(animation, dict):
                continue
            name = animation.get("name")
            if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_.-]{0,63}", name):
                continue
            speed = _safe_number(animation.get("speed"), 0, 100)
            items.append({
                "name": name,
                "overridden": animation.get("overridden") is True,
                "enabled": animation.get("enabled") if isinstance(animation.get("enabled"), bool) else None,
                "speed": speed,
                "bezier": _safe_string(animation.get("bezier"), 64),
                "style": _safe_string(animation.get("style"), 80),
            })

    beziers: list[dict[str, Any]] = []
    if isinstance(value[1], list):
        for bezier in value[1][:64]:
            if not isinstance(bezier, dict):
                continue
            name = bezier.get("name")
            coordinates = {key: _safe_number(bezier.get(key), 0, 1) for key in ("X0", "Y0", "X1", "Y1")}
            if isinstance(name, str) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_.-]{0,63}", name) \
                    and all(number is not None for number in coordinates.values()):
                beziers.append({"name": name, **coordinates})
    return {"items": items, "beziers": beziers}


def _read_toml(path: Path) -> dict[str, Any]:
    try:
        if path.stat().st_size > 256 * 1024:
            return {}
        with path.open("rb") as stream:
            value = tomllib.load(stream)
        return value if isinstance(value, dict) else {}
    except (OSError, tomllib.TOMLDecodeError):
        return {}


def _theme_snapshot() -> tuple[dict[str, Any], Path | None]:
    current = _current_directory()
    theme_directory = current / "theme"
    colors_file = theme_directory / "colors.toml"
    shell_file = theme_directory / "shell.toml"
    theme_name = None
    theme_name_path = current / "theme.name"
    try:
        if theme_name_path.is_file() and theme_name_path.stat().st_size <= 1024:
            theme_name = _safe_string(theme_name_path.read_text(encoding="utf-8"), 80)
    except (OSError, UnicodeError):
        pass
    if not theme_name:
        try:
            theme_name = _safe_string(theme_directory.resolve().name, 80)
        except (OSError, RuntimeError):
            theme_name = None

    raw_colors = _read_toml(colors_file)
    colors: dict[str, str] = {}
    for key, value in raw_colors.items():
        if key not in COLOR_KEYS or not isinstance(value, str) or len(value) > 80:
            continue
        value = value.strip()
        if _safe_color_value(key, value):
            colors[key] = value

    raw_shell = _read_toml(shell_file)
    shell: dict[str, dict[str, int | float | str | bool]] = {}
    for section, values in raw_shell.items():
        allowed_keys = SHELL_TOML_KEYS.get(section)
        if not allowed_keys:
            continue
        if not isinstance(values, dict):
            continue
        safe_values: dict[str, int | float | str | bool] = {}
        for key, value in values.items():
            if not isinstance(key, str) or key not in allowed_keys:
                continue
            safe_value = _safe_shell_setting(key, value)
            if safe_value is not None:
                safe_values[key] = safe_value
        if safe_values:
            shell[section] = safe_values

    active_border_value = _hyprland_value("general:col.active_border")
    active_border = _normalize_hyprland_gradient(active_border_value)
    inactive_border = _normalize_hyprland_gradient(
        _hyprland_value("general:col.inactive_border")
    )
    angle_match = GRADIENT_ANGLE.search(active_border_value) \
        if isinstance(active_border_value, str) else None
    active_border_angle = None
    if angle_match:
        try:
            parsed_angle = float(angle_match.group(1))
            if -360 <= parsed_angle <= 360:
                active_border_angle = parsed_angle % 360
        except ValueError:
            pass

    geometry = {
        "rounding": _hyprland_number("decoration:rounding"),
        "roundingPower": _hyprland_number("decoration:rounding_power", 1, 10),
        "borderWidth": _hyprland_number("general:border_size"),
        "activeBorder": active_border,
        "activeBorderAngle": active_border_angle,
        "inactiveBorder": inactive_border,
        "gapsIn": _hyprland_number("general:gaps_in"),
        "gapsOut": _hyprland_number("general:gaps_out"),
    }

    wallpaper: dict[str, Any] | None = None
    wallpaper_path: Path | None = None
    candidate = current / "background"
    try:
        resolved = candidate.resolve(strict=True)
        metadata = resolved.stat()
        if resolved.is_file() and resolved.suffix.lower() in IMAGE_SUFFIXES \
                and metadata.st_size <= MAX_WALLPAPER_BYTES:
            wallpaper_path = resolved
            fingerprint = hashlib.sha256(
                f"{metadata.st_size}:{metadata.st_mtime_ns}:{resolved}".encode("utf-8")
            ).hexdigest()[:32]
            wallpaper = {
                "id": fingerprint,
                "name": _safe_string(resolved.name, 120),
                "size": metadata.st_size,
                "mimeType": mimetypes.guess_type(resolved.name)[0] or "application/octet-stream",
            }
    except OSError:
        pass

    return ({
        "protocolVersion": PROTOCOL_VERSION,
        "hostName": _safe_string(socket.gethostname(), 80) or "Omarchy",
        "theme": {"name": theme_name, "colors": colors, "shell": shell},
        "hyprland": geometry,
        "animations": _hyprland_animations(),
        "wallpaper": wallpaper,
        "capturedAt": int(time.time()),
    }, wallpaper_path)


def _advertised_ipv4() -> str:
    # UDP connect selects the source address for the default route without
    # sending a packet. The service still only accepts TLS-pinned protocol data.
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 9))
        address = probe.getsockname()[0]
        parsed = ipaddress.ip_address(address)
        if parsed.version == 4 and any(parsed in network for network in PRIVATE_IPV4_NETWORKS):
            return address
    except OSError:
        pass
    finally:
        probe.close()
    try:
        addresses = socket.gethostbyname_ex(socket.gethostname())[2]
    except OSError:
        addresses = []
    for address in addresses:
        try:
            parsed = ipaddress.ip_address(address)
            if parsed.version == 4 and any(parsed in network for network in PRIVATE_IPV4_NETWORKS):
                return address
        except ValueError:
            continue
    return ""


def _advertised_ipv4_addresses() -> list[str]:
    """Return reachable RFC1918 IPv4 candidates from all active interfaces."""
    addresses: list[str] = []

    def add(value: object) -> None:
        if not isinstance(value, str) or value in addresses:
            return
        try:
            parsed = ipaddress.ip_address(value)
        except ValueError:
            return
        if parsed.version == 4 and any(parsed in network for network in PRIVATE_IPV4_NETWORKS):
            addresses.append(str(parsed))

    # Keep the address selected by the active route first for QR pairing and
    # for systems where iproute2 is unavailable.
    add(_advertised_ipv4())
    if len(addresses) >= 8:
        return addresses
    try:
        result = subprocess.run(
            ["ip", "-j", "-4", "addr", "show", "up"],
            check=True,
            capture_output=True,
            text=True,
            timeout=2,
        )
        interfaces = json.loads(result.stdout)
    except (OSError, subprocess.SubprocessError, json.JSONDecodeError):
        return addresses
    if not isinstance(interfaces, list):
        return addresses
    for interface in interfaces:
        if not isinstance(interface, dict) or "UP" not in interface.get("flags", []):
            continue
        for info in interface.get("addr_info", []):
            if not isinstance(info, dict) or info.get("family") != "inet" \
                    or info.get("scope") != "global":
                continue
            add(info.get("local"))
            if len(addresses) >= 8:
                return addresses
    return addresses


MDNS_GROUP = "224.0.0.251"
MDNS_PORT = 5353
MDNS_SERVICE_TYPE = "_omaapp._tcp.local."
MAX_MDNS_ADDRESSES = 8


def _dns_name_wire(name: str) -> bytes:
    labels = name.rstrip(".").split(".")
    encoded = bytearray()
    for label in labels:
        value = label.encode("ascii")
        if not value or len(value) > 63:
            raise ValueError("invalid DNS-SD label")
        encoded.append(len(value))
        encoded.extend(value)
    encoded.append(0)
    return bytes(encoded)


def _dns_read_name(packet: bytes, offset: int) -> tuple[str, int]:
    labels: list[str] = []
    cursor = offset
    resume_offset: int | None = None
    visited: set[int] = set()
    while cursor < len(packet):
        size = packet[cursor]
        if size & 0xC0 == 0xC0:
            if cursor + 1 >= len(packet):
                raise ValueError("truncated DNS name pointer")
            pointer = ((size & 0x3F) << 8) | packet[cursor + 1]
            if pointer >= len(packet) or pointer in visited:
                raise ValueError("invalid DNS name pointer")
            visited.add(pointer)
            if resume_offset is None:
                resume_offset = cursor + 2
            cursor = pointer
            continue
        if size & 0xC0 or size > 63:
            raise ValueError("invalid DNS label size")
        cursor += 1
        if size == 0:
            return ".".join(labels).lower() + ".", resume_offset or cursor
        if cursor + size > len(packet):
            raise ValueError("truncated DNS label")
        labels.append(packet[cursor:cursor + size].decode("ascii").lower())
        cursor += size
    raise ValueError("unterminated DNS name")


def _mdns_questions_match(packet: bytes, instance_name: str, target_name: str) -> bool:
    try:
        if len(packet) < 12:
            return False
        _transaction, flags, questions, _answers, _authorities, _additionals = \
            struct.unpack_from("!HHHHHH", packet)
        if flags & 0x8000 or questions < 1:
            return False
        offset = 12
        names = {MDNS_SERVICE_TYPE, instance_name.lower(), target_name.lower(),
                 "_services._dns-sd._udp.local."}
        for _ in range(min(questions, 32)):
            name, offset = _dns_read_name(packet, offset)
            if offset + 4 > len(packet):
                return False
            offset += 4
            if name in names:
                return True
    except (UnicodeDecodeError, ValueError, struct.error):
        return False
    return False


def _mdns_announcement(instance_name: str, target_name: str, address: str | list[str],
                       port: int, fingerprint: str, ttl: int = 120) -> bytes:
    raw_addresses = [address] if isinstance(address, str) else address
    address_bytes: list[bytes] = []
    for value in raw_addresses:
        parsed = ipaddress.ip_address(value)
        if parsed.version != 4:
            raise ValueError("invalid OmaApp DNS-SD service data")
        if parsed.packed not in address_bytes:
            address_bytes.append(parsed.packed)
    if not address_bytes or len(address_bytes) > MAX_MDNS_ADDRESSES \
            or not 1 <= port <= 65535 or not 0 <= ttl <= 4500 \
            or not re.fullmatch(r"[a-f0-9]{64}", fingerprint):
        raise ValueError("invalid OmaApp DNS-SD service data")

    def record(name: str, record_type: int, data: bytes, cache_flush: bool = True) -> bytes:
        rr_class = 0x8001 if cache_flush else 1
        return (_dns_name_wire(name) + struct.pack("!HHIH", record_type, rr_class, ttl, len(data))
                + data)

    ptr = record(MDNS_SERVICE_TYPE, 12, _dns_name_wire(instance_name), cache_flush=False)
    srv = record(instance_name, 33, struct.pack("!HHH", 0, 0, port) + _dns_name_wire(target_name))
    txt_fields = [f"fingerprint={fingerprint}".encode("ascii"), b"protocol=1"]
    txt_data = b"".join(bytes([len(field)]) + field for field in txt_fields)
    txt = record(instance_name, 16, txt_data)
    address_records = b"".join(record(target_name, 1, packed) for packed in address_bytes)
    header = struct.pack("!HHHHHH", 0, 0x8400, 0, 3 + len(address_bytes), 0, 0)
    return header + ptr + srv + txt + address_records


class MdnsAdvertiser:
    """Small DNS-SD responder for the pinned OmaApp service; it advertises no credentials."""

    def __init__(self, data_provider, *, address_provider=None,
                 multicast_group: str = MDNS_GROUP,
                 multicast_port: int = MDNS_PORT,
                 multicast_interface: str = "0.0.0.0"):
        group = ipaddress.ip_address(multicast_group)
        interface = ipaddress.ip_address(multicast_interface)
        if (group.version != 4 or not group.is_multicast or not 1 <= multicast_port <= 65535
                or interface.version != 4):
            raise ValueError("invalid OmaApp multicast destination")
        self.data_provider = data_provider
        self.address_provider = address_provider or (lambda: [self.data_provider()[0]])
        self.multicast_group = str(group)
        self.multicast_port = multicast_port
        self.multicast_interface = str(interface)
        self.stop_event = threading.Event()
        self.socket: socket.socket | None = None
        self.thread: threading.Thread | None = None
        self.instance_name = ""
        self.target_name = ""
        self.last_addresses: list[str] = []
        self.last_port = 0
        self.last_fingerprint = ""
        self.announce_lock = threading.Lock()
        self.memberships: set[str] = set()

    def start(self) -> None:
        address, port, fingerprint = self.data_provider()
        suffix = fingerprint[:12]
        self.instance_name = f"OmaApp-{suffix}.{MDNS_SERVICE_TYPE}"
        self.target_name = f"omaapp-{suffix}.local."
        addresses = self._current_addresses()
        # Do not seed DNS-SD with an old route-selected IP when no active
        # private IPv4 interface is available.
        _mdns_announcement(self.instance_name, self.target_name, addresses, port, fingerprint)

        listener = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        try:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(("", self.multicast_port))
            self._refresh_memberships(listener, addresses)
            listener.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)
            listener.settimeout(1.0)
        except OSError:
            listener.close()
            self.memberships.clear()
            raise
        self.socket = listener
        self.thread = threading.Thread(target=self._serve, name="omaapp-mdns", daemon=True)
        self.thread.start()
        self.announce()

    def announce(self, goodbye: bool = False) -> None:
        with self.announce_lock:
            listener = self.socket
            if listener is None:
                return
            if goodbye:
                self._send_announcement(listener, self.last_addresses, self.last_port,
                                        self.last_fingerprint, ttl=0)
                self.last_addresses = []
                self.last_port = 0
                self.last_fingerprint = ""
                return
            try:
                _address, port, fingerprint = self.data_provider()
                addresses = self._current_addresses()
                changed = (set(addresses) != set(self.last_addresses)
                           or port != self.last_port
                           or fingerprint != self.last_fingerprint)
                if self.last_addresses and changed:
                    # Expire the previous RRset before joining the new routes,
                    # including when all active interfaces have disappeared.
                    self._send_announcement(listener, self.last_addresses, self.last_port,
                                            self.last_fingerprint, ttl=0)
                    self.last_addresses = []
                    self.last_port = 0
                    self.last_fingerprint = ""
                self._refresh_memberships(listener, addresses)
                self._send_announcement(listener, addresses, port, fingerprint, ttl=120)
                self.last_addresses = list(addresses)
                self.last_port = port
                self.last_fingerprint = fingerprint
            except (OSError, ValueError):
                return

    def _send_announcement(self, listener: socket.socket, addresses: list[str],
                           port: int, fingerprint: str, *, ttl: int) -> None:
        if not addresses or port < 1 or not fingerprint:
            return
        packet = _mdns_announcement(self.instance_name, self.target_name, addresses,
                                    port, fingerprint, ttl=ttl)
        interfaces = [self.multicast_interface] if self.multicast_interface != "0.0.0.0" \
            else addresses
        sent = 0
        for interface in interfaces:
            try:
                listener.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF,
                                    socket.inet_aton(interface))
                listener.sendto(packet, (self.multicast_group, self.multicast_port))
                sent += 1
            except OSError:
                continue
        if sent == 0 and ttl != 0:
            raise OSError("DNS-SD announcement failed on all active interfaces")

    def _current_addresses(self) -> list[str]:
        try:
            addresses = self.address_provider()
        except (OSError, ValueError, TypeError):
            addresses = []
        if isinstance(addresses, str):
            addresses = [addresses]
        result = []
        for address in addresses or []:
            if not isinstance(address, str) or address in result:
                continue
            try:
                parsed = ipaddress.ip_address(address)
            except ValueError:
                continue
            if parsed.version == 4 and any(
                parsed in network for network in PRIVATE_IPV4_NETWORKS
            ):
                result.append(str(parsed))
            if len(result) >= MAX_MDNS_ADDRESSES:
                break
        return result

    def _refresh_memberships(self, listener: socket.socket, addresses: list[str]) -> None:
        desired = {self.multicast_interface} if self.multicast_interface != "0.0.0.0" \
            else set(addresses)
        for interface in self.memberships - desired:
            membership = socket.inet_aton(self.multicast_group) + socket.inet_aton(interface)
            try:
                listener.setsockopt(socket.IPPROTO_IP, socket.IP_DROP_MEMBERSHIP, membership)
            except OSError:
                pass
            self.memberships.discard(interface)
        join_error: OSError | None = None
        for interface in desired - self.memberships:
            membership = socket.inet_aton(self.multicast_group) + socket.inet_aton(interface)
            try:
                listener.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, membership)
                self.memberships.add(interface)
            except OSError as error:
                join_error = error
        if not self.memberships:
            if join_error is not None:
                raise join_error
            raise OSError("no active IPv4 interface is available for DNS-SD")

    def _serve(self) -> None:
        next_announce = time.monotonic() + 60.0
        while not self.stop_event.is_set():
            listener = self.socket
            if listener is None:
                return
            try:
                packet, _source = listener.recvfrom(4096)
            except socket.timeout:
                if time.monotonic() >= next_announce:
                    self.announce()
                    next_announce = time.monotonic() + 60.0
                continue
            except OSError:
                return
            if _mdns_questions_match(packet, self.instance_name, self.target_name):
                self.announce()
                next_announce = time.monotonic() + 60.0

    def stop(self) -> None:
        self.announce(goodbye=True)
        self.stop_event.set()
        listener, self.socket = self.socket, None
        if listener is not None:
            try:
                listener.close()
            except OSError:
                pass
        self.memberships.clear()
        if self.thread is not None and self.thread.is_alive():
            self.thread.join(timeout=2)


class ServiceState:
    def __init__(self, data_dir: Path, bind: str = "0.0.0.0", port: int = DEFAULT_PORT):
        self.data_dir = data_dir
        self.bind = bind
        self.port = port
        self.cert_path = data_dir / "server.crt"
        self.key_path = data_dir / "server.key"
        self.devices_path = data_dir / "devices.json"
        self.devices = _load_json(self.devices_path, {})
        if not isinstance(self.devices, dict):
            self.devices = {}
        self.pending: dict[str, dict[str, Any]] = {}
        self.pair_secret: str | None = None
        self.pair_expires = 0
        self.pair_attempts: dict[str, deque[float]] = defaultdict(deque)
        self.rate_history: dict[str, deque[float]] = defaultdict(deque)
        self.lock = threading.RLock()
        self.server: http.server.ThreadingHTTPServer | None = None
        self.server_thread: threading.Thread | None = None
        self.mdns_advertiser: MdnsAdvertiser | None = None
        self.advertised_address = ""
        self.wallpaper_cache: tuple[int, Path | None] | None = None
        self.event_hub = EventHub()
        self.monitor_stop = threading.Event()
        self.monitor_threads: list[threading.Thread] = []

    @property
    def fingerprint(self) -> str:
        der = subprocess.run(
            ["openssl", "x509", "-in", str(self.cert_path), "-outform", "DER"],
            check=True,
            capture_output=True,
            timeout=5,
        ).stdout
        return hashlib.sha256(der).hexdigest()

    def ensure_certificate(self, san_ip: str) -> None:
        _private_directory(self.data_dir)
        if self.cert_path.is_file() and self.key_path.is_file():
            try:
                self.cert_path.chmod(0o600)
                self.key_path.chmod(0o600)
                self.fingerprint
                return
            except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
                self.cert_path.unlink(missing_ok=True)
                self.key_path.unlink(missing_ok=True)
        certificate_tmp = self.data_dir / ("server.crt.tmp-" + secrets.token_hex(4))
        key_tmp = self.data_dir / ("server.key.tmp-" + secrets.token_hex(4))
        try:
            subprocess.run(
                [
                    "openssl", "req", "-x509", "-newkey", "ec",
                    "-pkeyopt", "ec_paramgen_curve:P-256", "-sha256", "-days", "3650",
                    "-nodes", "-keyout", str(key_tmp), "-out", str(certificate_tmp),
                    "-subj", "/CN=OmaApp-Omarchy",
                    "-addext", f"subjectAltName=IP:{san_ip}",
                ],
                check=True,
                capture_output=True,
                timeout=20,
            )
            os.chmod(key_tmp, 0o600)
            os.chmod(certificate_tmp, 0o600)
            os.replace(key_tmp, self.key_path)
            os.replace(certificate_tmp, self.cert_path)
        finally:
            key_tmp.unlink(missing_ok=True)
            certificate_tmp.unlink(missing_ok=True)

    def start_server(self) -> int:
        with self.lock:
            if self.server is not None:
                return int(self.server.server_address[1])
            advertised = _advertised_ipv4() if self.bind == "0.0.0.0" else self.bind
            if not advertised or (self.bind == "0.0.0.0" and ipaddress.ip_address(advertised).is_loopback):
                raise OSError("no reachable local IPv4 address is available")
            self.advertised_address = advertised
            self.ensure_certificate(advertised)
            state = self

            class RequestHandler(Handler):
                service_state = state

            server = BoundedThreadingHTTPServer((self.bind, self.port), RequestHandler)
            server.daemon_threads = True
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            context.load_cert_chain(str(self.cert_path), str(self.key_path))
            server.socket = context.wrap_socket(server.socket, server_side=True)
            self.server = server
            self.server_thread = threading.Thread(
                target=server.serve_forever,
                name="omaapp-https",
                daemon=True,
            )
            self.server_thread.start()
            self.port = int(server.server_address[1])
            self.start_monitors()
            if self.bind == "0.0.0.0" and self.refresh_advertised_address():
                try:
                    self.mdns_advertiser = MdnsAdvertiser(
                        self._mdns_data, address_provider=_advertised_ipv4_addresses)
                    self.mdns_advertiser.start()
                except (OSError, ValueError) as error:
                    if self.mdns_advertiser is not None:
                        self.mdns_advertiser.stop()
                    self.mdns_advertiser = None
                    print(f"OmaApp mDNS discovery unavailable: {type(error).__name__}",
                          file=sys.stderr, flush=True)
            return self.port

    def _mdns_data(self) -> tuple[str, int, str]:
        address = self.refresh_advertised_address()
        with self.lock:
            return self.advertised_address, self.port, self.fingerprint

    def refresh_advertised_address(self) -> str:
        address = _advertised_ipv4() if self.bind == "0.0.0.0" else self.bind
        try:
            is_local = bool(address) and any(ipaddress.ip_address(address) in network
                                             for network in PRIVATE_IPV4_NETWORKS)
        except ValueError:
            is_local = False
        with self.lock:
            if is_local:
                self.advertised_address = address
            return self.advertised_address

    def start_monitors(self) -> None:
        if any(thread.is_alive() for thread in self.monitor_threads):
            return
        self.monitor_stop.clear()
        self.monitor_threads = []
        monitors = [self.watch_theme_files, self.watch_hyprland_events]
        for target in monitors:
            thread = threading.Thread(target=target, name=f"omaapp-{target.__name__}", daemon=True)
            self.monitor_threads.append(thread)
            thread.start()

    def stop_monitors(self) -> None:
        self.monitor_stop.set()
        threads, self.monitor_threads = self.monitor_threads, []
        for thread in threads:
            if thread.is_alive() and thread is not threading.current_thread():
                thread.join(timeout=2)

    def watch_theme_files(self) -> None:
        try:
            libc = ctypes.CDLL(None, use_errno=True)
            init = libc.inotify_init1
            init.argtypes = [ctypes.c_int]
            init.restype = ctypes.c_int
            add_watch = libc.inotify_add_watch
            add_watch.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_uint32]
            add_watch.restype = ctypes.c_int
            descriptor = init(os.O_NONBLOCK | os.O_CLOEXEC)
        except (AttributeError, OSError):
            return
        if descriptor < 0:
            return

        mask = 0x00000002 | 0x00000004 | 0x00000008 | 0x00000040 | 0x00000080 \
            | 0x00000100 | 0x00000200 | 0x00000400 | 0x00000800
        watched: set[str] = set()

        def add_existing_directories() -> None:
            for directory in (_current_directory().parent, _current_directory(), _current_directory() / "theme"):
                try:
                    resolved = str(directory.resolve(strict=True))
                    if resolved not in watched and directory.is_dir():
                        if add_watch(descriptor, os.fsencode(resolved), mask) >= 0:
                            watched.add(resolved)
                except (OSError, RuntimeError):
                    continue

        try:
            add_existing_directories()
            while not self.monitor_stop.is_set():
                try:
                    readable, _, _ = select.select([descriptor], [], [], 1.0)
                    if readable:
                        os.read(descriptor, 64 * 1024)
                        self.event_hub.publish("theme")
                        # A theme switch can retarget the `current` symlink.
                        add_existing_directories()
                except (OSError, ValueError):
                    return
        finally:
            os.close(descriptor)

    def watch_hyprland_events(self) -> None:
        runtime = os.environ.get("XDG_RUNTIME_DIR", "")
        signature = os.environ.get("HYPRLAND_INSTANCE_SIGNATURE", "")
        if not runtime or not signature or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", signature):
            return
        event_socket = Path(runtime) / "hypr" / signature / ".socket2.sock"
        theme_events = {"configreloaded"}
        feature_events = {
            "workspace", "workspacev2", "createworkspace", "createworkspacev2",
            "destroyworkspace", "destroyworkspacev2", "focusedmon", "focusedmonv2",
            "monitoradded", "monitorremoved",
        }
        while not self.monitor_stop.is_set():
            connection = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            connection.settimeout(1.0)
            try:
                connection.connect(str(event_socket))
                buffer = b""
                while not self.monitor_stop.is_set():
                    try:
                        chunk = connection.recv(4096)
                    except socket.timeout:
                        continue
                    if not chunk:
                        break
                    buffer += chunk
                    if len(buffer) > 64 * 1024:
                        buffer = buffer[-4096:]
                    while b"\n" in buffer:
                        line, buffer = buffer.split(b"\n", 1)
                        event_name = line.split(b">>", 1)[0].decode("ascii", errors="ignore")
                        if event_name in theme_events:
                            self.event_hub.publish("theme")
                        elif event_name in feature_events:
                            self.event_hub.publish("features")
            except OSError:
                pass
            finally:
                connection.close()
            self.monitor_stop.wait(1.5)

    def stop_server(self) -> None:
        with self.lock:
            server, self.server = self.server, None
            self.server_thread = None
            advertiser, self.mdns_advertiser = self.mdns_advertiser, None
        if advertiser is not None:
            advertiser.stop()
        self.stop_monitors()
        if server is not None:
            server.shutdown()
            server.server_close()

    def expire_pending(self) -> list[str]:
        now = int(time.time())
        revoked_device = False
        with self.lock:
            expired = []
            for request_id, request in list(self.pending.items()):
                if request.get("status") == "pending":
                    expiry = int(request.get("createdAt", now)) + PAIRING_TTL_SECONDS
                else:
                    expiry = int(request.get("expiresAt", now + 1))
                if expiry <= now:
                    self.pending.pop(request_id, None)
                    expired.append(request_id)
                    if request.get("status") == "approved":
                        device_id = request.get("deviceId")
                        token = request.get("token")
                        device = self.devices.get(device_id)
                        if isinstance(token, str) and isinstance(device, dict) \
                                and hmac.compare_digest(
                                    hashlib.sha256(token.encode("ascii")).hexdigest(),
                                    str(device.get("tokenHash", "")),
                                ):
                            self.devices.pop(device_id, None)
                            revoked_device = True
            if revoked_device:
                self.save_devices()
        for request_id in expired:
            _emit({"event": "pair_expired", "requestId": request_id})
        if revoked_device:
            _emit({"event": "devices", "devices": self.public_devices()})
        return expired

    def maintenance(self) -> None:
        self.expire_pending()
        now = int(time.time())
        with self.lock:
            if self.pair_secret and now >= self.pair_expires:
                self.pair_secret = None
            should_start = bool(self.devices) and self.server is None
            should_stop = not self.devices and not self.pending and not self.pair_secret and self.server is not None
        if should_start:
            try:
                self.start_server()
                _emit({"event": "state", "serverRunning": True, "devices": self.public_devices()})
            except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                _emit({"event": "error", "message": f"Waiting for a local network: {type(error).__name__}"})
        elif should_stop:
            self.stop_server()

    def begin_pairing(self) -> dict[str, Any]:
        self.start_server()
        self.refresh_advertised_address()
        advertiser = self.mdns_advertiser
        if advertiser is not None:
            advertiser.announce()
        with self.lock:
            address = self.advertised_address
            self.pair_secret = secrets.token_urlsafe(32)
            self.pair_expires = int(time.time()) + PAIRING_TTL_SECONDS
            self.pending.clear()
            payload = {
                "schema": PROTOCOL_VERSION,
                "url": f"https://{address}:{self.port}",
                "fingerprint": self.fingerprint,
                "pairSecret": self.pair_secret,
                "expiresAt": self.pair_expires,
                "hostName": _safe_string(socket.gethostname(), 80) or "Omarchy",
            }
        return {
            "event": "pairing",
            "qrData": _qr_svg_data(json.dumps(payload, separators=(",", ":"))),
            "expiresAt": self.pair_expires,
            "hostName": payload["hostName"],
            "fingerprint": payload["fingerprint"],
        }

    def rate_allowed(self, source: str, limit: int = 120) -> bool:
        now = time.monotonic()
        with self.lock:
            if source not in self.rate_history and len(self.rate_history) >= 512:
                self.rate_history.pop(next(iter(self.rate_history)))
            history = self.rate_history[source]
            while history and now - history[0] > 60:
                history.popleft()
            if len(history) >= limit:
                return False
            history.append(now)
            return True

    def pair_attempt_allowed(self, source: str) -> bool:
        now = time.monotonic()
        with self.lock:
            if source not in self.pair_attempts and len(self.pair_attempts) >= 512:
                self.pair_attempts.pop(next(iter(self.pair_attempts)))
            history = self.pair_attempts[source]
            while history and now - history[0] > 60:
                history.popleft()
            if len(history) >= 5:
                return False
            history.append(now)
            return True

    def create_pair_request(self, body: dict[str, Any], source: str) -> tuple[int, dict[str, Any]]:
        self.expire_pending()
        if not self.pair_attempt_allowed(source):
            return 429, {"error": "rate_limited"}
        secret = body.get("pairSecret")
        device_id = body.get("deviceId")
        device_name = body.get("deviceName")
        valid = (
            isinstance(secret, str)
            and isinstance(device_id, str)
            and 16 <= len(device_id) <= 64
            and all(char in DEVICE_ID_CHARS for char in device_id)
            and isinstance(device_name, str)
            and 1 <= len(device_name) <= DEVICE_NAME_MAX
            and all(char.isprintable() for char in device_name)
        )
        if not valid:
            return 400, {"error": "invalid_request"}
        with self.lock:
            if not self.pair_secret or int(time.time()) >= self.pair_expires:
                return 410, {"error": "pairing_expired"}
            if not hmac.compare_digest(secret, self.pair_secret):
                return 403, {"error": "pairing_rejected"}
            if device_id in self.devices:
                return 409, {"error": "already_paired"}
            if len(self.devices) >= MAX_PAIRED_DEVICES:
                return 429, {"error": "device_limit_reached"}
            if len(self.pending) >= 3:
                return 429, {"error": "too_many_pending_requests"}
            self.pair_secret = None
            request_id = secrets.token_urlsafe(24)
            request = {
                "requestId": request_id,
                "deviceId": device_id,
                "deviceName": device_name,
                "createdAt": int(time.time()),
                "status": "pending",
            }
            self.pending[request_id] = request
        _emit({"event": "pair_request", "requestId": request_id, "deviceName": device_name})
        return 202, {"status": "pending", "requestId": request_id}

    def decide_pair_request(self, request_id: str, approve: bool) -> bool:
        self.expire_pending()
        with self.lock:
            request = self.pending.get(request_id)
            if not request or request.get("status") != "pending":
                return False
            if approve:
                device_id = request["deviceId"]
                token = secrets.token_urlsafe(32)
                self.devices[device_id] = {
                    "deviceName": request["deviceName"],
                    "tokenHash": hashlib.sha256(token.encode("ascii")).hexdigest(),
                    "createdAt": int(time.time()),
                }
                request["status"] = "approved"
                request["token"] = token
                self.save_devices()
            else:
                request["status"] = "rejected"
            request["expiresAt"] = int(time.time()) + 120
        _emit({"event": "devices", "devices": self.public_devices()})
        return True

    def poll_pair_request(self, request_id: str) -> tuple[int, dict[str, Any]]:
        self.expire_pending()
        with self.lock:
            request = self.pending.get(request_id)
            if not request:
                return 404, {"error": "request_not_found"}
            if request.get("expiresAt", int(time.time()) + 1) < int(time.time()):
                self.pending.pop(request_id, None)
                return 410, {"error": "request_expired"}
            status = request.get("status")
            if status == "pending":
                return 200, {"status": "pending"}
            if status == "rejected":
                return 200, {"status": "rejected"}
            return 200, {
                "status": "approved",
                "deviceId": request["deviceId"],
                "token": request["token"],
            }

    def cancel_pair_request(self, request_id: str) -> bool:
        with self.lock:
            request = self.pending.get(request_id)
            if not request or request.get("status") != "pending":
                return False
            request["status"] = "rejected"
            request["expiresAt"] = int(time.time()) + 120
        _emit({"event": "pair_cancelled", "requestId": request_id})
        return True

    def complete_pair_request(self, request_id: str, device_id: str) -> bool:
        self.expire_pending()
        with self.lock:
            request = self.pending.get(request_id)
            if not request or request.get("deviceId") != device_id:
                return False
            if request.get("status") == "completed":
                return True
            if request.get("status") != "approved":
                return False
            request["status"] = "completed"
            request.pop("token", None)
            request["expiresAt"] = int(time.time()) + 120
        _emit({"event": "pair_completed", "requestId": request_id})
        return True

    def reconnect_device(self, device_id: str, pair_secret: str) -> tuple[int, dict[str, Any]]:
        with self.lock:
            if not self.pair_secret or int(time.time()) >= self.pair_expires:
                return 410, {"error": "pairing_expired"}
            if not hmac.compare_digest(pair_secret, self.pair_secret):
                return 403, {"error": "pairing_rejected"}
            if device_id not in self.devices:
                return 404, {"error": "device_not_found"}
            self.pair_secret = None
        return 200, {"status": "reconnected"}

    def authenticate(self, headers: Any) -> tuple[str, dict[str, Any]] | None:
        device_id = headers.get("X-OmaApp-Device", "")
        authorization = headers.get("Authorization", "")
        if not isinstance(device_id, str) or not isinstance(authorization, str) \
                or not authorization.startswith("Bearer "):
            return None
        token = authorization[7:]
        with self.lock:
            device = self.devices.get(device_id)
            if not isinstance(device, dict) or not isinstance(device.get("tokenHash"), str):
                return None
            token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
            if not hmac.compare_digest(token_hash, device["tokenHash"]):
                return None
            return device_id, device

    @staticmethod
    def has_permission(device: dict[str, Any], permission: str) -> bool:
        permissions = device.get("permissions")
        return isinstance(permissions, dict) and permissions.get(permission) is True

    def set_permission(self, device_id: str, permission: str, enabled: bool) -> bool:
        if permission not in DEVICE_PERMISSIONS or not isinstance(enabled, bool):
            return False
        with self.lock:
            device = self.devices.get(device_id)
            if not isinstance(device, dict):
                return False
            permissions = device.get("permissions")
            if not isinstance(permissions, dict):
                permissions = {}
                device["permissions"] = permissions
            permissions[permission] = enabled
            self.save_devices()
        _emit({"event": "devices", "devices": self.public_devices()})
        self.event_hub.publish("features")
        return True

    def save_devices(self) -> None:
        _atomic_json(self.devices_path, self.devices)

    def public_devices(self) -> list[dict[str, Any]]:
        with self.lock:
            return [
                {"deviceId": device_id, "deviceName": str(device.get("deviceName", "Android"))[:DEVICE_NAME_MAX]}
                | {"permissions": {
                    permission: self.has_permission(device, permission)
                    for permission in DEVICE_PERMISSIONS
                }}
                for device_id, device in self.devices.items()
                if isinstance(device, dict)
            ]


class BoundedThreadingHTTPServer(http.server.ThreadingHTTPServer):
    request_queue_size = 32

    def __init__(self, server_address: Any, request_handler: Any):
        self._connection_slots = threading.BoundedSemaphore(16)
        super().__init__(server_address, request_handler)

    def process_request(self, request: Any, client_address: Any) -> None:
        if not self._connection_slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self._connection_slots.release()
            raise

    def process_request_thread(self, request: Any, client_address: Any) -> None:
        try:
            request.settimeout(10)
            super().process_request_thread(request, client_address)
        finally:
            self._connection_slots.release()


class Handler(http.server.BaseHTTPRequestHandler):
    service_state: ServiceState
    server_version = "OmaApp/1"
    sys_version = ""

    def log_message(self, format: str, *args: Any) -> None:
        # Keep device names, IPs, and request headers out of shell logs.
        return

    def _reply(self, status: int, body: dict[str, Any], content_type: str = "application/json") -> None:
        encoded = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", content_type + "; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(encoded)
        except OSError:
            # A phone may cancel a request while leaving a page or stopping discovery.
            # Once the peer closes its TLS socket, there is nothing useful to log or retry.
            return

    def _read_json(self, maximum: int = MAX_JSON_BYTES) -> dict[str, Any] | None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return None
        if length < 2 or length > maximum:
            return None
        try:
            value = json.loads(self.rfile.read(length))
        except (json.JSONDecodeError, UnicodeDecodeError):
            return None
        return value if isinstance(value, dict) else None

    def _rate_limit(self, limit: int = 120) -> bool:
        allowed = self.service_state.rate_allowed(self.client_address[0], limit)
        if not allowed:
            self._reply(429, {"error": "rate_limited"})
        return allowed

    def _authenticated(self) -> tuple[str, dict[str, Any]] | None:
        result = self.service_state.authenticate(self.headers)
        if result is None:
            self._reply(401, {"error": "unauthorized"})
        return result

    def _authorized_feature(self, permission: str) -> tuple[str, dict[str, Any]] | None:
        authenticated = self._authenticated()
        if authenticated is None:
            return None
        if not self.service_state.has_permission(authenticated[1], permission):
            self._reply(403, {"error": "permission_required", "permission": permission})
            return None
        return authenticated

    def _feature_json(self, permission: str, maximum: int = MAX_JSON_BYTES) -> dict[str, Any] | None:
        if self._authorized_feature(permission) is None:
            return None
        body = self._read_json(maximum)
        if body is None:
            self._reply(400, {"error": "invalid_json"})
            return None
        return body

    def _send_local_file(self, path: Path, name: str) -> None:
        try:
            descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > MAX_TRANSFER_BYTES:
                os.close(descriptor)
                self._reply(404, {"error": "file_unavailable"})
                return
        except OSError:
            self._reply(404, {"error": "file_unavailable"})
            return
        self.send_response(200)
        self.send_header("Content-Type", mimetypes.guess_type(name)[0] or "application/octet-stream")
        self.send_header("Content-Length", str(metadata.st_size))
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        try:
            with os.fdopen(descriptor, "rb") as stream:
                while chunk := stream.read(64 * 1024):
                    self.wfile.write(chunk)
        except (BrokenPipeError, ConnectionResetError, OSError):
            return

    def _receive_file(self) -> None:
        raw_name = self.headers.get("X-OmaApp-File-Name", "")
        name = _safe_transfer_name(unquote(raw_name))
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if not name or length < 1:
            self._reply(400, {"error": "invalid_file"})
            return
        if length > MAX_TRANSFER_BYTES:
            self._reply(413, {"error": "file_too_large"})
            return

        directory = _transfer_directory()
        try:
            _private_directory(directory)
        except OSError:
            self._reply(503, {"error": "destination_unavailable"})
            return
        descriptor, temporary_name = tempfile.mkstemp(prefix=".omaapp-", dir=directory)
        received = 0
        try:
            with os.fdopen(descriptor, "wb") as stream:
                while received < length:
                    chunk = self.rfile.read(min(64 * 1024, length - received))
                    if not chunk:
                        raise OSError("incomplete upload")
                    received += len(chunk)
                    stream.write(chunk)
                stream.flush()
                os.fsync(stream.fileno())
            destination = directory / name
            try:
                os.link(temporary_name, destination, follow_symlinks=False)
            except FileExistsError:
                self._reply(409, {"error": "file_exists"})
                return
            os.chmod(destination, 0o600)
        except OSError:
            self._reply(400, {"error": "incomplete_upload"})
            return
        finally:
            Path(temporary_name).unlink(missing_ok=True)
        self._reply(201, {"name": name, "size": received})

    def do_GET(self) -> None:  # noqa: N802
        if not self._rate_limit():
            return
        path = urlsplit(self.path).path
        if path == "/v1/health":
            self._reply(200, {"protocolVersion": PROTOCOL_VERSION, "service": "OmaApp Omarchy"})
            return
        if path == "/v1/events":
            if self._authenticated() is None:
                return
            query = parse_qs(urlsplit(self.path).query, max_num_fields=4)
            raw_revision = query.get("since", ["0"])[0]
            if len(raw_revision) > 20 or not raw_revision.isdecimal():
                self._reply(400, {"error": "invalid_revision"})
                return
            revision = int(raw_revision)
            current_revision = self.service_state.event_hub.revision
            if revision > current_revision:
                self._reply(400, {"error": "invalid_revision"})
                return
            try:
                self._reply(200, self.service_state.event_hub.wait_after(revision))
            except OSError:
                # A phone may cancel a pending long poll while reconnecting or
                # stopping its event listener. The client will retry with the
                # same revision, so a broken response socket is expected here.
                return
            return
        if path.startswith("/v1/pair/status/"):
            request_id = path.removeprefix("/v1/pair/status/")
            if len(request_id) > 64 or not request_id:
                self._reply(404, {"error": "request_not_found"})
                return
            status, body = self.service_state.poll_pair_request(request_id)
            self._reply(status, body)
            return
        if path == "/v1/status":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            snapshot, wallpaper_path = _theme_snapshot()
            self.service_state.wallpaper_cache = (snapshot["capturedAt"], wallpaper_path)
            self._reply(200, snapshot)
            return
        if path == "/v1/permissions":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            self._reply(200, {
                permission: self.service_state.has_permission(authenticated[1], permission)
                for permission in DEVICE_PERMISSIONS
            })
            return
        if path == "/v1/features":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            device = authenticated[1]
            permissions = {
                permission: self.service_state.has_permission(device, permission)
                for permission in DEVICE_PERMISSIONS
            }
            features: dict[str, Any] = {
                "permissions": permissions,
                "eventRevision": self.service_state.event_hub.revision,
            }
            if permissions["system.read"]:
                features["system"] = _system_status()
            if permissions["workspace.control"]:
                features["workspaces"] = _hyprland_workspaces()
            if permissions["media.control"]:
                features["players"] = _mpris_players()
            if permissions["commands"]:
                features["commands"] = [
                    {"id": command["id"], "label": command["label"]}
                    for command in _user_commands()
                ]
            if permissions["files.send"]:
                directory = _transfer_directory()
                files = []
                try:
                    for candidate in sorted(directory.iterdir(), key=lambda item: item.name.casefold())[:100]:
                        name = _safe_transfer_name(candidate.name)
                        try:
                            metadata = candidate.lstat()
                        except OSError:
                            continue
                        if name and candidate.is_file() and not candidate.is_symlink() \
                                and metadata.st_size <= MAX_TRANSFER_BYTES:
                            files.append({"name": name, "size": metadata.st_size})
                except OSError:
                    pass
                features["files"] = files
            self._reply(200, features)
            return
        if path == "/v1/system":
            if self._authorized_feature("system.read") is not None:
                self._reply(200, _system_status())
            return
        if path == "/v1/workspaces":
            if self._authorized_feature("workspace.control") is not None:
                self._reply(200, {"workspaces": _hyprland_workspaces()})
            return
        if path == "/v1/media":
            if self._authorized_feature("media.control") is not None:
                self._reply(200, {"players": _mpris_players()})
            return
        if path == "/v1/clipboard":
            if self._authorized_feature("clipboard") is None:
                return
            clipboard = _run_command(
                ["wl-paste", "--no-newline", "--type", "text/plain;charset=utf-8"],
                timeout=1.5,
                output_limit=MAX_CLIPBOARD_BYTES,
            )
            if clipboard is None:
                self._reply(503, {"error": "clipboard_unavailable"})
                return
            try:
                text = clipboard.decode("utf-8")
            except UnicodeDecodeError:
                self._reply(415, {"error": "clipboard_not_text"})
                return
            self._reply(200, {"text": text})
            return
        if path == "/v1/files" or path.startswith("/v1/files/"):
            if self._authorized_feature("files.send") is None:
                return
            if path == "/v1/files":
                directory = _transfer_directory()
                files = []
                try:
                    for candidate in sorted(directory.iterdir(), key=lambda item: item.name.casefold())[:100]:
                        name = _safe_transfer_name(candidate.name)
                        try:
                            metadata = candidate.lstat()
                        except OSError:
                            continue
                        if name and candidate.is_file() and not candidate.is_symlink() \
                                and metadata.st_size <= MAX_TRANSFER_BYTES:
                            files.append({"name": name, "size": metadata.st_size})
                except OSError:
                    pass
                self._reply(200, {"files": files})
                return
            name = _safe_transfer_name(unquote(path.removeprefix("/v1/files/")))
            if not name or "/" in path.removeprefix("/v1/files/"):
                self._reply(404, {"error": "file_unavailable"})
                return
            self._send_local_file(_transfer_directory() / name, name)
            return
        if path == "/v1/wallpaper":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            snapshot, wallpaper_path = _theme_snapshot()
            if not wallpaper_path:
                self._reply(404, {"error": "wallpaper_unavailable"})
                return
            try:
                with wallpaper_path.open("rb") as stream:
                    content = stream.read(MAX_WALLPAPER_BYTES + 1)
            except OSError:
                self._reply(404, {"error": "wallpaper_unavailable"})
                return
            if len(content) > MAX_WALLPAPER_BYTES:
                self._reply(413, {"error": "wallpaper_too_large"})
                return
            mime = mimetypes.guess_type(wallpaper_path.name)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(content)
            return
        self._reply(404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        if not self._rate_limit():
            return
        path = urlsplit(self.path).path
        if path == "/v1/pair/request":
            body = self._read_json()
            if body is None:
                self._reply(400, {"error": "invalid_json"})
                return
            status, result = self.service_state.create_pair_request(body, self.client_address[0])
            self._reply(status, result)
            return
        if path == "/v1/pair/cancel":
            body = self._read_json()
            request_id = body.get("requestId") if body else None
            if not isinstance(request_id, str) or not 32 <= len(request_id) <= 64:
                self._reply(400, {"error": "invalid_request"})
                return
            changed = self.service_state.cancel_pair_request(request_id)
            self._reply(200 if changed else 404, {"status": "cancelled"})
            return
        if path == "/v1/reconnect":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            body = self._read_json()
            device_id, _device = authenticated
            if not body or body.get("deviceId") != device_id \
                    or not isinstance(body.get("pairSecret"), str):
                self._reply(400, {"error": "invalid_request"})
                return
            status, result = self.service_state.reconnect_device(device_id, body["pairSecret"])
            self._reply(status, result)
            return
        if path == "/v1/clipboard":
            body = self._feature_json("clipboard", MAX_CLIPBOARD_JSON_BYTES)
            if body is None:
                return
            text = body.get("text")
            if not isinstance(text, str) or len(text.encode("utf-8")) > MAX_CLIPBOARD_BYTES:
                self._reply(400, {"error": "invalid_clipboard"})
                return
            result = _run_command(
                ["wl-copy", "--type", "text/plain;charset=utf-8"],
                timeout=1.5,
                input_data=text.encode("utf-8"),
                output_limit=1024,
            )
            if result is None:
                self._reply(503, {"error": "clipboard_unavailable"})
                return
            self._reply(200, {"status": "copied"})
            return
        if path == "/v1/workspaces/activate":
            body = self._feature_json("workspace.control")
            if body is None:
                return
            workspace_id = body.get("workspaceId")
            if isinstance(workspace_id, bool) or not isinstance(workspace_id, int) \
                    or not 0 <= workspace_id <= 1000:
                self._reply(400, {"error": "invalid_workspace"})
                return
            result = _run_command(["hyprctl", "dispatch", "workspace", str(workspace_id)])
            if result is None:
                self._reply(503, {"error": "workspace_unavailable"})
                return
            self._reply(200, {"status": "activated", "workspaceId": workspace_id})
            return
        if path == "/v1/media":
            body = self._feature_json("media.control")
            if body is None:
                return
            player = body.get("player")
            action = body.get("action")
            if not isinstance(player, str) or not MPRIS_PLAYER.fullmatch(player) \
                    or action not in {"play-pause", "play", "pause", "next", "previous", "stop"}:
                self._reply(400, {"error": "invalid_media_action"})
                return
            result = _run_command(["playerctl", "-p", player, action])
            if result is None:
                self._reply(503, {"error": "media_action_unavailable"})
                return
            self._reply(200, {"status": "sent", "player": player, "action": action})
            return
        if path == "/v1/commands/execute":
            body = self._feature_json("commands")
            if body is None:
                return
            command_id = body.get("commandId")
            if not isinstance(command_id, str) or not COMMAND_ID.fullmatch(command_id):
                self._reply(400, {"error": "invalid_command"})
                return
            if not any(command["id"] == command_id for command in _user_commands()):
                self._reply(404, {"error": "command_not_configured"})
                return
            if not _run_user_command(command_id):
                self._reply(503, {"error": "command_failed"})
                return
            self._reply(200, {"status": "completed", "commandId": command_id})
            return
        if path == "/v1/files":
            if self._authorized_feature("files.receive") is None:
                return
            self._receive_file()
            return
        if path == "/v1/pair/complete":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            body = self._read_json()
            request_id = body.get("requestId") if body else None
            device_id, _device = authenticated
            if not isinstance(request_id, str) or not 32 <= len(request_id) <= 64:
                self._reply(400, {"error": "invalid_request"})
                return
            completed = self.service_state.complete_pair_request(request_id, device_id)
            self._reply(200 if completed else 404,
                        {"status": "completed" if completed else "request_not_found"})
            return
        if path == "/v1/revoke":
            authenticated = self._authenticated()
            if authenticated is None:
                return
            device_id, _device = authenticated
            with self.service_state.lock:
                self.service_state.devices.pop(device_id, None)
                self.service_state.save_devices()
            self._reply(200, {"status": "revoked"})
            _emit({"event": "devices", "devices": self.service_state.public_devices()})
            return
        self._reply(404, {"error": "not_found"})


def _process_command(state: ServiceState, command: dict[str, Any]) -> bool:
    action = command.get("action")
    if action in {"ensure_service", "maintenance"}:
        state.maintenance()
        return True
    if action == "begin_pairing":
        try:
            _emit(state.begin_pairing())
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            _emit({"event": "error", "message": f"Could not start pairing: {type(error).__name__}"})
        return True
    if action in {"approve", "reject"}:
        request_id = command.get("requestId")
        if isinstance(request_id, str) and len(request_id) <= 64:
            changed = state.decide_pair_request(request_id, action == "approve")
            _emit({"event": "decision", "requestId": request_id, "accepted": changed})
        return True
    if action == "revoke":
        device_id = command.get("deviceId")
        if isinstance(device_id, str):
            with state.lock:
                state.devices.pop(device_id, None)
                state.save_devices()
            _emit({"event": "devices", "devices": state.public_devices()})
        return True
    if action == "set_permission":
        device_id = command.get("deviceId")
        permission = command.get("permission")
        enabled = command.get("enabled")
        if isinstance(device_id, str) and isinstance(permission, str) and isinstance(enabled, bool):
            if not state.set_permission(device_id, permission, enabled):
                _emit({"event": "error", "message": "Could not update this device permission"})
        return True
    if action == "stop":
        return False
    return True


def run_service(data_dir: Path, bind: str, port: int) -> int:
    state = ServiceState(data_dir, bind, port)
    if state.devices:
        try:
            state.start_server()
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            _emit({"event": "error", "message": f"Waiting for a local network: {type(error).__name__}"})
    _emit({"event": "state", "serverRunning": state.server is not None, "devices": state.public_devices()})
    try:
        for line in sys.stdin:
            if len(line) > 4096:
                continue
            try:
                command = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(command, dict) and not _process_command(state, command):
                break
    finally:
        state.stop_server()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=_state_directory())
    parser.add_argument("--bind", default="0.0.0.0", help=argparse.SUPPRESS)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error("port must be between 0 and 65535")
    return run_service(args.data_dir, args.bind, args.port)


if __name__ == "__main__":
    raise SystemExit(main())
