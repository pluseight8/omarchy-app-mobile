from pathlib import Path
import os
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidAppDiscoveryTests(unittest.TestCase):
    def test_renderer_log_uses_the_pid_after_translation_smoke_restart(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        translation_smoke = workflow.index(
            'bash scripts/ci/android_translation_smoke.sh "$smoke_artifacts/translations"'
        )
        refresh_pid = workflow.index(
            'app_pid="$(adb shell pidof app.omaapp | tr -d', translation_smoke
        )
        renderer_log = workflow.index(
            'adb logcat -d -v threadtime --pid="$app_pid" -t 10000 '
            '> "$smoke_artifacts/renderer-logcat.txt"', translation_smoke
        )
        self.assertLess(refresh_pid, renderer_log)

    def test_rhi_backend_is_asserted_from_initial_startup_log(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        startup_log = workflow.index(
            'startup_pid="$(adb shell pidof app.omaapp | tr -d',
        )
        renderer_log = workflow.index(
            'adb logcat -d -v threadtime --pid="$startup_pid" -t 10000 \\\n'
            '                > "$smoke_artifacts/startup-logcat.txt"'
        )
        backend_assertion = workflow.index(
            "grep -F 'OmaApp initialized Qt Quick graphics API: "
            "${{ matrix.rhi_backend }}'"
        )
        translation_smoke = workflow.index(
            'bash scripts/ci/android_translation_smoke.sh "$smoke_artifacts/translations"'
        )
        self.assertLess(startup_log, renderer_log)
        self.assertLess(renderer_log, backend_assertion)
        self.assertLess(backend_assertion, translation_smoke)
        self.assertIn(
            '"$smoke_artifacts/startup-logcat.txt"',
            workflow[backend_assertion : backend_assertion + 200],
        )
        self.assertNotIn(
            "OmaApp initialized Qt Quick graphics API:", workflow[translation_smoke:]
        )

    def test_package_lifecycle_refresh_is_scoped_and_coalesced(self):
        activity = (ROOT / "app/android/src/app/omaapp/OmaAppActivity.java").read_text(
            encoding="utf-8"
        )
        for action in (
            "ACTION_PACKAGE_ADDED",
            "ACTION_PACKAGE_REMOVED",
            "ACTION_PACKAGE_CHANGED",
            "ACTION_PACKAGE_REPLACED",
            "ACTION_PACKAGE_FULLY_REMOVED",
        ):
            self.assertIn(f"Intent.{action}", activity)
        self.assertIn('filter.addDataScheme("package")', activity)
        self.assertIn("Context.RECEIVER_EXPORTED", activity)
        self.assertIn("postDelayed(refreshLauncherAfterPackageChange, 250L)", activity)
        self.assertIn("unregisterReceiver(packageChanges)", activity)
        self.assertIn("removeCallbacks(refreshLauncherAfterPackageChange)", activity)

    def test_emulator_installs_and_removes_a_real_launcher_fixture(self):
        manifest = (
            ROOT
            / "scripts/ci/android/launcher_fixture/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        smoke = (ROOT / "scripts/ci/android_app_discovery_smoke.sh").read_text(
            encoding="utf-8"
        )
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn('package="app.omaapp.launcherfixture"', manifest)
        self.assertIn("android.intent.category.LAUNCHER", manifest)
        self.assertIn('adb install "$fixture_apk"', smoke)
        self.assertIn('adb uninstall "$fixture_package"', smoke)
        self.assertIn('await_launcher_count "$((baseline + 1))"', smoke)
        self.assertIn('await_launcher_count "$baseline"', smoke)
        self.assertIn("android_app_discovery_smoke.sh", workflow)

    def test_smoke_retries_after_transient_adb_logcat_disconnect(self):
        smoke = ROOT / "scripts/ci/android_app_discovery_smoke.sh"
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary = Path(temporary_directory)
            fake_bin = temporary / "bin"
            state = temporary / "state"
            artifacts = temporary / "artifacts"
            fake_bin.mkdir()
            state.mkdir()
            fake_adb = fake_bin / "adb"
            fake_adb.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
state="$OMAAPP_FAKE_ADB_STATE"
case "${1:-}" in
  shell) exit 0 ;;
  install) touch "$state/installed"; printf 'Success\\n'; exit 0 ;;
  uninstall) touch "$state/removed"; printf 'Success\\n'; exit 0 ;;
  reconnect) exit 0 ;;
  logcat)
    if [[ -e "$state/installed" && ! -e "$state/removed" && ! -e "$state/disconnected" ]]; then
      touch "$state/disconnected"
      printf 'error: device offline\\n' >&2
      exit 255
    fi
    if [[ -e "$state/installed" && ! -e "$state/removed" ]]; then count=11; else count=10; fi
    printf 'OmaApp: enumerated %s launcher activities\\n' "$count"
    ;;
  *) printf 'unexpected adb arguments: %s\\n' "$*" >&2; exit 2 ;;
