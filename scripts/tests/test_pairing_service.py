import http.client
import json
import os
import ssl
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


PLUGIN_ROOT = Path(__file__).resolve().parents[2] / "omarchy-plugin"
sys.path.insert(0, str(PLUGIN_ROOT))

import service  # noqa: E402


class PairingServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.state = service.ServiceState(Path(self.temp_dir.name), "127.0.0.1", 0)
        self.addCleanup(self.state.stop_server)

    def connection(self):
        context = ssl._create_unverified_context()
        return http.client.HTTPSConnection("127.0.0.1", self.state.port, context=context, timeout=2)

    def post(self, path, body, headers=None):
        connection = self.connection()
        request_headers = {"Content-Type": "application/json"}
        request_headers.update(headers or {})
        connection.request("POST", path, json.dumps(body), request_headers)
        response = connection.getresponse()
        payload = response.read()
        status = response.status
        connection.close()
        return status, json.loads(payload)

    def get(self, path, headers=None):
        connection = self.connection()
        connection.request("GET", path, headers=headers or {})
        response = connection.getresponse()
        payload = response.read()
        status = response.status
        connection.close()
        return status, json.loads(payload)

    def test_tls_pairing_requires_both_device_request_and_host_approval(self):
        event = self.state.begin_pairing()
        self.assertEqual(event["event"], "pairing")
        self.assertTrue(event["qrData"].startswith("data:image/svg+xml;base64,"))
        self.assertEqual(len(self.state.fingerprint), 64)
        self.assertEqual(self.state.key_path.stat().st_mode & 0o777, 0o600)

        status, health = self.get("/v1/health")
        self.assertEqual(status, 200)
        self.assertEqual(health["protocolVersion"], service.PROTOCOL_VERSION)
        self.assertEqual(self.get("/v1/status")[0], 401)

        device_id = "phone_test_device_123"
        status, _ = self.post("/v1/pair/request", {
            "pairSecret": "incorrect",
            "deviceId": device_id,
            "deviceName": "Test phone",
        })
        self.assertEqual(status, 403)

        status, pending = self.post("/v1/pair/request", {
            "pairSecret": self.state.pair_secret,
            "deviceId": device_id,
            "deviceName": "Test phone",
        })
        self.assertEqual(status, 202)
        self.assertEqual(self.get(f"/v1/pair/status/{pending['requestId']}")[1]["status"], "pending")

        self.assertTrue(self.state.decide_pair_request(pending["requestId"], approve=True))
        status, approved = self.get(f"/v1/pair/status/{pending['requestId']}")
        self.assertEqual(status, 200)
        self.assertEqual(approved["status"], "approved")
        self.assertEqual(approved["deviceId"], device_id)
        self.assertTrue(approved["token"])
        self.assertEqual(self.state.devices_path.stat().st_mode & 0o777, 0o600)

        auth = {
            "X-OmaApp-Device": device_id,
            "Authorization": f"Bearer {approved['token']}",
        }
        status, completed = self.post("/v1/pair/complete", {
            "requestId": pending["requestId"],
        }, headers=auth)
        self.assertEqual(status, 200)
        self.assertEqual(completed["status"], "completed")
        self.assertEqual(self.state.pending[pending["requestId"]]["status"], "completed")
        self.assertNotIn("token", self.state.pending[pending["requestId"]])
        retry_status, retry = self.post("/v1/pair/complete", {
            "requestId": pending["requestId"],
        }, headers=auth)
        self.assertEqual(retry_status, 200)
        self.assertEqual(retry["status"], "completed")

        status, snapshot = self.get("/v1/status", auth)
        self.assertEqual(status, 200)
        self.assertEqual(snapshot["protocolVersion"], service.PROTOCOL_VERSION)
        self.assertIn("theme", snapshot)
        self.assertIn("hyprland", snapshot)

        connection = self.connection()
        connection.request("POST", "/v1/revoke", headers=auth)
        response = connection.getresponse()
        response.read()
        connection.close()
        self.assertEqual(response.status, 200)
        self.assertEqual(self.get("/v1/status", auth)[0], 401)

    def test_rejected_pairing_never_returns_a_device_token(self):
        self.state.begin_pairing()
        request_status, pending = self.post("/v1/pair/request", {
            "pairSecret": self.state.pair_secret,
            "deviceId": "phone_test_device_456",
            "deviceName": "Second phone",
        })
        self.assertEqual(request_status, 202)
        self.assertTrue(self.state.decide_pair_request(pending["requestId"], approve=False))
        status, result = self.get(f"/v1/pair/status/{pending['requestId']}")
        self.assertEqual(status, 200)
        self.assertEqual(result["status"], "rejected")
        self.assertNotIn("token", result)
        self.assertEqual(self.state.devices, {})

    def test_pairing_request_expires_and_cannot_be_approved_later(self):
        self.state.begin_pairing()
        request_status, pending = self.post("/v1/pair/request", {
            "pairSecret": self.state.pair_secret,
            "deviceId": "phone_test_device_expired",
            "deviceName": "Expired phone",
        })
        self.assertEqual(request_status, 202)
        self.state.pending[pending["requestId"]]["createdAt"] -= service.PAIRING_TTL_SECONDS + 1
        status, result = self.get(f"/v1/pair/status/{pending['requestId']}")
        self.assertEqual(status, 404)
        self.assertEqual(result["error"], "request_not_found")
        self.assertFalse(self.state.decide_pair_request(pending["requestId"], approve=True))
        self.assertEqual(self.state.devices, {})

    def test_unclaimed_pairing_token_expiry_revokes_stale_device_record(self):
        self.state.begin_pairing()
        device_id = "phone_unclaimed_token_123"
        status, pending = self.post("/v1/pair/request", {
            "pairSecret": self.state.pair_secret,
            "deviceId": device_id,
            "deviceName": "Unclaimed phone",
        })
        self.assertEqual(status, 202)
        self.assertTrue(self.state.decide_pair_request(pending["requestId"], approve=True))
        self.assertIn(device_id, self.state.devices)

        self.state.pending[pending["requestId"]]["expiresAt"] = int(service.time.time()) - 1
        expired = self.state.expire_pending()
        self.assertIn(pending["requestId"], expired)
        self.assertNotIn(device_id, self.state.devices)
        self.assertEqual(self.get(f"/v1/pair/status/{pending['requestId']}")[0], 404)

        self.state.begin_pairing()
        retry_status, _ = self.post("/v1/pair/request", {
            "pairSecret": self.state.pair_secret,
            "deviceId": device_id,
            "deviceName": "Unclaimed phone",
        })
        self.assertEqual(retry_status, 202)

    def test_pairing_refuses_a_loopback_only_qr_address(self):
        state = service.ServiceState(Path(self.temp_dir.name) / "offline", "0.0.0.0", 0)
        with patch.object(service, "_advertised_ipv4", return_value=""):
            with self.assertRaises(OSError):
                state.begin_pairing()

    def test_pairing_device_count_is_bounded(self):
        self.state.begin_pairing()
        self.state.devices = {
            f"existing_device_{index:02d}": {"deviceName": "Existing", "tokenHash": "0" * 64}
            for index in range(service.MAX_PAIRED_DEVICES)
        }
        status, _ = self.state.create_pair_request({
            "pairSecret": self.state.pair_secret,
            "deviceId": "phone_test_device_overflow",
            "deviceName": "Overflow phone",
        }, "127.0.0.1")
        self.assertEqual(status, 429)

    def test_known_device_can_reconnect_using_a_new_qr_and_pinned_token(self):
        self.state.begin_pairing()
        device_id = "phone_reconnect_device_1"
        status, pending = self.post("/v1/pair/request", {
            "pairSecret": self.state.pair_secret,
            "deviceId": device_id,
            "deviceName": "Known phone",
        })
        self.assertEqual(status, 202)
        self.assertTrue(self.state.decide_pair_request(pending["requestId"], approve=True))
        _, approved = self.get(f"/v1/pair/status/{pending['requestId']}")
        auth = {
            "X-OmaApp-Device": device_id,
            "Authorization": f"Bearer {approved['token']}",
        }

        self.state.begin_pairing()
        status, _ = self.post("/v1/reconnect", {
            "pairSecret": self.state.pair_secret,
            "deviceId": device_id,
        })
        self.assertEqual(status, 401)
        status, _ = self.post("/v1/reconnect", {
            "pairSecret": "wrong",
            "deviceId": device_id,
        }, headers=auth)
        self.assertEqual(status, 403)
        status, body = self.post("/v1/reconnect", {
            "pairSecret": self.state.pair_secret,
            "deviceId": device_id,
        }, headers=auth)
        self.assertEqual(status, 200)
        self.assertEqual(body["status"], "reconnected")
        self.assertIsNone(self.state.pair_secret)
        self.assertEqual(self.get("/v1/status", auth)[0], 200)

    def test_theme_snapshot_uses_current_omarchy_files_and_hyprctl(self):
        root = Path(self.temp_dir.name) / "state" / "omarchy" / "current"
        theme_dir = root / "theme"
        theme_dir.mkdir(parents=True)
        (root / "theme.name").write_text("Tokyo Night\n", encoding="utf-8")
        (theme_dir / "colors.toml").write_text(
            'accent = "#7aa2f7"\nbackground = "#1a1b26"\nbrown = "#704e3f"\nignore_me = "unsafe"\n'
            'hyprland_active_border = "rgba(33ccffee) rgba(00ff99ee)"\n',
            encoding="utf-8",
        )
        (theme_dir / "shell.toml").write_text(
            "[spacing]\nscale = 1.25\nrow-gap = 8\npanel-gap = 16\npanel-padding = 24\n"
            "xxl = 12\n\n[bar]\nheight = 30\n",
            encoding="utf-8",
        )
        (root / "theme" / "wallpaper.png").write_bytes(b"png-bytes")
        (root / "background").symlink_to(theme_dir / "wallpaper.png")

        values = {
            "decoration:rounding": {"int": 12},
            "decoration:rounding_power": {"float": 4.0},
            "general:border_size": {"int": 2},
            "general:col.active_border": {"gradient": "ee33ccff ee00ff99 45deg"},
            "general:col.inactive_border": {"gradient": "aa595959 0deg"},
            "general:gaps_in": {"css": "6 6 6 6"},
            "general:gaps_out": {"css": "10 10 10 10"},
        }

        def fake_hyprctl(args, **_kwargs):
            return type("Result", (), {
                "returncode": 0,
                "stdout": json.dumps(values[args[-1]]),
            })()

        with patch.dict(os.environ, {"XDG_STATE_HOME": str(Path(self.temp_dir.name) / "state")}):
            with patch.object(service.subprocess, "run", side_effect=fake_hyprctl):
                snapshot, wallpaper_path = service._theme_snapshot()

        self.assertEqual(snapshot["theme"]["name"], "Tokyo Night")
        self.assertEqual(snapshot["theme"]["colors"]["accent"], "#7aa2f7")
        self.assertEqual(snapshot["theme"]["colors"]["brown"], "#704e3f")
        self.assertEqual(
            snapshot["theme"]["colors"]["hyprland_active_border"],
            "rgba(33ccffee) rgba(00ff99ee)",
        )
        self.assertNotIn("ignore_me", snapshot["theme"]["colors"])
        self.assertEqual(snapshot["theme"]["shell"]["spacing"]["panel-gap"], 16)
        self.assertEqual(snapshot["theme"]["shell"]["spacing"]["scale"], 1.25)
        self.assertEqual(snapshot["theme"]["shell"]["spacing"]["xxl"], 12)
        self.assertNotIn("bar", snapshot["theme"]["shell"])
        self.assertEqual(snapshot["hyprland"]["roundingPower"], 4.0)
        self.assertEqual(snapshot["hyprland"]["activeBorder"], "rgba(33ccffee) rgba(00ff99ee) 45deg")
        self.assertEqual(snapshot["hyprland"]["activeBorderAngle"], 45.0)
        self.assertEqual(snapshot["hyprland"]["inactiveBorder"], "rgba(595959aa) 0deg")
        self.assertEqual(snapshot["hyprland"]["gapsIn"], 6)
        self.assertEqual(snapshot["hyprland"]["gapsOut"], 10)
        self.assertEqual(snapshot["wallpaper"]["name"], "wallpaper.png")
        self.assertEqual(wallpaper_path, (theme_dir / "wallpaper.png").resolve())
        self.assertNotIn(str(wallpaper_path), json.dumps(snapshot))


if __name__ == "__main__":
    unittest.main()
