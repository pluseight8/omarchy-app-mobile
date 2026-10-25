# Compatibility investigation

## Starting point

The source Omalauncher repository is an Android application written in Kotlin and Jetpack Compose. It provides a home-screen launcher, themes, OWL widgets, settings, and an optional Arch Linux guest with PRoot and an in-app Wayland compositor. OmaApp removes the Linux guest and uses Qt Quick/QML for the launcher. It implements app enumeration/launch, HOME, launcher icons, search, dock and folders, up to five Home pages, dynamic Android app shortcuts, gesture preferences, drag and drop, offline layout backup/restore, and OWL source editing/rendering. The embedded original OWL compiler/runtime currently renders a bounded offline subset, including common layouts and Canvas operations; guest, command, network, asset, trigger, secret-state and interaction features are rejected. No PRoot or Arch Linux ARM is included.

## Qt and Quickshell

This workspace currently lacks CMake, a JDK, and an emulator; it cannot build the desktop app or Android APK locally. The available qmllint reports version 1.0 and exits with code 255 without diagnostics on the Qt Quick app files, so it is not a usable Qt linter here. The local suite passes 131 Python tests and validates 292 Russian UI strings. CI #231 and #233 passed desktop, arm64, and Android x86_64 OpenGL/Vulkan. CI #232 and #234 showed HOME readiness failures without identifying a cause. CI #235 passed arm64; the emulator jobs caught deleted CI instrumentation and Desktop caught a stale static assertion. CI #236 passed Desktop but both emulator builds stopped compiling because the Java instrumentation file retained a tool-output header. It is removed, and the regression test rejects truncation markers; a fresh run must verify the build and runtime flows. Other changes preserve queued DNS-SD retries and serialize callback registration/result delivery with lifecycle stop. No physical Android device is attached, so Pixel 10 / GrapheneOS verification remains outstanding.

The current Quickshell build documentation requires Linux desktop dependencies such as libdrm and Wayland, and the project relies on private Qt APIs tied to a specific Qt build. Its current CMake configuration enables Linux desktop integrations and contains no Android build path. That makes the complete Quickshell executable a poor candidate for inclusion in an Android APK. Qt Quick remains a supported Android rendering host; portable QML style logic can be adapted, while desktop shell services stay in the Omarchy plugin.

The app does not link the complete Quickshell runtime because it depends on Linux, Wayland and shell APIs that do not exist on Android. The current Omarchy shell separates portable theme/style logic (`Color`, `Style`, `Border`) from Linux-bound controls such as `BorderSurface` and `BorderOverlay`; OmaApp adapts compatible tokens and surface behavior to Qt Quick. It does not vendor the shell UI module or introduce a third installed component. Any future Quickshell code inclusion must preserve LGPL-3.0 obligations and provide a replaceable/relinkable Qt runtime arrangement.

## Omarchy plugin contract

The inspected Omarchy quattro branch discovers third-party plugins from git repositories with a root manifest using schema version 1. A plugin can declare service and bar-widget entry points. Third-party QML runs unsandboxed in the long-lived shell process, so plugin code must be treated as trusted code and must not turn network input into shell commands.

The implementation keeps one plugin package. The bundled helper is launched by the bar widget inside the Quickshell lifecycle, not installed as an independent product. The installer, full restart, duplicate-backup behavior, actual widget registration, and helper path were exercised against Omarchy 4.0.4 / Quickshell 0.3.1. A Linux integration test separately starts the TLS helper and verifies event delivery and revocation.

## Hyprland geometry

`HyprlandSurfaceItem` sends the rectangle to a custom Qt Quick scene-graph material. A QSB fragment shader evaluates Hyprland's Lp superellipse contour for each pixel, applies the border radius correction, clips fill and border coverage, converts up to ten gradient colors to/from OkLabA, and uses Hyprland's angle projection. Because the contour is evaluated per fragment instead of interpolated across triangles, it has no diagonal mesh seams. Pixel-ratio-scaled antialiasing handles high DPI without scaling configured logical radii. CPU reference tests exercise the Lp equation; emulator screenshots additionally check clipped corners, visible fill/border, a 37-degree gradient contour, and 160/240 density captures on OpenGL and Vulkan.

The earlier QML `ShaderEffect` experiment did not draw on OpenGL ES/SwiftShader despite successful shader compilation; the following mesh experiment drew but changed launcher screenshots beyond reviewed baselines. The current custom material uses the Qt scene-graph shader contract and CI checks the rendered contour and gradient under both OpenGL and Vulkan. It tracks the installed Omarchy Shell's Lp distance, edge smoothing, border-radius correction and gradient projection. The app applies `[font] base-size`, supported `[font]` size tokens, and `[spacing] scale-with-font` to QML text and spacing. The Omarchy plugin's bar titles use `Style` tokens directly. The Android app uses the platform `monospace` family because it cannot inherit the host fontconfig alias. See the current PR checks for the renderer's status on the latest source revision.

Android screenshot comparison keeps separate reviewed baselines for ANGLE/OpenGL and SwiftShader/Vulkan. The rendered window geometry and gradient probes run on both backends with the same 1.5% changed-pixel threshold; only text antialiasing is expected to differ between the renderers. The first OpenGL run against the Vulkan baseline failed on text and broad UI pixels, while its shader surface probe passed. Backend-specific snapshots address that renderer difference without relaxing the visual regression threshold. The PR head that adds these snapshots still needs its own complete pull-request CI run.

Hyprland is BSD-3-Clause. The rounding equation, border-radius correction, OkLabA conversion and gradient projection follow upstream rendering sources; notices ship in `THIRD_PARTY.md` and the APK documentation. The app reproduces window-surface geometry and gradient math; it does not reproduce compositor transforms, per-window state or Hyprland HDR color management.

## License review

- Omalauncher source: MIT. The required Simon Schubert acknowledgment is included in both README files.
- Omarchy shell source: MIT. No shell source file is vendored in the prototype.
- Quickshell: LGPL-3.0. No Quickshell source or library is linked in the prototype.
- Hyprland: BSD-3-Clause. The geometry implementation is attributed in THIRD_PARTY.md.
- Qt: the final APK must include the applicable Qt notices and meet the selected Qt distribution terms, including LGPL relinking requirements if those components are used.

This is an initial source review, not a complete release-time dependency or binary license audit.
