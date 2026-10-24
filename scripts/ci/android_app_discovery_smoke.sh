#!/usr/bin/env bash
set -euo pipefail

fixture_apk="${1:?usage: android_app_discovery_smoke.sh FIXTURE_APK ARTIFACT_DIR}"
artifacts="${2:?usage: android_app_discovery_smoke.sh FIXTURE_APK ARTIFACT_DIR}"
fixture_package="app.omaapp.launcherfixture"
mkdir -p "$artifacts"

latest_launcher_count() {
  local logcat_output
  if ! logcat_output="$(adb logcat -d -v brief -t 2000 2>/dev/null)"; then
    # Package install/uninstall can briefly invalidate the emulator's ADB
    # transport. Keep that transient failure inside the bounded wait below.
    return 0
  fi
  printf '%s\n' "$logcat_output" \
    | sed -n 's/.*OmaApp: enumerated \([0-9][0-9]*\) launcher activities.*/\1/p' \
    | tail -n 1
}

await_launcher_count() {
  local expected="$1"
  for attempt in {1..30}; do
    local observed
    observed="$(latest_launcher_count)"
    if [[ "$observed" == "$expected" ]]; then
      return 0
    fi
    if [[ -z "$observed" ]] && (( attempt % 5 == 0 )); then
      adb reconnect offline >/dev/null 2>&1 || true
    fi
    sleep 1
  done
  printf 'Timed out waiting for launcher activity count %s; observed %s.\n' \
    "$expected" "$(latest_launcher_count)" >&2
  adb logcat -d -v threadtime -t 3000 > "$artifacts/launcher-discovery-failure.log" 2>&1 \
    || printf 'ADB logcat remained unavailable after bounded launcher refresh retries.\n' \
      >> "$artifacts/launcher-discovery-failure.log"
  return 1
}

adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 2
baseline="$(latest_launcher_count)"
if [[ ! "$baseline" =~ ^[0-9]+$ ]] || (( baseline == 0 )); then
  printf 'Could not read the real launcher model package count.\n' >&2
  exit 1
fi

# The package is installed while OmaApp remains the selected foreground HOME.
# A changed model count therefore proves a package broadcast refreshed the live
# Qt launcher model rather than a later Activity resume doing the work.
adb install "$fixture_apk"
await_launcher_count "$((baseline + 1))"
adb logcat -d -v threadtime -t 3000 > "$artifacts/launcher-discovery-installed.log"
grep -F "OmaApp: enumerated $((baseline + 1)) launcher activities" \
  "$artifacts/launcher-discovery-installed.log"

adb uninstall "$fixture_package"
await_launcher_count "$baseline"
adb logcat -d -v threadtime -t 3000 > "$artifacts/launcher-discovery-removed.log"
grep -F "OmaApp: enumerated $baseline launcher activities" \
  "$artifacts/launcher-discovery-removed.log"
