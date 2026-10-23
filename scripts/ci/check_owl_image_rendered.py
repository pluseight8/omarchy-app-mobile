#!/usr/bin/env python3
"""Confirm the embedded OWL image asset reached an Android screen capture."""

from __future__ import annotations

import sys
from pathlib import Path

from check_visual_regression import decode_png

EXPECTED_BADGE = (165, 85, 85)
COLOR_TOLERANCE = 8
CLUSTER_SIZE = 12
MINIMUM_CLUSTER_PIXELS = 4
MAX_CANDIDATE_PIXELS = 8_192


def count_embedded_image_clusters(
    width: int,
    height: int,
    channels: int,
    rows: list[bytes],
) -> tuple[int, int]:
    """Count small connected badge regions, rejecting isolated wallpaper colors."""
    matching: set[int] = set()
    for y in range(height):
        row = rows[y]
        for x in range(width):
            offset = x * channels
            if channels == 4 and row[offset + 3] < 200:
                continue
            if all(abs(row[offset + channel] - EXPECTED_BADGE[channel]) <= COLOR_TOLERANCE
                   for channel in range(3)):
                matching.add(y * width + x)
                if len(matching) > MAX_CANDIDATE_PIXELS:
                    return 0, len(matching)

    regions = 0
    largest_region = 0
    while matching:
        first = matching.pop()
        pending = [first]
        min_x = max_x = first % width
        min_y = max_y = first // width
        size = 0
        while pending:
            pixel = pending.pop()
            x = pixel % width
            y = pixel // width
            size += 1
            min_x = min(min_x, x)
            max_x = max(max_x, x)
            min_y = min(min_y, y)
            max_y = max(max_y, y)
            for neighbor_y in range(max(0, y - 1), min(height, y + 2)):
                for neighbor_x in range(max(0, x - 1), min(width, x + 2)):
                    neighbor = neighbor_y * width + neighbor_x
                    if neighbor in matching:
                        matching.remove(neighbor)
                        pending.append(neighbor)

        largest_region = max(largest_region, size)
        if (size >= MINIMUM_CLUSTER_PIXELS
                and max_x - min_x < CLUSTER_SIZE
                and max_y - min_y < CLUSTER_SIZE):
            regions += 1
    return regions, largest_region


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check_owl_image_rendered.py HOME_SCREENSHOT.png", file=sys.stderr)
        return 2
    image = Path(sys.argv[1])
    try:
        width, height, channels, rows = decode_png(image)
    except Exception as error:
        print(f"could not read OWL screenshot: {error}", file=sys.stderr)
        return 1

    # The bundled badge uses a distinctive urgent-red center. Separate compact
    # clusters prove that both Image and Canvas.image painted their own copy.
    regions, largest_region = count_embedded_image_clusters(width, height, channels, rows)
    if regions < 2:
        print(
            "expected separate rendered OWL Image and Canvas image assets; "
            f"found {regions} small cluster(s), largest {largest_region} pixel(s)",
            file=sys.stderr,
        )
        return 1
    print(f"OWL Image and Canvas image rendered in {image.name}: {regions} separate clusters")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())