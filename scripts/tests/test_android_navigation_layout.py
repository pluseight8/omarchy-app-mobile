from __future__ import annotations

import struct
import sys
import unittest
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ci"))
from check_android_navigation_layout import check_navigation_layout  # noqa: E402


LABELS = ("Главный экран", "Приложения", "Устройства", "Оформление", "Настройки")


def write_png(path: Path, spill: bool) -> None:
    width, height = 120, 60
    pixels = bytearray([16, 19, 21, 255] * width * height)

    def set_pixel(x: int, y: int, color: tuple[int, int, int, int]) -> None:
        offset = (y * width + x) * 4
        pixels[offset:offset + 4] = bytes(color)

    # Bright foreground inside each of the five button cells.
    for left, right in ((8, 20), (32, 44), (56, 68), (80, 92), (104, 118)):
        for x in range(left + 4, min(left + 8, right)):
            set_pixel(x, 40, (202, 204, 204, 255))
    if spill:
        for y in range(38, 43):
            set_pixel(25, y, (202, 204, 204, 255))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data)))

    rows = bytearray()
    stride = width * 4
    for y in range(height):
        rows.append(0)
        rows.extend(pixels[y * stride:(y + 1) * stride])
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")
    )


def write_hierarchy(path: Path) -> None:
    root = ET.Element("hierarchy", bounds="[0,0][120,60]")
    viewport = ET.SubElement(root, "node", {
        "package": "app.omaapp",
        "content-desc": "",
        "clickable": "false",
        "bounds": "[0,0][120,60]",
    })
    for index, label in enumerate(LABELS):
        left = (8, 32, 56, 80, 104)[index]
        right = (20, 44, 68, 92, 118)[index]
        ET.SubElement(viewport, "node", {
            "package": "app.omaapp",
            "content-desc": label,
            "clickable": "true",
            "bounds": f"[{left},30][{right},50]",
        })
    ET.ElementTree(root).write(path, encoding="unicode")


class AndroidNavigationLayoutTests(unittest.TestCase):
    def test_fails_if_localized_text_paints_in_the_gap_between_buttons(self):
        with TemporaryDirectory() as directory:
            screenshot = Path(directory) / "screen.png"
            hierarchy = Path(directory) / "screen.xml"
            write_png(screenshot, spill=True)
            write_hierarchy(hierarchy)

            failures = check_navigation_layout(screenshot, hierarchy, LABELS)

        self.assertTrue(any("spills outside button gap" in item for item in failures))

    def test_accepts_labels_contained_in_their_button_cells(self):
        with TemporaryDirectory() as directory:
            screenshot = Path(directory) / "screen.png"
            hierarchy = Path(directory) / "screen.xml"
            write_png(screenshot, spill=False)
            write_hierarchy(hierarchy)

            failures = check_navigation_layout(screenshot, hierarchy, LABELS)

        self.assertEqual(failures, [])

    def test_navigation_controls_remain_named_and_wrapping_in_qml(self):
        qml = (Path(__file__).resolve().parents[2] / "app/qml/Main.qml").read_text(
            encoding="utf-8"
        )
        start = qml.rindex('model: [', 0, qml.index('label: qsTr("Home"), page: 0'))
        end = qml.index("        }\n    }\n}", start)
        navigation = qml[start:end]
        self.assertIn("Accessible.name: modelData.label", navigation)
        self.assertIn("Layout.minimumWidth: 0", navigation)
        self.assertIn("Layout.preferredWidth: 1", navigation)
        self.assertIn("wrapMode: Text.Wrap", navigation)


if __name__ == "__main__":
    unittest.main()
