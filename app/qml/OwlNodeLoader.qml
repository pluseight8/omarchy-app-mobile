import QtQuick

// OWL documents are recursive trees. Load each child node by URL so QML does
// not see OwlNode as a statically self-instantiating component.
Loader {
    id: root

    property var nodeData
    property var widgetRoot
    property bool configured: false

    function loadNode() {
        if (!configured || nodeData === undefined || widgetRoot === undefined)
            return
        setSource(Qt.resolvedUrl("OwlNode.qml"), {
            "nodeData": root.nodeData,
            "widgetRoot": root.widgetRoot
        })
    }

    Component.onCompleted: {
        configured = true
        loadNode()
    }
    onNodeDataChanged: loadNode()
    onWidgetRootChanged: loadNode()
}
