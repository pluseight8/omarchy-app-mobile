#!/usr/bin/env bash
set -euo pipefail

fixture_apk="${1:?usage: android_appwidget_host_smoke.sh FIXTURE_APK ARTIFACT_DIR}"
artifacts="${2:?usage: android_appwidget_host_smoke.sh FIXTURE_APK ARTIFACT_DIR}"
package="app.omaapp.widgetfixture"
instrumentation="app.omaapp/app.omaapp.PairingTestInstrumentation"
mkdir -p "$artifacts"
adb logcat -c
logcat_pid=""
stop_logcat_capture() {
  if [[ -n "$logcat_pid" ]]; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
    logcat_pid=""
  fi
}
trap stop_logcat_capture EXIT
adb logcat -b all -v threadtime > "$artifacts/appwidget-host-logcat.txt" &
logcat_pid=$!

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
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_corrupt_records_preserve_ids_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_qml_controls_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_resize_handle_passed' \
  "$artifacts/setup-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_resize_undo_passed' \
  "$artifacts/setup-instrumentation.txt"
adb pull /sdcard/Android/data/app.omaapp/files/omaapp-appwidget-home-layout.png \
  "$artifacts/widget-home-layout.png"
pid_before="$(sed -n 's/^INSTRUMENTATION_RESULT: processId=//p' \
  "$artifacts/setup-instrumentation.txt" | tail -n 1)"
if [[ -z "$pid_before" ]]; then
  printf 'The widget setup instrumentation did not report its process ID.\n' >&2
  exit 1
fi
printf '%s\n' "$pid_before" > "$artifacts/pid-before-process-death.txt"
adb shell am force-stop app.omaapp
pid_after_force_stop=""
for attempt in {1..40}; do
  pid_after_force_stop="$(adb shell pidof app.omaapp | tr -d '\r' || true)"
  if [[ -z "$pid_after_force_stop" ]]; then
    break
  fi
  sleep 0.25
done
if [[ -n "$pid_after_force_stop" ]]; then
  printf 'Android kept the OmaApp process alive after force-stop: %s\n' \
    "$pid_after_force_stop" >&2
  exit 1
fi
printf '%s\n' "$pid_after_force_stop" > "$artifacts/pid-after-force-stop.txt"
run_stage restore android_widget_host_process_restore_passed
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_backup_recovery_passed' \
  "$artifacts/restore-instrumentation.txt"
pid_after_start="$(sed -n 's/^INSTRUMENTATION_RESULT: processId=//p' \
  "$artifacts/restore-instrumentation.txt" | tail -n 1)"
if [[ -z "$pid_after_start" || "$pid_after_start" == "$pid_before" ]]; then
  printf 'OmaApp did not restore in a new process after AppWidgetHost force-stop.\n' >&2
  exit 1
fi
printf '%s\n' "$pid_after_start" > "$artifacts/pid-after-relaunch.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_geometry_restore_passed' \
  "$artifacts/restore-instrumentation.txt"
grep -F 'INSTRUMENTATION_STATUS: stage=android_widget_host_home_page_count_restore_passed' \
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
stop_logcat_capture
test -s "$artifacts/appwidget-host-logcat.txt"
if grep -E 'FATAL EXCEPTION|Fatal signal (6|11)|JNI DETECTED ERROR|CalledFromWrongThreadException|ANR in app\.omaapp|qrc:/qt/qml/OmaApp/.*(TypeError|ReferenceError)' \
  "$artifacts/appwidget-host-logcat.txt"; then
  printf 'Android widget host smoke found a fatal app or QML error; see appwidget-host-logcat.txt.\n' >&2
  exit 1
fi
adb uninstall "$package"
