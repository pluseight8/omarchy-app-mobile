import http.client
import json
import os
import socket
import ssl
import sys
import tempfile
import unittest
from urllib.parse import quote
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

    def get_bytes(self, path, headers=None):
        connection = self.connection()
        connection.request("GET", path, headers=headers or {})
        response = connection.getresponse()
        payload = response.read()
        status = response.status
        content_type = response.getheader("Content-Type")
        connection.close()
        return status, payload, content_type

    def enable_device(self, permissions):
        device_id = "phone_feature_test_device"
        token = "feature-test-token-that-is-long-enough"
        self.state.devices[device_id] = {
            "deviceName": "Feature test phone",
            "tokenHash": service.hashlib.sha256(token.encode("ascii")).hexdigest(),
            "permissions": dict(permissions),
        }
        self.state.save_devices()
        self.state.start_server()
        return {
            "X-OmaApp-Device": device_id,
            "Authorization": f"Bearer {token}",
        }

    def test_tls_pairing_requires_both_device_request_and_host_approval(self):
        event = self.state.begin_pairing()
        self.assertEqual(event["event"], "pairing")
        self.assertTrue(event["qrData"].startswith("data:image/svg+xml;base64,"))
        self.assertEqual(len(self.state.fingerprint), 64)
        self.assertEqual(self.state.key_path.stat().st_mode & 0o777, 0o600)
        self.assertIsNone(self.state.mdns_advertiser)

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

    def test_mdns_advertisement_contains_pinned_identity_without_secrets(self):
        fingerprint = "a" * 64
        instance = f"OmaApp-{fingerprint[:12]}.{service.MDNS_SERVICE_TYPE}"
        target = "omaapp-aaaaaaaaaaaa.local."
        packet = service._mdns_announcement(instance, target, "192.168.1.30", 44892,
                                            fingerprint)
        transaction, flags, questions, answers, authorities, additionals = \
            service.struct.unpack_from("!HHHHHH", packet)
        self.assertEqual((transaction, flags, questions, answers, authorities, additionals),
                         (0, 0x8400, 0, 4, 0, 0))

        offset = 12
        records = []
        for _ in range(answers):
            name, offset = service._dns_read_name(packet, offset)
            record_type, _record_class, ttl, size = service.struct.unpack_from("!HHIH", packet, offset)
            offset += 10
            value = packet[offset:offset + size]
            offset += size
            records.append((name, record_type, ttl, value))
        self.assertEqual([record[1] for record in records], [12, 33, 16, 1])
        self.assertEqual([record[2] for record in records], [120, 120, 120, 120])
        ptr_name, _ = service._dns_read_name(records[0][3], 0)
        self.assertEqual(ptr_name, instance.lower())
        txt = records[2][3]
        self.assertIn(b"fingerprint=" + fingerprint.encode("ascii"), txt)
        self.assertIn(b"protocol=1", txt)
        self.assertNotIn(b"pairSecret", packet)
        self.assertNotIn(b"token", packet)

    def test_dns_sd_advertises_all_private_ipv4_addresses_on_active_interfaces(self):
        ip_output = [
            {"ifname": "enp1s0", "flags": ["UP", "LOWER_UP"], "addr_info": [
                {"family": "inet", "scope": "global", "local": "192.168.1.30"},
                {"family": "inet", "scope": "global", "local": "10.0.0.4"},
            ]},
            {"ifname": "wlan0", "flags": ["UP"], "addr_info": [
                {"family": "inet", "scope": "global", "local": "172.20.4.5"},
            ]},
            {"ifname": "tun0", "flags": ["UP"], "addr_info": [
                {"family": "inet", "scope": "global", "local": "100.64.0.2"},
            ]},
            {"ifname": "enp2s0", "flags": ["BROADCAST"], "addr_info": [
                {"family": "inet", "scope": "global", "local": "10.0.0.6"},
            ]},
        ]
        completed = service.subprocess.CompletedProcess(
            ["ip"], 0, stdout=json.dumps(ip_output), stderr=""
        )
        with patch.object(service, "_advertised_ipv4", return_value="192.168.1.30"), \
                patch.object(service.subprocess, "run", return_value=completed):
            self.assertEqual(service._advertised_ipv4_addresses(),
                             ["192.168.1.30", "10.0.0.4", "172.20.4.5"])

    def test_mdns_advertiser_answers_with_multiple_a_records_over_loopback(self):
        fingerprint = "d" * 64
        instance = f"OmaApp-{fingerprint[:12]}.{service.MDNS_SERVICE_TYPE}"
        target = f"omaapp-{fingerprint[:12]}.local."
        group = "239.255.73.22"
        receiver = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        receiver.bind((group, 0))
        port = receiver.getsockname()[1]
        interface = "127.0.0.1"
        receiver.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP,
                             socket.inet_aton(group) + socket.inet_aton(interface))
        receiver.settimeout(3)
        advertiser = service.MdnsAdvertiser(
            lambda: (interface, 44892, fingerprint),
            address_provider=lambda: ["192.168.10.5", "10.1.0.5"],
            multicast_group=group,
            multicast_port=port,
            multicast_interface=interface,
        )
        try:
            advertiser.start()
            packet, _ = receiver.recvfrom(4096)
            answers = service.struct.unpack_from("!H", packet, 6)[0]
            offset = 12
            records = []
            for _ in range(answers):
                name, offset = service._dns_read_name(packet, offset)
                record_type, _record_class, ttl, size = service.struct.unpack_from(
                    "!HHIH", packet, offset
                )
                offset += 10
                value = packet[offset:offset + size]
                offset += size
                records.append((name, record_type, ttl, value))
            self.assertEqual([record[1] for record in records], [12, 33, 16, 1, 1])
            self.assertEqual([record[3] for record in records[-2:]],
                             [socket.inet_aton("192.168.10.5"), socket.inet_aton("10.1.0.5")])
            self.assertEqual([record[0] for record in records[-2:]],
                             [target.lower(), target.lower()])
        finally:
            advertiser.stop()
            receiver.close()

    def test_mdns_memberships_follow_address_changes(self):
        class RecordingSocket:
            def __init__(self):
                self.options = []

            def setsockopt(self, level, option, value):
                self.options.append((level, option, socket.inet_ntoa(value[4:])))

        advertiser = service.MdnsAdvertiser(
            lambda: ("192.168.1.2", 44892, "e" * 64),
            address_provider=lambda: [],
        )
        listener = RecordingSocket()
        advertiser._refresh_memberships(listener, ["192.168.1.2", "10.0.0.2"])
        advertiser._refresh_memberships(listener, ["10.0.0.2", "172.18.0.2"])
        self.assertEqual(
            set(listener.options[:2]),
            {
                (socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, "192.168.1.2"),
                (socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, "10.0.0.2"),
            },
        )
        self.assertEqual(
            listener.options[2:],
            [
                (socket.IPPROTO_IP, socket.IP_DROP_MEMBERSHIP, "192.168.1.2"),
                (socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, "172.18.0.2"),
            ],
        )

    def test_mdns_listener_matches_only_omaapp_service_questions(self):
        instance = "OmaApp-aabbccddeeff._omaapp._tcp.local."
        target = "omaapp-aabbccddeeff.local."

        def question(name):
            return (service.struct.pack("!HHHHHH", 0, 0, 1, 0, 0, 0)
                    + service._dns_name_wire(name)
                    + service.struct.pack("!HH", 12, 1))

        self.assertTrue(service._mdns_questions_match(
            question(service.MDNS_SERVICE_TYPE), instance, target))
        self.assertFalse(service._mdns_questions_match(
            question("_printer._tcp.local."), instance, target))
        self.assertFalse(service._mdns_questions_match(b"malformed", instance, target))

    def test_mdns_advertiser_sends_query_response_and_goodbye_over_loopback(self):
        group = "239.255.42.99"
        interface = "127.0.0.1"
        fingerprint = "c" * 64
        instance = f"OmaApp-{fingerprint[:12]}.{service.MDNS_SERVICE_TYPE}"
        target = f"omaapp-{fingerprint[:12]}.local."

        port_probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        port_probe.bind(("", 0))
        port = port_probe.getsockname()[1]
        port_probe.close()

        receiver = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        receiver.bind(("", port))
        receiver.setsockopt(
            socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP,
            socket.inet_aton(group) + socket.inet_aton(interface),
        )
        receiver.settimeout(2)

        advertiser = service.MdnsAdvertiser(
            lambda: (interface, 44892, fingerprint),
            multicast_group=group,
            multicast_port=port,
            multicast_interface=interface,
        )

        def receive_response():
            deadline = service.time.monotonic() + 2
            while service.time.monotonic() < deadline:
                packet, _source = receiver.recvfrom(4096)
                if len(packet) >= 4 and service.struct.unpack_from("!H", packet, 2)[0] & 0x8000:
                    return packet
            self.fail("Timed out waiting for an mDNS response.")

        def record_ttls(packet):
            answers = service.struct.unpack_from("!H", packet, 6)[0]
            offset = 12
            records = []
            for _ in range(answers):
                name, offset = service._dns_read_name(packet, offset)
                record_type, _record_class, ttl, length = service.struct.unpack_from(
                    "!HHIH", packet, offset
                )
                offset += 10
                value = packet[offset:offset + length]
                offset += length
                records.append((name, record_type, ttl, value))
            return records

        try:
            advertiser.start()
            announcement = receive_response()
            records = record_ttls(announcement)
            self.assertEqual([record[1] for record in records], [12, 33, 16, 1])
            self.assertEqual([record[2] for record in records], [120] * 4)
            self.assertIn(b"fingerprint=" + fingerprint.encode("ascii"), announcement)
            self.assertNotIn(b"token", announcement)
            self.assertNotIn(b"pairSecret", announcement)

            question = (
                service.struct.pack("!HHHHHH", 0, 0, 1, 0, 0, 0)
                + service._dns_name_wire(service.MDNS_SERVICE_TYPE)
                + service.struct.pack("!HH", 12, 1)
            )
            query_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            try:
                query_socket.setsockopt(
                    socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(interface)
                )
                query_socket.sendto(question, (group, port))
            finally:
                query_socket.close()

            response = receive_response()
            self.assertEqual([record[1] for record in record_ttls(response)], [12, 33, 16, 1])
            advertiser.stop()
            goodbye = receive_response()
            self.assertEqual([record[2] for record in record_ttls(goodbye)], [0] * 4)
            advertiser = None
        finally:
            if advertiser is not None:
                advertiser.stop()
            receiver.close()

    def test_mdns_goodbye_expires_records_and_rejects_non_ipv4_targets(self):
        fingerprint = "b" * 64
        instance = f"OmaApp-{fingerprint[:12]}.{service.MDNS_SERVICE_TYPE}"
        packet = service._mdns_announcement(instance, "omaapp-bbbbbbbbbbbb.local.",
                                            "192.168.1.40", 44892, fingerprint, ttl=0)
        offset = 12
        ttl_values = []
        for _ in range(4):
            _, offset = service._dns_read_name(packet, offset)
            _record_type, _record_class, ttl, data_length = service.struct.unpack_from(
                "!HHIH", packet, offset
            )
            ttl_values.append(ttl)
            offset += 10 + data_length
        self.assertEqual(ttl_values, [0, 0, 0, 0])
        with self.assertRaises(ValueError):
            service._mdns_announcement(instance, "omaapp-bbbbbbbbbbbb.local.",
                                       "fd00::40", 44892, fingerprint)

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
            "xxl = 12\n\n[bar]\nsize-horizontal = 30\nheight = 30\nbackground = \"#222222\"\n"
            "\n[controls]\nnormal-fill-alpha = 0.08\nnormal-border = \"hyprland.active-border\"\n"
            "\n[launcher]\nbackground-alpha = 0.95\nselected-text = \"#7aa2f7\"\nunsafe = \"ignore\"\n",
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
            "animations": [[
                {"name": "windows", "enabled": True, "overridden": True,
                 "speed": 3.5, "bezier": "easeOutQuint", "style": "popin 80%"},
                {"name": "bad;name", "enabled": True, "overridden": False,
                 "speed": 1, "bezier": "", "style": ""},
            ], [
                {"name": "easeOutQuint", "X0": 0.23, "Y0": 1, "X1": 0.32, "Y1": 1},
                {"name": "invalid", "X0": -1, "Y0": 1, "X1": 0, "Y1": 1},
            ]],
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
        self.assertEqual(snapshot["theme"]["shell"]["bar"]["size-horizontal"], 30)
        self.assertEqual(snapshot["theme"]["shell"]["bar"]["background"], "#222222")
        self.assertNotIn("height", snapshot["theme"]["shell"]["bar"])
        self.assertEqual(snapshot["theme"]["shell"]["controls"]["normal-fill-alpha"], 0.08)
        self.assertEqual(snapshot["theme"]["shell"]["controls"]["normal-border"], "hyprland.active-border")
        self.assertEqual(snapshot["theme"]["shell"]["launcher"]["background-alpha"], 0.95)
        self.assertNotIn("unsafe", snapshot["theme"]["shell"]["launcher"])
        self.assertEqual(snapshot["hyprland"]["roundingPower"], 4.0)
        self.assertEqual(snapshot["hyprland"]["activeBorder"], "rgba(33ccffee) rgba(00ff99ee) 45deg")
        self.assertEqual(snapshot["hyprland"]["activeBorderAngle"], 45.0)
        self.assertEqual(snapshot["hyprland"]["inactiveBorder"], "rgba(595959aa) 0deg")
        self.assertEqual(snapshot["hyprland"]["gapsIn"], 6)
        self.assertEqual(snapshot["hyprland"]["gapsOut"], 10)
        self.assertEqual(snapshot["wallpaper"]["name"], "wallpaper.png")
        self.assertEqual(snapshot["animations"]["items"][0]["name"], "windows")
        self.assertEqual(snapshot["animations"]["items"][0]["style"], "popin 80%")
        self.assertEqual(snapshot["animations"]["beziers"], [
            {"name": "easeOutQuint", "X0": 0.23, "Y0": 1, "X1": 0.32, "Y1": 1}
        ])
        self.assertEqual(wallpaper_path, (theme_dir / "wallpaper.png").resolve())
        self.assertNotIn(str(wallpaper_path), json.dumps(snapshot))

    def test_feature_permissions_are_independent_and_disabled_by_default(self):
        auth = self.enable_device({})
        status, body = self.get("/v1/system", auth)
        self.assertEqual(status, 403)
        self.assertEqual(body["permission"], "system.read")
        self.assertFalse(self.state.has_permission(self.state.devices["phone_feature_test_device"], "clipboard"))

        self.assertTrue(self.state.set_permission("phone_feature_test_device", "clipboard", True))
        self.assertFalse(self.state.has_permission(self.state.devices["phone_feature_test_device"], "files.send"))
        persisted = json.loads(self.state.devices_path.read_text(encoding="utf-8"))
        self.assertTrue(persisted["phone_feature_test_device"]["permissions"]["clipboard"])
        self.assertFalse(self.state.set_permission("phone_feature_test_device", "shell", True))

        status, body = self.get("/v1/system", auth)
        self.assertEqual(status, 403)
        self.assertEqual(body["permission"], "system.read")

    def test_system_workspace_and_mpris_routes_require_their_separate_grants(self):
        auth = self.enable_device({
            "system.read": True,
            "workspace.control": True,
            "media.control": True,
        })
        with patch.object(service, "_system_status", return_value={"battery": {"percent": 81}}):
            self.assertEqual(self.get("/v1/system", auth), (200, {"battery": {"percent": 81}}))
        with patch.object(service, "_hyprland_workspaces", return_value=[{"id": 2, "focused": True}]):
            self.assertEqual(self.get("/v1/workspaces", auth), (200, {"workspaces": [{"id": 2, "focused": True}]}))
        with patch.object(service, "_mpris_players", return_value=[{"player": "mpv", "status": "Playing"}]):
            self.assertEqual(self.get("/v1/media", auth), (200, {"players": [{"player": "mpv", "status": "Playing"}]}))

        calls = []
        with patch.object(service, "_run_command", side_effect=lambda args, **_kwargs: calls.append(args) or b""):
            self.assertEqual(self.post("/v1/workspaces/activate", {"workspaceId": 2}, auth),
                             (200, {"status": "activated", "workspaceId": 2}))
            self.assertEqual(self.post("/v1/media", {"player": "mpv", "action": "next"}, auth),
                             (200, {"status": "sent", "player": "mpv", "action": "next"}))
        self.assertEqual(calls, [
            ["hyprctl", "dispatch", "workspace", "2"],
            ["playerctl", "-p", "mpv", "next"],
        ])

        self.assertEqual(self.post("/v1/workspaces/activate", {"workspaceId": True}, auth)[0], 400)
        self.assertEqual(self.post("/v1/media", {"player": "mpv;id", "action": "next"}, auth)[0], 400)

    def test_mpris_playerctl_metadata_template_and_response(self):
        fixture_bin = Path(self.temp_dir.name) / "bin"
        fixture_bin.mkdir()
        playerctl = fixture_bin / "playerctl"
        playerctl.write_text(
            "#!/usr/bin/env python3\n"
            "import sys\n"
            "if sys.argv[1:] == ['-l']:\n"
            "    print('mpv')\n"
            "elif len(sys.argv) == 6 and sys.argv[1:3] == ['-p', 'mpv'] and sys.argv[3:5] == ['metadata', '--format']:\n"
            "    if sys.argv[5] != '{{status}}\\n{{artist}}\\n{{title}}\\n{{album}}':\n"
            "        raise SystemExit(2)\n"
            "    print('Playing\\nCI Artist\\nBefore next\\nOmaApp')\n"
            "else:\n"
            "    raise SystemExit(2)\n",
            encoding="utf-8",
        )
        playerctl.chmod(0o755)

        with patch.dict(os.environ, {
                "PATH": str(fixture_bin) + os.pathsep + os.environ.get("PATH", "")
        }):
            self.assertEqual(service._mpris_players(), [{
                "player": "mpv", "status": "Playing", "artist": "CI Artist",
                "title": "Before next", "album": "OmaApp",
            }])

    def test_clipboard_is_text_only_bounded_and_scoped(self):
        auth = self.enable_device({"clipboard": True})
        observed = []

        def fake_command(args, **kwargs):
            observed.append((args, kwargs.get("input_data")))
            return b"from computer" if args[0] == "wl-paste" else b""

        with patch.object(service, "_run_command", side_effect=fake_command):
            self.assertEqual(self.get("/v1/clipboard", auth), (200, {"text": "from computer"}))
            self.assertEqual(self.post("/v1/clipboard", {"text": "from phone"}, auth),
                             (200, {"status": "copied"}))
            escaped = "a\n" * 30_000
            self.assertEqual(self.post("/v1/clipboard", {"text": escaped}, auth),
                             (200, {"status": "copied"}))
            self.assertEqual(self.post("/v1/clipboard", {"text": "x" * (service.MAX_CLIPBOARD_BYTES + 1)}, auth)[0], 400)
        self.assertEqual(observed[1][1], b"from phone")
        self.assertEqual(observed[2][1], escaped.encode("utf-8"))

    def test_file_transfer_is_scoped_confined_and_does_not_overwrite(self):
        auth = self.enable_device({"files.receive": True, "files.send": True})
        downloads = Path(self.temp_dir.name) / "downloads"
        with patch.dict(os.environ, {"XDG_DOWNLOAD_DIR": str(downloads)}):
            payload = b"from phone\x00attachment"
            connection = self.connection()
            connection.request("POST", "/v1/files", payload, {
                "Content-Type": "application/octet-stream",
                "X-OmaApp-File-Name": quote("notes.txt"),
            } | auth)
            response = connection.getresponse()
            result = json.loads(response.read())
            connection.close()
            self.assertEqual(response.status, 201)
            self.assertEqual(result, {"name": "notes.txt", "size": len(payload)})
            saved = downloads / "OmaApp" / "notes.txt"
            self.assertEqual(saved.read_bytes(), payload)
            self.assertEqual(saved.stat().st_mode & 0o777, 0o600)

            status, listing = self.get("/v1/files", auth)
            self.assertEqual(status, 200)
            self.assertEqual(listing["files"], [{"name": "notes.txt", "size": len(payload)}])
            status, body, content_type = self.get_bytes("/v1/files/notes.txt", auth)
            self.assertEqual(status, 200)
            self.assertEqual(body, payload)
            self.assertEqual(content_type, "text/plain")

            connection = self.connection()
            connection.request("POST", "/v1/files", b"replacement", {
                "X-OmaApp-File-Name": "notes.txt",
            } | auth)
            response = connection.getresponse()
            conflict = json.loads(response.read())
            connection.close()
            self.assertEqual(response.status, 409)
            self.assertEqual(conflict["error"], "file_exists")
            self.assertEqual(saved.read_bytes(), payload)

            connection = self.connection()
            connection.request("POST", "/v1/files", b"bad", {
                "X-OmaApp-File-Name": quote("../outside.txt"),
            } | auth)
            response = connection.getresponse()
            invalid = json.loads(response.read())
            connection.close()
            self.assertEqual(response.status, 400)
            self.assertEqual(invalid["error"], "invalid_file")

    def test_file_upload_and_listing_have_separate_grants(self):
        downloads = Path(self.temp_dir.name) / "downloads"
        with patch.dict(os.environ, {"XDG_DOWNLOAD_DIR": str(downloads)}):
            receive_only = self.enable_device({"files.receive": True})
            connection = self.connection()
            connection.request("POST", "/v1/files", b"a", {
                "X-OmaApp-File-Name": "a.txt",
            } | receive_only)
            response = connection.getresponse()
            response.read()
            connection.close()
            self.assertEqual(response.status, 201)
            self.assertEqual(self.get("/v1/files", receive_only)[0], 403)

            send_only = self.enable_device({"files.send": True})
            self.assertEqual(self.get("/v1/files", send_only)[0], 200)
            self.assertEqual(self.get_bytes("/v1/files/a.txt", send_only)[0], 200)
            connection = self.connection()
            connection.request("POST", "/v1/files", b"b", {
                "X-OmaApp-File-Name": "b.txt",
            } | send_only)
            response = connection.getresponse()
            response.read()
            connection.close()
            self.assertEqual(response.status, 403)

    def test_file_receive_rejects_symlink_destination_directory(self):
        auth = self.enable_device({"files.receive": True})
        downloads = Path(self.temp_dir.name) / "downloads"
        downloads.mkdir()
        outside = Path(self.temp_dir.name) / "outside"
        outside.mkdir(mode=0o755)
        (downloads / "OmaApp").symlink_to(outside, target_is_directory=True)

        with patch.dict(os.environ, {"XDG_DOWNLOAD_DIR": str(downloads)}):
            connection = self.connection()
            connection.request("POST", "/v1/files", b"do not escape", {
                "X-OmaApp-File-Name": "unexpected.desktop",
            } | auth)
            response = connection.getresponse()
            result = json.loads(response.read())
            connection.close()

        self.assertEqual(response.status, 503)
        self.assertEqual(result, {"error": "destination_unavailable"})
        self.assertEqual(list(outside.iterdir()), [])
        self.assertEqual(outside.stat().st_mode & 0o777, 0o755)

    def test_user_commands_run_only_host_configured_argument_vectors(self):
        auth = self.enable_device({"commands": True})
        config_root = Path(self.temp_dir.name) / "config"
        config_directory = config_root / "omaapp"
        config_directory.mkdir(parents=True)
        marker = Path(self.temp_dir.name) / "command-ran.txt"
        script = Path(self.temp_dir.name) / "safe-command"
        script.write_text(
            "#!/usr/bin/python3\n"
            "import pathlib, sys\n"
            "pathlib.Path(sys.argv[1]).write_text(sys.argv[2], encoding='utf-8')\n",
            encoding="utf-8",
        )
        script.chmod(0o700)
        command_config = (
            "[[command]]\n"
            'id = "focus-terminal"\n'
            'label = "Focus terminal"\n'
            f"argv = [{json.dumps(str(script))}, {json.dumps(str(marker))}, \"fixed argument\"]\n"
        )
        (config_directory / "commands.toml").write_text(command_config, encoding="utf-8")

        with patch.dict(os.environ, {"XDG_CONFIG_HOME": str(config_root)}):
            status, features = self.get("/v1/features", auth)
            self.assertEqual(status, 200)
            self.assertEqual(features["commands"], [{"id": "focus-terminal", "label": "Focus terminal"}])
            self.assertEqual(self.post("/v1/commands/execute", {"commandId": "../etc/passwd"}, auth)[0], 400)
            self.assertEqual(self.post("/v1/commands/execute", {"commandId": "not-configured"}, auth)[0], 404)
            status, result = self.post("/v1/commands/execute", {"commandId": "focus-terminal"}, auth)
            self.assertEqual(status, 200)
            self.assertEqual(result["status"], "completed")
        self.assertEqual(marker.read_text(encoding="utf-8"), "fixed argument")


if __name__ == "__main__":
    unittest.main()
