pragma Singleton

import QtQuick

QtObject {
    readonly property color foreground: "#cacccc"
    readonly property color background: "#101315"
    readonly property color accent: "#cacccc"
    readonly property color urgent: "#a55555"
    readonly property color muted: "#707880"

    readonly property color surface: Qt.darker(background, 1.18)
    readonly property color surfaceRaised: Qt.darker(background, 1.34)
    readonly property color outline: Qt.rgba(foreground.r, foreground.g, foreground.b, 0.4)

    readonly property real spacingSmall: 8
    readonly property real spacingGroup: 16
    readonly property real spacingSection: 24
    readonly property real defaultRoundingPower: 2
    readonly property real defaultCornerRadius: 0

    property real previewRadius: 24
    property real previewPower: 2
    property real previewBorderWidth: 2
}
