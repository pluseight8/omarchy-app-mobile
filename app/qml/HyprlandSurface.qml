import QtQuick
import QtQuick.Window

Item {
    id: root

    property color fillColor: Theme.surface
    property color borderStart: Theme.outline
    property color borderEnd: borderStart
    property real radius: Theme.defaultCornerRadius
    property real roundingPower: Theme.defaultRoundingPower
    property real borderWidth: 1

    ShaderEffect {
        anchors.fill: parent
        supportsAtlasTextures: true

        property vector2d itemSize: Qt.vector2d(
            Math.max(1, root.width * Screen.devicePixelRatio),
            Math.max(1, root.height * Screen.devicePixelRatio))
        property color fillColor: root.fillColor
        property color borderStart: root.borderStart
        property color borderEnd: root.borderEnd
        property real radius: root.radius * Screen.devicePixelRatio
        property real roundingPower: root.roundingPower
        property real borderWidth: root.borderWidth * Screen.devicePixelRatio

        fragmentShader: "qrc:/shaders/hyprland_rounded.frag.qsb"
    }
}
