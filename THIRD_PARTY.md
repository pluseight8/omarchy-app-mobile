# Third-party notices

## Omalauncher

OmaApp acknowledges the original Omalauncher project by Simon Schubert:

- Source: https://github.com/SimonSchubert/omalauncher
- License: MIT
- Copyright notice in that project: Copyright (c) 2026 Simon Schubert and contributors

No Omalauncher source file has been copied into this first prototype. The required acknowledgment is included in both README files. Any later ported source must retain its original notices.

## Omarchy

The app's initial fallback color values are adapted from Omarchy's shell Color.qml:

- Source: https://github.com/omacom/omarchy/blob/quattro/shell/Commons/Color.qml
- License: MIT
- Copyright: Copyright (c) David Heinemeier Hansson

No Omarchy QML component is vendored in this prototype. Preserve the MIT notice if QML code is later copied or adapted.

## Hyprland geometry

The superellipse and rounding edge model in shared/geometry follows the public Hyprland rounding shader:

- Source: https://github.com/hyprwm/Hyprland/blob/main/src/render/shaders/glsl/rounding.glsl
- Related border behavior: https://github.com/hyprwm/Hyprland/blob/main/src/render/shaders/glsl/border.glsl
- License: BSD 3-Clause
- Copyright: Copyright (c) 2022-2026, vaxerski

BSD 3-Clause license text:

Copyright (c) 2022-2026, vaxerski. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The Qt ShaderEffect ports Hyprland's superellipse distance, edge smoothing, rounded border coverage, arbitrary-angle ten-stop OkLabA gradient interpolation, and the compositor's gamma-2.2 gradient conversion into Qt's QSB pipeline. It uses the same radius and rounding-power range in physical pixels after display-scale conversion. It does not reproduce compositor transforms or Hyprland's HDR color-management path.

## Qt

The build uses Qt 6.11 and packages Qt for Android libraries into the APK. Qt is available under LGPL-3.0-only, GPL licenses, or commercial terms depending on the module and distribution. The final release must ship the applicable notices and license texts, preserve a user-replaceable/relinkable Qt runtime where required, and include installation information required by the selected license terms.

The Android manifest follows Qt's Android package template conventions. Qt source: https://github.com/qt/qtbase/blob/v6.11.2/src/android/templates/AndroidManifest.xml

## Quickshell

Quickshell is not linked or bundled in the current prototype. The inspected project is LGPL-3.0. No Quickshell reuse is claimed. If it is added later, include the source and license material and satisfy LGPL relinking requirements.

## QR Code generator

`omarchy-plugin/third_party/qrcodegen.py` is vendored from Project Nayuki's Python QR Code generator library:

- Source: https://github.com/nayuki/QR-Code-generator/blob/master/python/qrcodegen.py
- License: MIT
- Copyright: Copyright (c) Project Nayuki

The upstream copyright and permission notice are included at the top of the vendored file.
