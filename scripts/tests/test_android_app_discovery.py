from pathlib import Path
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
            'adb logcat -d -v threadtime -t 10000 '
            '> "$smoke_artifacts/startup-logcat.txt"'
        )
        backend_assertion = workflow.index(
            "grep -F 'OmaApp initialized Qt Quick graphics API: "
            "${{ matrix.rhi_backend }}'"
        )
        translation_smoke = workflow.index(
            'bash scripts/ci/android_translation_smoke.sh "$smoke_artifacts/translations"'
        )
        self.assertLess(startup_log, backend_assertion)
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
        self.assertIn('attributes.get("fingerprint")', discovery)
        self.assertIn("advertisedFingerprint.equalsIgnoreCase(saved.optString(\"fingerprint\", \"\"))", bridge)
        self.assertIn('executeJson(candidate, "/v1/status", "GET", null, true)', bridge)
        self.assertIn('current.optString("token", "")', bridge)
        self.assertIn("PairingStore.writeCredential(appContext, current)", bridge)
        self.assertIn("DeviceDiscovery.start(this)", (
            ROOT / "app/android/src/app/omaapp/OmaAppActivity.java"
        ).read_text(encoding="utf-8"))
        self.assertIn("DeviceDiscovery.stop()", (
            ROOT / "app/android/src/app/omaapp/OmaAppActivity.java"
        ).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
