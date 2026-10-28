#!/usr/bin/env bash
set -euo pipefail

artifacts="${1:?usage: android_translation_smoke.sh ARTIFACT_DIRECTORY}"
mkdir -p "$artifacts"
adb logcat -c
original_font_scale=""

restore_android_font_scale() {
  if [[ -n "$original_font_scale" ]]; then
    adb shell settings put system font_scale "$original_font_scale" >/dev/null 2>&1 || true
  fi
}
trap restore_android_font_scale EXIT

dump_ui() {
  python3 scripts/ci/android_accessibility_dump.py \
    /sdcard/omaapp-translation-ui.xml "$artifacts/translation-ui.xml"
}

tap_label() {
  local label="$1"
  local target x y
  for attempt in {1..16}; do
    dump_ui > /dev/null
    if target="$(python3 scripts/ci/android_accessibility_tap_target.py \
      "$artifacts/translation-ui.xml" "$label" 2>/dev/null)"; then
      read -r x y <<< "$target"
      adb shell input tap "$x" "$y"
      return 0
    fi
    sleep 0.35
  done
  printf 'Could not find a clickable Android UI target: %s; accessibility snapshot: %s\n' \
    "$label" "$artifacts/translation-ui.xml" >&2
  return 1
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
      # Keep the gesture in the Settings card's left gutter. The center of the
      # screen can start on a button (for example Restore) after changing
      # languages, while x=20 is outside the content margin and cannot scroll.
      adb shell input swipe 32 300 32 720 650
    else
      adb shell input swipe 32 720 32 300 650
    fi
    sleep 0.4
  done
  adb exec-out screencap -p > "$artifacts/scroll-failure.png" 2>/dev/null || true
  printf 'Could not find visible Settings control %s.\n' "$label" >&2
  return 1
}

ensure_visible_settings_label() {
  local label="$1"
  for attempt in {1..12}; do
    dump_ui > /dev/null
    if python3 scripts/ci/android_accessibility_assert_visible.py \
      "$artifacts/translation-ui.xml" "$label" >/dev/null 2>&1; then
      return 0
    fi
    # Language changes can reflow the Settings card while preserving its
    # Flickable offset. Scroll the label into the viewport before asserting it.
    adb shell input swipe 32 720 32 300 650
    sleep 0.4
  done
  adb exec-out screencap -p > "$artifacts/visibility-failure.png" 2>/dev/null || true
  printf 'Could not bring Settings label %s into view.\n' "$label" >&2
  return 1
}

tap_scrolling_label() {
  local target x y
  target="$(find_scrolling_label "$1" "${2:-down}")"
  read -r x y <<< "$target"
  adb shell input tap "$x" "$y"
}

ensure_settings_tab() {
  local tab_label="$1"
  dump_ui
  if ! python3 scripts/ci/android_accessibility_assert_checked.py \
    "$artifacts/translation-ui.xml" "$tab_label" >/dev/null 2>&1; then
    tap_label "$tab_label"
    sleep 1
    dump_ui
  fi
  python3 scripts/ci/android_accessibility_assert_checked.py \
    "$artifacts/translation-ui.xml" "$tab_label"
}

restart_app_for_persistence() {
  # Leave the HOME activity before force-stop so Android can terminate the
  # launcher process instead of delivering the next start to its live task.
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
    printf 'OmaApp process survived force-stop; persistence check would be invalid.\n' >&2
    return 1
  fi
  adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity
  for attempt in {1..60}; do
    dump_ui > /dev/null
    if grep -Eq 'content-desc="(Settings tab|Вкладка настроек)"' "$artifacts/translation-ui.xml"; then
      return 0
    fi
    sleep 0.5
  done
  adb exec-out screencap -p > "$artifacts/launcher-readiness-failure.png" 2>/dev/null || true
  printf 'OmaApp Home accessibility did not become ready after process restart. UI: %s\n' \
    "$artifacts/translation-ui.xml" >&2
  return 1
}

dump_ui
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Apps'
tap_label 'Settings tab'
sleep 1
tap_scrolling_label 'Russian'
sleep 2
ensure_visible_settings_label 'Язык'
dump_ui
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Язык'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Выберите язык приложения. Системная настройка следует языку телефона; английский и русский доступны без подключения к сети.'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Часы Omarchy'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Локальные часы и дата на основе текстовых и компоновочных узлов OWL.'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Демонстрация Canvas'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Небольшой живой холст с фигурами OWL, выражениями и цветами Omarchy.'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Скопировать резервную копию раскладки в буфер обмена'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Восстановить раскладку главного экрана из JSON-копии'
if grep -Fq 'Custom widget deleted.' "$artifacts/translation-ui.xml"; then
  printf 'A stale English widget status message survived changing the app language.\n' >&2
  exit 1
