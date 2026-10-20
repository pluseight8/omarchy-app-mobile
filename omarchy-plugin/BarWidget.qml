import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Quickshell
import Quickshell.Io
import qs.Commons
import qs.Ui

BarWidget {
    id: root

    moduleName: "org.pluseight.omaapp"
    implicitWidth: button.implicitWidth
    implicitHeight: barSize

    property bool closing: false
    property string serviceMessage: qsTr("Plugin service is starting")
    property string pairingQrData: ""
    property double pairingExpiresAt: 0
    property string pendingRequestId: ""
    property string pendingDeviceName: ""
    property var pairedDevices: []

    function localPath(url) {
        const value = String(url)
        return value.startsWith("file://")
                ? decodeURIComponent(value.substring("file://".length))
                : ""
    }

    function sendServiceCommand(action, values) {
        const command = Object.assign({ action: action }, values || {});
        const line = JSON.stringify(command) + "\n";
        if (!service.running) {
            service.running = true;
            Qt.callLater(() => service.write(line));
        } else {
            service.write(line);
        }
    }

    function handleServiceMessage(line) {
        let message;
        try {
            message = JSON.parse(line);
        } catch (error) {
            console.warn("OmaApp plugin returned an invalid event");
            return;
        }

        if (message.event === "state") {
            pairedDevices = message.devices || [];
            serviceMessage = pairedDevices.length > 0
                    ? qsTr("Secure service ready")
                    : qsTr("No paired devices");
        } else if (message.event === "pairing") {
            pairingQrData = message.qrData;
            pairingExpiresAt = Number(message.expiresAt || 0);
            pendingRequestId = "";
            pendingDeviceName = "";
            serviceMessage = qsTr("Scan the QR code within three minutes");
            qrExpiryTimer.restart();
        } else if (message.event === "pair_request") {
            pendingRequestId = message.requestId;
            pendingDeviceName = message.deviceName;
            serviceMessage = qsTr("Approval required");
        } else if (message.event === "devices") {
            pairedDevices = message.devices || [];
            pairingQrData = "";
            pairingExpiresAt = 0;
            qrExpiryTimer.stop();
            pendingRequestId = "";
            pendingDeviceName = "";
            serviceMessage = pairedDevices.length > 0
                    ? qsTr("Secure service ready")
                    : qsTr("No paired devices");
        } else if (message.event === "pair_cancelled" || message.event === "pair_expired") {
            if (message.requestId === pendingRequestId) {
                pendingRequestId = "";
                pendingDeviceName = "";
                pairingQrData = "";
                pairingExpiresAt = 0;
                serviceMessage = message.event === "pair_expired"
                        ? qsTr("The pairing request expired")
                        : qsTr("The phone cancelled pairing");
            }
        } else if (message.event === "error") {
            serviceMessage = message.message || qsTr("Pairing service failed");
            pairingQrData = "";
            pairingExpiresAt = 0;
            qrExpiryTimer.stop();
        }
    }

    property string serviceScriptPath: localPath(Qt.resolvedUrl("service.py"))

    Process {
        id: service
        command: ["python3", root.serviceScriptPath]
        running: true
        stdinEnabled: true
        stdout: SplitParser {
            onRead: data => root.handleServiceMessage(data)
        }
        stderr: SplitParser {
            onRead: data => console.warn("OmaApp service:", data)
        }
        onExited: (exitCode, exitStatus) => {
            if (root.closing)
                return;
            if (exitCode !== 0)
                root.serviceMessage = qsTr("Pairing service stopped (%1)").arg(exitCode);
            restartTimer.start();
        }
    }

    Timer {
        id: restartTimer
        interval: 3000
        onTriggered: if (!root.closing) service.running = true
    }

    Timer {
        id: qrExpiryTimer
        repeat: false
        interval: Math.max(0, root.pairingExpiresAt * 1000 - Date.now())
        onTriggered: {
            root.pairingQrData = "";
            root.pairingExpiresAt = 0;
            root.serviceMessage = qsTr("The pairing code expired. Create a new one to continue.");
        }
    }

    Timer {
        interval: 10000
        repeat: true
        running: !root.closing
        onTriggered: root.sendServiceCommand("maintenance")
    }

    BarIconButton {
        id: button
        anchors.centerIn: parent
        text: "O"
        labelText: "OMA"
        active: root.pairedDevices.length > 0 || root.pendingRequestId.length > 0
        Accessible.name: root.pendingRequestId.length > 0
                ? qsTr("OmaApp pairing request needs approval")
                : root.pairedDevices.length > 0
                  ? qsTr("OmaApp has %1 paired devices").arg(root.pairedDevices.length)
                  : qsTr("OmaApp is not paired")
        onClicked: pairingWindow.visible = !pairingWindow.visible
    }

    MouseArea {
        anchors.fill: parent
        hoverEnabled: true
        acceptedButtons: Qt.NoButton
        onEntered: if (root.bar) root.bar.showTooltip(root, root.serviceMessage)
        onExited: if (root.bar) root.bar.hideTooltip(root)
    }

    PopupWindow {
        id: pairingWindow
        anchor.window: root.bar
        anchor.rect.x: Math.max(0, root.x - 140)
        anchor.rect.y: root.bar ? root.bar.height : 0
        width: 360
        height: root.pendingRequestId.length > 0 ? 380 : 440
        visible: false
        color: "transparent"
        grabFocus: true

        Rectangle {
            anchors.fill: parent
            color: Commons.Color.popups.background
            radius: 12
            border.color: Commons.Color.popups.border
            border.width: 1

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 18
                spacing: 12

                RowLayout {
                    Layout.fillWidth: true
                    Text {
                        Layout.fillWidth: true
                        text: qsTr("OmaApp")
                        color: Commons.Color.popups.text
                        font.pixelSize: 18
                        font.weight: Font.DemiBold
                    }
                    Button {
                        text: qsTr("Close")
                        onClicked: pairingWindow.visible = false
                    }
                }

                Text {
                    Layout.fillWidth: true
                    text: root.serviceMessage
                    color: Commons.Color.popups.text
                    wrapMode: Text.WordWrap
                }

                RowLayout {
                    Layout.fillWidth: true
                    visible: root.pendingRequestId.length === 0 && root.pairedDevices.length === 0
                    Button {
                        Layout.fillWidth: true
                        text: root.pairingQrData ? qsTr("Create another QR") : qsTr("Pair Android device")
                        onClicked: root.sendServiceCommand("begin_pairing")
                    }
                }

                RowLayout {
                    Layout.fillWidth: true
                    visible: root.pendingRequestId.length > 0
                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Allow %1 to connect and receive this computer’s theme?")
                                .arg(root.pendingDeviceName)
                        color: Commons.Color.popups.text
                        wrapMode: Text.WordWrap
                    }
                }

                RowLayout {
                    Layout.fillWidth: true
                    visible: root.pendingRequestId.length > 0
                    Button {
                        Layout.fillWidth: true
                        text: qsTr("Reject")
                        onClicked: root.sendServiceCommand("reject", { requestId: root.pendingRequestId })
                    }
                    Button {
                        Layout.fillWidth: true
                        text: qsTr("Approve")
                        highlighted: true
                        onClicked: root.sendServiceCommand("approve", { requestId: root.pendingRequestId })
                    }
                }

                Image {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: 228
                    Layout.preferredHeight: 228
                    visible: root.pairingQrData.length > 0 && root.pendingRequestId.length === 0
                    source: root.pairingQrData
                    fillMode: Image.PreserveAspectFit
                    cache: false
                    smooth: false
                }

                Text {
                    Layout.fillWidth: true
                    visible: root.pairingQrData.length > 0 && root.pendingRequestId.length === 0
                    text: qsTr("The QR code contains a temporary pairing secret. Approve only a device you can see.")
                    color: Commons.Color.popups.text
                    font.pixelSize: 12
                    wrapMode: Text.WordWrap
                    horizontalAlignment: Text.AlignHCenter
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    visible: root.pairedDevices.length > 0
                    spacing: 6
                    Repeater {
                        model: root.pairedDevices
                        delegate: RowLayout {
                            required property var modelData
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: modelData.deviceName
                                color: Commons.Color.popups.text
                                elide: Text.ElideRight
                            }
                            Button {
                                text: qsTr("Revoke")
                                onClicked: root.sendServiceCommand("revoke", { deviceId: modelData.deviceId })
                            }
                        }
                    }
                    Button {
                        Layout.fillWidth: true
                        text: qsTr("Pair another device")
                        onClicked: root.sendServiceCommand("begin_pairing")
                    }
                }

                Item { Layout.fillHeight: true }
            }
        }
    }

    Component.onDestruction: {
        closing = true;
        restartTimer.stop();
        service.running = false;
    }
}
