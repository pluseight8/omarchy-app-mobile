from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))
from android_accessibility_dump import dump_accessibility_xml  # noqa: E402


class AndroidAccessibilityDumpTests(unittest.TestCase):
    def test_retries_when_uiautomator_returns_an_empty_hierarchy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.xml"
            pulls = 0

            def run(command: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
                nonlocal pulls
                if command[:2] == ["adb", "pull"]:
                    pulls += 1
                    content = "<hierarchy />" if pulls == 1 else "<hierarchy><node /></hierarchy>"
                    Path(command[-1]).write_text(content, encoding="utf-8")
                return subprocess.CompletedProcess(command, 0, "", "")

            sleeps: list[float] = []
            result = dump_accessibility_xml(
                "/sdcard/screen.xml", output, attempts=3, delay_seconds=0.2,
                run=run, sleep=sleeps.append,
            )

        self.assertEqual(result, output)
        self.assertEqual(pulls, 2)
        self.assertEqual(sleeps, [0.2])

    def test_retries_when_uiautomator_reports_no_root(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.xml"
            calls: list[list[str]] = []
            root_checks = 0

            def run(command: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
                nonlocal root_checks
                calls.append(command)
                if command[2:4] == ["test", "-s"]:
                    root_checks += 1
                    return subprocess.CompletedProcess(command, 1 if root_checks == 1 else 0, "", "")
                if command[:3] == ["adb", "pull", "/sdcard/screen.xml"]:
                    Path(command[-1]).write_text("<hierarchy><node /></hierarchy>", encoding="utf-8")
                return subprocess.CompletedProcess(command, 0, "", "")

            sleeps: list[float] = []
            result = dump_accessibility_xml(
                "/sdcard/screen.xml", output, attempts=3, delay_seconds=0.2,
                run=run, sleep=sleeps.append,
            )
            self.assertTrue(output.is_file())

        self.assertEqual(result, output)
        self.assertEqual(root_checks, 2)
        self.assertEqual(sleeps, [0.2])
        self.assertEqual(sum(command[2:5] == ["uiautomator", "dump", "--compressed"] for command in calls), 2)

    def test_fails_instead_of_pulling_missing_hierarchy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.xml"
            calls: list[list[str]] = []

            def run(command: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
                calls.append(command)
                return subprocess.CompletedProcess(command, 1 if command[2:4] == ["test", "-s"] else 0,
                                                   "ERROR: null root node", "")

            with self.assertRaisesRegex(RuntimeError, "no hierarchy root"):
                dump_accessibility_xml(
                    "/sdcard/screen.xml", output, attempts=2, delay_seconds=0,
                    run=run, sleep=lambda _delay: None,
                )

        self.assertEqual(sum(command[:2] == ["adb", "pull"] for command in calls), 0)
        self.assertFalse(output.exists())

    def test_rejects_remote_shell_metacharacters(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.xml"
            with self.assertRaisesRegex(ValueError, "simple file path"):
                dump_accessibility_xml("/sdcard/screen.xml; touch /tmp/pwned", output)
        self.assertFalse(output.exists())

    def test_applies_a_bounded_timeout_to_each_adb_command(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.xml"
            timeouts: list[float] = []

            def run(command: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
                timeouts.append(float(kwargs["timeout"]))
                if command[:2] == ["adb", "pull"]:
                    Path(command[-1]).write_text("<hierarchy><node /></hierarchy>", encoding="utf-8")
                return subprocess.CompletedProcess(command, 0, "", "")

            dump_accessibility_xml(
                "/sdcard/screen.xml", output, command_timeout_seconds=3,
                run=run, sleep=lambda _delay: None,
            )

        self.assertEqual(timeouts, [3, 3, 3, 3])

    def test_retries_after_an_adb_command_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "screen.xml"
            calls = 0

            def run(command: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
                nonlocal calls
                if command[2:4] == ["uiautomator", "dump"]:
                    calls += 1
                    raise subprocess.TimeoutExpired(command, timeout=2)
                return subprocess.CompletedProcess(command, 0, "", "")

            with self.assertRaisesRegex(RuntimeError, "Could not capture Android UI hierarchy"):
                dump_accessibility_xml(
                    "/sdcard/screen.xml", output, attempts=2, command_timeout_seconds=2,
                    run=run, sleep=lambda _delay: None,
                )

        self.assertEqual(calls, 2)


if __name__ == "__main__":
    unittest.main()
