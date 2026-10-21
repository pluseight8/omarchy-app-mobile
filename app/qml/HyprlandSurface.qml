import QtQuick
import QtQuick.Window
import OmaApp.Native 1.0

HyprlandSurfaceItem {
    fillColor: Theme.surface
    borderStart: Theme.outline
    borderEnd: borderStart
    borderStops: []
    borderGradientAngle: Theme.borderGradientAngle
    radius: Theme.defaultCornerRadius
    roundingPower: Theme.defaultRoundingPower
    borderWidth: 1
    pixelRatio: Screen.devicePixelRatio > 0 ? Screen.devicePixelRatio : 1
}
