# Third-party notices

## Omalauncher

OmaApp acknowledges the original Omalauncher project by Simon Schubert:

- Source: https://github.com/SimonSchubert/omalauncher
- License: MIT
- Copyright notice in that project: Copyright (c) 2026 Simon Schubert and contributors

The Android APK includes the upstream OWL language compiler and expression evaluator from `owl/src/main/kotlin/dev/omalauncher/widgets/lang` at commit `96192005c8df113c9b720f750aed0bd51a3b3b49`. OmaApp uses this portable core to compile and render offline widgets; it does not include Omalauncher's PRoot/Linux guest or Compose UI.

- Source: https://github.com/SimonSchubert/omalauncher/tree/96192005c8df113c9b720f750aed0bd51a3b3b49/owl
- License: MIT
- Copyright notice: Copyright (c) 2026 Simon Schubert and contributors
- License text: `app/android/kotlin/dev/omalauncher/widgets/lang/LICENSE-MIT`

## Omarchy

OmaApp adapts the theme token and font-size behavior from Omarchy Shell's `Color.qml` and `Style.qml`. Its border treatment follows the `Border` and `BorderSurface` interface while using a Qt Quick scene-graph shader for Hyprland's per-pixel superellipse surface. The plugin bar reads `Style.font` from the installed Omarchy Shell. No Omarchy QML file is copied into the APK or plugin.

- Source: https://github.com/omacom/omarchy/tree/quattro/shell
- License: MIT
- Copyright: Copyright (c) David Heinemeier Hansson

## Hyprland geometry

The superellipse and rounding edge model in shared/geometry follows the public Hyprland rounding shader:

- Source: https://github.com/hyprwm/Hyprland/blob/main/src/render/shaders/glsl/rounding.glsl
- Related border behavior: https://github.com/hyprwm/Hyprland/blob/main/src/render/shaders/glsl/border.glsl
- Gradient conversion and projection: https://github.com/hyprwm/Hyprland/blob/main/src/render/shaders/glsl/gradient.glsl
- Radius correction: https://github.com/hyprwm/Hyprland/blob/main/src/render/decorations/CHyprBorderDecoration.cpp
- License: BSD 3-Clause
- Copyright: Copyright (c) 2022-2026, vaxerski

BSD 3-Clause license text:

Copyright (c) 2022-2026, vaxerski. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

`HyprlandSurfaceItem` uses Qt Quick's QSGMaterialShader and QSB resources to evaluate the Hyprland Lp contour and OkLabA border gradient per fragment. This avoids mesh interpolation seams and supports Qt RHI translation for OpenGL ES, Vulkan, Metal and Direct3D. The antialias band scales with device pixel ratio. The implementation does not reproduce compositor transforms, per-window state or Hyprland HDR color management.

## Qt

The build uses Qt 6.10.3 and packages the Qt for Android runtime libraries selected by Qt's Android deployment scanner. The CMake targets link Qt Core, Gui, Qml, Quick and Quick Controls 2. The build also requires Qt ShaderTools to compile the checked-in GLSL sources into QSB resources; the `qsb` tool and ShaderTools runtime library are not linked into or packaged with the APK. The QSB resource is consumed through Qt Gui/Qt Quick.

Qt modules and third-party components carry their own terms. The LGPL-3.0 text is included in the APK's **Settings → Open-source notices and licenses** screen and in the release notice bundle. The release workflow also publishes the Qt 6.10.3 Android arm64 SBOM, a native-library inventory extracted from the APK, and the corresponding official Qt 6.10.3 source archive after verifying its published SHA-256. The `LICENSE` and release notes explain how to obtain the app source and the build inputs used for a relinked build. This project does not claim that the SBOM alone replaces each applicable license notice.

Qt source release: https://download.qt.io/official_releases/qt/6.10/6.10.3/single/qt-everywhere-src-6.10.3.tar.xz
Qt source SHA-256: `cbc81e726b0ff3c0cdb0219bf74545e91cec013c4a8503c20f93f83d73dff5d2`

The Android manifest follows Qt's Android package template conventions. Qt source template: https://github.com/qt/qtbase/blob/v6.10.3/src/android/templates/AndroidManifest.xml

The Qt Online Installer places SPDX SBOM files under the installed target's `sbom/` directory. The release packaging step requires that directory from the `android_arm64_v8a` Qt 6.10.3 installation and publishes it alongside the APK. The included Apache-2.0 text applies to the AndroidX/CameraX/ML Kit/Kotlin components used by the Gradle Android source set; consult their resolved dependency metadata for exact transitive versions.

## Quickshell

Quickshell is not linked or bundled in the current prototype. The inspected project is LGPL-3.0. No Quickshell reuse is claimed. If it is added later, include the source and license material and satisfy LGPL relinking requirements.

## QR Code generator

`omarchy-plugin/third_party/qrcodegen.py` is vendored from Project Nayuki's Python QR Code generator library:

- Source: https://github.com/nayuki/QR-Code-generator/blob/master/python/qrcodegen.py
- License: MIT
- Copyright: Copyright (c) Project Nayuki

The upstream copyright and permission notice are included at the top of the vendored file.
