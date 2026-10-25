#!/usr/bin/env bash
set -euo pipefail

fixture_apk="${1:?usage: android_appwidget_host_smoke.sh FIXTURE_APK ARTIFACT_DIR}"
artifacts="${2:?usage: android_appwidget_host_smoke.sh FIXTURE_APK ARTIFACT_DIR}"
package="app.omaapp.widgetfixture"
instrumentation="app.omaapp/app.omaapp.PairingTestInstrumentation"
mkdir -p "$artifacts"

if ! adb shell pm list packages "$package" | tr -d '\r' | grep -Fq "package:$package"; then
  adb install "$fixture_apk"
fi

run_stage() {
  local mode="$1"
  local expected="$2"
  local output="$artifacts/$mode-instrumentation.txt"
  timeout 120s adb shell am instrument -w -e androidWidgetHost "$mode" "$instrumentation" \
    2>&1 | tee "$output"
  grep -F "INSTRUMENTATION_STATUS: stage=$expected" "$output"
  grep -F 'INSTRUMENTATION_CODE: -1' "$output"
}

run_stage setup android_widget_host_picker_bind_touch_passed
adb shell am force-stop app.omaapp
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity \
  > "$artifacts/relaunch-after-widget-host.txt"
run_stage restore android_widget_host_process_restore_passed
run_stage remove android_widget_host_id_release_passed
adb uninstall "$package"
