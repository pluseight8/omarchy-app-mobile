# Contributing

OmaApp is currently an early Qt Quick prototype. Contributions should keep the Android app and the Omarchy plugin as the only installable components. Do not add a standalone pairing server, desktop agent, or companion app.

## Build and check

Use Qt 6.11 with Quick Controls 2, ShaderTools, and Qt Test, CMake 3.24+, Ninja, and a C++17 compiler.

```sh
cmake -S . -B build/desktop -GNinja -DCMAKE_BUILD_TYPE=Debug
cmake --build build/desktop
ctest --test-dir build/desktop --output-on-failure
qmllint -I "$QT_ROOT_DIR/qml" -I build/desktop app/qml/Main.qml app/qml/Theme.qml app/qml/HyprlandSurface.qml
python3 scripts/validate_plugin_manifest.py omarchy-plugin
python3 -m unittest discover -s scripts/tests -v
bash -n omarchy-plugin/install.sh omarchy-plugin/uninstall.sh
```

The Android target additionally needs Qt for Android arm64, JDK 21, Android SDK 36, and NDK 27.2.12479018. See [installation](docs/INSTALLATION.md).

## Changes to remote features

Do not add a listener, command runner, pairing UI that claims to work, or credential storage without an implemented protocol, per-device authorization, revocation, threat-model updates, and abuse-case tests. Keep network access off until these controls are present.

## Pull requests

Describe the behavior, the supported platform, the checks you ran, and any device-only validation still needed. Include screenshots only from a real running build. Keep dependency and GitHub Actions changes pinned and minimal.
