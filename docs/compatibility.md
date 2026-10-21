# Compatibility investigation

## Starting point

The source Omalauncher repository is an Android application written in Kotlin and Jetpack Compose. It provides a home-screen launcher, themes, OWL widgets, settings, and an optional Arch Linux guest with PRoot and an in-app Wayland compositor. The requested migration removes that Linux guest and moves the user interface to Qt Quick/QML. OmaApp implements Android app enumeration/launch, HOME, system icons, search, a dock, persistent folders, 3–5 column grid settings, swipe navigation, and Omarchy pairing/theme sync. Its multi-page widget canvas, OWL interpreter/editor, and dynamic app shortcuts remain to port. No PRoot or Arch Linux ARM is included.

## Qt and Quickshell

Qt 6.11.2 desktop development tools, QtTest, `qmllint`, and `adb` are available in this workspace. CMake, a JDK, Android SDK, and emulator are not installed here, so this machine cannot build or install the APK locally. [GitHub Actions run 44](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557) passed desktop checks, the debug arm64 build, and x86_64 emulator coverage for launcher use, folder persistence, TLS pairing, theme and wallpaper sync, reconnect, and revocation. A physical phone is not connected, so GrapheneOS and device-to-Omarchy behavior remain unverified.

The current Quickshell build documentation requires Linux desktop dependencies such as libdrm and Wayland, and the project relies on private Qt APIs tied to a specific Qt build. Its current CMake configuration enables Linux desktop integrations and contains no Android build path. That makes the complete Quickshell executable a poor candidate for inclusion in an Android APK. Qt Quick remains a supported Android rendering host; portable QML style logic can be adapted, while desktop shell services stay in the Omarchy plugin.

No Quickshell source or library is included in the initial prototype. This is intentional and avoids claiming reuse that has not been built and verified. Any later inclusion of Quickshell code must preserve LGPL-3.0 obligations and provide a replaceable/relinkable Qt runtime arrangement.

## Omarchy plugin contract

The inspected Omarchy quattro branch discovers third-party plugins from git repositories with a root manifest using schema version 1. A plugin can declare service and bar-widget entry points. Third-party QML runs unsandboxed in the long-lived shell process, so plugin code must be treated as trusted code and must not turn network input into shell commands.

The implementation keeps one plugin package. The bundled helper is launched by the bar widget inside the Quickshell lifecycle, not installed as an independent product. The installer, full restart, duplicate-backup behavior, actual widget registration, and helper path were exercised against Omarchy 4.0.4 / Quickshell 0.3.1. A Linux integration test separately starts the TLS helper and verifies event delivery and revocation.

## Hyprland geometry

The C++ geometry reference implements Hyprland's Lp superellipse distance and smoothing band, and its unit tests cover the 1–10 rounding-power range. The Android UI currently uses Qt's native rounded rectangles and solid border because custom shader and gradient surfaces produced artifacts in emulator screenshots. OmaApp still reads and caches the rounding power and both border colors/angle, and the Style preview displays them, but it does not yet render the exact superellipse or gradient. Restore that rendering only after the Android screenshot test stays clean.

Hyprland is BSD-3-Clause. The rounding mask equation is adapted from `rounding.glsl`; the original notice and license terms ship in `THIRD_PARTY.md` and the APK documentation.

## License review

- Omalauncher source: MIT. The required Simon Schubert acknowledgment is included in both README files.
- Omarchy shell source: MIT. No shell source file is vendored in the prototype.
- Quickshell: LGPL-3.0. No Quickshell source or library is linked in the prototype.
- Hyprland: BSD-3-Clause. The geometry implementation is attributed in THIRD_PARTY.md.
- Qt: the final APK must include the applicable Qt notices and meet the selected Qt distribution terms, including LGPL relinking requirements if those components are used.

This is an initial source review, not a complete release-time dependency or binary license audit.
