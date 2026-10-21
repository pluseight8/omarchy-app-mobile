import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Item {
    id: root

    required property var owlModel
    required property string widgetId
    required property string themeJson
    property int itemIndex: -1
    property bool useSourceText: false
    property string sourceText: ""
    property var document: ({})
    property string renderError: ""

    implicitHeight: 156

    Drag.active: owlWidgetDragHandler.active
    Drag.dragType: Drag.Automatic
    Drag.supportedActions: Qt.MoveAction
    Drag.mimeData: ({
        "application/x-omaapp-owl-widget": root.widgetId
    })
    opacity: owlWidgetDragHandler.active ? 0.65 : 1

    DragHandler {
        id: owlWidgetDragHandler
        target: null
        acceptedButtons: Qt.LeftButton
        enabled: !root.useSourceText && root.widgetId.length > 0 && root.itemIndex >= 0
    }

    function refresh() {
        if (!owlModel || width <= 0 || height <= 0)
            return
        let rendered = {}
        try {
            const response = useSourceText
                    ? owlModel.renderSource(sourceText, width, height, themeJson)
                    : owlModel.renderWidget(widgetId, width, height, themeJson)
            rendered = JSON.parse(response)
        } catch (error) {
            rendered = { error: qsTr("OWL preview could not be rendered.") }
        }
        document = rendered
        renderError = rendered.error || ""
    }

    function colorValue(value, fallback) {
        if (value && typeof value === "object" && value.__owlColor === true) {
            const palette = JSON.parse(themeJson || "{}")
            const base = value.theme ? palette[value.theme] : value.argb
            if (!base)
                return fallback
            const color = Qt.color(base)
            return Qt.rgba(color.r, color.g, color.b,
                           Math.max(0, Math.min(1, color.a *
                               (value.alpha === undefined ? 1 : Number(value.alpha)))))
        }
        if (typeof value === "string" && value.startsWith("@theme/")) {
            const palette = JSON.parse(themeJson || "{}")
            return palette[value.substring(7)] || fallback
        }
        if (typeof value === "string" && value.length > 0)
            return Qt.color(value)
        return fallback
    }

    function paintValue(context, value, fallback) {
        if (!value || value.__owlGradient !== true)
            return colorValue(value, fallback)
        const coordinates = value.coords || []
        const colors = value.colors || []
        if (colors.length === 0)
            return fallback
        let gradient
        if (value.radial === true) {
            if (coordinates.length < 3)
                return fallback
            gradient = context.createRadialGradient(Number(coordinates[0]), Number(coordinates[1]), 0,
                Number(coordinates[0]), Number(coordinates[1]), Number(coordinates[2]))
        } else {
            if (coordinates.length < 4)
                return fallback
            gradient = context.createLinearGradient(Number(coordinates[0]), Number(coordinates[1]),
                Number(coordinates[2]), Number(coordinates[3]))
        }
        for (let index = 0; index < colors.length; ++index)
            gradient.addColorStop(colors.length === 1 ? 0 : index / (colors.length - 1),
                                  colorValue(colors[index], fallback))
        return gradient
    }

    function scalar(value, fallback) {
        const parsed = Number(value)
        return Number.isFinite(parsed) ? parsed : fallback
    }

    HyprlandSurface {
        anchors.fill: parent
        fillColor: root.colorValue(root.document.background, Theme.surface)
        borderStart: Theme.outline
        borderEnd: borderStart
        radius: Theme.defaultCornerRadius
        borderWidth: 1

        Item {
            anchors.fill: parent
            anchors.margins: root.scalar(root.document.padding, 14)
            visible: root.renderError.length === 0

            ColumnLayout {
                anchors.fill: parent
                spacing: Theme.space(8)

                Repeater {
                    model: root.document.nodes || []
                    delegate: OwlNode {
                        required property var modelData
                        nodeData: modelData
                        widgetRoot: root
                        Layout.fillWidth: true
                    }
                }
            }
        }

        Text {
            anchors.fill: parent
            anchors.margins: 18
            visible: root.renderError.length > 0
            text: root.renderError
            color: Theme.muted
            font.pixelSize: Theme.fontSize(12)
            wrapMode: Text.WordWrap
        }
    }

    DropArea {
        anchors.fill: parent
        z: 2
        enabled: !root.useSourceText && root.widgetId.length > 0
        keys: ["application/x-omaapp-owl-widget"]

        onDropped: function(drop) {
            const sourceId = drop.getDataAsString("application/x-omaapp-owl-widget")
            if (sourceId && sourceId !== root.widgetId
                    && root.owlModel.moveWidget(sourceId, root.itemIndex)) {
                drop.acceptProposedAction()
            }
        }
    }

    Timer {
        interval: 1000
        repeat: true
        running: root.visible && root.document.animated === true
        onTriggered: root.refresh()
    }

    onWidthChanged: refresh()
    onHeightChanged: refresh()
    onWidgetIdChanged: refresh()
    onThemeJsonChanged: refresh()
    onSourceTextChanged: refresh()
    onUseSourceTextChanged: refresh()
    Component.onCompleted: Qt.callLater(refresh)
}
