import QtQuick

// OWL documents are recursive trees. Load each child node by URL so QML does
// not see OwlNode as a statically self-instantiating component.
Loader {
    id: root

    property var nodeData
    property var widgetRoot
    property bool configured: false

    function loadNode() {
        // QML var properties start as null. Wait for both required bindings so
        // an early Repeater creation cannot instantiate an OwlNode with a null
        // renderer before the delegate's bindings have propagated.
        if (!configured || nodeData === undefined || nodeData === null
                || widgetRoot === undefined || widgetRoot === null)
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
