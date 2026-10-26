Warning: truncated output (original token count: 49605)
Total output lines: 3995

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
    property string androidWidgetMessage: ""
    property int pendingAndroidWidgetPage: -1
    property string pendingAndroidWidgetRecordId: ""
    property string draggingWidgetRecordId: ""
    property string deletingAndroidWidgetRecordId: ""
    property string deletingAndroidWidgetName: ""
    property var androidWidgetLayoutUndo: null
    property int androidWidgetDragPageDirection: 0
    readonly property int androidWidgetGridStep: 24
    readonly property int androidWidgetChromeHeight: 256
    property string openSourceNoticesText: ""
    readonly property bool androidWidgetModalVisible: forgetDialog.visible || homeAppDialog.visible
        || createFolderDialog.visible || folderDialog.visible || addFolderAppsDialog.visible
        || deleteFolderDialog.visible || restoreLayoutDialog.visible || owlEditorDialog.visible
        || openSourceNoticesDialog.visible || removeAndroidWidgetDialog.visible
    property bool languageSaveFailed: false
    property bool layoutRestoreRecoveryFailed: false
    property bool layoutRestoreCleanupPending: false

    Timer {
        id: androidWidgetUndoTimer
        interval: 7000
        onTriggered: window.androidWidgetLayoutUndo = null
    }

    Timer {
        id: androidWidgetPickerLaunchTimer
        interval: 50
        onTriggered: {
            const recordId = window.pendingAndroidWidgetRecordId
            window.pendingAndroidWidgetRecordId = ""
            const accepted = recordId.length > 0
                    ? launcher.rebindAndroidWidget(recordId)
                    : launcher.addAndroidWidgetOnPage(window.pendingAndroidWidgetPage)
            window.pendingAndroidWidgetPage = -1
            window.androidWidgetMessage = accepted
                    ? "" : qsTr("Android widget picker could not be opened.")
        }
    }

    Timer {
        id: androidWidgetPageDragTimer
        interval: 500
        repeat: true
        onTriggered: {
            if (window.draggingWidgetRecordId.length === 0)
                return
            const nextPage = Math.max(0, Math.min(launcher.homePageCount - 1,
                window.selectedHomePage + window.androidWidgetDragPageDirection))
            if (nextPage === window.selectedHomePage) {
                window.androidWidgetDragPageDirection = 0
                stop()
            } else {
                window.selectedHomePage = nextPage
            }
        }
    }

    Connections {
        target: appTranslations
        function onLanguageChanged() {
            owl.refreshLocalizedText()
            window.layoutMessage = ""
            window.widgetMessage = ""
        }
    }

    Connections {
        target: launcher
        function onSystemFontScaleChanged() {
            Theme.systemFontScale = launcher.systemFontScale
        }
        function onAndroidWidgetResizeFinished(recordId, oldWidthDp, oldHeightDp,
                                               newWidthDp, newHeightDp) {
            if (oldWidthDp === newWidthDp && oldHeightDp === newHeightDp)
                return
            const widget = launcher.androidWidgets.find(function(entry) {
                return entry.recordId === recordId
            })
            if (!widget || Number(widget.widthDp) !== newWidthDp
                    || Number(widget.heightDp) !== newHeightDp)
                return
            window.rememberAndroidWidgetLayout({
                recordId: recordId,
                page: widget.page,
                xDp: widget.xDp,
                yDp: widget.yDp,
                widthDp: oldWidthDp,
                heightDp: oldHeightDp
            })
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

    function inspectLayoutBackup(json) {
        if (typeof json !== "string" || json.length === 0 || json.length > 2 * 1024 * 1024)
            return null
        let parsed
        try {
            parsed = JSON.parse(json)
        } catch (error) {
            return null
        }
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
            return null
        const owlBackup = parsed.owlWidgets === undefined ? "" : JSON.stringify(parsed.owlWidgets)
        if (owlBackup.length > 0 && !owl.canImportWidgetBackup(owlBackup))
            return null
        const androidWidgetBackup = parsed.androidWidgets === undefined
                ? "" : JSON.stringify(parsed.androidWidgets)
        if (androidWidgetBackup.length > 0 && launcher.platformSupported
                && !launcher.canRestoreAndroidWidgetBackup(androidWidgetBackup))
            return null
        return { owlBackup: owlBackup, androidWidgetBackup: androidWidgetBackup }
    }

    function applyLayoutBackup(json, prepareAndroidWidgets) {
        const components = inspectLayoutBackup(json)
        if (!components)
            return false
        if (!launcher.importLayout(json))
            return false
        if (components.owlBackup.length > 0 && !owl.importWidgetBackup(components.owlBackup))
            return false
        return !prepareAndroidWidgets || components.androidWidgetBackup.length === 0
                || !launcher.platformSupported
                || launcher.prepareAndroidWidgetBackupRestore(components.androidWidgetBackup)
    }

    function rollbackLayoutBackup(backup) {
        const layoutRestored = applyLayoutBackup(backup, false)
        const widgetsRestored = !launcher.platformSupported
                || launcher.rollbackAndroidWidgetBackupRestore()
        if (!layoutRestored || !widgetsRestored || !launcher.clearLayoutRestoreJournal()) {
            layoutRestoreRecoveryFailed = true
            return false
        }
        return true
    }

    function restoreLayoutBackup(json) {
        if (layoutRestoreRecoveryFailed)
            return false
        if (layoutRestoreCleanupPending) {
            if (!recoverLayoutBackupRestore() || layoutRestoreCleanupPending)
                return false
        }
        const components = inspectLayoutBackup(json)
        if (!components)
            return false
        const rollbackBackup = launcher.createLayoutBackup(owl.exportWidgetBackup())
        if (rollbackBackup.length === 0)
            return false
        if (!launcher.beginLayoutRestoreJournal(rollbackBackup)) {
            if (launcher.layoutRestoreJournalPhase().length > 0) {
                layoutRestoreRecoveryFailed = true
                layoutMessage = qsTr("An interrupted Home restore needs recovery. Keep OmaApp installed and retry after checking device storage.")
            }
            return false
        }
        if (!applyLayoutBackup(json, true)) {
            rollbackLayoutBackup(rollbackBackup)
            return false
        }
        if (!launcher.commitLayoutRestoreJournal()) {
            rollbackLayoutBackup(rollbackBackup)
            return false
        }
        if (launcher.platformSupported && !launcher.commitAndroidWidgetBackupRestore()) {
            layoutRestoreCleanupPending = true
            return true
        }
        if (!launcher.clearLayoutRestoreJournal()) {
            layoutRestoreCleanupPending = true
        } else {
            layoutRestoreCleanupPending = false
        }
        return true
    }

    function recoverLayoutBackupRestore() {
        const phase = launcher.layoutRestoreJournalPhase()
        if (phase.length === 0) {
            layoutRestoreCleanupPending = false
            return !launcher.platformSupported || launcher.recoverAndroidWidgetBackupRestore()
        }
        if (phase === "committed") {
            if (launcher.platformSupported && !launcher.commitAndroidWidgetBackupRestore()) {
                layoutRestoreCleanupPending = true
                return true
            }
            if (!launcher.clearLayoutRestoreJournal()) {
                layoutRestoreCleanupPending = true
                return true
            }
            layoutRestoreCleanupPending = false
            return true
        }
        if (phase !== "applying")
            return false
        const rollbackBackup = launcher.layoutRestoreJournalBackup()
        if (rollbackBackup.length === 0 || !applyLayoutBackup(rollbackBackup, false))
            return false
        const recovered = (!launcher.platformSupported || launcher.rollbackAndroidWidgetBackupRestore())
                && launcher.clearLayoutRestoreJournal()
        if (recovered)
            layoutRestoreCleanupPending = false
        return recovered
    }

    function widgetResizeStep() {
        return Math.max(32, Math.round((homePageView.width / launcher.gridColumns) / 8) * 8)
    }

    function androidWidgetCanvasHeight(page) {
        let bottom = 0
        for (let index = 0; index < launcher.androidWidgets.length; ++index) {
            const widget = launcher.androidWidgets[index]
            if (widget.page === page)
                bottom = Math.max(bottom, Number(widget.yDp) + Number(widget.heightDp)
                                  + androidWidgetChromeHeight)
        }
        return Math.max(240, bottom + androidWidgetGridStep)
    }

    function rememberAndroidWidgetLayout(widget) {
        androidWidgetLayoutUndo = {
            recordId: widget.recordId,
            page: Number(widget.page),
            xDp: Number(widget.xDp),
            yDp: Number(widget.yDp),
            widthDp: Number(widget.widthDp),
            heightDp: Number(widget.heightDp)
        }
        androidWidgetUndoTimer.restart()
    }

    function undoAndroidWidgetLayout() {
        const previous = androidWidgetLayoutUndo
        if (!previous)
            return
        const placed = launcher.placeAndroidWidget(previous.recordId, previous.page,
            previous.xDp, previous.yDp)
        const resized = placed && launcher.resizeAndroidWidget(previous.recordId,
            previous.widthDp, previous.heightDp)
        androidWidgetLayoutUndo = null
        androidWidgetUndoTimer.stop()
        androidWidgetMessage = placed && resized ? ""
            : qsTr("Android widget layout could not be restored.")
    }

    function placeAndroidWidgetTo(recordId, page, xDp, yDp, availableWidthDp) {
        const widget = launcher.androidWidgets.find(function(entry) {
            return entry.recordId === recordId
        })
        if (!widget)
            return
        const canvasWidth = Math.max(0, Number(availableWidthDp) || 0)
        const step = androidWidgetGridStep
        const maxX = Math.max(0, canvasWidth - Math.max(280, Number(widget.widthDp)))
        const maxY = Math.max(0, 20000 - Number(widget.heightDp) - androidWidgetChromeHeight)
        const x = Math.floor(Math.max(0, Math.min(maxX, xDp)) / step) * step
        const y = Math.floor(Math.max(0, Math.min(maxY, yDp)) / step) * step
        rememberAndroidWidgetLayout(widget)
        if (!launcher.placeAndroidWidget(recordId, page, x, y)) {
            androidWidgetLayoutUndo = null
            androidWidgetUndoTimer.stop()
            androidWidgetMessage = qsTr("Android widget placement could not be saved.")
        } else {
            androidWidgetMessage = ""
        }
    }

    function moveAndroidWidgetTo(recordId, page, order) {
        const widget = launcher.androidWidgets.find(function(entry) {
            return entry.recordId === recordId
        })
        if (!widget)
            return
        rememberAndroidWidgetLayout(widget)
        if (!launcher.moveAndroidWidget(recordId, page, order)) {
            androidWidgetLayoutUndo = null
            androidWidgetUndoTimer.stop()
            androidWidgetMessage = qsTr("Android widget placement could not be saved.")
        } else {
            androidWidgetMessage = ""
        }
    }

    function updateAndroidWidgetDragPage(scenePosition) {
        if (!draggingWidgetRecordId || !scenePosition)
            return
        const pointer = homePageView.mapFromItem(null, scenePosition.x, scenePosition.y)
        const edge = Math.max(24, Math.min(48, homePageView.width * 0.12))
        let direction = 0
        if (pointer.x >= homePageView.width - edge
                && selectedHomePage + 1 < launcher.homePageCount) {
            direction = 1
        } else if (pointer.x <= edge && selectedHomePage > 0) {
            direction = -1
        }
        androidWidgetDragPageDirection = direction
        if (direction !== 0 && !androidWidgetPageDragTimer.running)
            androidWidgetPageDragTimer.start()
        else if (direction === 0)
            androidWidgetPageDragTimer.stop()
    }

    function resizeAndroidWidgetTo(recordId, widthDp, heightDp) {
        const widget = launcher.androidWidgets.find(function(entry) {
            return entry.recordId === recordId
        })
        if (!widget)
            return
        rememberAndroidWidgetLayout(widget)
        if (!launcher.resizeAndroidWidget(recordId, widthDp, heightDp)) {
            androidWidgetLayoutUndo = null
            androidWidgetUndoTimer.stop()
            androidWidgetMessage = qsTr("Android widget size could not be saved.")
        } else {
            androidWidgetMessage = ""
        }
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
                : angleMatch ? Number(angleMat…37605 tokens truncated…                               }
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
                            delegate: ColumnLayout {
                                required property var modelData
                                Layout.fillWidth: true
                                spacing: Theme.space(6)

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
                                        wrapMode: Text.WordWrap
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

                                RowLayout {
                                    Layout.fillWidth: true
                                    spacing: Theme.space(6)
                                    Button {
                                        Layout.fillWidth: true
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
                                        Layout.fillWidth: true
                                        text: modelData.editable ? qsTr("Edit") : qsTr("Copy")
                                        Accessible.name: modelData.editable
                                                         ? qsTr("Edit %1").arg(modelData.name)
                                                         : qsTr("Make an editable copy of %1").arg(modelData.name)
                                        onClicked: editOwlWidget(modelData.id, !modelData.editable)
                                    }
                                    Button {
                                        Layout.fillWidth: true
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
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: Theme.space(6)
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

                        Text {
                            Layout.fillWidth: true
                            visible: launcher.systemFontScale > 1.01
                            text: qsTr("Android accessibility text scale: %1×")
                                  .arg(launcher.systemFontScale.toFixed(1))
                            Accessible.name: text
                            color: Theme.muted
                            font.pixelSize: Theme.fontSize(11)
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
                                        borderEnd: parent.checked ? Theme.accent : Theme.outline
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
                    { label: qsTr("Home"), compactLabel: qsTr("Home compact"), page: 0 },
                    { label: qsTr("Apps"), compactLabel: qsTr("Apps compact"), page: 1 },
                    { label: qsTr("Devices"), compactLabel: qsTr("Devices compact"), page: 2 },
                    { label: qsTr("Style"), compactLabel: qsTr("Style compact"), page: 3 },
                    { label: qsTr("Settings"), compactLabel: qsTr("Settings compact"), page: 4 }
                ]

                delegate: Button {
                    required property var modelData
                    Layout.fillWidth: true
                    Layout.minimumWidth: 0
                    Layout.preferredWidth: 1
                    text: appTranslations.language === "ru"
                          ? modelData.compactLabel : modelData.label
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
                        font.pixelSize: Theme.fontSize(10)
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        wrapMode: Text.Wrap
                    }
                }
            }
        }
    }
}
