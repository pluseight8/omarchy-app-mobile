import json
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.validate_plugin_manifest import validate_plugin


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
PLUGIN_ROOT = REPOSITORY_ROOT / "omarchy-plugin"


class PluginManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.plugin_dir = Path(self.temp_dir.name) / "plugin"
        shutil.copytree(PLUGIN_ROOT, self.plugin_dir)
        self.manifest_path = self.plugin_dir / "manifest.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def read_manifest(self):
        return json.loads(self.manifest_path.read_text(encoding="utf-8"))

    def write_manifest(self, manifest):
        self.manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    def test_repository_plugin_manifest_is_valid(self):
        self.assertEqual(validate_plugin(PLUGIN_ROOT), [])

    def test_rejects_parent_directory_entry_point(self):
        manifest = self.read_manifest()
        manifest["entryPoints"]["barWidget"] = "../Main.qml"
        self.write_manifest(manifest)
        self.assertTrue(any("safe relative path" in error for error in validate_plugin(self.plugin_dir)))

    def test_rejects_reserved_namespace(self):
        manifest = self.read_manifest()
        manifest["id"] = "omarchy.custom"
        self.write_manifest(manifest)
        self.assertTrue(any("reserved" in error for error in validate_plugin(self.plugin_dir)))

    def test_rejects_unknown_default_bar_section(self):
        manifest = self.read_manifest()
        manifest["barWidget"]["defaultSection"] = "center-right"
        self.write_manifest(manifest)
        self.assertTrue(any("defaultSection" in error for error in validate_plugin(self.plugin_dir)))

    def test_rejects_symbolic_links(self):
        (self.plugin_dir / "linked.qml").symlink_to(self.plugin_dir / "BarWidget.qml")
        self.assertTrue(any("symbolic links" in error for error in validate_plugin(self.plugin_dir)))


if __name__ == "__main__":
    unittest.main()
