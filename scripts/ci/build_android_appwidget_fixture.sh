#!/usr/bin/env bash
set -euo pipefail

output_apk="${1:?usage: build_android_appwidget_fixture.sh OUTPUT_APK}"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
  printf 'ANDROID_SDK_ROOT or ANDROID_HOME must identify the CI Android SDK.\n' >&2
  exit 2
fi

build_tools_version="${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
android_jar="$sdk_root/platforms/android-36/android.jar"
build_tools="$sdk_root/build-tools/$build_tools_version"
aapt2="$build_tools/aapt2"
d8="$build_tools/d8"
apksigner="$build_tools/apksigner"
for required in "$android_jar" "$aapt2" "$d8" "$apksigner"; do
  if [[ ! -x "$required" && ! -f "$required" ]]; then
    printf 'Required Android SDK tool is unavailable: %s\n' "$required" >&2
    exit 2
  fi
done

source_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/android/widget_fixture" && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/generated" "$work_dir/classes" "$work_dir/dex" "$(dirname "$output_apk")"

"$aapt2" compile --dir "$source_dir/res" -o "$work_dir/resources.zip"
"$aapt2" link \
  -o "$work_dir/unsigned.apk" \
  -I "$android_jar" \
  --manifest "$source_dir/AndroidManifest.xml" \
  --java "$work_dir/generated" \
  --min-sdk-version 29 \
  --target-sdk-version 36 \
  "$work_dir/resources.zip"

find "$source_dir/java" "$work_dir/generated" -type f -name '*.java' -print0 \
  | xargs -0 javac -source 8 -target 8 -bootclasspath "$android_jar" \
      -d "$work_dir/classes"
jar --create --file "$work_dir/classes.jar" -C "$work_dir/classes" .
"$d8" --min-api 29 --lib "$android_jar" --output "$work_dir/dex" "$work_dir/classes.jar"
(
  cd "$work_dir/dex"
  zip -q -j "$work_dir/unsigned.apk" classes.dex
)

# This short-lived key signs only the disposable CI provider fixture. It is
# separate from and never used by OmaApp's release signing workflow.
keytool -genkeypair -noprompt -keystore "$work_dir/fixture.keystore" \
  -storepass android -keypass android -alias omaapp-ci-widget-fixture \
  -dname 'CN=OmaApp CI Widget Fixture' -keyalg RSA -keysize 2048 -validity 2
"$apksigner" sign --ks "$work_dir/fixture.keystore" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$output_apk" "$work_dir/unsigned.apk"
"$apksigner" verify --verbose "$output_apk"
