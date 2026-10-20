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
    property real borderGradientAngle: 45

    readonly property color surface: Qt.darker(background, 1.18)
    readonly property color surfaceRaised: Qt.darker(background, 1.34)
    readonly property color outline: Qt.rgba(foreground.r, foreground.g, foreground.b, 0.4)

    property real spacingSmall: 8
    property real spacingGroup: 16
    property real spacingSection: 24
    property real defaultRoundingPower: 2
    property real defaultCornerRadius: 0

    property real previewRadius: 24
    property real previewPower: 2
    property real previewBorderWidth: 2
}