esac
""",
                encoding="utf-8",
            )
            fake_adb.chmod(0o755)
            fake_sleep = fake_bin / "sleep"
            fake_sleep.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            fake_sleep.chmod(0o755)
            environment = os.environ.copy()
            environment["PATH"] = f"{fake_bin}{os.pathsep}{environment['PATH']}"
            environment["OMAAPP_FAKE_ADB_STATE"] = str(state)

            completed = subprocess.run(
                ["bash", str(smoke), "fixture.apk", str(artifacts)],
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("OmaApp: enumerated 11 launcher activities", completed.stdout)
        self.assertIn("OmaApp: enumerated 10 launcher activities", completed.stdout)

    def test_dns_sd_discovery_requires_saved_trust_and_pinned_tls_refresh(self):
        discovery = (ROOT / "app/android/src/app/omaapp/DeviceDiscovery.java").read_text(
            encoding="utf-8"
        )
        bridge = (ROOT / "app/android/src/app/omaapp/PairingBridge.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("!PairingBridge.hasTrustedDevice(context)", discovery)
        self.assertIn('service.getServiceName().startsWith("OmaApp-")', discovery)
        self.assertIn("address.isSiteLocalAddress()", discovery)
        self.assertIn("service.getHostAddresses()", discovery)
        self.assertIn("firstSiteLocalIpv4(resolved)", discovery)
        self.assertIn('attributes.get("fingerprint")', discovery)
        self.assertIn("advertisedFingerprint.equalsIgnoreCase(saved.optString(\"fingerprint\", \"\"))", bridge)
        self.assertIn('executeJson(candidate, "/v1/status", "GET", null, true)', bridge)
        self.assertIn('current.optString("token", "")', bridge)
        self.assertIn("PairingStore.writeCredential(appContext, current)", bridge)
        self.assertIn("Queued DNS-SD reconnect until the current network operation finishes.", bridge)
        self.assertIn("MAIN.postDelayed(() -> retryQueuedDiscovery(appContext), 250L)", bridge)
        self.assertIn("queuedDiscoveryAddress = address", bridge)
        self.assertIn("if (!startNetwork)", bridge)
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", bridge)
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", discovery)
        self.assertNotIn("Log.i(\"OmaAppDiscovery\", address", bridge)
        self.assertIn("DeviceDiscovery.start(this)", (
            ROOT / "app/android/src/app/omaapp/OmaAppActivity.java"
        ).read_text(encoding="utf-8"))
        self.assertIn("DeviceDiscovery.stop()", (
            ROOT / "app/android/src/app/omaapp/OmaAppActivity.java"
        ).read_text(encoding="utf-8"))

    def test_android_emulator_exercises_real_nsd_and_pinned_endpoint_change(self):
        instrumentation = (
            ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
        ).read_text(encoding="utf-8")
        nsd_fixture = (
            ROOT / "scripts/ci/android/launcher_fixture/NsdFixtureService.java"
        ).read_text(encoding="utf-8")
        nsd_activity = (
            ROOT / "scripts/ci/android/launcher_fixture/NsdFixtureActivity.java"
        ).read_text(encoding="utf-8")
        fixture_manifest = (
            ROOT / "scripts/ci/android/launcher_fixture/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        fixture_build = (
            ROOT / "scripts/ci/build_android_launcher_fixture.sh"
        ).read_text(encoding="utf-8")
        prepare = (ROOT / "scripts/ci/prepare_android_pairing_smoke.py").read_text(
            encoding="utf-8"
        )
        pairing_smoke = (ROOT / "scripts/ci/android_pairing_smoke.py").read_text(
            encoding="utf-8"
        )
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("startDnsSdFixture(context", instrumentation)
        self.assertIn("NsdFixtureService", instrumentation)
        self.assertIn("NsdFixtureActivity", instrumentation)
        self.assertIn("context.startActivity(fixtureActivity)", instrumentation)
        self.assertIn("stopDnsSdFixture(context)", instrumentation)
        self.assertIn("manager.registerService(service, NsdManager.PROTOCOL_DNS_SD", nsd_fixture)
        self.assertIn("startService(service)", nsd_activity)
        self.assertIn("stopService(new Intent(this, NsdFixtureService.class))", nsd_activity)
        self.assertIn('"app.omaapp", "app.omaapp.OmaAppActivity"', nsd_activity)
        self.assertIn("DeviceDiscovery.start(context)", instrumentation)
        self.assertIn('"0".repeat(64)', instrumentation)
        self.assertIn("An untrusted DNS-SD fingerprint changed the saved endpoint.", instrumentation)
        self.assertIn("Android NSD did not reconnect to the TLS-pinned endpoint.", instrumentation)
        self.assertIn("class TestTlsProxy implements AutoCloseable", instrumentation)
        self.assertIn('new InetSocketAddress("10.0.2.2", targetPort)', instrumentation)
        self.assertIn("service.setHostAddresses(Collections.singletonList(host))", nsd_fixture)
        self.assertIn('android:name=".NsdFixtureService"', fixture_manifest)
        self.assertIn('android:name=".NsdFixtureActivity"', fixture_manifest)
        self.assertIn('android:exported="false"', fixture_manifest)
        self.assertIn('"$source_dir/NsdFixtureActivity.java"', fixture_build)
        self.assertIn('"$source_dir/NsdFixtureService.java"', fixture_build)
        self.assertIn('FIXTURE_QUERY =', prepare)
        install = workflow.index("adb install build/android-launcher-fixture/launcher-fixture.apk")
        run_smoke = workflow.index("python3 scripts/ci/android_pairing_smoke.py")
        uninstall = workflow.index("adb uninstall app.omaapp.launcherfixture", run_smoke)
        self.assertLess(install, run_smoke)
        self.assertLess(run_smoke, uninstall)
        self.assertIn('"dnsSdPort"', instrumentation)
        self.assertIn("dns_sd_port=state.port", pairing_smoke)
        self.assertIn('patch.object(service.MdnsAdvertiser, "start"', pairing_smoke)
        self.assertIn("restarted_state.port == old_port", pairing_smoke)


if __name__ == "__main__":
    unittest.main()
