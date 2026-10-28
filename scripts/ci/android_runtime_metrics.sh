#!/usr/bin/env bash
set -euo pipefail

artifacts="${1:?usage: android_runtime_metrics.sh ARTIFACT_DIRECTORY}"
mkdir -p "$artifacts"

# Leave HOME before force-stop; Android may otherwise immediately keep or
# relaunch the selected launcher and report a warm/unknown launch.
adb shell am start -a android.settings.SETTINGS >/dev/null
sleep 1
adb shell am force-stop app.omaapp
for attempt in {1..15}; do
  if [[ -z "$(adb shell pidof app.omaapp | tr -d '\r')" ]]; then
    break
  fi
  sleep 0.5
done
if [[ -n "$(adb shell pidof app.omaapp | tr -d '\r')" ]]; then
  printf 'OmaApp process survived force-stop; cold-start measurement would be invalid.\n' >&2
  exit 1
fi

launch_output="$(adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity | tr -d '\r')"
printf '%s\n' "$launch_output" > "$artifacts/cold-start.txt"

app_pid=""
for attempt in {1..15}; do
  app_pid="$(adb shell pidof app.omaapp | tr -d '\r' | awk '{print $1}')"
  if [[ -n "$app_pid" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "$app_pid" ]]; then
  printf 'OmaApp did not start for runtime measurements.\n' >&2
  exit 1
fi

# Let first-frame rendering and startup work settle before collecting the idle sample.
sleep 8
adb shell dumpsys meminfo "$app_pid" > "$artifacts/meminfo-idle.txt"
adb shell dumpsys cpuinfo > "$artifacts/cpuinfo-idle.txt"
adb shell top -H -b -n 2 -d 1 -p "$app_pid" -o pid,tid,%cpu,name \
  > "$artifacts/top-threads-idle.txt"

python3 - "$artifacts" "$app_pid" <<'PY'
import json
import re
import sys
from pathlib import Path

directory = Path(sys.argv[1])
pid = sys.argv[2]
launch = (directory / "cold-start.txt").read_text(encoding="utf-8", errors="replace")
memory = (directory / "meminfo-idle.txt").read_text(encoding="utf-8", errors="replace")
cpu = (directory / "cpuinfo-idle.txt").read_text(encoding="utf-8", errors="replace")
threads = (directory / "top-threads-idle.txt").read_text(encoding="utf-8", errors="replace")

def first(pattern, text):
    match = re.search(pattern, text, re.MULTILINE)
    return match.group(1) if match else None

ui_thread_samples = []
for line in threads.splitlines():
    fields = line.split()
    if len(fields) < 4 or fields[0] != pid or fields[1] != pid:
        continue
    try:
        ui_thread_samples.append(float(fields[2].removesuffix("%")))
    except ValueError:
        continue

total_time = first(r"^TotalTime:\s*(\d+)", launch)
this_time = first(r"^ThisTime:\s*(\d+)", launch)
wait_time = first(r"^WaitTime:\s*(\d+)", launch)
launch_state = first(r"^LaunchState:\s*(\S+)", launch)
pss = first(r"^\s*TOTAL PSS:\s*([\d,]+)", memory)
cpu_sample = first(r"^\s*([\d.]+)%\s+\d+/app\.omaapp(?:\s|:)", cpu)

report = {
    "coldStartLaunchState": launch_state,
    "coldStartTotalTimeMs": int(total_time) if total_time else None,
    "coldStartActivityTimeMs": int(this_time) if this_time else None,
    "coldStartWaitTimeMs": int(wait_time) if wait_time else None,
    "idleSampleSecondsAfterLaunch": 8,
    "idleProcessResidentSetPssKb": int(pss.replace(",", "")) if pss else None,
    "idleProcessCpuPercent": float(cpu_sample) if cpu_sample else None,
    "uiThreadCpuPercent": ui_thread_samples[-1] if ui_thread_samples else None,
    "uiThreadSampleCount": len(ui_thread_samples),
    "appPid": pid,
}
missing = [key for key in (
    "coldStartTotalTimeMs",
    "idleProcessResidentSetPssKb",
    "uiThreadCpuPercent",
) if report[key] is None]
if launch_state != "COLD":
    missing.append("cold LaunchState (reported " + repr(launch_state) + ")")
if missing:
    raise SystemExit("Android runtime measurements are missing: " + ", ".join(missing))
(directory / "runtime-metrics.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
)
print(json.dumps(report, sort_keys=True))
PY
