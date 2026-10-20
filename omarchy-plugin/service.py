#!/usr/bin/env python3
"""Small, TLS-pinned OmaApp companion service owned by the Omarchy plugin."""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import http.server
import ipaddress
import json
import mimetypes
import os
import re
import secrets
import socket
import ssl
import subprocess
import sys
import threading
import time
import tomllib
from collections import defaultdict, deque
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

from third_party.qrcodegen import QrCode


PROTOCOL_VERSION = 1
DEFAULT_PORT = 44892
PAIRING_TTL_SECONDS = 180
MAX_JSON_BYTES = 16 * 1024
MAX_WALLPAPER_BYTES = 30 * 1024 * 1024
DEVICE_ID_CHARS = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_")
DEVICE_NAME_MAX = 80
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


def _private_directory(path: Path) -> None:
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        path.chmod(0o700)
    except OSError:
        pass


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
    shell: dict[str, dict[str, int | float | str]] = {}
    for section, values in raw_shell.items():
        if section != "spacing":
            continue
        if not isinstance(section, str) or not section.replace("-", "").isalnum() \
                or not isinstance(values, dict):
            continue
        safe_values: dict[str, int | float | str] = {}
        for key, value in values.items():
            if key not in SHELL_SPACING_KEYS:
                continue
            number = _safe_number(value, 0, 1000)
            if number is not None:
                safe_values[key] = number
        if safe_values:
            shell[section[:64]] = safe_values

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
        self.advertised_address = ""
        self.wallpaper_cache: tuple[int, Path | None] | None = None

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
            return self.port

    def stop_server(self) -> None:
        with self.lock:
            server, self.server = self.server, None
            self.server_thread = None
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

    def save_devices(self) -> None:
        _atomic_json(self.devices_path, self.devices)

    def public_devices(self) -> list[dict[str, Any]]:
        with self.lock:
            return [
                {"deviceId": device_id, "deviceName": str(device.get("deviceName", "Android"))[:DEVICE_NAME_MAX]}
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
        self.send_response(status)
        self.send_header("Content-Type", content_type + "; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(encoded)

    def _read_json(self) -> dict[str, Any] | None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return None
        if length < 2 or length > MAX_JSON_BYTES:
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

    def do_GET(self) -> None:  # noqa: N802
        if not self._rate_limit():
            return
        path = urlsplit(self.path).path
        if path == "/v1/health":
            self._reply(200, {"protocolVersion": PROTOCOL_VERSION, "service": "OmaApp Omarchy"})
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
