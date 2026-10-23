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
    property real nodePadding: widgetRoot.scalar(nodeProps.padding, 0)
    property color nodeForeground: widgetRoot.colorValue(nodeProps.color, Theme.foreground)
    property real spacerSize: widgetRoot.scalar(nodeProps.size, 0)
    property real contentImplicitWidth: nodeType === "Text" ? textContent.implicitWidth
                                       : nodeType === "Row" ? rowContent.implicitWidth
                                       : nodeType === "Flow" ? flowContent.implicitWidth
                                       : nodeType === "Grid" ? gridContent.implicitWidth
                                       : nodeType === "Box" ? boxContent.childrenRect.width
                                       : contentLayout.implicitWidth
    property real contentImplicitHeight: nodeType === "Text" ? textContent.implicitHeight
                                        : nodeType === "Row" ? rowContent.implicitHeight
                                        : nodeType === "Flow" ? flowContent.implicitHeight
                                        : nodeType === "Grid" ? gridContent.implicitHeight
                                        : nodeType === "Box" ? boxContent.childrenRect.height
                                        : contentLayout.implicitHeight

    implicitWidth: preferredWidth > 0 ? preferredWidth
                   : nodeType === "Spacer" ? spacerSize
                   : nodeType === "Ring" ? 64
                   : nodeType === "Sparkline" ? 96
                   : nodeType === "Image" ? 48
                   : Math.max(48, contentImplicitWidth + nodePadding * 2)
    implicitHeight: preferredHeight > 0 ? preferredHeight
                    : nodeType === "Spacer" ? spacerSize
                    : nodeType === "Ring" ? 64
                    : nodeType === "Sparkline" ? 36
                    : nodeType === "Image" ? 48
                    : Math.max(24, contentImplicitHeight + nodePadding * 2)
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
        id: rowContent
        anchors.fill: parent
        anchors.margins: node.nodePadding
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

    Flow {
        id: flowContent
        anchors.fill: parent
        anchors.margins: node.nodePadding
        visible: node.nodeType === "Flow"
        spacing: widgetRoot.scalar(node.nodeProps.gap, 4)

        Repeater {
            model: node.nodeType === "Flow" ? node.nodeChildren : []
            delegate: OwlNodeLoader {
                required property var modelData
                width: implicitWidth
                height: implicitHeight
                nodeData: modelData
                widgetRoot: node.widgetRoot
            }
        }
    }

    GridLayout {
        id: gridContent
        anchors.fill: parent
        anchors.margins: node.nodePadding
        visible: node.nodeType === "Grid"
        columns: Math.max(1, Math.min(8, Math.floor(widgetRoot.scalar(node.nodeProps.columns, 2))))
        rowSpacing: widgetRoot.scalar(node.nodeProps.gap, 4)
        columnSpacing: widgetRoot.scalar(node.nodeProps.gap, 4)

        Repeater {
            model: node.nodeType === "Grid" ? node.nodeChildren : []
            delegate: OwlNodeLoader {
                required property var modelData
                nodeData: modelData
                widgetRoot: node.widgetRoot
                Layout.fillWidth: true
            }
        }
    }

    Item {
        id: spacer
        anchors.fill: parent
        visible: node.nodeType === "Spacer"
        Accessible.ignored: true
    }

    Text {
        id: textContent
        anchors.fill: parent
        anchors.margins: node.nodePadding
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

    Image {
        anchors.fill: parent
        visible: node.nodeType === "Image"
        source: visible ? widgetRoot.imageSource(node.nodeProps.src) : ""
        fillMode: node.nodeProps.fit === "cover" ? Image.PreserveAspectCrop
                  : node.nodeProps.fit === "fill" ? Image.Stretch : Image.PreserveAspectFit
        asynchronous: true
        smooth: true
        Accessible.name: String(node.nodeProps.label || "OWL image")
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
        id: ring
        anchors.fill: parent
        visible: node.nodeType === "Ring"
        antialiasing: true
        onPaint: {
            const context = getContext("2d")
            context.reset()
            context.clearRect(0, 0, width, height)

            const maximum = Math.max(0.000001, widgetRoot.scalar(node.nodeProps.max, 1))
            const value = Math.max(0, Math.min(maximum, widgetRoot.scalar(node.nodeProps.value, 0)))
            const ratio = value / maximum
            const thickness = Math.max(0.5, Math.min(
                Math.min(width, height) / 2,
                widgetRoot.scalar(node.nodeProps.thickness, 6)))
            const radius = Math.max(0, Math.min(width, height) / 2 - thickness / 2)
            const start = (-90 + widgetRoot.scalar(node.nodeProps.start, 0)) * Math.PI / 180
            const sweep = widgetRoot.scalar(node.nodeProps.sweep, 360) * Math.PI / 180
            const centerX = width / 2
            const centerY = height / 2

            context.lineWidth = thickness
            context.lineCap = "round"
            context.strokeStyle = widgetRoot.colorValue(node.nodeProps.track, Theme.surfaceRaised)
            context.beginPath()
            context.arc(centerX, centerY, radius, start, start + sweep, sweep < 0)
            context.stroke()

            if (ratio > 0) {
                context.strokeStyle = widgetRoot.colorValue(node.nodeProps.color, Theme.accent)
                context.beginPath()
                context.arc(centerX, centerY, radius, start, start + sweep * ratio, sweep < 0)
                context.stroke()
            }
        }
    }

    Canvas {
        id: sparkline
        anchors.fill: parent
        visible: node.nodeType === "Sparkline"
        antialiasing: true
        onPaint: {
            const context = getContext("2d")
            context.reset()
            context.clearRect(0, 0, width, height)
            const values = node.nodeProps.values
            if (!Array.isArray(values) || values.length === 0 || width <= 0 || height <= 0)
                return

            const samples = values.map(function(value) {
                return value === null ? NaN : Number(value)
            }).filter(function(value) { return Number.isFinite(value) })
            if (samples.length === 0)
                return

            let minimum = widgetRoot.scalar(node.nodeProps.min,
                samples.reduce(function(a, b) { return Math.min(a, b) }, samples[0]))
            let maximum = widgetRoot.scalar(node.nodeProps.max,
                samples.reduce(function(a, b) { return Math.max(a, b) }, samples[0]))
            if (maximum <= minimum) {
                maximum = minimum + 1
            }

            const inset = Math.max(1, widgetRoot.scalar(node.nodeProps.thickness, 2))
            const point = function(index) {
                const x = samples.length === 1 ? width / 2 : index * width / (samples.length - 1)
                const fraction = Math.max(0, Math.min(1, (samples[index] - minimum) / (maximum - minimum)))
                return Qt.point(x, height - inset - fraction * Math.max(0, height - 2 * inset))
            }
            const color = widgetRoot.colorValue(node.nodeProps.color, Theme.accent)
            context.lineWidth = inset
            context.lineCap = "round"
            context.lineJoin = "round"
            context.strokeStyle = color
            context.beginPath()
            for (let index = 0; index < samples.length; ++index) {
                const p = point(index)
                if (index === 0)
                    context.moveTo(p.x, p.y)
                else
                    context.lineTo(p.x, p.y)
            }
            if (node.nodeProps.area === true
                    || (node.nodeProps.area !== undefined && node.nodeProps.area !== false
                        && node.nodeProps.area !== null)) {
                const last = point(samples.length - 1)
                const first = point(0)
                context.lineTo(last.x, height)
                context.lineTo(first.x, height)
                context.closePath()
                const areaColor = node.nodeProps.area === true
                        ? Qt.rgba(color.r, color.g, color.b, 0.18)
                        : widgetRoot.colorValue(node.nodeProps.area, Qt.rgba(color.r, color.g, color.b, 0.18))
                context.fillStyle = areaColor
                context.fill()
                context.beginPath()
                for (let index = 0; index < samples.length; ++index) {
                    const p = point(index)
                    if (index === 0)
                        context.moveTo(p.x, p.y)
                    else
                        context.lineTo(p.x, p.y)
                }
            }
            context.stroke()
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

        onImageLoaded: requestPaint()

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
                } else if (item.op === "image" && args.length >= 5) {
                    const source = widgetRoot.imageSource(args[0])
                    if (!canvas.isImageLoaded(source)
                            && !canvas.isImageLoading(source)
                            && !canvas.isImageError(source)) {
                        canvas.loadImage(source)
                    }
                    if (canvas.isImageLoaded(source))
                        context.drawImage(source, Number(args[1]), Number(args[2]),
                                          Number(args[3]), Number(args[4]))
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

    onNodeDataChanged: {
        if (nodeType === "Canvas")
            canvas.requestPaint()
        else if (nodeType === "Ring")
            ring.requestPaint()
        else if (nodeType === "Sparkline")
            sparkline.requestPaint()
    }
}
