#!/usr/bin/env bash
set -euo pipefail

smoke_artifacts="${1:?usage: android_launcher_home_smoke.sh ARTIFACT_DIR APP_PID}"
app_pid="${2:?usage: android_launcher_home_smoke.sh ARTIFACT_DIR APP_PID}"
mkdir -p "$smoke_artifacts"
folder_name="newfolder"

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
folder_name_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-new-folder-name.xml" 'New folder name')"
read -r folder_name_x folder_name_y <<< "$folder_name_target"
adb shell input tap "$folder_name_x" "$folder_name_y"
adb shell input text "$folder_name"
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
  /sdcard/omaapp-folder-on-home.xml "$smoke_artifacts/window-folder-on-home.xml"
grep -F "Open $folder_name folder, 1 apps" "$smoke_artifacts/window-folder-on-home.xml"
adb exec-out screencap -p > "$smoke_artifacts/launcher-before-pin.png"
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-before-pin.xml "$smoke_artifacts/window-before-pin.xml"
pin_target="$(python3 scripts/ci/android_launcher_tap_target.py "$smoke_artifacts/window-before-pin.xml")"
printf '%s\n' "$pin_target" > "$smoke_artifacts/pin-target.txt"
read -r pin_x pin_y <<< "$pin_target"
printf 'Long-pressing launcher app at %s,%s\n' "$pin_x" "$pin_y"
adb shell input swipe "$pin_x" "$pin_y" "$pin_x" "$pin_y" 1200
sleep 2
adb exec-out screencap -p > "$smoke_artifacts/launcher-after-pin.png"
adb logcat -d -v threadtime --pid="$app_pid" -t 5000 > "$smoke_artifacts/pinned-logcat.txt"
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
adb logcat -d -v threadtime --pid="$restarted_pid" -t 10000 > "$smoke_artifacts/restarted-logcat.txt"
grep -E 'OmaApp: loaded [1-9][0-9]* pinned apps' "$smoke_artifacts/restarted-logcat.txt"
grep -E 'OmaApp: cached launcher icon for ' "$smoke_artifacts/restarted-logcat.txt"
adb exec-out screencap -p > "$smoke_artifacts/launcher-before-tap.png"
test -s "$smoke_artifacts/launcher-before-tap.png"
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-after-restart.xml "$smoke_artifacts/window-after-restart.xml"
grep -F "Open $folder_name folder, 1 apps" "$smoke_artifacts/window-after-restart.xml"
persisted_folder_target="$(python3 scripts/ci/android_accessibility_tap_target.py "$smoke_artifacts/window-after-restart.xml" "Open $folder_name folder, 1 apps")"
read -r persisted_folder_x persisted_folder_y <<< "$persisted_folder_target"
adb shell input tap "$persisted_folder_x" "$persisted_folder_y"
sleep 1
python3 scripts/ci/android_accessibility_dump.py \
  /sdcard/omaapp-persisted-folder.xml "$smoke_artifacts/window-persisted-folder.xml"
grep -F 'Launch Drive' "$smoke_artifacts/window-persisted-folder.xml"
adb shell input keyevent 4
sleep 1
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
