#!/usr/bin/env bash
set -euo pipefail

smoke_artifacts="${1:?usage: android_launcher_home_smoke.sh ARTIFACT_DIR APP_PID}"
app_pid="${2:?usage: android_launcher_home_smoke.sh ARTIFACT_DIR APP_PID}"
mkdir -p "$smoke_artifacts"
folder_name="newfolder"

select_home_when_needed() {
  local accessibility_xml="$1"
  local device_xml="$2"
  for attempt in 1 2 3 4 5; do
    if python3 scripts/ci/android_accessibility_assert_checked.py \
      "$accessibility_xml" 'Home' >/dev/null 2>&1; then
      return 0
    fi
    local target
    # A newly resumed Qt Activity can briefly expose only Android's root view
    # to UIAutomator. Refresh that empty hierarchy instead of treating it as
    # proof that the Home tab is missing.
    if target="$(python3 scripts/ci/android_accessibility_tap_target.py \
      "$accessibility_xml" 'Home' 2>/dev/null)"; then
      local x y
      read -r x y <<< "$target"
      adb shell input tap "$x" "$y"
      sleep 1
    else
      sleep 1
    fi
    python3 scripts/ci/android_accessibility_dump.py "$device_xml" "$accessibility_xml"
  done
  python3 scripts/ci/android_accessibility_assert_checked.py "$accessibility_xml" 'Home'
}

select_tab_when_needed() {
  local tab_label="$1"
  local accessibility_xml="$2"
  local device_xml="$3"
  for attempt in 1 2 3 4 5; do
    if python3 scripts/ci/android_accessibility_assert_checked.py \
      "$accessibility_xml" "$tab_label" >/dev/null 2>&1; then
      return 0
    fi
    local target
    target="$(python3 scripts/ci/android_accessibility_tap_target.py "$accessibility_xml" "$tab_label")"
    local x y
    read -r x y <<< "$target"
    adb shell input tap "$x" "$y"
    sleep 1
    python3 scripts/ci/android_accessibility_dump.py "$device_xml" "$accessibility_xml"
  done
  python3 scripts/ci/android_accessibility_assert_checked.py "$accessibility_xml" "$tab_label"
}

scroll_to_home_folder() {
  local accessibility_xml="$1"
  local device_xml="$2"
  local folder_label="Open $folder_name folder, 1 apps"
  for attempt in 1 2 3 4 5 6 7 8; do
    if python3 scripts/ci/android_accessibility_tap_target.py \
      --home-folder "$accessibility_xml" "$folder_label" >/dev/null 2>&1; then
      return 0
    fi
    adb shell input swipe 205 720 205 280 500
    sleep 1
    python3 scripts/ci/android_accessibility_dump.py "$device_xml" "$accessibility_xml"
  done
  python3 scripts/ci/android_accessibility_tap_target.py \
    --home-folder "$accessibility_xml" "$folder_label" >/dev/null
}

select_apps_when_needed() {
  local accessibility_xml="$1"
  local device_xml="$2"
  select_tab_when_needed 'Apps' "$accessibility_xml" "$device_xml"
}

grep -F 'app.omaapp' role.txt
grep -F 'app.omaapp' activities.txt
grep -F 'app.omaapp' resolved-home.txt

# The caller runs this while Apps is on screen; capture that live tree before
# opening the folder editor so the coordinates match the visible page.
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-create-folder.xml "$smoke_artifacts/window-create-folder.xml"
create_folder_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-create-folder.xml" 'Create folder')"
read -r create_folder_x create_folder_y <<< "$create_folder_target"
adb shell input tap "$create_folder_x" "$create_folder_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-new-folder-name.xml "$smoke_artifacts/window-new-folder-name.xml"
python3 - "$smoke_artifacts/window-new-folder-name.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
fields = [
    node for node in root.iter("node")
    if node.attrib.get("package") == "app.omaapp"
    and node.attrib.get("class") == "android.widget.EditText"
    and "New folder name" in node.attrib.get("content-desc", "")
]
if len(fields) != 1 or fields[0].attrib.get("focused") != "true":
    raise SystemExit("Create-folder dialog did not focus its name field on open.")
