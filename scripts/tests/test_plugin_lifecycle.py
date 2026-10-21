from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
PLUGIN_ID = "org.pluseight.omaapp"


class PluginLifecycleTests(unittest.TestCase):
    def test_uninstall_keeps_backup_outside_quickshell_plugin_scan(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            home = root / "home"
            config = home / ".config"
            state = home / ".local" / "state"
            plugins = config / "omarchy" / "plugins"
            target = plugins / PLUGIN_ID
            fake_bin = root / "bin"
            target.mkdir(parents=True)
            fake_bin.mkdir()
            (target / "manifest.json").write_text(
                json.dumps({"id": PLUGIN_ID}), encoding="utf-8"
            )
            (target / "BarWidget.qml").write_text("BarWidget {}\n", encoding="utf-8")
            user_state = state / "omarchy" / "omaapp"
            user_state.mkdir(parents=True)
            (user_state / "trusted-devices.json").write_text("{}", encoding="utf-8")
            (fake_bin / "omarchy").write_text(
                "#!/bin/sh\n"
                "[ \"$1 $2 $3\" = \"plugin disable org.pluseight.omaapp\" ]\n",
                encoding="utf-8",
            )
            (fake_bin / "omarchy").chmod(0o755)
            env = os.environ.copy()
            env.update({
                "HOME": str(home),
                "XDG_CONFIG_HOME": str(config),
                "XDG_STATE_HOME": str(state),
                "PATH": str(fake_bin) + os.pathsep + env.get("PATH", ""),
            })

            result = subprocess.run(
                [str(REPOSITORY / "omarchy-plugin" / "uninstall.sh")],
                check=False, capture_output=True, text=True, env=env, timeout=10,
            )

            backup_root = state / "omarchy" / "plugin-backups"
            backups = list(backup_root.glob(f"{PLUGIN_ID}.*"))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(target.exists())
            self.assertEqual(len(backups), 1)
            self.assertTrue((backups[0] / "manifest.json").is_file())
            self.assertEqual(backup_root.stat().st_mode & 0o777, 0o700)
            self.assertTrue((user_state / "trusted-devices.json").is_file())
            self.assertEqual(list(plugins.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
