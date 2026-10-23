from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class WidgetQmlGraphTests(unittest.TestCase):
    def test_recursive_owl_nodes_are_loaded_dynamically(self):
        node_qml = (ROOT / "app/qml/OwlNode.qml").read_text(encoding="utf-8")
        loader_qml = (ROOT / "app/qml/OwlNodeLoader.qml").read_text(encoding="utf-8")

        self.assertNotIn("delegate: OwlNode {", node_qml)
        self.assertNotIn("                OwlNode {", node_qml)
        self.assertIn("OwlNodeLoader {", node_qml)
        self.assertIn('setSource(Qt.resolvedUrl("OwlNode.qml")', loader_qml)

    def test_box_only_instantiates_its_own_children(self):
        node_qml = (ROOT / "app/qml/OwlNode.qml").read_text(encoding="utf-8")

        self.assertIn('model: node.nodeType === "Box" ? node.nodeChildren : []', node_qml)

    def test_home_media_controls_use_the_plugin_permission_key(self):
        main_qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")

        self.assertNotIn('featureEnabled("media")', main_qml)
        self.assertIn('featureEnabled("media.control")', main_qml)

    def test_text_nodes_measure_their_rendered_text(self):
        node_qml = (ROOT / "app/qml/OwlNode.qml").read_text(encoding="utf-8")

        self.assertIn('nodeType === "Text" ? Math.max(48, textContent.implicitWidth + nodePadding * 2)', node_qml)
        self.assertIn('nodeType === "Text" ? Math.max(24, textContent.implicitHeight + nodePadding * 2)', node_qml)
        self.assertIn("id: textContent", node_qml)

    def test_only_event_long_poll_owns_the_persisted_event_cursor(self):
        bridge = (ROOT / "app/android/src/app/omaapp/PairingBridge.java").read_text(encoding="utf-8")
        watch_events = bridge.split("private static void watchEvents(", 1)[1].split(
            "private static void queueEventRefresh(", 1)[0]
        fetch_features = bridge.split("private static void fetchFeatures(", 1)[1].split(
            "private static void runFeatureRequest(", 1)[0]

        self.assertIn("EventRevisionCursor.acceptEvent(preferences, event)", watch_events)
        self.assertNotIn("event_revision", fetch_features)
        self.assertNotIn("EventRevisionCursor", fetch_features)


if __name__ == "__main__":
    unittest.main()
