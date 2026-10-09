#!/usr/bin/env python3
"""Draw the app's launcher icon: a tree in front of a crescent moon, on a night blue.

Android draws a launcher icon from layers of 108 x 108 units, of which a launcher shows the
central 72 and a mask may keep only the central circle of radius 33; every shape here stays
inside that circle. The script writes four vector drawables, and the output is committed --
the build never needs this script nor shapely:

  - res/drawable/ic_launcher_background.xml, the night blue;
  - res/drawable/ic_launcher_foreground.xml, the moon and the tree in their colours;
  - res/drawable/ic_launcher_monochrome.xml, the same shapes in one colour, which Android 13
    and later tints itself when the user asks for themed icons;
  - res/drawable/app_mark_retro.xml, the retro theme's mark beside the home screen's title, in
    pixels, from the grid of themes/retro/app-mark.txt.

That grid is drawn by hand: at 22 pixels the band around the tree falls between two pixels.
`--pixel` prints the grid the shapes give, each pixel the shape covering most of it, to start
from when the shapes change.

The tree is ringed by a band of nothing, so that where it overlaps the moon the two still read
as two shapes -- in the monochrome above all, where colour cannot tell them apart. The band is
cut out of the moon rather than painted over it in the background colour, which the
monochrome layer could not do: it is drawn by its alpha alone. Cutting one shape out of
another is what shapely does; a vector drawable only takes the resulting outlines.

Changing the icon = changing the shapes or colours below, running this script (in an
environment that has shapely: `pip install shapely`), committing.

Usage:
    ./scripts/make_launcher_icon.py            write the four drawables
    ./scripts/make_launcher_icon.py --pixel    print the shapes' grid in pixels
"""

import sys
from pathlib import Path

from shapely.geometry import MultiPolygon, Point, box
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"
PIXEL_GRID = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune" / "themes" / "retro" / "app-mark.txt"

BACKGROUND = "#1E2A47"
MOON = "#F2E6BF"
LEAVES = "#76B578"
TRUNK = "#558B58"

# Segments per circle: at the largest launcher size (432 px for 108 units) a chord of the moon
# then strays from the true circle by under a twentieth of a pixel.
RESOLUTION = 64

# The moon is a disc with a second disc cut out of it, offset towards the top right: what
# remains is a crescent whose horns curl round the tree.
MOON_DISC = (54, 52, 32)
MOON_CUT = (61.07, 44.93, 27)

# The crown, four overlapping discs; the trunk, a bar with rounded ends; the band around both.
CROWN = [(54, 45.08, 14.72), (42.96, 53.36, 10.58), (65.04, 53.36, 10.58), (54, 57.04, 9.2)]
TRUNK_BOX = (51.5, 60, 56.5, 84)
BAND = 2.6

# The pixel mark: its side in pixels, the retro theme's icon box, over the square of the shapes'
# bounds with a pixel of night around them; a pixel is a shape when it covers this much of it.
PIXELS = 22
PIXEL_WINDOW = (18.8, 16.8, 89.2, 87.2)
PIXEL_COVERAGE = 0.45


