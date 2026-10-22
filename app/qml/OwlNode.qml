import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Item {
    id: node
    required property var nodeData
    required property var widgetRoot
    property var nodeProps: nodeData.props || ({})
    property var nodeChildren: nodeData.children || []
    property string nodeType: nodeData.type || ""
    property real preferredWidth: widgetRoot.scalar(nodeProps.width, 0)
    property real preferredHeight: widgetRoot.scalar(nodeProps.height, 0)
    property color nodeForeground: widgetRoot.colorValue(nodeProps.color, Theme.foreground)

    implicitWidth: preferredWidth > 0 ? preferredWidth : Math.max(48, contentLayout.implicitWidth)
    implicitHeight: preferredHeight > 0 ? preferredHeight : Math.max(24, contentLayout.implicitHeight)
    height: implicitHeight

    ColumnLayout {
        id: contentLayout
        anchors.fill: parent
        anchors.margins: widgetRoot.scalar(node.nodeProps.padding, 0)
        visible: node.nodeType === "Column" || node.nodeType === ""
        spacing: widgetRoot.scalar(node.nodeProps.gap, 4)

        Repeater {
            model: node.nodeType === "Column" || node.nodeType === "" ? node.nodeChildren : []
            delegate: OwlNodeLoader {
                required property var modelData
                nodeData: modelData
                widgetRoot: node.widgetRoot
                Layout.fillWidth: node.nodeProps.fill === "width" ||
                                  widgetRoot.scalar(node.nodeProps.weight, 0) > 0
            }
        }
    }

    Item {
        id: boxContent
        anchors.fill: parent
        anchors.margins: widgetRoot.scalar(node.nodeProps.padding, 0)
        visible: node.nodeType === "Box"

        Repeater {
            model: node.nodeType === "Box" ? node.nodeChildren : []
            delegate: Item {
                id: alignedChild
                required property var modelData
                property string alignment: String(node.nodeProps.align || "center")
                width: child.implicitWidth
                height: child.implicitHeight
                x: alignment === "end" || alignment.endsWith("End")
                   ? boxContent.width - width
                   : alignment === "center" ? (boxContent.width - width) / 2 : 0
                y: alignment === "bottom" || alignment.startsWith("bottom")
                   ? boxContent.height - height
                   : alignment === "top" || alignment.startsWith("top") ? 0
                     : (boxContent.height - height) / 2

                OwlNodeLoader {
                    id: child
                    anchors.fill: parent
                    nodeData: alignedChild.modelData
                    widgetRoot: node.widgetRoot
                }
            }
        }
    }

    RowLayout {
        anchors.fill: parent
        anchors.margins: widgetRoot.scalar(node.nodeProps.padding, 0)
        visible: node.nodeType === "Row"
        spacing: widgetRoot.scalar(node.nodeProps.gap, 4)
        Repeater {
            model: node.nodeType === "Row" ? node.nodeChildren : []
            delegate: OwlNodeLoader {
                required property var modelData
                nodeData: modelData
                widgetRoot: node.widgetRoot
                Layout.fillWidth: widgetRoot.scalar(node.nodeProps.weight, 0) > 0
            }
        }
    }

    Text {
        anchors.fill: parent
        visible: node.nodeType === "Text"
        text: node.nodeProps.text === undefined ? "" : String(node.nodeProps.text)
        color: node.nodeForeground
        font.pixelSize: Theme.fontSize(widgetRoot.scalar(node.nodeProps.fontSize,
                                          node.nodeProps.style === "huge" ? 34
                                          : node.nodeProps.style === "title" ? 20
                                          : node.nodeProps.style === "small" || node.nodeProps.style === "muted" ? 12 : 15))
        font.family: node.nodeProps.font === "mono" ? "monospace"
                     : node.nodeProps.font === "serif" ? "serif"
                     : node.nodeProps.font === "sans" ? "sans-serif" : Theme.fontFamily
        font.weight: node.nodeProps.bold === true ? Font.DemiBold
                     : widgetRoot.scalar(node.nodeProps.fontWeight, Font.Normal)
        font.italic: node.nodeProps.italic === true
        font.letterSpacing: widgetRoot.scalar(node.nodeProps.letterSpacing, 0)
        horizontalAlignment: node.nodeProps.align === "center"
                             || String(node.nodeProps.align || "").endsWith("Center") ? Text.AlignHCenter
                             : node.nodeProps.align === "end"
                               || String(node.nodeProps.align || "").endsWith("End") ? Text.AlignRight : Text.AlignLeft
        verticalAlignment: String(node.nodeProps.align || "").startsWith("top") ? Text.AlignTop
                            : String(node.nodeProps.align || "").startsWith("bottom") ? Text.AlignBottom
                              : Text.AlignVCenter
        maximumLineCount: Math.max(1, widgetRoot.scalar(node.nodeProps.maxLines, 4))
        wrapMode: Text.WordWrap
        elide: Text.ElideRight
    }

    Button {
        anchors.fill: parent
        visible: node.nodeType === "Button"
        enabled: false
        text: String(node.nodeProps.text || "")
        Accessible.name: text
    }

    ProgressBar {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        visible: node.nodeType === "Progress"
        from: 0
        to: Math.max(1, widgetRoot.scalar(node.nodeProps.max, 1))
        value: widgetRoot.scalar(node.nodeProps.value, 0)
        background: Rectangle {
            implicitHeight: 4
            color: node.widgetRoot.colorValue(node.nodeProps.track, Theme.surfaceRaised)
            radius: height / 2
        }
        contentItem: Item {
            Rectangle {
                width: parent.width * Math.max(0, Math.min(1,
                    node.widgetRoot.scalar(node.nodeProps.value, 0)
                    / Math.max(1, node.widgetRoot.scalar(node.nodeProps.max, 1))))
                height: parent.height
                color: node.widgetRoot.colorValue(node.nodeProps.color, Theme.accent)
                radius: height / 2
            }
        }
    }

    Canvas {
        id: canvas
        anchors.fill: parent
        visible: node.nodeType === "Canvas"
        antialiasing: true
        onPaint: {
            const context = getContext("2d")
            context.reset()
            context.clearRect(0, 0, width, height)
            drawNodes(context, node.nodeChildren)
        }

        function drawNodes(context, items) {
            for (const item of items) {
                if (item.type !== "draw")
                    continue
                const args = item.args || []
                const named = item.named || ({})
                const fallbackColor = widgetRoot.colorValue(named.color, Theme.foreground)
                const fill = widgetRoot.paintValue(context, named.fill, fallbackColor)
                const stroke = widgetRoot.paintValue(context, named.stroke, fallbackColor)
                context.fillStyle = fill
                context.strokeStyle = stroke
                context.lineWidth = Math.max(0.5, widgetRoot.scalar(named.width, 1))
                context.lineCap = named.cap === "round" ? "round" : named.cap === "square" ? "square" : "butt"

                if (item.op === "rect" && args.length >= 4) {
                    const x = Number(args[0])
                    const y = Number(args[1])
                    const w = Number(args[2])
                    const h = Number(args[3])
                    const radius = Math.max(0, Math.min(widgetRoot.scalar(named.radius, 0), Math.abs(w) / 2, Math.abs(h) / 2))
                    context.beginPath()
                    if (radius === 0) {
                        context.rect(x, y, w, h)
                    } else {
                        context.moveTo(x + radius, y)
                        context.lineTo(x + w - radius, y)
                        context.quadraticCurveTo(x + w, y, x + w, y + radius)
                        context.lineTo(x + w, y + h - radius)
                        context.quadraticCurveTo(x + w, y + h, x + w - radius, y + h)
                        context.lineTo(x + radius, y + h)
                        context.quadraticCurveTo(x, y + h, x, y + h - radius)
                        context.lineTo(x, y + radius)
                        context.quadraticCurveTo(x, y, x + radius, y)
                        context.closePath()
                    }
                    if (named.fill !== undefined || named.stroke === undefined)
                        context.fill()
                    if (named.stroke !== undefined)
                        context.stroke()
                } else if (item.op === "circle" && args.length >= 3) {
                    context.beginPath()
                    context.arc(Number(args[0]), Number(args[1]), Math.max(0, Number(args[2])), 0, Math.PI * 2)
                    if (named.fill !== undefined || named.stroke === undefined)
                        context.fill()
                    if (named.stroke !== undefined)
                        context.stroke()
                } else if (item.op === "arc" && args.length >= 5) {
                    const start = (-90 + Number(args[3])) * Math.PI / 180
                    const sweep = Number(args[4]) * Math.PI / 180
                    context.beginPath()
                    context.arc(Number(args[0]), Number(args[1]), Math.max(0, Number(args[2])),
                                start, start + sweep, sweep < 0)
                    if (named.fill !== undefined)
                        context.fill()
                    if (named.stroke !== undefined || named.fill === undefined)
                        context.stroke()
                } else if (item.op === "line" && args.length >= 4) {
                    context.beginPath()
                    context.moveTo(Number(args[0]), Number(args[1]))
                    context.lineTo(Number(args[2]), Number(args[3]))
                    context.strokeStyle = stroke
                    context.stroke()
                } else if (item.op === "text" && args.length >= 3) {
                    const weight = named.bold === true || Number(named.fontWeight) >= 600 ? "600 " : ""
                    context.font = weight + String(Theme.fontSize(widgetRoot.scalar(named.size, 14))) + "px monospace"
                    context.textAlign = named.align === "center" ? "center" : named.align === "end" ? "right" : "left"
                    context.textBaseline = "middle"
                    context.fillStyle = widgetRoot.paintValue(context, named.color, fallbackColor)
                    context.fillText(String(args[0]), Number(args[1]), Number(args[2]))
                } else if (item.op === "path" && args.length >= 1 && Array.isArray(args[0])) {
                    const points = args[0]
                    context.beginPath()
                    points.forEach(function(point, index) {
                        const x = Array.isArray(point) ? Number(point[0]) : Number(point.x)
                        const y = Array.isArray(point) ? Number(point[1]) : Number(point.y)
                        if (index === 0)
                            context.moveTo(x, y)
                        else
                            context.lineTo(x, y)
                    })
                    if (named.closed === true)
                        context.closePath()
                    if (named.fill !== undefined)
                        context.fill()
                    if (named.stroke !== undefined)
                        context.stroke()
                } else if (item.op === "sprite" && args.length >= 4) {
                    const rows = args[0]
                    const cell = Math.max(1, Number(args[3]))
                    const palette = named.palette || ({})
                    if (Array.isArray(rows)) {
                        rows.forEach(function(row, rowIndex) {
                            const pixels = String(row)
                            for (let column = 0; column < pixels.length; ++column) {
                                const key = pixels.charAt(column)
                                const pixelColor = palette[key] === undefined
                                        ? widgetRoot.colorValue(named.fill, fallbackColor)
                                        : widgetRoot.colorValue(palette[key], fallbackColor)
                                if (key === ".")
                                    continue
                                context.fillStyle = pixelColor
                                context.fillRect(Number(args[1]) + column * cell,
                                                 Number(args[2]) + rowIndex * cell, cell, cell)
                            }
                        })
                    }
                } else if (item.children) {
                    context.save()
                    if (item.op === "translate" && args.length >= 2) {
                        context.translate(Number(args[0]), Number(args[1]))
                    } else if (item.op === "rotate" && args.length >= 1) {
                        if (args.length >= 3) {
                            context.translate(Number(args[1]), Number(args[2]))
                            context.rotate(Number(args[0]) * Math.PI / 180)
                            context.translate(-Number(args[1]), -Number(args[2]))
                        } else {
                            context.rotate(Number(args[0]) * Math.PI / 180)
                        }
                    } else if (item.op === "scale" && args.length >= 1) {
                        const sx = Number(args[0])
                        const sy = args.length >= 2 ? Number(args[1]) : sx
                        if (args.length >= 4) {
                            context.translate(Number(args[2]), Number(args[3]))
                            context.scale(sx, sy)
                            context.translate(-Number(args[2]), -Number(args[3]))
                        } else {
                            context.scale(sx, sy)
                        }
                    } else if (item.op === "clip" && args.length >= 4) {
                        context.beginPath()
                        context.rect(Number(args[0]), Number(args[1]), Number(args[2]), Number(args[3]))
                        context.clip()
                    } else if (item.op === "layer" && args.length >= 1) {
                        context.globalAlpha *= Math.max(0, Math.min(1, Number(args[0])))
                    }
                    drawNodes(context, item.children)
                    context.restore()
                }
            }
        }
    }

    onNodeDataChanged: if (nodeType === "Canvas") canvas.requestPaint()
}