PY
folder_name_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-new-folder-name.xml" 'New folder name')"
read -r folder_name_x folder_name_y <<< "$folder_name_target"
adb shell input tap "$folder_name_x" "$folder_name_y"
sleep 2
for ((index = 0; index < ${#folder_name}; index++)); do
  adb shell input text "${folder_name:index:1}"
  sleep 0.35
done
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-create-folder-confirm.xml "$smoke_artifacts/window-create-folder-confirm.xml"
create_confirm_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-create-folder-confirm.xml" 'OK')"
read -r create_confirm_x create_confirm_y <<< "$create_confirm_target"
adb shell input tap "$create_confirm_x" "$create_confirm_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-folder-contents.xml "$smoke_artifacts/window-folder-contents.xml"
add_folder_apps_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-folder-contents.xml" "Add apps to $folder_name")"
read -r add_folder_apps_x add_folder_apps_y <<< "$add_folder_apps_target"
adb shell input tap "$add_folder_apps_x" "$add_folder_apps_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-folder-apps.xml "$smoke_artifacts/window-folder-apps.xml"
add_drive_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-folder-apps.xml" "Add Drive to $folder_name")"
read -r add_drive_x add_drive_y <<< "$add_drive_target"
adb shell input tap "$add_drive_x" "$add_drive_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-folder-drive-added.xml "$smoke_artifacts/window-folder-drive-added.xml"
grep -F "Remove Drive from $folder_name" "$smoke_artifacts/window-folder-drive-added.xml"
adb shell input keyevent 4
adb shell input keyevent 4
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-home-before-restart-navigation.xml \
  "$smoke_artifacts/window-home-before-restart-navigation.xml"
select_home_when_needed "$smoke_artifacts/window-home-before-restart-navigation.xml" \
  /sdcard/omaapp-home-before-restart-navigation.xml
scroll_to_home_folder "$smoke_artifacts/window-home-before-restart-navigation.xml" \
  /sdcard/omaapp-home-before-restart-navigation.xml
cp "$smoke_artifacts/window-home-before-restart-navigation.xml" \
  "$smoke_artifacts/window-folder-on-home.xml"
grep -F "Open $folder_name folder, 1 apps" "$smoke_artifacts/window-folder-on-home.xml"
apps_navigation_xml="$smoke_artifacts/window-apps-before-pin-navigation.xml"
cp "$smoke_artifacts/window-folder-on-home.xml" "$apps_navigation_xml"
select_tab_when_needed 'Apps' "$apps_navigation_xml" /sdcard/omaapp-apps-before-pin.xml
cp "$apps_navigation_xml" "$smoke_artifacts/window-apps-before-pin.xml"
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$smoke_artifacts/window-apps-before-pin.xml" 'Apps'
adb exec-out screencap -p > "$smoke_artifacts/launcher-before-pin.png"
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-before-pin.xml "$smoke_artifacts/window-before-pin.xml"
pin_target="$(python3 scripts/ci/android_launcher_tap_target.py "$smoke_artifacts/window-before-pin.xml")"
printf '%s\n' "$pin_target" > "$smoke_artifacts/pin-target.txt"
pin_succeeded=0
for attempt in 1 2 3; do
  if (( attempt > 1 )); then
    python3 scripts/ci/android_accessibility_dump.py \
      /sdcard/omaapp-before-pin-retry.xml "$smoke_artifacts/window-before-pin.xml"
    select_apps_when_needed "$smoke_artifacts/window-before-pin.xml" \
      /sdcard/omaapp-before-pin-retry.xml
    pin_target="$(python3 scripts/ci/android_launcher_tap_target.py \
      "$smoke_artifacts/window-before-pin.xml")"
    printf '%s\n' "$pin_target" > "$smoke_artifacts/pin-target.txt"
  fi
  read -r pin_x pin_y <<< "$pin_target"
  printf 'Long-pressing launcher app at %s,%s (attempt %s/3)\n' \
    "$pin_x" "$pin_y" "$attempt"
  # Keep the held pointer inside the same icon target with a minimal, nonzero path.
  adb shell input swipe "$pin_x" "$pin_y" "$((pin_x + 1))" "$((pin_y + 1))" 1200
  sleep 2
  adb exec-out screencap -p > "$smoke_artifacts/launcher-after-pin.png"
  adb logcat -d -v threadtime --pid="$app_pid" -t 5000 > "$smoke_artifacts/pinned-logcat.txt"
  pinned_count="$(grep -E 'OmaApp: pinned app count [0-9]+' \
    "$smoke_artifacts/pinned-logcat.txt" | tail -n 1 \
    | sed -E 's/.*count ([0-9]+)$/\1/' || true)"
  if [[ "${pinned_count:-0}" -gt 0 ]]; then
    pin_succeeded=1
    break
  fi
  if (( attempt < 3 )); then
    # Back exits the selected HOME app when the long-press did not open another
    # activity. Explicitly foreground OmaApp instead, so each retry starts from
    # a fresh Apps accessibility tree without leaving the HOME task.
    adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity >/dev/null
    sleep 1
  fi
done
if (( pin_succeeded == 0 )); then
  printf 'Long-press did not pin an app after 3 attempts.\n' >&2
  exit 1
fi
grep -E 'OmaApp: pinned app count [1-9][0-9]*' "$smoke_artifacts/pinned-logcat.txt"
shortcut_output="$(adb shell am instrument -w -e launcherShortcuts true app.omaapp/app.omaapp.PairingTestInstrumentation)"
printf '%s\n' "$shortcut_output"
grep -F 'INSTRUMENTATION_STATUS: stage=launcher_shortcuts_passed' <<< "$shortcut_output"
adb shell am force-stop app.omaapp
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity
sleep 8
restarted_pid="$(adb shell pidof app.omaapp | tr -d '\r')"
test -n "$restarted_pid"
printf '%s\n' "$restarted_pid" > "$smoke_artifacts/restarted-pid.txt"
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-after-restart-navigation.xml \
  "$smoke_artifacts/window-after-restart-navigation.xml"
select_tab_when_needed 'Apps' "$smoke_artifacts/window-after-restart-navigation.xml" \
  /sdcard/omaapp-after-restart-navigation.xml
for attempt in {1..10}; do
  adb logcat -d -v threadtime --pid="$restarted_pid" -t 10000 \
    > "$smoke_artifacts/restarted-logcat.txt"
  if grep -E 'OmaApp: cached launcher icon for ' \
    "$smoke_artifacts/restarted-logcat.txt" >/dev/null; then
    break
  fi
  sleep 1
done
grep -E 'OmaApp: loaded [1-9][0-9]* pinned apps' "$smoke_artifacts/restarted-logcat.txt"
grep -E 'OmaApp: cached launcher icon for ' "$smoke_artifacts/restarted-logcat.txt"
select_tab_when_needed 'Home' "$smoke_artifacts/window-after-restart-navigation.xml" \
  /sdcard/omaapp-after-restart-navigation.xml
adb exec-out screencap -p > "$smoke_artifacts/launcher-before-tap.png"
test -s "$smoke_artifacts/launcher-before-tap.png"
scroll_to_home_folder "$smoke_artifacts/window-after-restart-navigation.xml" \
  /sdcard/omaapp-after-restart-navigation.xml
cp "$smoke_artifacts/window-after-restart-navigation.xml" \
  "$smoke_artifacts/window-after-restart-folder.xml"
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-after-restart-folder.xml "$smoke_artifacts/window-after-restart-folder.xml"
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$smoke_artifacts/window-after-restart-folder.xml" 'Home'
grep -F "Open $folder_name folder, 1 apps" "$smoke_artifacts/window-after-restart-folder.xml"
persisted_folder_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
  --home-folder "$smoke_artifacts/window-after-restart-folder.xml" \
  "Open $folder_name folder, 1 apps")"
read -r persisted_folder_x persisted_folder_y <<< "$persisted_folder_target"
adb shell input tap "$persisted_folder_x" "$persisted_folder_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-persisted-folder.xml "$smoke_artifacts/window-persisted-folder.xml"
grep -F 'Launch Drive' "$smoke_artifacts/window-persisted-folder.xml"
adb shell input keyevent 4
sleep 1
adb shell input swipe 205 280 205 720 500
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-after-restart.xml "$smoke_artifacts/window-after-restart.xml"
select_apps_when_needed "$smoke_artifacts/window-after-restart.xml" \
  /sdcard/omaapp-after-restart.xml
tap_target="$(python3 scripts/ci/android_launcher_tap_target.py "$smoke_artifacts/window-after-restart.xml")"
printf '%s\n' "$tap_target" > "$smoke_artifacts/tap-target.txt"
read -r tap_x tap_y <<< "$tap_target"
printf 'Tapping launcher app at %s,%s\n' "$tap_x" "$tap_y"
adb shell input tap "$tap_x" "$tap_y"
sleep 3
adb logcat -d -v threadtime --pid="$restarted_pid" -t 5000 > "$smoke_artifacts/launch-logcat.txt"
grep -E 'OmaApp: launch activity ' "$smoke_artifacts/launch-logcat.txt" || true
adb shell screencap -p /sdcard/omaapp-after-tap.png
adb pull /sdcard/omaapp-after-tap.png "$smoke_artifacts/launcher-after-tap.png"
adb shell dumpsys activity activities > launched-app.txt
resumed_activity="$(tr -d '\r' < launched-app.txt | grep -E 'mResumedActivity|topResumedActivity|^[[:space:]]*Resumed:' | head -n 1)"
printf '%s\n' "$resumed_activity"
printf '%s\n' "$resumed_activity" > "$smoke_artifacts/resumed-activity.txt"
if [[ -z "$resumed_activity" || "$resumed_activity" == *app.omaapp* ]]; then
  printf 'Tapping the first launcher entry did not open another app.\n' >&2
  exit 1
fi
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 8

# Install the bundled offline OWL canvas widget from its on-device store and
# check that QML draws both its native Image node and Canvas image(...) call.
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-home-before-owl-store.xml "$smoke_artifacts/window-home-before-owl-store.xml"
select_home_when_needed "$smoke_artifacts/window-home-before-owl-store.xml" \
  /sdcard/omaapp-home-before-owl-store.xml
settings_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
  "$smoke_artifacts/window-home-before-owl-store.xml" 'Settings tab')"
read -r settings_x settings_y <<< "$settings_target"
adb shell input tap "$settings_x" "$settings_y"
sleep 1
owl_add_target=""
for attempt in {1..10}; do
  python3 scripts/ci/android_accessibility_dump.py \
    /sdcard/omaapp-owl-store.xml "$smoke_artifacts/window-owl-store.xml"
  if owl_add_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
    "$smoke_artifacts/window-owl-store.xml" 'Add Canvas sampler to Home' 2>/dev/null)"; then
    break
  fi
  adb shell input swipe 205 720 205 300 450
  sleep 1
