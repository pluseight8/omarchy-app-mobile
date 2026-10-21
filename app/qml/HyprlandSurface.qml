import QtQuick

Item {
    id: root

    property color fillColor: Theme.surface
    property color borderStart: Theme.outline
    property color borderEnd: borderStart
    property real borderGradientAngle: Theme.borderGradientAngle
    property real radius: Theme.defaultCornerRadius
    property real roundingPower: Theme.defaultRoundingPower
    property real borderWidth: 1

    readonly property real safeRadius: Math.max(0, Math.min(radius, Math.min(width, height) * 0.5))
    readonly property real safeBorderWidth: Math.max(0, Math.min(borderWidth, Math.min(width, height) * 0.5))

    Rectangle {
        anchors.fill: parent
        color: root.fillColor
        radius: root.safeRadius
        border.color: root.borderStart
        border.width: root.safeBorderWidth
    }
}
