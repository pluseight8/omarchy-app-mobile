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
        visible: root.safeBorderWidth > 0
        radius: root.safeRadius
        gradient: Gradient {
            orientation: {
                const angle = ((root.borderGradientAngle % 180) + 180) % 180
                return angle < 45 || angle > 135 ? Gradient.Horizontal : Gradient.Vertical
            }
            GradientStop { position: 0; color: root.borderStart }
            GradientStop { position: 1; color: root.borderEnd }
        }
    }

    Rectangle {
        anchors.fill: parent
        anchors.margins: root.safeBorderWidth
        color: root.fillColor
        radius: Math.max(0, root.safeRadius - root.safeBorderWidth)
    }
}
