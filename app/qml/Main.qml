import QtQuick
import QtQuick.Accessibility
import QtQuick.Controls
import QtQuick.Layouts
import OmaApp.Native 1.0

ApplicationWindow {
    id: window

    visible: true
    width: 412
    height: 892
    minimumWidth: 340
    minimumHeight: 560
    title: qsTr("OmaApp")
    color: Theme.background

    property int selectedPage: 0

    LauncherModel {
        id: launcher
    }

    PairingModel {
        id: pairing
    }

    function parseHyprlandColor(value, fallback) {
        const raw = String(value || "").trim()
        const hex = raw.match(/^#([0-9a-f]{6})([0-9a-f]{2})?$/i)
        if (hex)
            return hex[2] ? "#" + hex[2] + hex[1] : "#" + hex[1]
        const rgba = raw.match(/^rgba\(([0-9a-f]{8})\)$/i)
        if (rgba) {
            const channels = rgba[1]
            return "#" + channels.slice(6, 8) + channels.slice(0, 6)
        }
        const rgb = raw.match(/^rgb\(([0-9a-f]{6})\)$/i)
        if (rgb)
            return "#" + rgb[1]
        return fallback
    }

    function boundedNumber(value, fallback, minimum, maximum) {
        const number = Number(value)
        return Number.isFinite(number) ? Math.max(minimum, Math.min(maximum, number)) : fallback
    }

    function applySyncedTheme() {
        if (pairing.snapshot.theme === undefined) {
            Theme.foreground = "#cacccc"
            Theme.background = "#101315"
            Theme.accent = "#cacccc"
            Theme.urgent = "#a55555"
            Theme.muted = "#707880"
            Theme.activeBorderStart = Theme.accent
            Theme.activeBorderEnd = Theme.accent
            Theme.borderGradientAngle = 45
            Theme.spacingSmall = 8
            Theme.spacingGroup = 16
            Theme.spacingSection = 24
            Theme.defaultCornerRadius = 0
            Theme.defaultRoundingPower = 2
            Theme.previewRadius = 24
            Theme.previewPower = 2
            Theme.previewBorderWidth = 1
            return
        }

        const theme = pairing.snapshot.theme || {}
        const colors = theme.colors || {}
        const shell = theme.shell || {}
        const spacing = shell.spacing || {}
        const geometry = pairing.snapshot.hyprland || {}

        Theme.background = parseHyprlandColor(colors.background, Theme.background)
        Theme.foreground = parseHyprlandColor(colors.foreground, Theme.foreground)
        Theme.accent = parseHyprlandColor(colors.accent || colors.color4, Theme.accent)
        Theme.urgent = parseHyprlandColor(colors.red || colors.color1, Theme.urgent)
        Theme.muted = parseHyprlandColor(colors.muted || colors.color8, Theme.muted)

        const border = String(geometry.activeBorder || colors.hyprland_active_border || "")
        const borderStops = border.match(/rgba\([0-9a-f]{8}\)|rgb\([0-9a-f]{6}\)|#[0-9a-f]{6,8}/ig) || []
        Theme.activeBorderStart = parseHyprlandColor(borderStops[0], Theme.accent)
        Theme.activeBorderEnd = parseHyprlandColor(borderStops[1] || borderStops[0], Theme.activeBorderStart)
        const angleMatch = border.match(/(-?\d+(?:\.\d+)?)deg\b/i)
        const angle = geometry.activeBorderAngle !== null && geometry.activeBorderAngle !== undefined
                ? Number(geometry.activeBorderAngle)
                : angleMatch ? Number(angleMatch[1]) : NaN
        if (Number.isFinite(angle))
            Theme.borderGradientAngle = ((angle % 360) + 360) % 360

        if (geometry.rounding !== null && geometry.rounding !== undefined)
            Theme.previewRadius = boundedNumber(geometry.rounding, Theme.previewRadius, 0, 100)
        if (geometry.roundingPower !== null && geometry.roundingPower !== undefined) {
            Theme.previewPower = boundedNumber(geometry.roundingPower, Theme.previewPower, 1, 10)
            Theme.defaultRoundingPower = Theme.previewPower
        }
        if (geometry.borderWidth !== null && geometry.borderWidth !== undefined)
            Theme.previewBorderWidth = boundedNumber(geometry.borderWidth, Theme.previewBorderWidth, 0, 12)
        Theme.defaultCornerRadius = Theme.previewRadius

        const spacingScale = boundedNumber(spacing.scale, 1, 0.5, 3)
        let smallSpacing = 8
        if (spacing["row-gap"] !== undefined)
            smallSpacing = spacing["row-gap"]
        else if (spacing.md !== undefined)
            smallSpacing = spacing.md
        else if (spacing.sm !== undefined)
            smallSpacing = spacing.sm
        else if (geometry.gapsIn !== null && geometry.gapsIn !== undefined)
            smallSpacing = geometry.gapsIn

        let groupSpacing = 16
        if (spacing["panel-gap"] !== undefined)
            groupSpacing = spacing["panel-gap"]
        else if (spacing.xxl !== undefined)
            groupSpacing = spacing.xxl
        else if (geometry.gapsOut !== null && geometry.gapsOut !== undefined)
            groupSpacing = geometry.gapsOut

        let sectionSpacing = 24
        if (spacing["panel-padding"] !== undefined)
            sectionSpacing = spacing["panel-padding"]
        else if (spacing.huge !== undefined)
            sectionSpacing = spacing.huge

        Theme.spacingSmall = boundedNumber(smallSpacing, 8, 0, 100) * spacingScale
        Theme.spacingGroup = boundedNumber(groupSpacing, 16, 0, 100) * spacingScale
        Theme.spacingSection = boundedNumber(sectionSpacing, 24, 0, 100) * spacingScale
    }

    Connections {
        target: pairing
        function onStateChanged() { window.applySyncedTheme() }
    }

    Dialog {
        id: forgetDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        title: qsTr("Forget this computer?")
        standardButtons: Dialog.Ok | Dialog.Cancel
        onAccepted: pairing.forget()

        Label {
            text: qsTr("This clears the saved key and cached theme on this phone. If the computer is offline, remove this phone from its OmaApp widget to revoke its saved token.")
            wrapMode: Text.WordWrap
        }
    }

    Component.onCompleted: {
        launcher.refresh()
        applySyncedTheme()
    }

    Image {
        anchors.fill: parent
        z: -2
        source: pairing.wallpaperUrl
        visible: source.toString().length > 0
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: false
    }

    Rectangle {
        anchors.fill: parent
        z: -1
        color: Theme.background
        opacity: pairing.wallpaperUrl.length > 0 ? 0.78 : 1
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 22
        anchors.rightMargin: 22
        anchors.topMargin: 24
        anchors.bottomMargin: 16
        spacing: Theme.spacingGroup

        RowLayout {
            Layout.fillWidth: true
            spacing: 12

            Rectangle {
                Layout.preferredWidth: 10
                Layout.preferredHeight: 10
                radius: 5
                color: Theme.accent
                    Accessible.ignored: true
            }

            Text {
                text: qsTr("OMA / APP")
                color: Theme.foreground
                font.pixelSize: 15
                font.weight: Font.DemiBold
                font.letterSpacing: 2.2
            }

            Item { Layout.fillWidth: true }

            Text {
                text: launcher.platformSupported ? qsTr("ANDROID") : qsTr("DESKTOP PREVIEW")
                color: Theme.muted
                font.pixelSize: 11
                font.letterSpacing: 1.1
            }
        }

        StackLayout {
            id: pages
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: window.selectedPage

            ColumnLayout {
                spacing: Theme.spacingSection

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    Text {
                        text: qsTr("One shell.")
                        color: Theme.foreground
                        font.pixelSize: 34
                        font.weight: Font.DemiBold
                    }

                    Text {
                        text: qsTr("Every device.")
                        color: Theme.accent
                        font.pixelSize: 34
                        font.weight: Font.DemiBold
                    }

                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Your Android apps, with Omarchy’s visual language.")
                        color: Theme.muted
                        font.pixelSize: 15
                        wrapMode: Text.WordWrap
                    }
                }

                RowLayout {
                    Layout.fillWidth: true

                    Text {
                        text: qsTr("Pinned apps")
                        color: Theme.foreground
                        font.pixelSize: 15
                        font.weight: Font.DemiBold
                    }

                    Text {
                        text: launcher.favorites.length > 0
                              ? qsTr("Hold to unpin")
                              : qsTr("Hold an app below to pin it")
                        color: Theme.muted
                        font.pixelSize: 11
                    }

                    Item { Layout.fillWidth: true }

                    ToolButton {
                        text: "↻"
                        Accessible.name: qsTr("Refresh installed apps")
                        onClicked: launcher.refresh()
                    }
                }

                Item {
                    Layout.fillWidth: true
                    Layout.preferredHeight: launcher.favorites.length > 0 ? 86 : 0
                    visible: launcher.favorites.length > 0

                    Flickable {
                        anchors.fill: parent
                        contentWidth: pinnedRow.implicitWidth
                        clip: true
                        boundsBehavior: Flickable.StopAtBounds
                        flickableDirection: Flickable.HorizontalFlick

                        Row {
                            id: pinnedRow
                            spacing: 8

                            Repeater {
                                model: launcher.favorites

                                delegate: Button {
                                    required property var modelData
                                    width: 72
                                    height: 82
                                    Accessible.name: qsTr("Launch %1").arg(modelData.name)
                                    onClicked: launcher.launchActivity(modelData.packageName, modelData.activityName)
                                    onPressAndHold: launcher.removeFavorite(modelData.packageName, modelData.activityName)

                                    contentItem: Column {
                                        spacing: 5
                                        Image {
                                            width: 42
                                            height: 42
                                            anchors.horizontalCenter: parent.horizontalCenter
                                            source: modelData.iconUrl
                                            fillMode: Image.PreserveAspectFit
                                            asynchronous: true
                                        }
                                        Text {
                                            width: 68
                                            anchors.horizontalCenter: parent.horizontalCenter
                                            text: modelData.name
                                            color: Theme.foreground
                                            font.pixelSize: 10
                                            horizontalAlignment: Text.AlignHCenter
                                            elide: Text.ElideRight
                                            maximumLineCount: 1
                                        }
                                    }

                                    background: HyprlandSurface {
                                        fillColor: parent.down ? Theme.surfaceRaised : "transparent"
                                        borderStart: parent.activeFocus ? Theme.accent : "transparent"
                                        borderEnd: borderStart
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: parent.activeFocus ? 1 : 0
                                    }
                                }
                            }
                        }
                    }
                }

                TextField {
                    id: appSearch
                    Layout.fillWidth: true
                    placeholderText: qsTr("Search apps")
                    Accessible.name: qsTr("Search installed apps")
                    color: Theme.foreground
                    placeholderTextColor: Theme.muted
                    leftPadding: 16
                    rightPadding: 16
                    topPadding: 14
                    bottomPadding: 14
                    background: HyprlandSurface {
                        fillColor: Theme.surface
                        borderStart: Theme.outline
                        borderEnd: Theme.outline
                        radius: Theme.defaultCornerRadius
                        roundingPower: Theme.defaultRoundingPower
                        borderWidth: 1
                    }
                    onTextChanged: launcher.filter = text
                }

                Item {
                    Layout.fillWidth: true
                    Layout.fillHeight: true

                    GridView {
                        id: appGrid
                        anchors.fill: parent
                        clip: true
                        cellWidth: Math.max(104, Math.floor(width / 3))
                        cellHeight: 104
                        model: launcher
                        boundsBehavior: Flickable.StopAtBounds

                        delegate: Button {
                            required property int index
                            required property string name
                            required property string packageName
                            required property string iconUrl
                            required property bool favorite

                            width: appGrid.cellWidth - 8
                            height: appGrid.cellHeight - 8
                            text: name
                            Accessible.name: qsTr("Launch %1").arg(name)
                            display: AbstractButton.TextUnderIcon
                            spacing: 7
                            onClicked: launcher.launch(index)
                            onPressAndHold: launcher.toggleFavorite(index)

                            contentItem: Column {
                                spacing: 7
                                anchors.centerIn: parent

                                Item {
                                    width: 42
                                    height: 42
                                    anchors.horizontalCenter: parent.horizontalCenter

                                    Image {
                                        id: appIcon
                                        anchors.fill: parent
                                        source: iconUrl
                                        fillMode: Image.PreserveAspectFit
                                        asynchronous: true
                                    }

                                    Rectangle {
                                        anchors.fill: parent
                                        visible: appIcon.status !== Image.Ready
                                        color: Theme.surfaceRaised
                                        border.color: Theme.outline
                                        border.width: 1
                                        radius: Theme.defaultCornerRadius

                                        Text {
                                            anchors.centerIn: parent
                                            text: name.length > 0 ? name.substring(0, 1).toUpperCase() : "•"
                                            color: Theme.accent
                                            font.pixelSize: 21
                                            font.weight: Font.Medium
                                        }
                                    }

                                    Text {
                                        anchors.right: parent.right
                                        anchors.top: parent.top
                                        text: favorite ? "★" : ""
                                        color: Theme.accent
                                        font.pixelSize: 13
                                    }
                                }

                                Text {
                                    width: appGrid.cellWidth - 16
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    text: name
                                    color: Theme.foreground
                                    font.pixelSize: 12
                                    horizontalAlignment: Text.AlignHCenter
                                    elide: Text.ElideRight
                                    maximumLineCount: 1
                                }
                            }

                            background: HyprlandSurface {
                                fillColor: parent.down ? Theme.surfaceRaised : "transparent"
                                borderStart: parent.activeFocus ? Theme.accent : "transparent"
                                borderEnd: borderStart
                                radius: Theme.defaultCornerRadius
                                borderWidth: parent.activeFocus ? 1 : 0
                            }
                        }
                    }

                    ColumnLayout {
                        anchors.centerIn: parent
                        width: parent.width * 0.82
                        spacing: 10
                        visible: launcher.count === 0

                        Text {
                            Layout.fillWidth: true
                            text: launcher.platformSupported
                                  ? qsTr("No apps match this search.")
                                  : qsTr("Installed Android apps appear here when OmaApp runs on Android.")
                            color: Theme.foreground
                            font.pixelSize: 15
                            horizontalAlignment: Text.AlignHCenter
                            wrapMode: Text.WordWrap
                        }

                        Text {
                            Layout.fillWidth: true
                            visible: !launcher.platformSupported
                            text: qsTr("This desktop window is a Qt Quick preview. It does not invent a sample app list.")
                            color: Theme.muted
                            font.pixelSize: 12
                            horizontalAlignment: Text.AlignHCenter
                            wrapMode: Text.WordWrap
                        }
                    }
                }
            }

            ColumnLayout {
                spacing: Theme.spacingSection

                ColumnLayout {
                    spacing: 8
                    Text {
                        text: qsTr("Devices")
                        color: Theme.foreground
                        font.pixelSize: 32
                        font.weight: Font.DemiBold
                    }
                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Pair with your Omarchy computer to sync its live theme and wallpaper.")
                        color: Theme.muted
                        font.pixelSize: 15
                        wrapMode: Text.WordWrap
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: pairing.hasCandidate ? 260
                                             : pairing.hasPairedDevice ? 280 : 224
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: 10

                        Text {
                            text: pairing.connected ? qsTr("CONNECTED")
                                  : pairing.hasPendingRequest ? qsTr("AWAITING APPROVAL")
                                  : pairing.phase === "requesting" || pairing.phase === "connecting" ? qsTr("CONNECTING")
                                  : pairing.phase === "revoking" ? qsTr("REVOKING ACCESS")
                                  : pairing.hasPairedDevice ? qsTr("OFFLINE · CACHED THEME")
                                  : qsTr("NOT PAIRED")
                            color: pairing.connected ? Theme.accent : Theme.muted
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            font.letterSpacing: 1.3
                        }

                        Text {
                            Layout.fillWidth: true
                            text: pairing.hasCandidate
                                  ? qsTr("%1 %2. Verify the computer certificate fingerprint: %3")
                                        .arg(pairing.hasPairedDevice ? qsTr("Reconnect to") : qsTr("Pair with"))
                                        .arg(pairing.candidateHostName)
                                        .arg(pairing.candidateFingerprint)
                                  : pairing.connected
                                    ? qsTr("Securely connected to %1. Its current Omarchy theme is %2.")
                                          .arg(pairing.hostName)
                                          .arg((pairing.snapshot.theme || {}).name || qsTr("unknown"))
                                    : pairing.hasPendingRequest
                                      ? qsTr("The request was sent. Approve this phone in the OmaApp widget on the computer.")
                                      : pairing.phase === "requesting" || pairing.phase === "connecting"
                                        ? qsTr("Checking the scanned code and reconnecting to the saved computer.")
                                      : pairing.hasPairedDevice
                                        ? qsTr("%1 is unreachable. OmaApp is keeping the last synchronized theme and wallpaper on this phone.")
                                              .arg(pairing.hostName)
                                        : pairing.platformSupported
                                          ? qsTr("Scan the QR code shown by the OmaApp widget on your computer. The connection verifies a pinned certificate and requires approval on both devices.")
                                          : qsTr("Run this screen in OmaApp on Android to pair with an Omarchy computer.")
                            color: Theme.foreground
                            font.pixelSize: 13
                            wrapMode: Text.WordWrap
                        }

                        Text {
                            Layout.fillWidth: true
                            visible: pairing.error.length > 0
                            text: pairing.error
                            color: Theme.urgent
                            font.pixelSize: 12
                            wrapMode: Text.WordWrap
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            visible: pairing.hasCandidate
                            Button {
                                Layout.fillWidth: true
                                text: qsTr("Cancel")
                                onClicked: pairing.discardCandidate()
                            }
                            Button {
                                Layout.fillWidth: true
                                text: pairing.hasPairedDevice ? qsTr("Reconnect") : qsTr("Trust computer")
                                highlighted: true
                                onClicked: pairing.approveCandidate()
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            visible: !pairing.hasCandidate && !pairing.hasPendingRequest
                                     && !pairing.hasPairedDevice
                            Button {
                                Layout.fillWidth: true
                                text: pairing.hasPairedDevice ? qsTr("Scan to reconnect") : qsTr("Scan pairing QR")
                                enabled: pairing.platformSupported
                                onClicked: pairing.startScan()
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            visible: !pairing.hasCandidate && pairing.hasPendingRequest
                            Button {
                                Layout.fillWidth: true
                                text: qsTr("Cancel")
                                onClicked: pairing.cancelPairing()
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            visible: pairing.hasPairedDevice && !pairing.hasPendingRequest
                            Text {
                                Layout.fillWidth: true
                                text: pairing.connected
                                      ? qsTr("Theme updates automatically while connected.")
                                      : qsTr("The cached theme stays available offline.")
                                color: Theme.muted
                                font.pixelSize: 12
                                wrapMode: Text.WordWrap
                            }
                            Button {
                                text: qsTr("Scan")
                                enabled: pairing.platformSupported
                                onClicked: pairing.startScan()
                            }
                            Button {
                                text: qsTr("Forget")
                                onClicked: forgetDialog.open()
                            }
                            Button {
                                text: qsTr("Revoke")
                                enabled: pairing.connected
                                onClicked: pairing.revoke()
                            }
                        }
                    }
                }

                Text {
                    Layout.fillWidth: true
                    visible: pairing.snapshot.theme !== undefined
                    text: qsTr("Synced geometry: radius %1 · power %2 · border %3 px")
                          .arg((pairing.snapshot.hyprland || {}).rounding || 0)
                          .arg((pairing.snapshot.hyprland || {}).roundingPower || 2)
                          .arg((pairing.snapshot.hyprland || {}).borderWidth || 0)
                    color: Theme.muted
                    font.pixelSize: 12
                    wrapMode: Text.WordWrap
                }

                Item { Layout.fillHeight: true }
            }

            Flickable {
                contentWidth: width
                contentHeight: appearanceColumn.implicitHeight
                clip: true
                boundsBehavior: Flickable.StopAtBounds

                ColumnLayout {
                    id: appearanceColumn
                    width: parent.width
                    spacing: Theme.spacingSection

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 8
                        Text {
                            text: qsTr("Appearance")
                            color: Theme.foreground
                            font.pixelSize: 32
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Explore the same superellipse controls used by the Hyprland rounding model.")
                            color: Theme.muted
                            font.pixelSize: 15
                            wrapMode: Text.WordWrap
                        }
                    }

                    HyprlandSurface {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 156
                        fillColor: Theme.surfaceRaised
                        borderStart: Theme.activeBorderStart
                        borderEnd: Theme.activeBorderEnd
                        radius: Theme.previewRadius
                        roundingPower: Theme.previewPower
                        borderWidth: Theme.previewBorderWidth

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 18
                            spacing: 8
                            Text {
                                text: qsTr("OMARCHY SURFACE")
                                color: Theme.foreground
                                font.pixelSize: 13
                                font.weight: Font.DemiBold
                                font.letterSpacing: 1
                            }
                            Text {
                                text: qsTr("Power %1  •  Radius %2 px").arg(Theme.previewPower.toFixed(1)).arg(Math.round(Theme.previewRadius))
                                color: Theme.muted
                                font.pixelSize: 13
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 8
                        Text {
                            text: qsTr("Rounding power")
                            color: Theme.foreground
                            font.pixelSize: 14
                        }
                        Slider {
                            id: powerSlider
                            Layout.fillWidth: true
            from: 1
            to: 10
            stepSize: 0.5
                            value: Theme.previewPower
                            Accessible.name: qsTr("Rounding power")
                            onMoved: Theme.previewPower = value
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 8
                        Text {
                            text: qsTr("Corner radius")
                            color: Theme.foreground
                            font.pixelSize: 14
                        }
                        Slider {
                            Layout.fillWidth: true
                            from: 0
                            to: 100
                            stepSize: 1
                            value: Theme.previewRadius
                            Accessible.name: qsTr("Corner radius")
                            onMoved: Theme.previewRadius = value
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 8
                        Text {
                            text: qsTr("Border width")
                            color: Theme.foreground
                            font.pixelSize: 14
                        }
                        Slider {
                            Layout.fillWidth: true
                            from: 0
                            to: 12
                            stepSize: 1
                            value: Theme.previewBorderWidth
                            Accessible.name: qsTr("Border width")
                            onMoved: Theme.previewBorderWidth = value
                        }
                    }

                    Text {
                        Layout.fillWidth: true
                        text: pairing.connected
                              ? qsTr("Colors, border gradient, rounding, power, spacing and wallpaper follow the connected Omarchy theme.")
                              : pairing.hasPairedDevice
                                ? qsTr("Showing the last saved Omarchy theme while offline.")
                                : qsTr("Preview values stay local until a computer is paired.")
                        color: Theme.muted
                        font.pixelSize: 12
                        wrapMode: Text.WordWrap
                    }
                }
            }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 8

            Repeater {
                model: [
                    { label: qsTr("Apps"), page: 0 },
                    { label: qsTr("Devices"), page: 1 },
                    { label: qsTr("Style"), page: 2 }
                ]

                delegate: Button {
                    required property var modelData
                    Layout.fillWidth: true
                    text: modelData.label
                    checkable: true
                    checked: window.selectedPage === modelData.page
                    Accessible.name: modelData.label
                    onClicked: window.selectedPage = modelData.page
                    background: HyprlandSurface {
                        fillColor: parent.checked ? Theme.surfaceRaised : "transparent"
                        borderStart: parent.checked ? Theme.accent : "transparent"
                        borderEnd: borderStart
                        radius: Theme.defaultCornerRadius
                        borderWidth: parent.checked ? 1 : 0
                    }
                    contentItem: Text {
                        text: parent.text
                        color: parent.checked ? Theme.foreground : Theme.muted
                        font.pixelSize: 13
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                }
            }
        }
    }
}
