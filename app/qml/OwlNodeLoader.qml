import QtQuick

// OWL documents are recursive trees. Load each child node by URL so QML does
// not see OwlNode as a statically self-instantiating component.
Loader {
    id: root

    property var nodeData
    property var widgetRoot
    property bool configured: false

    function logNodeState(phase) {
        if (!omaappQmlDiagnostics)
            return
        const type = nodeData && typeof nodeData === "object" ? String(nodeData.type || "") : "unset"
        console.warn("OmaApp OWL loader:", phase, type,
                     "configured", configured,
                     "data", nodeData !== undefined && nodeData !== null,
                     "renderer", widgetRoot !== undefined && widgetRoot !== null,
                     "status", status, "item", item !== null)
    }

    function loadNode() {
        logNodeState("request")
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

    onStatusChanged: logNodeState("status")
    Component.onCompleted: {
        configured = true
        loadNode()
    }
    onNodeDataChanged: loadNode()
    onWidgetRootChanged: loadNode()
}
