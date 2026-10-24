#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}"
output="${1:?usage: build_android_launcher_fixture.sh OUTPUT_APK}"
build_tools="$sdk_root/build-tools/36.0.0"
android_jar="$sdk_root/platforms/android-36/android.jar"
source_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/android/launcher_fixture" && pwd)"
work_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/omaapp-launcher-fixture-$$"
mkdir -p "$work_dir/classes" "$work_dir/dex" "$(dirname "$output")"

for tool in aapt d8 zipalign apksigner; do
  test -x "$build_tools/$tool" || { printf 'Missing Android build tool: %s\n' "$tool" >&2; exit 1; }
done
test -f "$android_jar"

javac -source 8 -target 8 -bootclasspath "$android_jar" \
  -d "$work_dir/classes" \
  "$source_dir/LauncherFixtureActivity.java" "$source_dir/NsdFixtureService.java"
mapfile -t fixture_classes < <(find "$work_dir/classes" -type f -name '*.class' -print)
test "${#fixture_classes[@]}" -gt 0
"$build_tools/d8" --min-api 23 --output "$work_dir/dex" \
  "${fixture_classes[@]}"
"$build_tools/aapt" package -f -M "$source_dir/AndroidManifest.xml" \
  -I "$android_jar" -F "$work_dir/unsigned.apk"
(cd "$work_dir/dex" && "$build_tools/aapt" add -f "$work_dir/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f 4 "$work_dir/unsigned.apk" "$work_dir/aligned.apk"

# This short-lived debug identity signs only the CI package-discovery fixture;
# it is never bundled with OmaApp or used by a release workflow.
keytool -genkeypair -noprompt -keystore "$work_dir/fixture.keystore" \
  -storepass omaapp-ci-only -keypass omaapp-ci-only -alias launcher-fixture \
  -keyalg RSA -keysize 2048 -validity 2 -dname "CN=OmaApp CI launcher fixture"
"$build_tools/apksigner" sign --ks "$work_dir/fixture.keystore" \
  --ks-pass pass:omaapp-ci-only --key-pass pass:omaapp-ci-only \
  --out "$output" "$work_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$output"
