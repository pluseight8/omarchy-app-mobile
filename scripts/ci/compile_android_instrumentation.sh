#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
android_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
android_jar="${ANDROID_JAR:-}"
if [[ -z "$android_jar" && -n "$android_sdk" ]]; then
    android_jar="$(find "$android_sdk/platforms" -mindepth 2 -maxdepth 2 -name android.jar | sort -V | tail -n 1)"
fi
if [[ -z "$android_jar" || ! -f "$android_jar" ]]; then
    printf 'Android android.jar is required to compile the instrumentation checks.\n' >&2
    exit 1
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/stubs/org/qtproject/qt/android/bindings" \
    "$work_dir/stubs/app/omaapp" "$work_dir/stubs/androidx/annotation" "$work_dir/classes"
cat > "$work_dir/stubs/org/qtproject/qt/android/bindings/QtActivity.java" <<'JAVA'
package org.qtproject.qt.android.bindings;
public class QtActivity extends android.app.Activity { }
JAVA
cat > "$work_dir/stubs/app/omaapp/QrScannerActivity.java" <<'JAVA'
package app.omaapp;
public final class QrScannerActivity extends android.app.Activity { }
JAVA
cat > "$work_dir/stubs/app/omaapp/OwlBridge.java" <<'JAVA'
package app.omaapp;
public final class OwlBridge {
    public static String render(String source, double width, double height, String themeJson) {
        return "";
    }
}
JAVA
cat > "$work_dir/stubs/androidx/annotation/Nullable.java" <<'JAVA'
package androidx.annotation;
public @interface Nullable { }
JAVA

mapfile -t sources < <(find "$repo_root/app/android/src" -name '*.java' \
    ! -name 'QrScannerActivity.java' -print)
sources+=("$repo_root/scripts/ci/android/PairingTestInstrumentation.java"
    "$work_dir/stubs/org/qtproject/qt/android/bindings/QtActivity.java"
    "$work_dir/stubs/app/omaapp/QrScannerActivity.java"
    "$work_dir/stubs/app/omaapp/OwlBridge.java"
    "$work_dir/stubs/androidx/annotation/Nullable.java")
if command -v javac >/dev/null 2>&1; then
    compiler=(javac)
else
    compiler=(java com.sun.tools.javac.Main)
fi
"${compiler[@]}" -encoding UTF-8 -classpath "$android_jar" -d "$work_dir/classes" "${sources[@]}"
test -f "$work_dir/classes/app/omaapp/PairingTestInstrumentation.class"
