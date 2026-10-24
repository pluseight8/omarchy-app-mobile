pragma ComponentBehavior: Bound

import QtQuick
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
    font.family: Theme.fontFamily
    palette.window: Theme.background
    palette.windowText: Theme.foreground
    palette.base: Theme.surface
    palette.text: Theme.foreground
    palette.button: Theme.surfaceRaised
    palette.buttonText: Theme.foreground
    palette.highlight: Theme.accent
    palette.highlightedText: Theme.background
    palette.placeholderText: Theme.muted

    property int selectedPage: 0
    property int selectedHomePage: 0
    property string selectedFolderId: ""
    property string selectedFolderName: ""
    property string deletingFolderId: ""
    property var editingHomeApp: null
    property string layoutMessage: ""
    property string editingWidgetId: ""
    property string widgetMessage: ""
    property string openSourceNoticesText: ""
    property bool languageSaveFailed: false

    Connections {
        target: appTranslations
        function onLanguageChanged() {
            owl.refreshLocalizedText()
            window.layoutMessage = ""
            window.widgetMessage = ""
        }
    }

    function widgetThemeColor(value) {
        const color = Qt.color(value)
        function hexByte(channel) {
            return ("0" + Math.round(channel * 255).toString(16)).slice(-2)
        }
        return "#" + hexByte(color.r) + hexByte(color.g) + hexByte(color.b)
    }

    function owlThemeJson() {
        return JSON.stringify({
            foreground: widgetThemeColor(Theme.foreground),
            background: widgetThemeColor(Theme.background),
            accent: widgetThemeColor(Theme.accent),
            muted: widgetThemeColor(Theme.muted),
            surface: widgetThemeColor(Theme.surface),
            surfaceRaised: widgetThemeColor(Theme.surfaceRaised),
            urgent: widgetThemeColor(Theme.urgent),
            outline: widgetThemeColor(Theme.outline)
        })
    }

    function editOwlWidget(widgetId, makeCopy) {
        const targetId = makeCopy ? owl.copyWidgetForEditing(widgetId) : widgetId
        if (!targetId)
            return
        editingWidgetId = targetId
        owlEditor.text = owl.source(targetId)
        owlEditorError.text = ""
        owlEditorDialog.open()
    }

    function createOwlWidget() {
        const newId = owl.createWidget(qsTr("My widget"))
        editOwlWidget(newId, false)
    }

    function saveOwlWidget() {
        owlEditorPreview.refresh()
        if (owlEditorPreview.renderError.length > 0) {
            owlEditorError.text = owlEditorPreview.renderError
            return
        }
        if (!owl.saveWidget(editingWidgetId, owlEditor.text)) {
            owlEditorError.text = qsTr("The widget could not be saved on this device.")
            return
        }
        owlEditorDialog.close()
    }

    function restoreLayoutBackup(json) {
        let parsed
        try {
            parsed = JSON.parse(json)
        } catch (error) {
            return false
        }
        const owlBackup = parsed.owlWidgets === undefined ? "" : JSON.stringify(parsed.owlWidgets)
        if (owlBackup.length > 0 && !owl.canImportWidgetBackup(owlBackup))
            return false
        if (!launcher.importLayout(json))
            return false
        return owlBackup.length === 0 || owl.importWidgetBackup(owlBackup)
    }

    function openFolder(folderId, folderName) {
        selectedFolderId = folderId
        selectedFolderName = folderName
        renameFolderField.text = folderName
        folderDialog.open()
    }

    LauncherModel {
        id: launcher
        objectName: "omaappLauncherModel"
    }

    PairingModel {
        id: pairing
    }

    OwlModel {
        id: owl
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

    function shellColor(value, fallback) {
        const raw = String(value || "").trim()
        if (raw.startsWith("hyprland."))
            return parseHyprlandColor((pairing.snapshot.hyprland || {}).activeBorder, fallback)
        if (raw.startsWith("colors."))
            return parseHyprlandColor(((pairing.snapshot.theme || {}).colors || {})[raw.substring(7)], fallback)
        return parseHyprlandColor(raw, fallback)
    }

    function withAlpha(value, alpha) {
        const color = Qt.color(value)
        return Qt.rgba(color.r, color.g, color.b, boundedNumber(alpha, 1, 0, 1))
    }

    function boundedNumber(value, fallback, minimum, maximum) {
        const number = Number(value)
        return Number.isFinite(number) ? Math.max(minimum, Math.min(maximum, number)) : fallback
    }

    function featureEnabled(permission) {
        const permissions = pairing.features.permissions || {}
        return permissions[permission] === true
    }

    function localizedFeatureMessage(message) {
        const raw = String(message || "")
        switch (raw) {
        case "There is no text in the phone clipboard.":
            return qsTr("There is no text in the phone clipboard.")
        case "Clipboard text is empty or exceeds 64 KiB.":
            return qsTr("Clipboard text is empty or exceeds 64 KiB.")
        case "Could not read phone clipboard text.":
            return qsTr("Could not read phone clipboard text.")
        case "Pair OmaApp with a computer first.":
            return qsTr("Pair OmaApp with a computer first.")
        case "Reading computer clipboard…":
            return qsTr("Reading computer clipboard…")
        case "The computer clipboard exceeds 64 KiB.":
            return qsTr("The computer clipboard exceeds 64 KiB.")
        case "Android clipboard is unavailable.":
            return qsTr("Android clipboard is unavailable.")
        case "Computer text copied to the phone clipboard.":
            return qsTr("Computer text copied to the phone clipboard.")
        case "This media action is not allowed.":
            return qsTr("This media action is not allowed.")
        case "This computer command is not configured.":
            return qsTr("This computer command is not configured.")
        case "The selected file name or size is not supported.":
            return qsTr("The selected file name or size is not supported.")
        case "Android could not open this file.":
            return qsTr("Android could not open this file.")
        case "The selected file changed while sending.":
            return qsTr("The selected file changed while sending.")
        case "Enable this feature for this phone in the Omarchy plugin permissions.":
            return qsTr("Enable this feature for this phone in the Omarchy plugin permissions.")
        case "The computer clipboard is unavailable in this session.":
            return qsTr("The computer clipboard is unavailable in this session.")
        case "Computer feature request failed. Check the local network and plugin permissions.":
            return qsTr("Computer feature request failed. Check the local network and plugin permissions.")
        case "Sending command to the computer…":
            return qsTr("Sending command to the computer…")
        case "Phone clipboard sent to the computer.":
            return qsTr("Phone clipboard sent to the computer.")
        case "Computer action completed.":
            return qsTr("Computer action completed.")
        }

        let match = raw.match(/^Sending (.+) to the computer…$/)
        if (match)
            return qsTr("Sending %1 to the computer…").arg(match[1])
        match = raw.match(/^Sent (.+) to Downloads\/OmaApp\.$/)
        if (match)
            return qsTr("Sent %1 to Downloads/OmaApp.").arg(match[1])
        match = raw.match(/^Computer rejected the file \(HTTP ([0-9]{3})\)\.$/)
        if (match)
            return qsTr("Computer rejected the file (HTTP %1).").arg(match[1])
        match = raw.match(/^Computer feature request failed \(HTTP ([0-9]{3})\)\.$/)
        if (match)
            return qsTr("Computer feature request failed (HTTP %1).").arg(match[1])

        return raw.length === 0 ? ""
                                : qsTr("Computer feature request failed. Check the local network and plugin permissions.")
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
            Theme.spacingScale = 1
            Theme.spacingScaleWithFont = true
            Theme.fontBaseSize = 12
            Theme.fontOverrides = ({})
            Theme.controlFillAlpha = 0.04
            Theme.selectedFillAlpha = 0.18
            Theme.launcherBackgroundAlpha = 1
            Theme.popupBackgroundAlpha = 1
            Theme.surface = Qt.darker(Theme.background, 1.18)
            Theme.surfaceRaised = Qt.darker(Theme.background, 1.34)
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
        const launcherStyle = shell.launcher || {}
        const popupStyle = shell.popups || {}
        const controlsStyle = shell.controls || {}
        const fontStyle = shell.font || {}
        const geometry = pairing.snapshot.hyprland || {}

        Theme.background = parseHyprlandColor(colors.background, Theme.background)
        Theme.foreground = parseHyprlandColor(colors.foreground, Theme.foreground)
        Theme.accent = parseHyprlandColor(colors.accent || colors.color4, Theme.accent)
        Theme.urgent = parseHyprlandColor(colors.red || colors.color1, Theme.urgent)
        Theme.muted = parseHyprlandColor(colors.muted || colors.color8, Theme.muted)

        const border = String(geometry.activeBorder || colors.hyprland_active_border || "")
        const borderColors = border.match(/rgba\([0-9a-f]{8}\)|rgb\([0-9a-f]{6}\)|#[0-9a-f]{6,8}/ig) || []
        Theme.activeBorderStops = borderColors.length > 0
                ? borderColors.slice(0, 10).map(value => parseHyprlandColor(value, Theme.accent))
                : [Theme.accent]
        Theme.activeBorderStart = Theme.activeBorderStops[0]
        Theme.activeBorderEnd = Theme.activeBorderStops[Theme.activeBorderStops.length - 1]
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

        Theme.fontBaseSize = boundedNumber(fontStyle["base-size"], 12, 6, 48)
        Theme.fontOverrides = fontStyle
        const spacingScale = boundedNumber(spacing.scale, 1, 0.5, 3)
        Theme.spacingScaleWithFont = spacing["scale-with-font"] !== false
        const spacingFontScale = Theme.spacingScaleWithFont ? Theme.fontBaseSize / 12 : 1
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

        Theme.spacingSmall = boundedNumber(smallSpacing, 8, 0, 100) * spacingScale * spacingFontScale
        Theme.spacingGroup = boundedNumber(groupSpacing, 16, 0, 100) * spacingScale * spacingFontScale
        Theme.spacingSection = boundedNumber(sectionSpacing, 24, 0, 100) * spacingScale * spacingFontScale
        Theme.spacingScale = spacingScale

        const launcherAlpha = boundedNumber(launcherStyle["background-alpha"], 1, 0, 1)
        const popupAlpha = boundedNumber(popupStyle["background-alpha"], 1, 0, 1)
        Theme.launcherBackgroundAlpha = launcherAlpha
        Theme.popupBackgroundAlpha = popupAlpha
        Theme.surface = withAlpha(shellColor(launcherStyle.background, Theme.background), launcherAlpha)
        Theme.surfaceRaised = withAlpha(shellColor(popupStyle.background, Theme.background), popupAlpha)
        Theme.controlFillAlpha = boundedNumber(controlsStyle["normal-fill-alpha"], 0.04, 0, 1)
        Theme.selectedFillAlpha = boundedNumber(
            launcherStyle["selected-background-alpha"] !== undefined
                    ? launcherStyle["selected-background-alpha"] : controlsStyle["selected-fill-alpha"],
            0.18, 0, 1)
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

    Dialog {
        id: homeAppDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 32, 360)
        title: editingHomeApp ? qsTr("Move %1").arg(editingHomeApp.name) : qsTr("Home app")
        standardButtons: Dialog.Close

        ColumnLayout {
            width: parent.width
            spacing: Theme.space(8)

            Label {
                Layout.fillWidth: true
                text: qsTr("Choose a home page for this app.")
                color: Theme.muted
                wrapMode: Text.WordWrap
            }

            Repeater {
                model: launcher.homePageCount
                delegate: Button {
                    required property int index
                    Layout.fillWidth: true
                    text: qsTr("Home page %1%2").arg(index + 1)
                         .arg(window.selectedHomePage === index ? "  ·  " + qsTr("Current") : "")
                    Accessible.name: qsTr("Move %1 to home page %2")
                                       .arg(editingHomeApp ? editingHomeApp.name : "")
                                       .arg(index + 1)
                    onClicked: {
                        if (editingHomeApp)
                            launcher.setFavoritePage(editingHomeApp.packageName,
                                                     editingHomeApp.activityName, index)
                        homeAppDialog.close()
                    }
                }
            }

            Button {
                Layout.fillWidth: true
                text: qsTr("Remove from Home")
                enabled: editingHomeApp !== null
                onClicked: {
                    if (editingHomeApp)
                        launcher.removeFavorite(editingHomeApp.packageName,
                                                editingHomeApp.activityName)
                    homeAppDialog.close()
                }
            }
        }
    }

    Dialog {
        id: createFolderDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 32, 420)
        title: qsTr("Create folder")
        standardButtons: Dialog.Ok | Dialog.Cancel
        onOpened: newFolderField.forceActiveFocus()
        onAccepted: {
            const folderName = newFolderField.text.trim()
            const folderId = launcher.createFolder(folderName)
            if (folderId.length === 0) {
                folderCreationError.visible = true
                open()
                return
            }
            folderCreationError.visible = false
            newFolderField.clear()
            window.openFolder(folderId, folderName)
        }
        onRejected: {
            newFolderField.clear()
            folderCreationError.visible = false
        }

        ColumnLayout {
            width: parent.width
            spacing: Theme.space(8)

            TextField {
                id: newFolderField
                Layout.fillWidth: true
                maximumLength: 40
                placeholderText: qsTr("Folder name")
                Accessible.name: qsTr("New folder name")
                onTextChanged: folderCreationError.visible = false
            }

            Label {
                id: folderCreationError
                Layout.fillWidth: true
                visible: false
                text: qsTr("Choose a unique name. You can create up to 24 folders.")
                color: Theme.urgent
                wrapMode: Text.WordWrap
            }
        }
    }

    Dialog {
        id: folderDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 32, 440)
        height: Math.min(window.height - 72, 560)
        title: selectedFolderName
        standardButtons: Dialog.Close

        ColumnLayout {
            anchors.fill: parent
            spacing: Theme.space(10)

            RowLayout {
                Layout.fillWidth: true
                TextField {
                    id: renameFolderField
                    Layout.fillWidth: true
                    maximumLength: 40
                    placeholderText: qsTr("Folder name")
                    Accessible.name: qsTr("Folder name")
                    onTextChanged: renameFolderError.visible = false
                }
                Button {
                    text: qsTr("Save")
                    Accessible.name: qsTr("Rename folder")
                    onClicked: {
                        if (launcher.renameFolder(selectedFolderId, renameFolderField.text)) {
                            selectedFolderName = renameFolderField.text.trim()
                        } else {
                            renameFolderError.visible = true
                        }
                    }
                }
            }

            Label {
                id: renameFolderError
                Layout.fillWidth: true
                visible: false
                text: qsTr("Choose a unique folder name.")
                color: Theme.urgent
                wrapMode: Text.WordWrap
            }

            RowLayout {
                Layout.fillWidth: true
                Label {
                    text: qsTr("Apps")
                    color: Theme.foreground
                    font.weight: Font.DemiBold
                }
                Item { Layout.fillWidth: true }
                Button {
                    text: qsTr("Add apps")
                    Accessible.name: qsTr("Add apps to %1").arg(selectedFolderName)
                    onClicked: {
                        folderAppSearch.clear()
                        addFolderAppsDialog.open()
                    }
                }
            }

            ListView {
                id: folderAppsList
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                spacing: Theme.space(4)
                model: {
                    const exists = launcher.folders.some(folder => folder.id === selectedFolderId)
                    return exists ? launcher.folderApps(selectedFolderId) : []
                }
                delegate: RowLayout {
                    required property var modelData
                    width: folderAppsList.width

                    Button {
                        Layout.fillWidth: true
                        text: modelData.name
                        Accessible.name: qsTr("Launch %1").arg(modelData.name)
                        onClicked: {
                            launcher.performHapticFeedback()
                            launcher.launchActivity(modelData.packageName, modelData.activityName)
                        }
                    }
                    ToolButton {
                        text: "−"
                        Accessible.name: qsTr("Remove %1 from %2").arg(modelData.name).arg(selectedFolderName)
                        onClicked: launcher.setFolderContains(selectedFolderId,
                                                              modelData.packageName,
                                                              modelData.activityName,
                                                              false)
                    }
                }
                Label {
                    anchors.centerIn: parent
                    width: parent.width * 0.88
                    visible: folderAppsList.count === 0
                    text: qsTr("This folder is empty. Add installed apps to keep them together.")
                    color: Theme.muted
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                }
            }

            RowLayout {
                Layout.fillWidth: true
                Button {
                    text: qsTr("Delete folder")
                    Accessible.name: qsTr("Delete %1 folder").arg(selectedFolderName)
                    onClicked: {
                        deletingFolderId = selectedFolderId
                        deleteFolderDialog.open()
                    }
                }
                Item { Layout.fillWidth: true }
            }
        }
    }

    Dialog {
        id: addFolderAppsDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 32, 440)
        height: Math.min(window.height - 72, 560)
        title: qsTr("Add apps to %1").arg(selectedFolderName)
        standardButtons: Dialog.Close

        ColumnLayout {
            anchors.fill: parent
            spacing: Theme.space(10)

            TextField {
                id: folderAppSearch
                Layout.fillWidth: true
                placeholderText: qsTr("Search installed apps")
                Accessible.name: qsTr("Search installed apps to add to folder")
            }

            ListView {
                id: availableFolderApps
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                spacing: Theme.space(4)
                model: {
                    const exists = launcher.folders.some(folder => folder.id === selectedFolderId)
                    return exists ? launcher.availableApps(selectedFolderId, folderAppSearch.text) : []
                }
                delegate: Button {
                    required property var modelData
                    width: availableFolderApps.width
                    height: 48
                    Accessible.name: modelData.inFolder
                                   ? qsTr("Remove %1 from %2").arg(modelData.name).arg(selectedFolderName)
                                   : qsTr("Add %1 to %2").arg(modelData.name).arg(selectedFolderName)
                    onClicked: launcher.setFolderContains(selectedFolderId,
                                                          modelData.packageName,
                                                          modelData.activityName,
                                                          !modelData.inFolder)

                    contentItem: RowLayout {
                        spacing: Theme.space(12)
                        Image {
                            Layout.preferredWidth: 28
                            Layout.preferredHeight: 28
                            source: modelData.iconUrl
                            fillMode: Image.PreserveAspectFit
                            asynchronous: true
                        }
                        Text {
                            Layout.fillWidth: true
                            text: modelData.name
                            color: Theme.foreground
                            elide: Text.ElideRight
                        }
                        Text {
                            text: modelData.inFolder ? "✓" : "+"
                            color: Theme.accent
                            font.pixelSize: Theme.fontSize(18)
                        }
                    }
                }
            }
        }
    }

    Dialog {
        id: deleteFolderDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        title: qsTr("Delete folder?")
        standardButtons: Dialog.Ok | Dialog.Cancel
        onAccepted: {
            launcher.deleteFolder(deletingFolderId)
            if (deletingFolderId === selectedFolderId)
                folderDialog.close()
        }
        Label {
            text: qsTr("The folder will be removed. Installed apps remain on this device.")
            wrapMode: Text.WordWrap
        }
    }

    Dialog {
        id: restoreLayoutDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 32, 460)
        height: Math.min(window.height - 72, 620)
        title: qsTr("Restore Home layout")
        standardButtons: Dialog.Close

        ColumnLayout {
            anchors.fill: parent
            spacing: Theme.space(10)

            Label {
                Layout.fillWidth: true
                text: qsTr("Paste a JSON backup copied from OmaApp. Restoring replaces Home pages, pinned apps, folders, built-in and OWL widgets, widget order and source, grid, gesture preferences and haptic feedback.")
                color: Theme.muted
                wrapMode: Text.WordWrap
            }

            Button {
                Layout.fillWidth: true
                text: qsTr("Paste from clipboard")
                onClicked: layoutBackupEditor.text = launcher.clipboardText()
            }

            TextArea {
                id: layoutBackupEditor
                Layout.fillWidth: true
                Layout.fillHeight: true
                wrapMode: TextEdit.NoWrap
                selectByMouse: true
                placeholderText: qsTr("OmaApp layout backup JSON")
                Accessible.name: qsTr("Layout backup JSON to restore")
            }

            Label {
                id: layoutRestoreError
                Layout.fillWidth: true
                visible: false
                text: qsTr("This backup is invalid or was created by a newer version. No settings were changed.")
                color: Theme.urgent
                wrapMode: Text.WordWrap
            }

            Button {
                Layout.fillWidth: true
                text: qsTr("Restore layout")
                onClicked: {
                    if (restoreLayoutBackup(layoutBackupEditor.text)) {
                        window.layoutMessage = qsTr("Home layout restored.")
                        layoutRestoreError.visible = false
                        restoreLayoutDialog.close()
                    } else {
                        layoutRestoreError.visible = true
                    }
                }
            }
        }
    }

    Dialog {
        id: owlEditorDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 28, 620)
        height: Math.min(window.height - 44, 820)
        title: qsTr("Edit OWL widget")
        standardButtons: Dialog.NoButton
        onOpened: owlEditor.forceActiveFocus()

        ColumnLayout {
            anchors.fill: parent
            spacing: Theme.space(10)

            Label {
                Layout.fillWidth: true
                text: qsTr("This editor runs the portable OWL widget language locally. Linux commands, network sources, triggers, and unsupported nodes are rejected; saved widgets work offline.")
                color: Theme.muted
                wrapMode: Text.WordWrap
            }

            TextArea {
                id: owlEditor
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.minimumHeight: 180
                wrapMode: TextEdit.NoWrap
                selectByMouse: true
                font.family: Theme.fontFamily
                font.pixelSize: Theme.fontSize(12)
                placeholderText: qsTr("widget \"My widget\" { ... }")
                Accessible.name: qsTr("OWL widget source editor")
            }

            Label {
                Layout.fillWidth: true
                text: qsTr("Live preview")
                color: Theme.foreground
                font.weight: Font.DemiBold
            }

            OwlWidgetView {
                id: owlEditorPreview
                Layout.fillWidth: true
                Layout.preferredHeight: 156
                owlModel: owl
                widgetId: ""
                themeJson: window.owlThemeJson()
                useSourceText: true
                sourceText: owlEditor.text
            }

            Label {
                id: owlEditorError
                Layout.fillWidth: true
                visible: text.length > 0
                color: Theme.urgent
                wrapMode: Text.WordWrap
            }

            RowLayout {
                Layout.fillWidth: true
                Button {
                    Layout.fillWidth: true
                    text: qsTr("Cancel")
                    onClicked: owlEditorDialog.close()
                }
                Button {
                    Layout.fillWidth: true
                    text: qsTr("Save widget")
                    highlighted: true
                    onClicked: saveOwlWidget()
                }
            }
        }
    }

    Dialog {
        id: openSourceNoticesDialog
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(window.width - 28, 720)
        height: Math.min(window.height - 44, 760)
        title: qsTr("Open-source notices and licenses")
        standardButtons: Dialog.Close
        readonly property bool licenseTextsAvailable:
            window.openSourceNoticesText.includes("GNU LESSER GENERAL PUBLIC LICENSE")
            && window.openSourceNoticesText.includes("Apache License")
            && window.openSourceNoticesText.includes("Simon Schubert")
            && window.openSourceNoticesText.includes("vaxerski")

        contentItem: ColumnLayout {
            spacing: Theme.space(8)

            Label {
                Layout.fillWidth: true
                text: openSourceNoticesDialog.licenseTextsAvailable
                      ? qsTr("License texts are available offline.")
                      : qsTr("License texts could not be loaded.")
                color: openSourceNoticesDialog.licenseTextsAvailable ? Theme.accent : Theme.urgent
            }

            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                ScrollBar.vertical.policy: ScrollBar.AsNeeded

                TextArea {
                    width: parent.width
                    text: window.openSourceNoticesText
                    readOnly: true
                    selectByMouse: true
                    wrapMode: TextEdit.Wrap
                    textFormat: TextEdit.PlainText
                    color: Theme.foreground
                    font.family: "monospace"
                    font.pixelSize: Theme.fontSize(10)
                    background: null
                    Accessible.name: qsTr("Open-source license texts")
                }
            }
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
        opacity: pairing.wallpaperUrl.length > 0 ? Theme.launcherBackgroundAlpha : 1
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: Theme.space(22)
        anchors.rightMargin: Theme.space(22)
        anchors.topMargin: Theme.space(24)
        anchors.bottomMargin: Theme.space(16)
        spacing: Theme.spacingGroup

        RowLayout {
            Layout.fillWidth: true
            spacing: Theme.space(12)

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
                font.pixelSize: Theme.fontSize(15)
                font.weight: Font.DemiBold
                font.letterSpacing: 2.2
            }

            Item { Layout.fillWidth: true }

            Text {
                text: launcher.platformSupported ? qsTr("ANDROID") : qsTr("DESKTOP PREVIEW")
                color: Theme.muted
                font.pixelSize: Theme.fontSize(11)
                font.letterSpacing: 1.1
            }
        }

        SwipeView {
            id: pages
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            interactive: launcher.gestures.sectionSwipe !== false
            currentIndex: window.selectedPage

            onCurrentIndexChanged: {
                if (window.selectedPage !== currentIndex)
                    window.selectedPage = currentIndex
            }

            ListView {
                id: homePageView
                Layout.fillWidth: true
                Layout.fillHeight: true
                orientation: ListView.Horizontal
                interactive: launcher.gestures.homeSwipe !== false
                snapMode: ListView.SnapOneItem
                highlightRangeMode: ListView.StrictlyEnforceRange
                boundsBehavior: Flickable.StopAtBounds
                clip: true
                model: launcher.homePageCount
                currentIndex: window.selectedHomePage

                onCurrentIndexChanged: {
                    if (window.selectedHomePage !== currentIndex)
                        window.selectedHomePage = currentIndex
                }

                Connections {
                    target: launcher
                    function onHomePagesChanged() {
                        if (homePageView.currentIndex >= launcher.homePageCount)
                            homePageView.currentIndex = launcher.homePageCount - 1
                    }
                }

                delegate: Item {
                    required property int index
                    width: homePageView.width
                    height: homePageView.height

                    Flickable {
                        anchors.fill: parent
                        contentWidth: width
                        contentHeight: homeContent.implicitHeight
                        clip: true
                        boundsBehavior: Flickable.StopAtBounds

                        ColumnLayout {
                            id: homeContent
                            width: parent.width
                            spacing: Theme.spacingGroup

                            RowLayout {
                                Layout.fillWidth: true
                                Text {
                                    text: qsTr("Home")
                                    color: Theme.foreground
                                    font.pixelSize: Theme.fontSize(32)
                                    font.weight: Font.DemiBold
                                }
                                Item { Layout.fillWidth: true }
                                Text {
                                    text: qsTr("%1 / %2").arg(index + 1).arg(launcher.homePageCount)
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(13)
                                    Accessible.name: qsTr("Home page %1 of %2")
                                                       .arg(index + 1).arg(launcher.homePageCount)
                                }
                            }

                            HyprlandSurface {
                                Layout.fillWidth: true
                                Layout.preferredHeight: 122
                                visible: launcher.homeWidgets.clock === true
                                fillColor: Theme.surfaceRaised
                                borderStart: Theme.activeBorderStart
                                borderEnd: Theme.activeBorderEnd
                                borderStops: Theme.activeBorderStops
                                radius: Theme.defaultCornerRadius
                                borderWidth: Theme.previewBorderWidth

                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 18
                                    spacing: Theme.space(4)
                                    Text {
                                        text: qsTr("LOCAL TIME")
                                        color: Theme.accent
                                        font.pixelSize: Theme.fontSize(11)
                                        font.weight: Font.DemiBold
                                        font.letterSpacing: 1.1
                                    }
                                    Text {
                                        id: homeClock
                                        property date now: new Date()
                                        Layout.fillWidth: true
                                        text: Qt.formatTime(now, "h:mm AP")
                                        color: Theme.foreground
                                        font.pixelSize: Theme.fontSize(38)
                                        font.weight: Font.DemiBold
                                    }
                                    Text {
                                        text: Qt.formatDate(homeClock.now, "dddd, d MMMM")
                                        color: Theme.muted
                                        font.pixelSize: Theme.fontSize(13)
                                    }
                                }
                            }

                            Timer {
                                interval: 1000
                                repeat: true
                                running: true
                                onTriggered: homeClock.now = new Date()
                            }

                            HyprlandSurface {
                                Layout.fillWidth: true
                                Layout.preferredHeight: 108
                                visible: launcher.homeWidgets.computer === true
                                fillColor: Theme.surface
                                borderStart: Theme.outline
                                borderEnd: Theme.outline
                                radius: Theme.defaultCornerRadius
                                borderWidth: 1

                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 16
                                    spacing: Theme.space(5)
                                    RowLayout {
                                        Layout.fillWidth: true
                                        Text {
                                            text: qsTr("OMARCHY COMPUTER")
                                            color: Theme.accent
                                            font.pixelSize: Theme.fontSize(11)
                                            font.weight: Font.DemiBold
                                            font.letterSpacing: 0.9
                                        }
                                        Item { Layout.fillWidth: true }
                                        Text {
                                            Layout.preferredWidth: 88
                                            text: pairing.connected ? qsTr("CONNECTED")
                                                  : pairing.hasPairedDevice ? qsTr("OFFLINE") : qsTr("NOT PAIRED")
                                            color: pairing.connected ? Theme.accent : Theme.muted
                                            font.pixelSize: Theme.fontSize(10)
                                            font.weight: Font.DemiBold
                                            horizontalAlignment: Text.AlignRight
                                            elide: Text.ElideRight
                                        }
                                    }
                                    Text {
                                        Layout.fillWidth: true
                                        text: pairing.hasPairedDevice
                                              ? pairing.hostName + (pairing.connected
                                                    ? " · " + ((pairing.snapshot.theme || {}).name || qsTr("theme synced"))
                                                    : " · " + qsTr("cached theme available"))
                                              : qsTr("Pair an Omarchy computer to sync its theme and use PC controls.")
                                        color: Theme.foreground
                                        font.pixelSize: Theme.fontSize(13)
                                        wrapMode: Text.WordWrap
                                        elide: Text.ElideRight
                                        maximumLineCount: 2
                                    }
                                    Button {
                                        Layout.alignment: Qt.AlignRight
                                        Layout.preferredWidth: 128
                                        text: pairing.hasPairedDevice ? qsTr("Open devices") : qsTr("Pair computer")
                                        Accessible.name: pairing.hasPairedDevice
                                                         ? qsTr("Open connected devices") : qsTr("Pair an Omarchy computer")
                                        onClicked: window.selectedPage = 2
                                    }
                                }
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                visible: launcher.homeWidgets.media === true
                                spacing: Theme.space(8)
                                RowLayout {
                                    Layout.fillWidth: true
                                    Text {
                                        text: qsTr("Media")
                                        color: Theme.foreground
                                        font.pixelSize: Theme.fontSize(19)
                                        font.weight: Font.DemiBold
                                    }
                                    Item { Layout.fillWidth: true }
                                }
                                Text {
                                    Layout.fillWidth: true
                                    visible: (pairing.features.players || []).length === 0
                                    text: window.featureEnabled("media.control")
                                          ? qsTr("No active media player on the computer.")
                                          : qsTr("Enable media permission in the Omarchy plugin to use these controls.")
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(11)
                                    wrapMode: Text.WordWrap
                                }
                                Repeater {
                                    model: pairing.features.players || []
                                    delegate: HyprlandSurface {
                                        required property var modelData
                                        Layout.fillWidth: true
                                        Layout.preferredHeight: 72
                                        fillColor: Theme.surface
                                        borderStart: Theme.outline
                                        borderEnd: Theme.outline
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: 1

                                        RowLayout {
                                            anchors.fill: parent
                                            anchors.margins: 12
                                            Text {
                                                Layout.fillWidth: true
                                                text: [modelData.title, modelData.artist].filter(Boolean).join(" · ")
                                                      || modelData.player
                                                color: Theme.foreground
                                                font.pixelSize: Theme.fontSize(12)
                                                elide: Text.ElideRight
                                            }
                                            Button {
                                                text: modelData.status === "Playing" ? qsTr("Pause") : qsTr("Play")
                                                Accessible.name: modelData.status === "Playing"
                                                                 ? qsTr("Pause %1").arg(modelData.player)
                                                                 : qsTr("Play %1").arg(modelData.player)
                                                enabled: pairing.connected && window.featureEnabled("media.control")
                                                onClicked: pairing.mediaAction(modelData.player,
                                                                               modelData.status === "Playing" ? "pause" : "play")
                                            }
                                            ToolButton {
                                                text: "›"
                                                Accessible.name: qsTr("Next track on %1").arg(modelData.player)
                                                enabled: pairing.connected && window.featureEnabled("media.control")
                                                onClicked: pairing.mediaAction(modelData.player, "next")
                                            }
                                        }
                                    }
                                }
                            }

                            RowLayout {
                                Layout.fillWidth: true
                                visible: owl.installedWidgets.length > 0
                                Text {
                                    text: qsTr("OWL widgets")
                                    color: Theme.foreground
                                    font.pixelSize: Theme.fontSize(19)
                                    font.weight: Font.DemiBold
                                }
                                Item { Layout.fillWidth: true }
                                Button {
                                    text: qsTr("Manage")
                                    Accessible.name: qsTr("Manage OWL widgets")
                                    onClicked: window.selectedPage = 4
                                }
                            }

                            Text {
                                Layout.fillWidth: true
                                visible: owl.installedWidgets.length > 1
                                text: qsTr("Hold and drag a widget to change its order.")
                                color: Theme.muted
                                font.pixelSize: Theme.fontSize(11)
                            }

                            Repeater {
                                model: owl.installedWidgets
                                delegate: OwlWidgetView {
                                    required property var modelData
                                    required property int index
                                    Layout.fillWidth: true
                                    owlModel: owl
                                    widgetId: modelData.id
                                    itemIndex: index
                                    themeJson: window.owlThemeJson()
                                }
                            }

                            RowLayout {
                                Layout.fillWidth: true
                                Text {
                                    text: qsTr("Pinned apps")
                                    color: Theme.foreground
                                    font.pixelSize: Theme.fontSize(19)
                                    font.weight: Font.DemiBold
                                }
                                Item { Layout.fillWidth: true }
                                Button {
                                    Layout.preferredWidth: 104
                                    text: qsTr("All apps")
                                    Accessible.name: qsTr("Open all installed apps")
                                    onClicked: window.selectedPage = 1
                                }
                            }

                            GridView {
                                id: homeAppsGrid
                                Layout.fillWidth: true
                                Layout.preferredHeight: Math.max(cellHeight,
                                    Math.ceil(count / launcher.gridColumns) * cellHeight)
                                cellWidth: Math.floor(width / launcher.gridColumns)
                                cellHeight: 96
                                interactive: false
                                clip: false
                                model: launcher.homePageApps(index)

                                delegate: Button {
                                    required property var modelData
                                    width: homeAppsGrid.cellWidth - 8
                                    height: homeAppsGrid.cellHeight - 8
                                    Accessible.name: qsTr("Launch %1").arg(modelData.name)
                                    onClicked: {
                                        launcher.performHapticFeedback()
                                        launcher.launchActivity(modelData.packageName,
                                                                modelData.activityName)
                                    }
                                    onPressAndHold: {
                                        if (launcher.gestures.longPressEdit) {
                                            launcher.performHapticFeedback()
                                            window.editingHomeApp = modelData
                                            homeAppDialog.open()
                                        }
                                    }

                                    Drag.active: homeAppDragHandler.active
                                    Drag.dragType: Drag.Automatic
                                    Drag.supportedActions: Qt.MoveAction
                                    Drag.mimeData: ({
                                        "application/x-omaapp-activity": JSON.stringify({
                                            "packageName": modelData.packageName,
                                            "activityName": modelData.activityName
                                        })
                                    })

                                    DragHandler {
                                        id: homeAppDragHandler
                                        target: null
                                        acceptedButtons: Qt.LeftButton
                                    }

                                    contentItem: Column {
                                        spacing: Theme.space(6)
                                        Image {
                                            width: 42
                                            height: 42
                                            anchors.horizontalCenter: parent.horizontalCenter
                                            source: modelData.iconUrl
                                            fillMode: Image.PreserveAspectFit
                                            asynchronous: true
                                        }
                                        Text {
                                            width: homeAppsGrid.cellWidth - 16
                                            anchors.horizontalCenter: parent.horizontalCenter
                                            text: modelData.name
                                            color: Theme.foreground
                                            font.pixelSize: Theme.fontSize(11)
                                            horizontalAlignment: Text.AlignHCenter
                                            elide: Text.ElideRight
                                            maximumLineCount: 1
                                        }
                                    }
                                    background: HyprlandSurface {
                                        fillColor: parent.down ? Theme.surfaceRaised : Theme.controlSurface
                                        borderStart: parent.activeFocus ? Theme.accent : "transparent"
                                        borderEnd: parent.activeFocus ? Theme.accent : "transparent"
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: parent.activeFocus ? 1 : 0
                                    }
                                }

                                DropArea {
                                    id: homePageDropArea
                                    anchors.fill: parent
                                    z: 10
                                    keys: ["application/x-omaapp-activity"]

                                    onDropped: function(drop) {
                                        try {
                                            const payload = JSON.parse(drop.getDataAsString(
                                                "application/x-omaapp-activity"))
                                            const row = Math.floor(drop.y / homeAppsGrid.cellHeight)
                                            const column = Math.floor(drop.x / homeAppsGrid.cellWidth)
                                            const targetIndex = row * launcher.gridColumns + column
                                            if (launcher.placeFavorite(payload.packageName,
                                                                       payload.activityName,
                                                                       index, targetIndex)) {
                                                drop.acceptProposedAction()
                                            }
                                        } catch (error) {
                                            console.warn("OmaApp: ignored an invalid launcher drag payload")
                                        }
                                    }

                                    HyprlandSurface {
                                        anchors.fill: parent
                                        visible: homePageDropArea.containsDrag
                                        fillColor: "transparent"
                                        borderStart: Theme.accent
                                        borderEnd: Theme.accent
                                        borderWidth: 2
                                        radius: Theme.defaultCornerRadius
                                    }
                                }
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: Theme.space(8)

                                RowLayout {
                                    Layout.fillWidth: true
                                    Text {
                                        text: qsTr("Dock")
                                        color: Theme.foreground
                                        font.pixelSize: Theme.fontSize(16)
                                        font.weight: Font.DemiBold
                                    }
                                    Item { Layout.fillWidth: true }
                                    Text {
                                        Layout.preferredWidth: 190
                                        Layout.maximumWidth: window.width * 0.6
                                        text: launcher.favorites.length > 0
                                              ? qsTr("%1 shortcuts").arg(launcher.favorites.length)
                                              : qsTr("Hold an app in All apps to add it")
                                        color: Theme.muted
                                        font.pixelSize: Theme.fontSize(11)
                                        horizontalAlignment: Text.AlignRight
                                        elide: Text.ElideRight
                                    }
                                }

                                Flickable {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 84
                                    contentWidth: homeDockRow.implicitWidth
                                    contentHeight: height
                                    clip: true
                                    boundsBehavior: Flickable.StopAtBounds
                                    flickableDirection: Flickable.HorizontalFlick

                                    Row {
                                        id: homeDockRow
                                        spacing: Theme.space(8)

                                        Repeater {
                                            model: launcher.favorites
                                            delegate: Button {
                                                required property var modelData
                                                width: 72
                                                height: 78
                                                Accessible.name: qsTr("Dock shortcut: launch %1")
                                                                   .arg(modelData.name)
                                                onClicked: {
                                                    launcher.performHapticFeedback()
                                                    launcher.launchActivity(modelData.packageName,
                                                                            modelData.activityName)
                                                }
                                                onPressAndHold: {
                                                    if (launcher.gestures.longPressEdit) {
                                                        launcher.performHapticFeedback()
                                                        window.editingHomeApp = modelData
                                                        homeAppDialog.open()
                                                    }
                                                }
                                                contentItem: Column {
                                                    spacing: Theme.space(5)
                                                    Image {
                                                        width: 38
                                                        height: 38
                                                        anchors.horizontalCenter: parent.horizontalCenter
                                                        source: modelData.iconUrl
                                                        fillMode: Image.PreserveAspectFit
                                                        asynchronous: true
                                                    }
                                                    Text {
                                                        width: 64
                                                        anchors.horizontalCenter: parent.horizontalCenter
                                                        text: modelData.name
                                                        color: Theme.foreground
                                                        font.pixelSize: Theme.fontSize(10)
                                                        horizontalAlignment: Text.AlignHCenter
                                                        elide: Text.ElideRight
                                                        maximumLineCount: 1
                                                    }
                                                }
                                                background: HyprlandSurface {
                                                    fillColor: parent.down ? Theme.surfaceRaised
                                                                          : Theme.controlSurface
                                                    borderStart: parent.activeFocus ? Theme.accent : "transparent"
                                                    borderEnd: parent.activeFocus ? Theme.accent : "transparent"
                                                    radius: Theme.defaultCornerRadius
                                                    borderWidth: parent.activeFocus ? 1 : 0
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Label {
                                Layout.fillWidth: true
                                visible: launcher.homePageApps(index).length === 0
                                text: qsTr("No apps on this page yet. Long-press an app in All apps to pin it here.")
                                color: Theme.muted
                                horizontalAlignment: Text.AlignHCenter
                                wrapMode: Text.WordWrap
                            }

                            RowLayout {
                                Layout.alignment: Qt.AlignHCenter
                                Repeater {
                                    model: launcher.homePageCount
                                    delegate: ToolButton {
                                        required property int index
                                        text: homePageView.currentIndex === index ? "●" : "○"
                                        Accessible.name: qsTr("Go to home page %1").arg(index + 1)
                                        onClicked: homePageView.currentIndex = index
                                    }
                                }
                            }

                            Flow {
                                Layout.fillWidth: true
                                visible: launcher.folders.length > 0
                                spacing: Theme.spacingSmall
                                Repeater {
                                    model: launcher.folders
                                    delegate: Button {
                                        required property var modelData
                                        width: 96
                                        height: 58
                                        Accessible.name: qsTr("Open %1 folder, %2 apps")
                                                           .arg(modelData.name).arg(modelData.appCount)
                                        onClicked: window.openFolder(modelData.id, modelData.name)
                                        contentItem: Column {
                                            spacing: Theme.space(2)
                                            Text {
                                                text: "▦"
                                                color: Theme.accent
                                                font.pixelSize: Theme.fontSize(18)
                                                horizontalAlignment: Text.AlignHCenter
                                            }
                                            Text {
                                                width: 88
                                                text: modelData.name
                                                color: Theme.foreground
                                                font.pixelSize: Theme.fontSize(10)
                                                horizontalAlignment: Text.AlignHCenter
                                                elide: Text.ElideRight
                                            }
                                        }
                                        background: HyprlandSurface {
                                            fillColor: parent.down ? Theme.surfaceRaised : Theme.controlSurface
                                            borderStart: Theme.outline
                                            borderEnd: Theme.outline
                                            radius: Theme.defaultCornerRadius
                                            borderWidth: 1
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Connections {
                        target: launcher
                        function onHomePagesChanged() {
                            homeAppsGrid.model = launcher.homePageApps(index)
                        }
                    }
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: Math.min(Theme.spacingSection, 16)

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: Theme.space(8)

                    Text {
                        text: qsTr("One shell.")
                        color: Theme.foreground
                        font.pixelSize: Theme.fontSize(34)
                        font.weight: Font.DemiBold
                    }

                    Text {
                        text: qsTr("Every device.")
                        color: Theme.accent
                        font.pixelSize: Theme.fontSize(34)
                        font.weight: Font.DemiBold
                    }

                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Your Android apps, with Omarchy’s visual language.")
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(15)
                        wrapMode: Text.WordWrap
                    }
                }

                RowLayout {
                    Layout.fillWidth: true

                    Text {
                        text: qsTr("Dock")
                        color: Theme.foreground
                        font.pixelSize: Theme.fontSize(15)
                        font.weight: Font.DemiBold
                    }

                    Text {
                        text: launcher.favorites.length > 0
                              ? qsTr("Hold to unpin")
                              : qsTr("Hold an app below to pin it")
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(11)
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

                    HyprlandSurface {
                        anchors.fill: parent
                        z: -1
                        fillColor: Theme.surface
                        borderStart: Theme.outline
                        borderEnd: Theme.outline
                        radius: Theme.defaultCornerRadius
                        borderWidth: 1
                    }

                    Flickable {
                        anchors.fill: parent
                        contentWidth: pinnedRow.implicitWidth
                        clip: true
                        boundsBehavior: Flickable.StopAtBounds
                        flickableDirection: Flickable.HorizontalFlick

                        Row {
                            id: pinnedRow
                            spacing: Theme.space(8)

                            Repeater {
                                model: launcher.favorites

                                delegate: Button {
                                    required property var modelData
                                    width: 72
                                    height: 82
                                    Accessible.name: qsTr("Launch %1").arg(modelData.name)
                                    onClicked: {
                                        launcher.performHapticFeedback()
                                        launcher.launchActivity(modelData.packageName,
                                                                modelData.activityName)
                                    }
                                    onPressAndHold: {
                                        if (launcher.gestures.longPressEdit)
                                            launcher.removeFavorite(modelData.packageName,
                                                                    modelData.activityName)
                                    }

                                    contentItem: Column {
                                        spacing: Theme.space(5)
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
                                            font.pixelSize: Theme.fontSize(10)
                                            horizontalAlignment: Text.AlignHCenter
                                            elide: Text.ElideRight
                                            maximumLineCount: 1
                                        }
                                    }

                                    background: HyprlandSurface {
                                            fillColor: parent.down ? Theme.surfaceRaised : Theme.controlSurface
                                        borderStart: parent.activeFocus ? Theme.accent : "transparent"
                                        borderEnd: parent.activeFocus ? Theme.accent : "transparent"
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: parent.activeFocus ? 1 : 0
                                    }
                                }
                            }
                        }
                    }

                    DropArea {
                        id: dockDropArea
                        anchors.fill: parent
                        z: 10
                        keys: ["application/x-omaapp-activity"]

                        onDropped: function(drop) {
                            try {
                                const payload = JSON.parse(drop.getDataAsString(
                                    "application/x-omaapp-activity"))
                                const targetIndex = Math.floor(drop.x / 80)
                                if (launcher.placeFavorite(payload.packageName,
                                                           payload.activityName,
                                                           window.selectedHomePage,
                                                           targetIndex)) {
                                    drop.acceptProposedAction()
                                }
                            } catch (error) {
                                console.warn("OmaApp: ignored an invalid dock drag payload")
                            }
                        }

                        HyprlandSurface {
                            anchors.fill: parent
                            visible: dockDropArea.containsDrag
                            fillColor: "transparent"
                            borderStart: Theme.accent
                            borderEnd: Theme.accent
                            borderWidth: 2
                            radius: Theme.defaultCornerRadius
                        }
                    }


                }

                RowLayout {
                    Layout.fillWidth: true

                    Text {
                        text: qsTr("Folders")
                        color: Theme.foreground
                        font.pixelSize: Theme.fontSize(15)
                        font.weight: Font.DemiBold
                    }

                    Item { Layout.fillWidth: true }

                    Button {
                        text: qsTr("New")
                        Accessible.name: qsTr("Create folder")
                        onClicked: createFolderDialog.open()
                    }
                }

                Item {
                    Layout.fillWidth: true
                    Layout.preferredHeight: launcher.folders.length > 0 ? 72 : 0
                    visible: launcher.folders.length > 0

                    Flickable {
                        anchors.fill: parent
                        contentWidth: folderRow.implicitWidth
                        clip: true
                        boundsBehavior: Flickable.StopAtBounds
                        flickableDirection: Flickable.HorizontalFlick

                        Row {
                            id: folderRow
                            spacing: Theme.spacingSmall

                            Repeater {
                                model: launcher.folders

                                delegate: Button {
                                    required property var modelData
                                    width: 84
                                    height: 68
                                    Accessible.name: qsTr("Open %1 folder, %2 apps")
                                                       .arg(modelData.name)
                                                       .arg(modelData.appCount)
                                    onClicked: window.openFolder(modelData.id, modelData.name)

                                    contentItem: Column {
                                        spacing: Theme.space(2)
                                        Text {
                                            width: parent.width
                                            text: "▦"
                                            color: Theme.accent
                                            font.pixelSize: Theme.fontSize(20)
                                            horizontalAlignment: Text.AlignHCenter
                                        }
                                        Text {
                                            width: parent.width
                                            text: modelData.name
                                            color: Theme.foreground
                                            font.pixelSize: Theme.fontSize(10)
                                            horizontalAlignment: Text.AlignHCenter
                                            elide: Text.ElideRight
                                            maximumLineCount: 1
                                        }
                                        Text {
                                            width: parent.width
                                            text: qsTr("%1 apps").arg(modelData.appCount)
                                            color: Theme.muted
                                            font.pixelSize: Theme.fontSize(9)
                                            horizontalAlignment: Text.AlignHCenter
                                        }
                                    }

                                    background: HyprlandSurface {
                                        fillColor: parent.down ? Theme.surfaceRaised : Theme.controlSurface
                                        borderStart: parent.activeFocus ? Theme.accent : Theme.outline
                                        borderEnd: parent.activeFocus ? Theme.accent : Theme.outline
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: 1
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
                        cellWidth: Math.floor(width / launcher.gridColumns)
                        cellHeight: 104
                        model: launcher
                        boundsBehavior: Flickable.StopAtBounds

                        delegate: Button {
                            required property int index
                            required property string name
                            required property string packageName
                            required property string activityName
                            required property string iconUrl
                            required property bool favorite

                            width: appGrid.cellWidth - 8
                            height: appGrid.cellHeight - 8
                            text: name
                            Accessible.name: qsTr("Launch %1").arg(name)
                            display: AbstractButton.TextUnderIcon
                            spacing: Theme.space(7)
                            onClicked: {
                                launcher.performHapticFeedback()
                                launcher.launch(index)
                            }
                            onPressAndHold: {
                                if (launcher.gestures.longPressPin) {
                                    launcher.performHapticFeedback()
                                    launcher.toggleFavorite(index, window.selectedHomePage)
                                }
                            }

                            Drag.active: appDragHandler.active
                            Drag.dragType: Drag.Automatic
                            Drag.supportedActions: Qt.MoveAction
                            Drag.mimeData: ({
                                "application/x-omaapp-activity": JSON.stringify({
                                    "packageName": packageName,
                                    "activityName": activityName
                                })
                            })

                            DragHandler {
                                id: appDragHandler
                                target: null
                                acceptedButtons: Qt.LeftButton
                            }

                            contentItem: Column {
                                spacing: Theme.space(7)
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
                                            font.pixelSize: Theme.fontSize(21)
                                            font.weight: Font.Medium
                                        }
                                    }

                                    Text {
                                        anchors.right: parent.right
                                        anchors.top: parent.top
                                        text: favorite ? "★" : ""
                                        color: Theme.accent
                                        font.pixelSize: Theme.fontSize(13)
                                    }
                                }

                                Text {
                                    width: appGrid.cellWidth - 16
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    text: name
                                    color: Theme.foreground
                                    font.pixelSize: Theme.fontSize(12)
                                    horizontalAlignment: Text.AlignHCenter
                                    elide: Text.ElideRight
                                    maximumLineCount: 1
                                }
                            }

                            background: HyprlandSurface {
                                        fillColor: parent.down ? Theme.surfaceRaised : Theme.controlSurface
                                borderStart: parent.activeFocus ? Theme.accent : "transparent"
                                borderEnd: parent.activeFocus ? Theme.accent : "transparent"
                                radius: Theme.defaultCornerRadius
                                borderWidth: parent.activeFocus ? 1 : 0
                            }
                        }
                    }

                    ColumnLayout {
                        anchors.centerIn: parent
                        width: parent.width * 0.82
                        spacing: Theme.space(10)
                        visible: launcher.count === 0

                        Text {
                            Layout.fillWidth: true
                            text: launcher.platformSupported
                                  ? qsTr("No apps match this search.")
                                  : qsTr("Installed Android apps appear here when OmaApp runs on Android.")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(15)
                            horizontalAlignment: Text.AlignHCenter
                            wrapMode: Text.WordWrap
                        }

                        Text {
                            Layout.fillWidth: true
                            visible: !launcher.platformSupported
                            text: qsTr("This desktop window is a Qt Quick preview. It does not invent a sample app list.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            horizontalAlignment: Text.AlignHCenter
                            wrapMode: Text.WordWrap
                        }
                    }
                }
            }

            Flickable {
                Layout.fillWidth: true
                Layout.fillHeight: true
                contentWidth: width
                contentHeight: deviceColumn.implicitHeight
                clip: true
                boundsBehavior: Flickable.StopAtBounds

                ColumnLayout {
                    id: deviceColumn
                    width: parent.width
                    spacing: Theme.spacingSection

                ColumnLayout {
                    spacing: Theme.space(8)
                    Text {
                        text: qsTr("Devices")
                        color: Theme.foreground
                        font.pixelSize: Theme.fontSize(32)
                        font.weight: Font.DemiBold
                    }
                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Pair with your Omarchy computer to sync its live theme and wallpaper.")
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(15)
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
                        spacing: Theme.space(10)

                        Text {
                            text: pairing.connected ? qsTr("CONNECTED")
                                  : pairing.hasPendingRequest ? qsTr("AWAITING APPROVAL")
                                  : pairing.phase === "requesting" || pairing.phase === "connecting" ? qsTr("CONNECTING")
                                  : pairing.phase === "revoking" ? qsTr("REVOKING ACCESS")
                                  : pairing.hasPairedDevice ? qsTr("OFFLINE · CACHED THEME")
                                  : qsTr("NOT PAIRED")
                            color: pairing.connected ? Theme.accent : Theme.muted
                            font.pixelSize: Theme.fontSize(12)
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
                            font.pixelSize: Theme.fontSize(13)
                            wrapMode: Text.WordWrap
                        }

                        Text {
                            Layout.fillWidth: true
                            visible: pairing.error.length > 0
                            text: pairing.error
                            color: Theme.urgent
                            font.pixelSize: Theme.fontSize(12)
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
                                font.pixelSize: Theme.fontSize(12)
                                wrapMode: Text.WordWrap
                            }
                            Button {
                                text: qsTr("Retry")
                                enabled: !pairing.connected
                                onClicked: pairing.retry()
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
                    font.pixelSize: Theme.fontSize(12)
                    wrapMode: Text.WordWrap
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    visible: pairing.hasPairedDevice
                    spacing: Theme.space(10)

                    Text {
                        text: qsTr("Quick actions")
                        color: Theme.foreground
                        font.pixelSize: Theme.fontSize(20)
                        font.weight: Font.DemiBold
                    }

                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Each action requires its own approval in the Omarchy plugin. Clipboard access runs only when you tap a button while OmaApp is open.")
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(12)
                        wrapMode: Text.WordWrap
                    }

                    GridLayout {
                        Layout.fillWidth: true
                        columns: window.width >= 560 ? 2 : 1
                        rowSpacing: 8
                        columnSpacing: 8

                        Button {
                            Layout.fillWidth: true
                            Layout.minimumHeight: 48
                            text: qsTr("Send phone clipboard")
                            enabled: pairing.connected && window.featureEnabled("clipboard")
                            Accessible.name: qsTr("Send phone clipboard to the computer")
                            onClicked: pairing.pushClipboard()
                        }
                        Button {
                            Layout.fillWidth: true
                            Layout.minimumHeight: 48
                            text: qsTr("Read computer clipboard")
                            enabled: pairing.connected && window.featureEnabled("clipboard")
                            Accessible.name: qsTr("Copy computer clipboard to the phone")
                            onClicked: pairing.pullClipboard()
                        }
                        Button {
                            Layout.fillWidth: true
                            Layout.minimumHeight: 48
                            text: qsTr("Transfer files")
                            enabled: pairing.connected && (window.featureEnabled("files.send")
                                                             || window.featureEnabled("files.receive"))
                            onClicked: pairing.startFileTransfer()
                        }
                    }

                    Text {
                        Layout.fillWidth: true
                        visible: pairing.featureMessage.length > 0
                        text: window.localizedFeatureMessage(pairing.featureMessage)
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(12)
                        wrapMode: Text.WordWrap
                    }

                    HyprlandSurface {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 94
                        visible: pairing.features.system !== undefined
                        fillColor: Theme.surface
                        borderStart: Theme.outline
                        borderEnd: Theme.outline
                        radius: Theme.defaultCornerRadius
                        borderWidth: 1

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 14
                            spacing: Theme.space(4)
                            Text {
                                text: qsTr("COMPUTER STATUS")
                                color: Theme.accent
                                font.pixelSize: Theme.fontSize(11)
                                font.weight: Font.DemiBold
                                font.letterSpacing: 1
                            }
                            Text {
                                Layout.fillWidth: true
                                property var systemState: pairing.features.system || ({})
                                property var batteryState: systemState.battery || ({})
                                property var loadState: systemState.loadAverage || []
                                text: qsTr("Load %1 · Memory %2 / %3 GiB · Battery %4")
                                      .arg(loadState.length > 0 ? loadState[0] : "—")
                                      .arg(systemState.memoryTotalBytes > 0
                                           ? ((systemState.memoryTotalBytes - systemState.memoryAvailableBytes) / 1073741824).toFixed(1)
                                           : "—")
                                      .arg(systemState.memoryTotalBytes > 0
                                           ? (systemState.memoryTotalBytes / 1073741824).toFixed(1)
                                           : "—")
                                      .arg(batteryState.percent !== undefined
                                           ? batteryState.percent + "% · " + (batteryState.status || "")
                                           : qsTr("AC power / unavailable"))
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(13)
                                wrapMode: Text.WordWrap
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        visible: pairing.features.workspaces !== undefined
                        spacing: Theme.space(6)
                        Text {
                            text: qsTr("Hyprland workspaces")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(16)
                            font.weight: Font.DemiBold
                        }
                        Flow {
                            Layout.fillWidth: true
                            spacing: Theme.space(8)
                            Repeater {
                                model: pairing.features.workspaces || []
                                delegate: Button {
                                    required property var modelData
                                    text: (modelData.name || qsTr("Workspace %1").arg(modelData.id))
                                          + (modelData.focused ? "  ·  " + qsTr("Active") : "")
                                    Accessible.name: qsTr("Switch to workspace %1").arg(modelData.name || modelData.id)
                                    onClicked: pairing.activateWorkspace(modelData.id)
                                }
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        visible: pairing.features.players !== undefined
                        spacing: Theme.space(6)
                        Text {
                            text: qsTr("Media")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(16)
                            font.weight: Font.DemiBold
                        }
                        Repeater {
                            model: pairing.features.players || []
                            delegate: RowLayout {
                                required property var modelData
                                Layout.fillWidth: true
                                Text {
                                    Layout.fillWidth: true
                                    text: [modelData.title, modelData.artist].filter(Boolean).join(" · ")
                                          || modelData.player
                                    color: Theme.foreground
                                    elide: Text.ElideRight
                                }
                                Button {
                                    text: modelData.status === "Playing" ? qsTr("Pause") : qsTr("Play")
                                    Accessible.name: modelData.status === "Playing"
                                                     ? qsTr("Pause %1").arg(modelData.player)
                                                     : qsTr("Play %1").arg(modelData.player)
                                    onClicked: pairing.mediaAction(modelData.player,
                                                                   modelData.status === "Playing" ? "pause" : "play")
                                }
                                Button {
                                    text: qsTr("Next")
                                    Accessible.name: qsTr("Next track on %1").arg(modelData.player)
                                    onClicked: pairing.mediaAction(modelData.player, "next")
                                }
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        visible: pairing.features.commands !== undefined
                        spacing: Theme.space(6)
                        Text {
                            text: qsTr("Approved commands")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(16)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: (pairing.features.commands || []).length === 0
                            text: qsTr("No user commands are configured on the computer.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                        Repeater {
                            model: pairing.features.commands || []
                            delegate: Button {
                                required property var modelData
                                Layout.fillWidth: true
                                Layout.minimumHeight: 48
                                text: modelData.label
                                Accessible.name: qsTr("Run approved computer command %1").arg(modelData.label)
                                onClicked: pairing.executeCommand(modelData.id)
                            }
                        }
                    }

                    Text {
                        Layout.fillWidth: true
                        visible: pairing.hasPairedDevice && !window.featureEnabled("system.read")
                                 && !window.featureEnabled("workspace.control")
                                 && !window.featureEnabled("media.control")
                        text: qsTr("Enable optional computer features in the OmaApp plugin’s Permissions menu. Each permission is independent and off by default.")
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(12)
                        wrapMode: Text.WordWrap
                    }
                }

                    Item { Layout.preferredHeight: 8 }
                }
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
                        spacing: Theme.space(8)
                        Text {
                            text: qsTr("Appearance")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(32)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Preview Omarchy colors and adjust rounded-surface values on this phone.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(15)
                            wrapMode: Text.WordWrap
                        }
                    }

                    HyprlandSurface {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 156
                        fillColor: Theme.surfaceRaised
                        borderStart: Theme.activeBorderStart
                        borderEnd: Theme.activeBorderEnd
                        borderStops: Theme.activeBorderStops
                        radius: Theme.previewRadius
                        roundingPower: Theme.previewPower
                        borderWidth: Theme.previewBorderWidth

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 18
                            spacing: Theme.space(8)
                            Text {
                                text: qsTr("OMARCHY SURFACE")
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(13)
                                font.weight: Font.DemiBold
                                font.letterSpacing: 1
                            }
                            Text {
                                text: qsTr("Power %1  •  Radius %2 px").arg(Theme.previewPower.toFixed(1)).arg(Math.round(Theme.previewRadius))
                                Accessible.name: text
                                color: Theme.muted
                                font.pixelSize: Theme.fontSize(13)
                            }
                            RowLayout {
                                spacing: Theme.space(8)
                                Text {
                                    text: qsTr("Border colors")
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(12)
                                }
                                Repeater {
                                    model: Theme.activeBorderStops
                                    delegate: Rectangle {
                                        required property int index
                                        required property color modelData
                                        width: 22
                                        height: 16
                                        radius: 4
                                        color: modelData
                                        Accessible.name: qsTr("Synced border color %1").arg(index + 1)
                                    }
                                }
                                Text {
                                    text: qsTr("%1 stops").arg(Theme.activeBorderStops.length)
                                    Accessible.name: text
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(11)
                                }
                                Text {
                                    text: qsTr("%1°").arg(Math.round(Theme.borderGradientAngle))
                                    Accessible.name: text
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(12)
                                }
                            }
                        }
                    }

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: Theme.space(8)

                        Repeater {
                            model: [1.0, 2.0, 4.0]

                            delegate: ColumnLayout {
                                required property real modelData
                                Layout.fillWidth: true
                                spacing: Theme.space(4)

                                HyprlandSurface {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 42
                                    fillColor: Theme.surfaceRaised
                                    borderStops: Theme.activeBorderStops
                                    borderGradientAngle: Theme.borderGradientAngle
                                    radius: 14
                                    roundingPower: modelData
                                    borderWidth: Theme.previewBorderWidth
                                    Accessible.name: qsTr("Rounding power %1 preview").arg(modelData.toFixed(1))
                                }

                                Text {
                                    Layout.alignment: Qt.AlignHCenter
                                    text: "p=" + modelData.toFixed(1)
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(10)
                                    Accessible.name: qsTr("Rounding power %1").arg(modelData.toFixed(1))
                                }
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: Theme.space(6)
                        RowLayout {
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: qsTr("Rounding power")
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(14)
                            }
                            ToolButton {
                                text: "−"
                                Accessible.name: qsTr("Decrease rounding power")
                                onClicked: Theme.previewPower = Math.max(1, Theme.previewPower - 0.5)
                            }
                            Text {
                                text: Theme.previewPower.toFixed(1)
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(13)
                            }
                            ToolButton {
                                text: "+"
                                Accessible.name: qsTr("Increase rounding power")
                                onClicked: Theme.previewPower = Math.min(10, Theme.previewPower + 0.5)
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: Theme.space(6)
                        RowLayout {
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: qsTr("Corner radius")
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(14)
                            }
                            ToolButton {
                                text: "−"
                                Accessible.name: qsTr("Decrease corner radius")
                                onClicked: Theme.previewRadius = Math.max(0, Theme.previewRadius - 2)
                            }
                            Text {
                                text: Math.round(Theme.previewRadius) + " px"
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(13)
                            }
                            ToolButton {
                                text: "+"
                                Accessible.name: qsTr("Increase corner radius")
                                onClicked: Theme.previewRadius = Math.min(100, Theme.previewRadius + 2)
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: Theme.space(6)
                        RowLayout {
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: qsTr("Border width")
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(14)
                            }
                            ToolButton {
                                text: "−"
                                Accessible.name: qsTr("Decrease border width")
                                onClicked: Theme.previewBorderWidth = Math.max(0, Theme.previewBorderWidth - 1)
                            }
                            Text {
                                text: Math.round(Theme.previewBorderWidth) + " px"
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(13)
                            }
                            ToolButton {
                                text: "+"
                                Accessible.name: qsTr("Increase border width")
                                onClicked: Theme.previewBorderWidth = Math.min(12, Theme.previewBorderWidth + 1)
                            }
                        }
                    }

                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Quickshell tokens · %1× spacing · %2 px base font · %3% control fill · %4% launcher opacity")
                                  .arg(Theme.spacingScale.toFixed(2))
                                  .arg(Theme.fontBaseSize.toFixed(0))
                                  .arg(Math.round(Theme.controlFillAlpha * 100))
                                  .arg(Math.round(Theme.launcherBackgroundAlpha * 100))
                        Accessible.name: text
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(12)
                        wrapMode: Text.WordWrap
                    }

                    ColumnLayout {
                        id: animationsPanel
                        property var animationSnapshot: pairing.snapshot.animations || ({})
                        Layout.fillWidth: true
                        spacing: Theme.space(6)

                        Text {
                            text: qsTr("Hyprland animations")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(18)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: (animationsPanel.animationSnapshot.items || []).length === 0
                            text: pairing.snapshot.theme === undefined
                                  ? qsTr("Pair a computer to see its live animation settings.")
                                  : qsTr("Hyprland did not provide animation data in the last sync.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                        Repeater {
                            model: animationsPanel.animationSnapshot.items || []
                            delegate: RowLayout {
                                required property var modelData
                                Layout.fillWidth: true
                                spacing: Theme.space(8)
                                Text {
                                    Layout.fillWidth: true
                                    text: modelData.name
                                    color: Theme.foreground
                                    font.pixelSize: Theme.fontSize(12)
                                    elide: Text.ElideRight
                                }
                                Text {
                                    text: modelData.enabled === false ? qsTr("OFF") : qsTr("ON")
                                    color: modelData.enabled === false ? Theme.muted : Theme.accent
                                    font.pixelSize: Theme.fontSize(10)
                                    font.weight: Font.DemiBold
                                }
                                Text {
                                    Layout.preferredWidth: 160
                                    text: [modelData.style || modelData.bezier || "",
                                          modelData.speed === null ? "" : qsTr("speed %1").arg(modelData.speed)]
                                          .filter(value => value.length > 0).join(" · ")
                                    color: Theme.muted
                                    font.pixelSize: Theme.fontSize(10)
                                    elide: Text.ElideRight
                                    horizontalAlignment: Text.AlignRight
                                }
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: (animationsPanel.animationSnapshot.beziers || []).length > 0
                            text: qsTr("%1 custom Bezier curves are available on this computer.")
                                      .arg((animationsPanel.animationSnapshot.beziers || []).length)
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(11)
                            wrapMode: Text.WordWrap
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
                        font.pixelSize: Theme.fontSize(12)
                        wrapMode: Text.WordWrap
                    }
                }
            }

        Flickable {
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: width
            contentHeight: settingsColumn.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds

            ColumnLayout {
                id: settingsColumn
                width: parent.width
                spacing: Theme.spacingSection

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: Theme.space(8)
                    Text {
                        text: qsTr("Settings")
                        color: Theme.foreground
                        font.pixelSize: Theme.fontSize(32)
                        font.weight: Font.DemiBold
                    }
                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Tune the launcher on this phone. These choices stay available when the computer is offline.")
                        color: Theme.muted
                        font.pixelSize: Theme.fontSize(14)
                        wrapMode: Text.WordWrap
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: appGridSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: appGridSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(12)
                        Text {
                            text: qsTr("App grid")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(17)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Choose how many app columns fit across the home screen and app drawer.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            Repeater {
                                model: [3, 4, 5]
                                delegate: Button {
                                    required property int modelData
                                    Layout.fillWidth: true
                                    text: String(modelData)
                                    checkable: true
                                    checked: launcher.gridColumns === modelData
                                    Accessible.name: qsTr("Use %1 app columns").arg(modelData)
                                    onClicked: launcher.setGridColumns(modelData)
                                    background: HyprlandSurface {
                                        fillColor: parent.checked ? Theme.selectedSurface : Theme.controlSurface
                                        borderStart: parent.checked ? Theme.accent : Theme.outline
                                        borderEnd: parent.checked ? Theme.accent : Theme.outline
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: 1
                                    }
                                }
                            }
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: qsTr("Home pages")
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(14)
                            }
                            ToolButton {
                                text: "−"
                                Accessible.name: qsTr("Remove a home page")
                                enabled: launcher.homePageCount > 1
                                onClicked: launcher.setHomePageCount(launcher.homePageCount - 1)
                            }
                            Text {
                                text: String(launcher.homePageCount)
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(13)
                            }
                            ToolButton {
                                text: "+"
                                Accessible.name: qsTr("Add a home page")
                                enabled: launcher.homePageCount < 5
                                onClicked: launcher.setHomePageCount(launcher.homePageCount + 1)
                            }
                        }
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: homeWidgetSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: homeWidgetSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(8)
                        Text {
                            text: qsTr("Home widgets")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(17)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Choose which live cards appear on your home pages. Computer and media values come from the paired device.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                        Repeater {
                            model: [
                                { id: "clock", label: qsTr("Local clock and date") },
                                { id: "computer", label: qsTr("Omarchy computer status") },
                                { id: "media", label: qsTr("Computer media controls") }
                            ]
                            delegate: CheckBox {
                                required property var modelData
                                Layout.fillWidth: true
                                text: modelData.label
                                Accessible.name: qsTr("Show %1 on Home").arg(modelData.label)
                                checked: launcher.homeWidgets[modelData.id] === true
                                onToggled: launcher.setHomeWidgetEnabled(modelData.id, checked)
                            }
                        }
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: gestureSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: gestureSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(6)
                        Text {
                            text: qsTr("Gestures")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(17)
                            font.weight: Font.DemiBold
                        }
                        Repeater {
                            model: [
                                { id: "sectionSwipe", label: qsTr("Swipe between Home, Apps, Devices, Style, and Settings") },
                                { id: "homeSwipe", label: qsTr("Swipe between Home pages") },
                                { id: "longPressPin", label: qsTr("Long press an app to pin or unpin it") },
                                { id: "longPressEdit", label: qsTr("Long press a Home shortcut to move or remove it") }
                            ]
                            delegate: CheckBox {
                                required property var modelData
                                Layout.fillWidth: true
                                text: modelData.label
                                Accessible.name: modelData.label
                                checked: launcher.gestures[modelData.id] !== false
                                onToggled: launcher.setGestureEnabled(modelData.id, checked)
                            }
                        }
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: hapticSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: hapticSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(8)
                        Text {
                            text: qsTr("Haptic feedback")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(17)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Use Android touch feedback when opening apps and editing Home. System settings can still disable haptics.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                        CheckBox {
                            Layout.fillWidth: true
                            text: qsTr("Enable haptic feedback")
                            Accessible.name: qsTr("Enable haptic feedback")
                            checked: launcher.hapticFeedbackEnabled
                            onToggled: {
                                if (launcher.setHapticFeedbackEnabled(checked) && checked)
                                    launcher.performHapticFeedback()
                            }
                        }
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: owlStoreSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: owlStoreSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(8)

                        RowLayout {
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: qsTr("OWL widget store")
                                color: Theme.foreground
                                font.pixelSize: Theme.fontSize(17)
                                font.weight: Font.DemiBold
                            }
                            Button {
                                text: qsTr("New")
                                Accessible.name: qsTr("Create an OWL widget")
                                onClicked: createOwlWidget()
                            }
                        }

                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Install local widgets, edit your own source, or make an editable copy of a built-in. Widget data stays on this phone.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }

                        Repeater {
                            model: owl.widgetStore
                            delegate: RowLayout {
                                required property var modelData
                                Layout.fillWidth: true
                                spacing: Theme.space(8)

                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: Theme.space(2)
                                    Text {
                                        Layout.fillWidth: true
                                        text: modelData.name
                                        Accessible.name: text
                                        color: Theme.foreground
                                        font.pixelSize: Theme.fontSize(13)
                                        font.weight: Font.DemiBold
                                        elide: Text.ElideRight
                                    }
                                    Text {
                                        Layout.fillWidth: true
                                        text: modelData.description
                                        Accessible.name: text
                                        color: Theme.muted
                                        font.pixelSize: Theme.fontSize(10)
                                        wrapMode: Text.WordWrap
                                    }
                                }

                                Button {
                                    text: modelData.installed ? qsTr("Remove") : qsTr("Add")
                                    Accessible.name: modelData.installed
                                                     ? qsTr("Remove %1 from Home").arg(modelData.name)
                                                     : qsTr("Add %1 to Home").arg(modelData.name)
                                    onClicked: {
                                        const wasInstalled = modelData.installed
                                        const widgetId = modelData.id
                                        owl.setWidgetInstalled(widgetId, !wasInstalled)
                                        window.widgetMessage = wasInstalled
                                                ? qsTr("Widget removed from Home.")
                                                : qsTr("Widget added to Home.")
                                    }
                                }
                                Button {
                                    text: modelData.editable ? qsTr("Edit") : qsTr("Copy")
                                    Accessible.name: modelData.editable
                                                     ? qsTr("Edit %1").arg(modelData.name)
                                                     : qsTr("Make an editable copy of %1").arg(modelData.name)
                                    onClicked: editOwlWidget(modelData.id, !modelData.editable)
                                }
                                Button {
                                    visible: modelData.editable
                                    text: qsTr("Delete")
                                    Accessible.name: qsTr("Delete %1").arg(modelData.name)
                                    onClicked: {
                                        owl.removeWidget(modelData.id)
                                        window.widgetMessage = qsTr("Custom widget deleted.")
                                    }
                                }
                            }
                        }

                        Label {
                            Layout.fillWidth: true
                            visible: window.widgetMessage.length > 0
                            text: window.widgetMessage
                            color: Theme.accent
                            wrapMode: Text.WordWrap
                        }
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: layoutBackupSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: layoutBackupSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(8)
                        Text {
                            text: qsTr("Layout backup")
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(17)
                            font.weight: Font.DemiBold
                        }
                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Copy a versioned JSON backup of your local Home layout, installed OWL widgets, and saved widget source. Restore by pasting that JSON here.")
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            Button {
                                Layout.fillWidth: true
                                text: qsTr("Copy backup")
                                Accessible.name: qsTr("Copy Home layout backup to clipboard")
                                onClicked: window.layoutMessage = launcher.copyLayoutBackup(owl.exportWidgetBackup())
                                    ? qsTr("Backup JSON copied to clipboard.")
                                    : qsTr("The clipboard is unavailable.")
                            }
                            Button {
                                Layout.fillWidth: true
                                text: qsTr("Restore")
                                Accessible.name: qsTr("Restore Home layout from JSON backup")
                                onClicked: {
                                    layoutBackupEditor.clear()
                                    layoutRestoreError.visible = false
                                    restoreLayoutDialog.open()
                                }
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: window.layoutMessage.length > 0
                            text: window.layoutMessage
                            color: Theme.accent
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }
                    }
                }

                Text {
                    Layout.fillWidth: true
                    text: qsTr("Home pages, pinned apps, folders, widget choices, and grid columns are saved on this device. They remain available when the computer is offline.")
                    color: Theme.muted
                    font.pixelSize: Theme.fontSize(12)
                    wrapMode: Text.WordWrap
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: languageSettings.implicitHeight + 36
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        id: languageSettings
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: Theme.space(12)

                        Text {
                            text: qsTr("Language")
                            Accessible.name: text
                            color: Theme.foreground
                            font.pixelSize: Theme.fontSize(17)
                            font.weight: Font.DemiBold
                        }

                        Text {
                            Layout.fillWidth: true
                            text: qsTr("Choose the app language. System default follows the phone language; English and Russian are available offline.")
                            Accessible.name: text
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(12)
                            wrapMode: Text.WordWrap
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: Theme.space(6)

                            Repeater {
                                model: [
                                    { value: "system", label: qsTr("System default") },
                                    { value: "en", label: qsTr("English") },
                                    { value: "ru", label: qsTr("Russian") }
                                ]

                                delegate: Button {
                                    required property var modelData
                                    Layout.fillWidth: true
                                    checkable: true
                                    checked: appTranslations.languagePreference === modelData.value
                                    text: modelData.label
                                    font.pixelSize: Theme.fontSize(11)
                                    Accessible.name: text

                                    onClicked: {
                                        if (!appTranslations.setLanguage(modelData.value))
                                            window.languageSaveFailed = true
                                        else
                                            window.languageSaveFailed = false
                                    }

                                    background: HyprlandSurface {
                                        fillColor: parent.checked ? Theme.selectedSurface : Theme.controlSurface
                                        borderStart: parent.checked ? Theme.accent : Theme.outline
                                        borderEnd: borderStart
                                        radius: Theme.defaultCornerRadius
                                        borderWidth: 1
                                    }
                                }
                            }
                        }

                        Text {
                            Layout.fillWidth: true
                            visible: window.languageSaveFailed
                            text: qsTr("The language preference could not be saved.")
                            color: Theme.urgent
                            font.pixelSize: Theme.fontSize(11)
                            wrapMode: Text.WordWrap
                        }
                    }
                }

                Button {
                    Layout.fillWidth: true
                    text: qsTr("Open-source notices and licenses")
                    Accessible.name: text
                    onClicked: openSourceNoticesDialog.open()
                }
            }
        }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: Theme.space(8)

            Repeater {
                model: [
                    { label: qsTr("Home"), page: 0 },
                    { label: qsTr("Apps"), page: 1 },
                    { label: qsTr("Devices"), page: 2 },
                    { label: qsTr("Style"), page: 3 },
                    { label: qsTr("Settings"), page: 4 }
                ]

                delegate: Button {
                    required property var modelData
                    Layout.fillWidth: true
                    Layout.minimumWidth: 0
                    Layout.preferredWidth: 1
                    text: modelData.label
                    checkable: true
                    checked: window.selectedPage === modelData.page
                    Accessible.name: modelData.label
                    onClicked: window.selectedPage = modelData.page
                    background: HyprlandSurface {
                        fillColor: parent.checked ? Theme.selectedSurface : Theme.controlSurface
                        borderStart: parent.checked ? Theme.accent : "transparent"
                        borderEnd: parent.checked ? Theme.accent : "transparent"
                        radius: Theme.defaultCornerRadius
                        borderWidth: parent.checked ? 1 : 0
                    }
                    contentItem: Text {
                        text: parent.text
                        color: parent.checked ? Theme.foreground : Theme.muted
                        font.pixelSize: Theme.fontSize(11)
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        wrapMode: Text.Wrap
                    }
                }
            }
        }
    }
}
