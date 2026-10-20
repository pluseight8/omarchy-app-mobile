# Compatibility investigation

## Starting point

The source Omalauncher repository is an Android application written in Kotlin and Jetpack Compose. It already provides an Android home-screen launcher, theme and wallpaper support, widgets, settings, and an optional Arch Linux guest with PRoot and an in-app Wayland compositor. The requested migration explicitly removes that Linux guest and moves the user interface to Qt Quick/QML. OmaApp currently implements Android app enumeration/launch, a HOME activity, the first Omarchy pairing screen, and a shared theme preview; the original home-page, folder, shortcut, and OWL widget feature set remains to port.

## Qt and Quickshell

Qt 6.11.2 desktop development tools and `qmllint` are available in this workspace. CMake, a JDK, Android SDK, `adb`, and an emulator are not installed here, so this machine cannot currently build or install the APK. GitHub Actions has produced a debug arm64 APK at the previous checkpoint; the current pairing changes still need a new build and Android runtime check.

The current Quickshell build documentation requires Linux desktop dependencies such as libdrm and Wayland, and the project relies on private Qt APIs tied to a specific Qt build. Its current CMake configuration enables Linux desktop integrations and contains no Android build path. That makes the complete Quickshell executable a poor candidate for inclusion in an Android APK. Qt Quick remains a supported Android rendering host; portable QML style logic can be adapted, while desktop shell services stay in the Omarchy plugin.

No Quickshell source or library is included in the initial prototype. This is intentional and avoids claiming reuse that has not been built and verified. Any later inclusion of Quickshell code must preserve LGPL-3.0 obligations and provide a replaceable/relinkable Qt runtime arrangement.

## Omarchy plugin contract

The inspected Omarchy quattro branch discovers third-party plugins from git repositories with a root manifest using schema version 1. A plugin can declare service and bar-widget entry points. Third-party QML runs unsandboxed in the long-lived shell process, so plugin code must be treated as trusted code and must not turn network input into shell commands.

The implementation will keep one plugin package. Any helper process will be included in that package and launched under the plugin lifecycle, not installed as an independent product.

## Hyprland geometry

The current Hyprland rounding shader measures corner distance with the Lp superellipse norm, using rounding power in the 1–10 range. It applies a smoothstep antialiasing band based on M_PI / 5.34665792551. OmaApp ports that mask equation into a Qt ShaderEffect with nested masks, device-pixel scaling, two gradient endpoints, and the active-border angle. Qt ShaderTools packages it through the QSB pipeline in CI. The shader still does not reproduce Hyprland's full compositor transforms, up to ten gradient stops in OkLab, ICC color management, or every border fallback path; these remain compatibility work.

Hyprland is BSD-3-Clause. The rounding mask equation is adapted from `rounding.glsl`; the original notice and license terms ship in `THIRD_PARTY.md` and the APK documentation.

## License review

- Omalauncher source: MIT. The required Simon Schubert acknowledgment is included in both README files.
- Omarchy shell source: MIT. No shell source file is vendored in the prototype.
- Quickshell: LGPL-3.0. No Quickshell source or library is linked in the prototype.
- Hyprland: BSD-3-Clause. The geometry implementation is attributed in THIRD_PARTY.md.
- Qt: the final APK must include the applicable Qt notices and meet the selected Qt distribution terms, including LGPL relinking requirements if those components are used.

This is an initial source review, not a complete release-time dependency or binary license audit.
