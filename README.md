# OmaApp — One Shell. Every Device.

[![CI](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml) · [English](README.md) · [Русский](README_RU.md)

![OmaApp project banner](assets/hero.svg)

OmaApp is an Android launcher and direct Omarchy companion, built with Qt Quick. It pairs directly with one Omarchy plugin and keeps launcher data and cached appearance available offline. Physical Pixel/GrapheneOS verification remains.

## Current state

- The Android app uses Qt 6.10+ / C++ / QML and includes a local Home screen with up to five swipeable pages, a pinned-app dock, full Android launcher icons, app search, folders, adjustable 3–5 column grid, and settings for the live clock/computer/media cards. Pinned apps can be moved between pages or removed with a long press. Android launcher data stays local, and the optional HOME activity works without an Omarchy computer.
- Pairing is a direct HTTPS connection to the one Omarchy plugin. The first pairing requires approval in OmaApp and in the plugin; the client pins the QR's computer certificate and keeps its bearer token encrypted with Android Keystore.
- After QR trust is established, foreground DNS-SD discovery can find the same trusted plugin after a local IP change. It advertises only the public certificate fingerprint; OmaApp still verifies the pinned TLS certificate and stored token before updating an address.
- The plugin reads active Omarchy colors, `shell.toml`, wallpaper, Hyprland border gradients, rounding, rounding power, gaps, animations, and Bezier curves. Theme/configuration and workspace events are event-driven; a slower 30-second foreground refresh covers metrics and missed events. OmaApp keeps the last successful theme while offline.
- Clipboard, file transfer, MPRIS, system metrics, Hyprland workspace switching, and configured user commands are available behind independent per-device permissions. Clipboard is tapped and foreground-only; files use Android's system picker and a 100 MiB limit; arbitrary shell commands are never accepted.
- Home supports drag and drop between pages and the dock, dynamic Android app shortcuts, configurable gestures, and local backup/restore. It includes an offline OWL widget store/editor, an embedded copy of the original OWL compiler/runtime, and an OWL Canvas renderer for bounded local widgets. Android apps, widget sources and layout backups stay usable without the computer.
- Optional Home haptics use Android's system-respecting `View.performHapticFeedback` API for app launches and Home editing. They are off by default, saved locally, included in layout backups, and remain subject to Android's touch-feedback settings.
- English and Russian UI are available offline, with Android language detection and an in-app language selector. App installs and removals refresh the launcher model from Android package events.
- CI also exercises a real Android `AppWidgetHost` binding and interactive host view over QtActivity. This is a proof of concept only; ordinary Android widgets are not yet integrated into saved Home layouts.
- OWL currently runs an offline subset: layout/text/basic controls plus Canvas shapes, paths, transforms, clipping and gradients. Widgets requiring shell commands, network, assets, background triggers, interactive handlers, secrets or sampled state are rejected with an editor error instead of being run.
- The desktop target is a preview. It does not fake an installed-app list.
- The listener starts when pairing is requested or saved devices are present. The Python helper and QR encoder are files inside the one plugin; no extra product or system service is installed.
- OmaApp does not install Arch Linux ARM or PRoot. It does not bundle the Quickshell executable or Linux/Wayland modules into the APK.
- This development iteration adds localization, automatic launcher-app refresh, an Android widget-host proof, secure DNS-SD rediscovery, and opt-in haptic feedback. Check the current build and emulator results in [GitHub Actions](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml). It does not claim a signed release.
- No Quickshell executable or library is included. The Android app uses Qt Quick; the Omarchy plugin runs inside Quickshell.

The plugin has also been installed and restarted in a real Omarchy 4.0.4 / Quickshell 0.3.1 session. This working copy passes 101 Python tests, a 280-string translation check, shell/Python syntax checks, and the Android version-code regression test. The full build is unavailable locally because the Qt ShaderTools package and Android SDK/JDK are missing; GitHub Actions validates the Android build and emulator. No Pixel device is attached, so Pixel 10 / GrapheneOS behavior remains unverified.

## Build the desktop preview

Requirements: CMake 3.24+, Ninja, a Qt 6.10+ SDK with Qt Quick Controls 2, Qt ShaderTools, and Qt Test, and a C++17 compiler.

    cmake -S . -B build/desktop -GNinja -DCMAKE_BUILD_TYPE=Debug
    cmake --build build/desktop
    ctest --test-dir build/desktop --output-on-failure

Run the generated `omaapp` executable to inspect the launcher shell and GPU-rendered rounding preview.

## Build an Android APK

The Android target uses Qt for Android arm64, JDK 17, Android SDK 36, and NDK 27.2.12479018. GitHub Actions installs this toolchain and builds the APK artifact.

For a local build, configure CMake with the Qt Android arm64 kit and the Android SDK/NDK paths, then build the apk target:

    qt-cmake -S . -B build/android -GNinja -DANDROID_SDK_ROOT=/path/to/Android/Sdk -DANDROID_NDK_ROOT=/path/to/Android/Sdk/ndk/27.2.12479018
    cmake --build build/android --target apk

See the [Pixel 10 + GrapheneOS installation and test guide](docs/GRAPHENEOS.md). The current commit has not yet been installed on a physical phone.

## Install the Omarchy plugin

From a checkout of this repository on an Omarchy computer:

    ./omarchy-plugin/install.sh

The installer checks Python 3.11+, OpenSSL, and Omarchy, then validates and copies the single plugin into the Omarchy user plugin directory. The plugin owns its helper process and does not install a separate server or system service. Pairing and install notes are in [the plugin guide](omarchy-plugin/README.md) and [the protocol status](docs/PAIRING.md).

## Plan and limits

See [architecture](docs/ARCHITECTURE.md), [installation](docs/INSTALLATION.md), [pairing and sync](docs/PAIRING.md), [GrapheneOS status](docs/GRAPHENEOS.md), [compatibility findings](docs/compatibility.md), [the threat model](docs/threat-model.md), and [the implementation plan](tasks/plan.md). The first test release is configured as a GitHub Pre-release and requires the repository owner's signing secrets; the workflow publishes no APK if those secrets are absent.

OmaApp is an independent project and is not an official Omarchy product.

## Special Mention

OmaApp builds upon the original Omalauncher project created by Simon Schubert. His work provided the original foundation for this project, and we gratefully acknowledge his contribution to the open-source community.

Original project: [SimonSchubert/omalauncher](https://github.com/SimonSchubert/omalauncher)
Original author: [Simon Schubert](https://github.com/SimonSchubert)
