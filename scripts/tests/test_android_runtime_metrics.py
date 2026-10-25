import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class AndroidRuntimeMetricsTests(unittest.TestCase):
    def test_emulator_runtime_report_parses_real_android_measurement_formats(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary = Path(temporary_directory)
            fake_bin = temporary / "bin"
            fake_bin.mkdir()
            adb = fake_bin / "adb"
            adb.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  "shell am start -a android.settings.SETTINGS") exit 0 ;;
  "shell am force-stop app.omaapp") : > "$FAKE_ADB_STOPPED" ;;
  "shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity")
    : > "$FAKE_ADB_STARTED"
    printf 'Status: ok\\r\\nLaunchState: COLD\\r\\nThisTime: 812\\r\\nTotalTime: 812\\r\\nWaitTime: 824\\r\\n'
    ;;
  "shell pidof app.omaapp")
    if [[ -f "$FAKE_ADB_STARTED" ]]; then printf '4321\\r\\n'; fi
    ;;
  "shell dumpsys meminfo 4321") printf 'TOTAL PSS: 42,816 TOTAL RSS: 80,104\\n' ;;
  "shell dumpsys cpuinfo") printf '  0.2%% 4321/app.omaapp:remote\\n' ;;
  *) printf 'unexpected adb arguments: %s\\n' "$*" >&2; exit 2 ;;
esac
""",
                encoding="utf-8",
            )
            adb.chmod(0o755)
            fake_sleep = fake_bin / "sleep"
            fake_sleep.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            fake_sleep.chmod(0o755)
            environment = os.environ.copy()
            environment["PATH"] = f"{fake_bin}{os.pathsep}{environment['PATH']}"
            environment["FAKE_ADB_STOPPED"] = str(temporary / "adb-stopped")
            environment["FAKE_ADB_STARTED"] = str(temporary / "adb-started")
            artifacts = temporary / "artifacts"

            completed = subprocess.run(
                ["bash", str(ROOT / "scripts/ci/android_runtime_metrics.sh"), str(artifacts)],
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(completed.returncode, 0, completed.stderr)
            report = json.loads((artifacts / "runtime-metrics.json").read_text(encoding="utf-8"))
            self.assertEqual(report["coldStartLaunchState"], "COLD")
            self.assertEqual(report["coldStartTotalTimeMs"], 812)
            self.assertEqual(report["coldStartActivityTimeMs"], 812)
            self.assertEqual(report["coldStartWaitTimeMs"], 824)
            self.assertEqual(report["idleSampleSecondsAfterLaunch"], 8)
            self.assertEqual(report["idleProcessResidentSetPssKb"], 42816)
            self.assertEqual(report["idleProcessCpuPercent"], 0.2)
            self.assertEqual(report["appPid"], "4321")

    def test_runtime_measurements_are_uploaded_as_android_smoke_artifacts(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn('bash scripts/ci/android_runtime_metrics.sh "$smoke_artifacts/runtime-metrics"', workflow)
        self.assertIn('path: ${{ github.workspace }}/ci-artifacts/android-smoke/', workflow)

    def test_runtime_report_fails_if_required_android_measurements_are_missing(self):
        script = (ROOT / "scripts/ci/android_runtime_metrics.sh").read_text(encoding="utf-8")

        self.assertIn('"coldStartTotalTimeMs"', script)
        self.assertIn('"idleProcessResidentSetPssKb"', script)
        self.assertIn('if missing:', script)
        self.assertIn('"COLD"', script)
        self.assertIn('android.settings.SETTINGS', script)


if __name__ == "__main__":
    unittest.main()
