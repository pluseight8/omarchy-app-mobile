#!/usr/bin/env python3
"""Add the CI-only Android instrumentation runner to the emulator debug build."""

from pathlib import Path
import shutil


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "scripts/ci/android/PairingTestInstrumentation.java"
DESTINATION = ROOT / "app/android/src/app/omaapp/PairingTestInstrumentation.java"
MANIFEST = ROOT / "app/android/AndroidManifest.xml"
MARKER = "    <supports-screens\n"
INSTRUMENTATION = '''    <instrumentation
        android:name="app.omaapp.PairingTestInstrumentation"
        android:targetPackage="app.omaapp"
        android:label="OmaApp pairing smoke test" />

'''


def main() -> None:
    manifest = MANIFEST.read_text(encoding="utf-8")
    if "app.omaapp.PairingTestInstrumentation" in manifest:
        raise SystemExit("The CI instrumentation runner must not be part of the product manifest.")
    if MARKER not in manifest:
        raise SystemExit("Could not find the Android manifest insertion point.")
    shutil.copyfile(SOURCE, DESTINATION)
    MANIFEST.write_text(manifest.replace(MARKER, INSTRUMENTATION + MARKER, 1), encoding="utf-8")


if __name__ == "__main__":
    main()