fi
adb exec-out screencap -p > "$artifacts/settings-russian.png"
cp "$artifacts/translation-ui.xml" "$artifacts/translation-ui-russian.xml"
python3 scripts/ci/check_android_navigation_layout.py \
  "$artifacts/settings-russian.png" "$artifacts/translation-ui-russian.xml" \
  'Главный экран' 'Приложения' 'Устройства' 'Оформление' 'Вкладка настроек'

# Native Android Activities query the Qt catalog selected in-app. Restart first
# so the check covers loading the saved Russian catalog into a fresh Qt engine.
restart_app_for_persistence
ensure_settings_tab 'Вкладка настроек'
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Русский'
native_translation_output="$(adb shell am instrument -w -e nativeQtTranslations true app.omaapp/app.omaapp.PairingTestInstrumentation)"
printf '%s\n' "$native_translation_output"
grep -F 'INSTRUMENTATION_STATUS: stage=native_qt_translations_passed' \
  <<< "$native_translation_output"
restart_app_for_persistence
ensure_settings_tab 'Вкладка настроек'
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Русский'
sleep 1
tap_scrolling_label 'Английский' down
sleep 2
ensure_visible_settings_label 'Language'
dump_ui
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Language'
adb exec-out screencap -p > "$artifacts/settings-english-after-switch.png"
cp "$artifacts/translation-ui.xml" "$artifacts/translation-ui-english.xml"
python3 scripts/ci/check_android_navigation_layout.py \
  "$artifacts/settings-english-after-switch.png" "$artifacts/translation-ui-english.xml" \
  Home Apps Devices Style 'Settings tab'
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'English'

# Check English native messages after explicitly selecting English. Instrumentation
# tears down OmaApp on exit, so start it again and verify the saved choice before
# measuring same-process locale changes.
native_english_output="$(adb shell am instrument -w -e nativeEnglishStrings true app.omaapp/app.omaapp.PairingTestInstrumentation)"
printf '%s\n' "$native_english_output"
grep -F 'INSTRUMENTATION_STATUS: stage=native_english_strings_passed' \
  <<< "$native_english_output"
restart_app_for_persistence
ensure_settings_tab 'Settings tab'
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'English'
live_locale_process="$(adb shell pidof app.omaapp | tr -d '\r')"
test -n "$live_locale_process"

# Exercise rapid locale changes without recreating the Activity. This catches
# stale OWL labels and one-way translation bindings after several retranslate() calls.
tap_scrolling_label 'Russian' down
sleep 2
ensure_visible_settings_label 'Язык'
dump_ui
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Язык'
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Часы Omarchy'
tap_scrolling_label 'Английский' down
sleep 2
dump_ui
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Language'
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'English'
locale_process_after_switches="$(adb shell pidof app.omaapp | tr -d '\r')"
if [[ "$locale_process_after_switches" != "$live_locale_process" ]]; then
  printf 'Live locale switches recreated the OmaApp process.\n' >&2
  exit 1
fi

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
restart_app_for_persistence
ensure_settings_tab 'Settings tab'
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'English'
sleep 1
find_scrolling_label 'Enable haptic feedback' down > /dev/null
python3 scripts/ci/android_accessibility_assert_checked.py \
  "$artifacts/translation-ui.xml" 'Enable haptic feedback'

# Explicit QML pixelSize values must honor Android's system accessibility text
# scale. Restart at a larger setting, then change back while OmaApp is alive.
original_font_scale="$(adb shell settings get system font_scale | tr -d '\r')"
if [[ ! "$original_font_scale" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  original_font_scale=1.0
fi
adb shell settings put system font_scale 1.4
restart_app_for_persistence
ensure_settings_tab 'Settings tab'
find_scrolling_label 'Android accessibility text scale: 1.4×' down > /dev/null
python3 scripts/ci/android_accessibility_assert_visible.py \
  "$artifacts/translation-ui.xml" 'Android accessibility text scale: 1.4×'
adb exec-out screencap -p > "$artifacts/settings-large-font.png"
cp "$artifacts/translation-ui.xml" "$artifacts/translation-ui-large-font.xml"

adb shell settings put system font_scale "$original_font_scale"
adb shell am start -a android.settings.SETTINGS >/dev/null
sleep 1
adb shell am start -W -n app.omaapp/app.omaapp.OmaAppActivity
sleep 2
dump_ui
ensure_settings_tab 'Settings tab'
if grep -Fq 'Android accessibility text scale:' "$artifacts/translation-ui.xml"; then
  printf 'The Android accessibility scale indicator remained after restoring system text scale.\n' >&2
  exit 1
fi
original_font_scale=""

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

adb logcat -d -v threadtime -t 30000 > "$artifacts/qml-diagnostics.log"
if grep -F 'Binding loop detected' "$artifacts/qml-diagnostics.log"; then
  printf 'A QML binding loop was reported during the Android UI smoke.\n' >&2
  exit 1
fi
