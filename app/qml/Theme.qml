pragma Singleton

import QtQuick

QtObject {
    property color foreground: "#cacccc"
    property color background: "#101315"
    property color accent: "#cacccc"
    property color urgent: "#a55555"
    property color muted: "#707880"
    property color activeBorderStart: accent
    property color activeBorderEnd: accent
    property var activeBorderStops: [accent, accent]
    property real borderGradientAngle: 45

    property color surface: Qt.darker(background, 1.18)
    property color surfaceRaised: Qt.darker(background, 1.34)
    readonly property color outline: Qt.rgba(foreground.r, foreground.g, foreground.b, 0.4)
    readonly property color controlSurface: Qt.rgba(foreground.r, foreground.g, foreground.b, controlFillAlpha)
    readonly property color selectedSurface: Qt.rgba(accent.r, accent.g, accent.b, selectedFillAlpha)

    property real spacingSmall: 8
    property real spacingGroup: 16
    property real spacingSection: 24
    property real spacingScale: 1
    property real fontBaseSize: 12
    property real controlFillAlpha: 0.04
    property real selectedFillAlpha: 0.18
    property real launcherBackgroundAlpha: 1
    property real popupBackgroundAlpha: 1
    property real defaultRoundingPower: 2
    property real defaultCornerRadius: 0

    property real previewRadius: 24
    property real previewPower: 2
    property real previewBorderWidth: 2
}