def disc(cx, cy, r):
    return Point(cx, cy).buffer(r, quad_segs=RESOLUTION // 4)


def bar(x0, y0, x1, y1, grow=0.0):
    """A vertical bar with fully rounded ends, widened on every side by grow."""
    half = (x1 - x0) / 2 + grow
    cx = (x0 + x1) / 2
    top, bottom = y0 - grow + half, y1 + grow - half
    return unary_union([box(cx - half, top, cx + half, bottom),
                        disc(cx, top, half), disc(cx, bottom, half)])


def shapes():
    crown = unary_union([disc(x, y, r) for x, y, r in CROWN])
    trunk = bar(*TRUNK_BOX)
    band = unary_union([disc(x, y, r + BAND) for x, y, r in CROWN] + [bar(*TRUNK_BOX, grow=BAND)])
    moon = disc(*MOON_DISC).difference(disc(*MOON_CUT)).difference(band)
    # The trunk runs under the crown: only the part below it is drawn, so the two never overlap.
    return moon, trunk.difference(crown), crown


def path_data(geometry):
    """SVG path syntax, which a vector drawable reads as is: one closed outline per ring."""
    polygons = geometry.geoms if isinstance(geometry, MultiPolygon) else [geometry]
    parts = []
    for polygon in polygons:
        polygon = orient(polygon, sign=1.0)
        for ring in [polygon.exterior, *polygon.interiors]:
            points = list(ring.coords)[:-1]
            parts.append("M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in points) + "Z")
    return "".join(parts)


def pixel_grid():
    """The shapes in PIXELS rows of PIXELS characters: '.' the night, 'm', 't', 'l' a shape."""
    moon, trunk, crown = shapes()
    x0, y0, x1, _ = PIXEL_WINDOW
    side = (x1 - x0) / PIXELS
    rows = []
    for j in range(PIXELS):
        row = ""
        for i in range(PIXELS):
            cell = box(x0 + i * side, y0 + j * side, x0 + (i + 1) * side, y0 + (j + 1) * side)
            area, mark = max((shape.intersection(cell).area, mark) for shape, mark in ((moon, "m"), (trunk, "t"), (crown, "l")))
            row += mark if area >= PIXEL_COVERAGE * side * side else "."
        rows.append(row)
    return rows


def pixel_vector():
    """The retro mark from the grid drawn by hand: a path per colour, a rectangle per run of a row."""
    rows = [line for line in PIXEL_GRID.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]
    if len(rows) != PIXELS or any(len(row) != PIXELS for row in rows):
        sys.exit(f"{PIXEL_GRID.name}: {PIXELS} rows of {PIXELS} pixels expected")
    colours = {".": BACKGROUND, "m": MOON, "l": LEAVES, "t": TRUNK}
    runs = {mark: [] for mark in colours}
    for y, row in enumerate(rows):
        x = 0
        while x < PIXELS:
            mark = row[x]
            if mark not in colours:
                sys.exit(f"{PIXEL_GRID.name}: unknown pixel '{mark}' at row {y + 1}")
            end = x
            while end < PIXELS and row[end] == mark:
                end += 1
            runs[mark].append(f"M{x},{y}H{end}V{y + 1}H{x}Z")
            x = end
    body = "\n".join(
        f'    <path\n        android:fillColor="{colours[mark]}"\n        android:pathData="{"".join(parts)}" />'
        for mark, parts in runs.items() if parts)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- The app's mark in the retro theme, in pixels. Written by scripts/make_launcher_icon.py "
        "from themes/retro/app-mark.txt: change it there. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{PIXELS}dp"\n    android:height="{PIXELS}dp"\n'
        f'    android:viewportWidth="{PIXELS}"\n    android:viewportHeight="{PIXELS}">\n'
        f"{body}\n</vector>\n")


def vector(paths, comment):
    body = "\n".join(
        f'    <path\n        android:fillColor="{color}"\n        android:fillType="evenOdd"\n'
        f'        android:pathData="{data}" />'
        for color, data in paths)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment} Written by scripts/make_launcher_icon.py: change it there. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        f"{body}\n</vector>\n")


def main():
    if sys.argv[1:] == ["--pixel"]:
        print("\n".join(pixel_grid()))
        return
    moon, trunk, crown = shapes()
    outputs = {
        "ic_launcher_background.xml": vector(
            [(BACKGROUND, "M0,0H108V108H0Z")],
            "The launcher icon's background layer: the night behind the moon."),
        "ic_launcher_foreground.xml": vector(
            [(MOON, path_data(moon)), (TRUNK, path_data(trunk)), (LEAVES, path_data(crown))],
            "The launcher icon's foreground layer: the moon and the tree."),
        "ic_launcher_monochrome.xml": vector(
            [("#FF000000", path_data(unary_union([moon, trunk, crown])))],
            "The launcher icon's monochrome layer, which the system tints: the same shapes in one colour."),
        "app_mark_retro.xml": pixel_vector(),
    }
    for name, text in outputs.items():
        target = DRAWABLE / name
        staged = target.with_suffix(".xml.part")
        staged.write_text(text, encoding="utf-8")
        staged.replace(target)
        print(f"wrote {name}")


if __name__ == "__main__":
    main()