done
if [[ -z "$owl_add_target" ]]; then
  printf 'Could not find the offline Canvas sampler in the OWL store.\n' >&2
  exit 1
fi
read -r owl_add_x owl_add_y <<< "$owl_add_target"
adb shell input tap "$owl_add_x" "$owl_add_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-settings-after-owl-add.xml "$smoke_artifacts/window-settings-after-owl-add.xml"
select_home_when_needed "$smoke_artifacts/window-settings-after-owl-add.xml" \
  /sdcard/omaapp-settings-after-owl-add.xml
for attempt in {1..10}; do
  adb exec-out screencap -p > "$smoke_artifacts/home-with-owl-canvas.png"
  if python3 scripts/ci/check_owl_image_rendered.py \
    "$smoke_artifacts/home-with-owl-canvas.png"; then
    break
  fi
  adb shell input swipe 205 720 205 300 450
  sleep 1
done
python3 scripts/ci/check_owl_image_rendered.py "$smoke_artifacts/home-with-owl-canvas.png"

# Exercise the offline widget editor as a real launcher user: create a local
# widget, save its source, then remove it from the store.
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-home-before-widget-editor.xml "$smoke_artifacts/window-home-before-widget-editor.xml"
select_home_when_needed "$smoke_artifacts/window-home-before-widget-editor.xml" \
  /sdcard/omaapp-home-before-widget-editor.xml
