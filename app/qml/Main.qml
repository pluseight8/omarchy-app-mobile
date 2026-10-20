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

    Component.onCompleted: launcher.refresh()

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

                            width: appGrid.cellWidth - 8
                            height: appGrid.cellHeight - 8
                            text: name
                            Accessible.name: qsTr("Launch %1").arg(name)
                            display: AbstractButton.TextUnderIcon
                            spacing: 7
                            onClicked: launcher.launch(index)

                            contentItem: Column {
                                spacing: 7
                                anchors.centerIn: parent

                                Rectangle {
                                    width: 42
                                    height: 42
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    color: Theme.surfaceRaised
                                    border.color: Theme.outline
                                    border.width: 1
                                    radius: 0

                                    Text {
                                        anchors.centerIn: parent
                                        text: name.length > 0 ? name.substring(0, 1).toUpperCase() : "•"
                                        color: Theme.accent
                                        font.pixelSize: 21
                                        font.weight: Font.Medium
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
                        text: qsTr("A direct, encrypted connection to Omarchy will appear here.")
                        color: Theme.muted
                        font.pixelSize: 15
                        wrapMode: Text.WordWrap
                    }
                }

                HyprlandSurface {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 168
                    fillColor: Theme.surface
                    borderStart: Theme.outline
                    borderEnd: Theme.outline
                    radius: Theme.defaultCornerRadius
                    borderWidth: 1

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 18
                        spacing: 12

                        Text {
                            text: qsTr("NOT PAIRED")
                            color: Theme.accent
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            font.letterSpacing: 1.3
                        }

                        Text {
                            Layout.fillWidth: true
                            text: qsTr("The pairing protocol is not enabled in this prototype. No remote commands or background network access are active.")
                            color: Theme.foreground
                            font.pixelSize: 14
                            wrapMode: Text.WordWrap
                        }
                    }
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
                        borderStart: Theme.accent
                        borderEnd: Theme.urgent
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
                            from: 2
                            to: 8
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
                            to: 40
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
                            to: 6
                            stepSize: 1
                            value: Theme.previewBorderWidth
                            Accessible.name: qsTr("Border width")
                            onMoved: Theme.previewBorderWidth = value
                        }
                    }

                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Preview controls are local and temporary. Theme sync and persistence are part of a later milestone.")
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
