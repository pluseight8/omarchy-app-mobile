# OmaApp — One Shell. Every Device.

[![CI](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml) · [English](README.md) · [Русский](README_RU.md)

![OmaApp project banner](assets/hero.svg)

OmaApp is the planned Android companion for Omarchy. This repository is at the Qt Quick prototype stage.

## Current state

- The Android app source is a Qt 6.10+ / C++ / QML launcher shell.
- On Android, it queries launcher activities through a small Java bridge and starts the selected app. The manifest registers OmaApp as a selectable Android home screen.
- The desktop target is a preview. It does not fake an installed-app list.
- The Omarchy component is one manifest-based status widget. It currently reports “not paired”.
- Pairing, sync, remote access, the ported Omalauncher feature set, and a downloadable APK are not available yet.
- No Quickshell executable or library is included in this prototype.

The current local workspace has Qt 6.11.2 desktop tools and `qmllint`, but lacks CMake, Qt ShaderTools, JDK, Android SDK, and Android NDK. Android packaging is configured in GitHub Actions; a successful CI run is needed before claiming an installable APK.

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

This prototype has not been installed on GrapheneOS or a Pixel device. The first Android APK is not yet available from Releases.

## Install the Omarchy status widget

From a checkout of this repository on an Omarchy computer:

    ./omarchy-plugin/install.sh

The installer validates and copies the single plugin into the Omarchy user plugin directory. It does not install a separate server or system service.

## Plan and limits

See [architecture](docs/ARCHITECTURE.md), [installation](docs/INSTALLATION.md), [pairing status](docs/PAIRING.md), [GrapheneOS status](docs/GRAPHENEOS.md), [compatibility findings](docs/compatibility.md), [the threat model](docs/threat-model.md), and [the implementation plan](tasks/plan.md). Remote features remain unavailable until the pairing protocol, device authorization, key revocation, and security checks are implemented.

OmaApp is an independent project and is not an official Omarchy product.

## Special Mention

OmaApp builds upon the original Omalauncher project created by Simon Schubert. His work provided the original foundation for this project, and we gratefully acknowledge his contribution to the open-source community.

Original project: [SimonSchubert/omalauncher](https://github.com/SimonSchubert/omalauncher)
Original author: [Simon Schubert](https://github.com/SimonSchubert)
