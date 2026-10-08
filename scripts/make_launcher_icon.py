#!/usr/bin/env python3
"""Draw the app's launcher icon: a tree in front of a crescent moon, on a night blue.

Android draws a launcher icon from layers of 108 x 108 units, of which a launcher shows the
central 72 and a mask may keep only the central circle of radius 33; every shape here stays
inside that circle. The script writes three vector drawables, and the output is committed --
the build never needs this script nor shapely:

  - res/drawable/ic_launcher_background.xml, the night blue;
  - res/drawable/ic_launcher_foreground.xml, the moon and the tree in their colours;
  - res/drawable/ic_launcher_monochrome.xml, the same shapes in one colour, which Android 13
    and later tints itself when the user asks for themed icons.

The tree is ringed by a band of nothing, so that where it overlaps the moon the two still read
as two shapes -- in the monochrome above all, where colour cannot tell them apart. The band is
cut out of the moon rather than painted over it in the background colour, which the
monochrome layer could not do: it is drawn by its alpha alone. Cutting one shape out of
another is what shapely does; a vector drawable only takes the resulting outlines.

Changing the icon = changing the shapes or colours below, running this script (in an
environment that has shapely: `pip install shapely`), committing.

Usage:
    ./scripts/make_launcher_icon.py
"""

from pathlib import Path

from shapely.geometry import MultiPolygon, Point, box
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"

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
    }
    for name, text in outputs.items():
        target = DRAWABLE / name
        staged = target.with_suffix(".xml.part")
        staged.write_text(text, encoding="utf-8")
        staged.replace(target)
        print(f"wrote {name}")


if __name__ == "__main__":
    main()
