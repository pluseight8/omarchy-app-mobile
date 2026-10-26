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
  timeout 180s adb shell am instrument -w -e androidWidgetHost "$mode" "$instrumentation" \
    2>&1 | tee "$output"
  grep -F "INSTRUMENTATION_STATUS: stage=$expected" "$output"
  grep -F 'INSTRUMENTATION_CODE: -1' "$output"
}

run_stage setup android_widget_host_picker_bind_touch_passed
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_second_home_page_ready' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_second_page_placement_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_cross_page_move_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_layout_roundtrip_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_backup_transaction_rollback_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_backup_recovery_prepared' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_schema_migration_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_qml_controls_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_resize_handle_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_resize_undo_passed' \
  "$artifacts/setup-instrumentation.txt"
adb pull /sdcard/Android/data/app.omaapp/files/omaapp-appwidget-home-layout.png \
  "$artifacts/widget-home-layout.png"
# Some Android builds terminate the target Qt process when the instrumentation
# runner exits. An empty PID here is a valid already-stopped starting state.
pid_before="$(adb shell pidof app.omaapp | tr -d '\r' || true)"
printf '%s\n' "$pid_before" > "$artifacts/pid-before-process-death.txt"
adb shell am force-stop app.omaapp
pid_after_force_stop="$pid_before"
if [[ -n "$pid_before" ]]; then
  for attempt in {1..40}; do
    pid_after_force_stop="$(adb shell pidof app.omaapp | tr -d '\r' || true)"
    if [[ "$pid_after_force_stop" != "$pid_before" ]]; then
      break
    fi
    sleep 0.25
  done
  if [[ "$pid_after_force_stop" == "$pid_before" ]]; then
    printf 'Android did not terminate or restart the OmaApp process after force-stop.\n' >&2
    exit 1
  fi
fi
printf '%s\n' "$pid_after_force_stop" > "$artifacts/pid-after-force-stop.txt"
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity \
  > "$artifacts/relaunch-after-widget-host.txt"
pid_after_start=""
for attempt in {1..40}; do
  pid_after_start="$(adb shell pidof app.omaapp | tr -d '\r' || true)"
  if [[ -n "$pid_after_start" ]]; then
    break
  fi
  sleep 0.25
done
if [[ -z "$pid_after_start" || "$pid_after_start" == "$pid_before" ]]; then
  printf 'OmaApp did not relaunch in a new process after AppWidgetHost force-stop.\n' >&2
  exit 1
fi
printf '%s\n' "$pid_after_start" > "$artifacts/pid-after-relaunch.txt"
run_stage restore android_widget_host_process_restore_passed
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_geometry_restore_passed' \
  "$artifacts/restore-instrumentation.txt"
run_stage remove android_widget_host_id_release_passed
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_backup_placeholder_passed' \
  "$artifacts/remove-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_backup_transaction_commit_passed' \
  "$artifacts/remove-instrumentation.txt"
adb pull /sdcard/Android/data/app.omaapp/files/omaapp-appwidget-rebind-placeholder.png \
  "$artifacts/widget-rebind-placeholder.png"
test -s "$artifacts/widget-home-layout.png"
test -s "$artifacts/widget-rebind-placeholder.png"
adb uninstall "$package"
