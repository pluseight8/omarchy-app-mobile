#!/usr/bin/env bash
set -euo pipefail

artifacts="${1:?usage: android_translation_smoke.sh ARTIFACT_DIRECTORY}"
mkdir -p "$artifacts"

dump_ui() {
  python3 scripts/ci/android_accessibility_dump.py \
    /sdcard/omaapp-translation-ui.xml "$artifacts/translation-ui.xml"
}

tap_label() {
  local label="$1"
  local target x y
  target="$(python3 scripts/ci/android_accessibility_tap_target.py \
    "$artifacts/translation-ui.xml" "$label")"
  read -r x y <<< "$target"
  adb shell input tap "$x" "$y"
}

find_scrolling_label() {
  local label="$1"
  local direction="${2:-down}"
  local target
  for attempt in {1..12}; do
    # This function's stdout is captured by tap_scrolling_label; keep the
    # diagnostic artifact path out of the coordinate pair it returns.
    dump_ui > /dev/null
    if target="$(python3 scripts/ci/android_accessibility_tap_target.py \
      "$artifacts/translation-ui.xml" "$label" 2>/dev/null)"; then
      printf '%s\n' "$target"
      return 0
    fi
    if [[ "$direction" == "up" ]]; then
      adb shell input swipe 205 300 205 720 450
    else
      adb shell input swipe 205 720 205 300 450
    fi
    sleep 0.3
  done
  printf 'Could not find visible Settings control %s.\n' "$label" >&2
  return 1
}

tap_scrolling_label() {
  local target x y
  target="$(find_scrolling_label "$1" "${2:-down}")"
  read -r x y <<< "$target"
  adb shell input tap "$x" "$y"
}

dump_ui
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Apps'
tap_label 'Settings'
sleep 1
tap_scrolling_label 'Russian'
sleep 2
dump_ui
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Язык'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Выберите язык приложения. Системная настройка следует языку телефона; английский и русский доступны без подключения к сети.'
adb exec-out screencap -p > "$artifacts/settings-russian.png"
cp "$artifacts/translation-ui.xml" "$artifacts/translation-ui-russian.xml"
python3 scripts/ci/check_android_navigation_layout.py \
  "$artifacts/settings-russian.png" "$artifacts/translation-ui-russian.xml" \
  'Главный экран' 'Приложения' 'Устройства' 'Оформление' 'Настройки'
native_translation_output="$(adb shell am instrument -w -e nativeQtTranslations true app.omaapp/app.omaapp.PairingTestInstrumentation)"
printf '%s\n' "$native_translation_output"
grep -F 'INSTRUMENTATION_STATUS: stage=native_qt_translations_passed' \
  <<< "$native_translation_output"

# Language choice is app-local and survives process death, then can be changed
# back without clearing launcher data.
adb shell am force-stop app.omaapp
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity
sleep 5
dump_ui
tap_label 'Настройки'
sleep 1
tap_scrolling_label 'Английский'
sleep 2
dump_ui
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Language'
adb exec-out screencap -p > "$artifacts/settings-english-after-switch.png"
cp "$artifacts/translation-ui.xml" "$artifacts/translation-ui-english.xml"
python3 scripts/ci/check_android_navigation_layout.py \
  "$artifacts/settings-english-after-switch.png" "$artifacts/translation-ui-english.xml" \
  Home Apps Devices Style Settings

# Haptics are explicitly opt-in, use Android's View API, and remain enabled
# after Activity recreation. The emulator may not have a vibration motor, so
# assert the native request reached the Android API instead of asserting a buzz.
tap_scrolling_label 'Enable haptic feedback' up
sleep 1
dump_ui
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Enable haptic feedback'
adb exec-out screencap -p > "$artifacts/settings-haptics-enabled.png"
adb logcat -d -v threadtime -t 3000 > "$artifacts/haptic-feedback.log"
grep -F 'OmaApp: Android haptic feedback request dispatched' \
  "$artifacts/haptic-feedback.log"
adb shell am force-stop app.omaapp
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity
sleep 5
dump_ui
tap_label 'Settings'
sleep 1
find_scrolling_label 'Enable haptic feedback' down > /dev/null
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Enable haptic feedback'

# Returning from Android Settings invokes the Activity lifecycle callback and
# refreshes the real PackageManager launcher list without a timer poll.
adb logcat -c
adb shell am start -a android.settings.SETTINGS
sleep 1
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity
sleep 2
resumed_pid="$(adb shell pidof app.omaapp | tr -d '\r')"
test -n "$resumed_pid"
adb logcat -d -v threadtime --pid="$resumed_pid" -t 3000 > "$artifacts/launcher-refresh-on-resume.log"
grep -E 'OmaApp: enumerated [0-9]+ launcher activities' \
  "$artifacts/launcher-refresh-on-resume.log"
