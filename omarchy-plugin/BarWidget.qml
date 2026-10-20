import QtQuick
import Quickshell
import qs.Commons
import qs.Ui

BarWidget {
    id: root

    moduleName: "org.pluseight.omaapp"
    implicitWidth: button.implicitWidth
    implicitHeight: barSize

    BarIconButton {
        id: button
        anchors.centerIn: parent
        text: "O"
        labelText: "OMA"
        active: false
        Accessible.name: qsTr("OmaApp is not paired")
    }

    MouseArea {
        anchors.fill: parent
        hoverEnabled: true
        acceptedButtons: Qt.NoButton
        onEntered: if (root.bar) root.bar.showTooltip(root, qsTr("OmaApp: not paired"))
        onExited: if (root.bar) root.bar.hideTooltip(root)
    }
}