settings_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
  "$smoke_artifacts/window-home-before-widget-editor.xml" 'Settings tab')"
read -r settings_x settings_y <<< "$settings_target"
adb shell input tap "$settings_x" "$settings_y"
sleep 1
# Settings preserves its previous scroll offset; bring the editor action back
# into the visible header before asking accessibility for a real tap target.
adb shell input swipe 205 280 205 760 450
adb shell input swipe 205 280 205 760 450
sleep 1
new_widget_target=""
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-widget-editor-store.xml "$smoke_artifacts/window-widget-editor-store.xml"
if ! new_widget_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
  "$smoke_artifacts/window-widget-editor-store.xml" 'Create an OWL widget' 2>/dev/null)"; then
  adb exec-out screencap -p > "$smoke_artifacts/widget-editor-create-action-missing.png"
fi
if [[ -z "$new_widget_target" ]]; then
  printf 'The OWL widget editor action was not visible at the top of Settings.\n' >&2
  exit 1
fi
read -r new_widget_x new_widget_y <<< "$new_widget_target"
adb shell input tap "$new_widget_x" "$new_widget_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-widget-editor.xml "$smoke_artifacts/window-widget-editor.xml"
grep -F 'OWL widget source editor' "$smoke_artifacts/window-widget-editor.xml"
adb shell input keyevent 4
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-widget-editor-ready.xml "$smoke_artifacts/window-widget-editor-ready.xml"
grep -F 'OWL widget source editor' "$smoke_artifacts/window-widget-editor-ready.xml"
save_widget_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
  "$smoke_artifacts/window-widget-editor-ready.xml" 'Save widget')"
read -r save_widget_x save_widget_y <<< "$save_widget_target"
adb shell input tap "$save_widget_x" "$save_widget_y"
sleep 1
delete_widget_target=""
for attempt in {1..10}; do
  python3 scripts/ci/android_accessibility_dump.py \
    /sdcard/omaapp-widget-editor-saved.xml "$smoke_artifacts/window-widget-editor-saved.xml"
  if delete_widget_target="$(python3 scripts/ci/android_accessibility_tap_target.py \
    "$smoke_artifacts/window-widget-editor-saved.xml" 'Delete My widget' 2>/dev/null)"; then
    break
  fi
  adb shell input swipe 205 720 205 300 450
  sleep 1
done
if [[ -z "$delete_widget_target" ]]; then
  printf 'The saved local OWL widget did not appear in the store.\n' >&2
  exit 1
fi
read -r delete_widget_x delete_widget_y <<< "$delete_widget_target"
adb shell input tap "$delete_widget_x" "$delete_widget_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-widget-editor-deleted.xml "$smoke_artifacts/window-widget-editor-deleted.xml"
grep -F 'Custom widget deleted.' "$smoke_artifacts/window-widget-editor-deleted.xml"
