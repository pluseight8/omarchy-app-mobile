import hashlib
import http.client
import json
import os
import runpy
import select
import socket
import ssl
import struct
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "omarchy-plugin" / "service.py"
sys.path.insert(0, str(SERVICE.parent))
try:
    HTTP_HANDLER = runpy.run_path(str(SERVICE), run_name="oma_service_test")["Handler"]
finally:
    sys.path.pop(0)
DEVICE_ID = "pixel-10-integration"
TOKEN = "a-real-helper-process-test-token"


class PluginRuntimeIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.data_dir = Path(self.temp.name) / "plugin-state"
        self.data_dir.mkdir(mode=0o700)
        self.state_home = Path(self.temp.name) / "xdg-state"
        self.current_theme = self.state_home / "omarchy" / "current" / "theme"
        self.current_theme.mkdir(parents=True)
        (self.current_theme / "colors.toml").write_text('accent = "#4488cc"\n', encoding="utf-8")
        self._write_device()
        self.process = None
        self.port = self._available_port()
        self.addCleanup(self.stop_helper)

    def _write_device(self):
        token_hash = hashlib.sha256(TOKEN.encode("ascii")).hexdigest()
        path = self.data_dir / "devices.json"
        path.write_text(json.dumps({DEVICE_ID: {
            "deviceName": "Pixel integration test",
            "tokenHash": token_hash,
            "createdAt": int(time.time()),
            "permissions": {},
        }}), encoding="utf-8")
        path.chmod(0o600)

    @staticmethod
    def _available_port():
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
            server.bind(("127.0.0.1", 0))
            return server.getsockname()[1]

    def start_helper(self):
        environment = os.environ.copy()
        environment["XDG_STATE_HOME"] = str(self.state_home)
        self.process = subprocess.Popen(
            [sys.executable, str(SERVICE), "--data-dir", str(self.data_dir),
             "--bind", "127.0.0.1", "--port", str(self.port)],
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
            env=environment,
        )
        return self.read_event()

    def read_event(self, timeout=8):
        self.assertIsNotNone(self.process)
        readable, _, _ = select.select([self.process.stdout], [], [], timeout)
        self.assertTrue(readable, "the plugin helper did not emit a lifecycle event")
        line = self.process.stdout.readline()
        self.assertTrue(line, "the plugin helper stopped before emitting its lifecycle event")
        return json.loads(line)

    def command(self, value):
        self.process.stdin.write(json.dumps(value) + "\n")
        self.process.stdin.flush()

    def auth(self):
        return {
            "X-OmaApp-Device": DEVICE_ID,
            "Authorization": "Bearer " + TOKEN,
        }

    def test_reply_suppresses_a_client_disconnect_while_writing(self):
        class DisconnectedWriter:
            def write(self, _payload):
                raise BrokenPipeError("client closed the socket")

        class HandlerStub:
            wfile = DisconnectedWriter()

            def send_response(self, _status):
                pass

            def send_header(self, _name, _value):
                pass

            def end_headers(self):
                pass

        HTTP_HANDLER._reply(HandlerStub(), 200, {"ok": True})

    def request(self, path, headers=None):
        context = ssl._create_unverified_context()
        connection = http.client.HTTPSConnection("127.0.0.1", self.port, context=context, timeout=3)
        connection.request("GET", path, headers=headers or {})
        response = connection.getresponse()
        body = response.read()
        status = response.status
        connection.close()
        return status, json.loads(body)

    def stop_helper(self):
        if self.process is None:
            return
        if self.process.poll() is None:
            try:
                self.command({"action": "stop"})
                self.process.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait(timeout=5)
        for stream in (self.process.stdin, self.process.stdout, self.process.stderr):
            if stream is not None:
                stream.close()
        self.process = None

    def test_helper_process_starts_serves_persists_grants_and_revokes_after_restart(self):
        state = self.start_helper()
        self.assertEqual(state["event"], "state")
        self.assertTrue(state["serverRunning"])
        self.assertEqual(self.request("/v1/health")[0], 200)
        self.assertEqual(self.request("/v1/status")[0], 401)
        status, snapshot = self.request("/v1/status", self.auth())
        self.assertEqual(status, 200)
        self.assertIn("theme", snapshot)
        self.assertIn("hyprland", snapshot)

        theme_wait = []
        theme_waiter = threading.Thread(
            target=lambda: theme_wait.append(self.request(
                f"/v1/events?since={self.request('/v1/features', self.auth())[1]['eventRevision']}",
                self.auth())),
        )
        theme_waiter.start()
        time.sleep(0.1)
        (self.current_theme / "colors.toml").write_text(
            'accent = "#cc8844"\nbackground = "#101820"\n', encoding="utf-8")
        theme_waiter.join(timeout=4)
        self.assertFalse(theme_waiter.is_alive(), "the Omarchy file watcher did not wake the paired client")
        self.assertEqual(theme_wait[0][0], 200)
        self.assertEqual(theme_wait[0][1]["kind"], "theme")
        status, updated_snapshot = self.request("/v1/status", self.auth())
        self.assertEqual(status, 200)
        self.assertEqual(updated_snapshot["theme"]["colors"]["accent"], "#cc8844")

        status, before_features = self.request("/v1/features", self.auth())
        self.assertEqual(status, 200)
        event_result = []
        waiter = threading.Thread(
            target=lambda: event_result.append(self.request(
                f"/v1/events?since={before_features['eventRevision']}", self.auth())),
        )
        waiter.start()

        self.command({"action": "set_permission", "deviceId": DEVICE_ID,
                      "permission": "system.read", "enabled": True})
        while True:
            event = self.read_event()
            if event.get("event") == "devices":
                self.assertTrue(event["devices"][0]["permissions"]["system.read"])
                break
        waiter.join(timeout=4)
        self.assertFalse(waiter.is_alive(), "the plugin event wait did not wake after a permission change")
        self.assertEqual(event_result[0][0], 200)
        self.assertEqual(event_result[0][1]["kind"], "features")
        self.stop_helper()

        self.port = self._available_port()
        state = self.start_helper()
        self.assertTrue(state["serverRunning"])
        status, features = self.request("/v1/features", self.auth())
        self.assertEqual(status, 200)
        self.assertTrue(features["permissions"]["system.read"])
        self.assertIn("system", features)

        self.command({"action": "revoke", "deviceId": DEVICE_ID})
        while True:
            event = self.read_event()
            if event.get("event") == "devices":
                self.assertEqual(event["devices"], [])
                break
        self.assertEqual(self.request("/v1/status", self.auth())[0], 401)

    def test_abandoned_event_long_poll_does_not_log_disconnect_traceback(self):
        self.start_helper()
        status, features = self.request("/v1/features", self.auth())
        self.assertEqual(status, 200)

        context = ssl._create_unverified_context()
        raw = socket.create_connection(("127.0.0.1", self.port), timeout=3)
        client = context.wrap_socket(raw, server_hostname="localhost")
        revision = features["eventRevision"]
        request = (
            f"GET /v1/events?since={revision} HTTP/1.1\r\n"
            "Host: localhost\r\n"
            f"X-OmaApp-Device: {DEVICE_ID}\r\n"
            f"Authorization: Bearer {TOKEN}\r\n"
            "Connection: keep-alive\r\n\r\n"
        )
        client.sendall(request.encode("ascii"))
        time.sleep(0.1)
        client.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
        client.close()

        self.command({"action": "set_permission", "deviceId": DEVICE_ID,
                      "permission": "system.read", "enabled": True})
        event = self.read_event(timeout=3)
        self.assertEqual(event.get("event"), "devices")
        time.sleep(0.1)

        process = self.process
        self.command({"action": "stop"})
        _, stderr = process.communicate(timeout=5)
        self.process = None
        self.assertNotIn("Traceback", stderr)
        self.assertNotIn("BrokenPipeError", stderr)


if __name__ == "__main__":
    unittest.main()
