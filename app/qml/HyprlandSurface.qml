import QtQuick
import QtQuick.Window

Item {
    id: root

    property color fillColor: Theme.surface
    property color borderStart: Theme.outline
    property color borderEnd: borderStart
    property real borderGradientAngle: Theme.borderGradientAngle
    property real radius: Theme.defaultCornerRadius
    property real roundingPower: Theme.defaultRoundingPower
    property real borderWidth: 1
    readonly property bool shaderSupported: Qt.platform.os !== "android"

    ShaderEffect {
        anchors.fill: parent
        visible: root.shaderSupported
        supportsAtlasTextures: true

        property vector2d itemSize: Qt.vector2d(
            Math.max(1, root.width * Screen.devicePixelRatio),
            Math.max(1, root.height * Screen.devicePixelRatio))
        property color fillColor: root.fillColor
        property color borderStart: root.borderStart
        property color borderEnd: root.borderEnd
        property real borderGradientAngle: root.borderGradientAngle
        property real radius: root.radius * Screen.devicePixelRatio
        property real roundingPower: root.roundingPower
        property real borderWidth: root.borderWidth * Screen.devicePixelRatio

        fragmentShader: "qrc:/shaders/hyprland_rounded.frag.qsb"
    }

    // Android CI screenshots show diagonal artifacts from ShaderEffect here.
    // Use a native Quick rectangle on Android; keep the Hyprland shader on
    // desktop, where it is used for the superellipse preview.
    Rectangle {
        anchors.fill: parent
        visible: !root.shaderSupported
        color: root.fillColor
        radius: Math.max(0, Math.min(root.radius, Math.min(width, height) * 0.5))
        border.color: root.borderStart
        border.width: Math.max(0, Math.min(root.borderWidth, Math.min(width, height) * 0.5))
    }
}
