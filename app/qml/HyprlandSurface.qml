import QtQuick
import QtQuick.Window

Item {
    id: root

    property color fillColor: Theme.surface
    property color borderStart: Theme.outline
    property color borderEnd: borderStart
    property var borderStops: []
    property real borderGradientAngle: Theme.borderGradientAngle
    property real radius: Theme.defaultCornerRadius
    property real roundingPower: Theme.defaultRoundingPower
    property real borderWidth: 1

    readonly property real pixelRatio: Screen.devicePixelRatio > 0 ? Screen.devicePixelRatio : 1
    readonly property real safeBorderWidth: Math.max(0, Math.min(borderWidth, Math.min(width, height) * 0.5))
    // Hyprland expands a window's border outwards and adds the border width to
    // its outer radius. This Item contains that full border box, so reserve the
    // border inset and keep the inner content radius equal to decoration:rounding.
    readonly property real safeRadius: Math.max(0, Math.min(
        radius, Math.min(width, height) * 0.5 - safeBorderWidth))
    readonly property var effectiveBorderStops: borderStops && borderStops.length > 0
                                               ? borderStops.slice(0, 10)
                                               : [borderStart, borderEnd]

    function gradientStop(index) {
        return effectiveBorderStops[Math.min(index, effectiveBorderStops.length - 1)]
    }

    ShaderEffect {
        id: surfaceShader

        anchors.fill: parent

        property vector2d itemSize: Qt.vector2d(
            Math.max(1, root.width * root.pixelRatio),
            Math.max(1, root.height * root.pixelRatio))
        property color fillColor: root.fillColor
        property color borderStart: root.borderStart
        property color borderEnd: root.borderEnd
        // Keep this a float: Qt's OpenGL ES 2.0 shader variant does not
        // implement integer clamp/min overloads used by GLSL ES 1.00.
        property real borderStopCount: root.effectiveBorderStops.length
        property color borderStop0: root.gradientStop(0)
        property color borderStop1: root.gradientStop(1)
        property color borderStop2: root.gradientStop(2)
        property color borderStop3: root.gradientStop(3)
        property color borderStop4: root.gradientStop(4)
        property color borderStop5: root.gradientStop(5)
        property color borderStop6: root.gradientStop(6)
        property color borderStop7: root.gradientStop(7)
        property color borderStop8: root.gradientStop(8)
        property color borderStop9: root.gradientStop(9)
        property real borderGradientAngle: root.borderGradientAngle
        property real radius: (root.safeRadius + root.safeBorderWidth) * root.pixelRatio
        property real roundingPower: root.roundingPower
        property real borderWidth: root.safeBorderWidth * root.pixelRatio

        // Compile both stages from the same GLSL target set and share their
        // uniform block layout across graphics backends.
        vertexShader: "qrc:/shaders/hyprland_rounded.vert.qsb"
        fragmentShader: "qrc:/shaders/hyprland_rounded.frag.qsb"

        onStatusChanged: {
            if (status === ShaderEffect.Error)
                console.error("HyprlandSurface shader failed: " + log)
            else if (status === ShaderEffect.Compiled)
                console.info("HyprlandSurface shader ready")
        }

        Component.onCompleted: {
            console.info("HyprlandSurface shader initial status: " + status + " " + log)
        }
    }
}
