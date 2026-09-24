#!/usr/bin/env python3
"""Generate what the app reads of the icon set, from the Lucide copy in third_party/lucide.

The app's icon vocabulary is Lucide, all of it: an icon name is a Lucide name. This script
turns the vendored source into three things the app ships, and the output is committed --
the build generates nothing, so it runs on a machine with neither this script's inputs nor
Node:

  - app/src/main/res/drawable/lucide_<name>.xml, one vector drawable per icon;
  - app/src/main/assets/icons/index.json: each icon's name, tags and categories, the
    categories with the icon that stands for each, and the table of former names -- every
    alias Lucide lists, mapped to the name it became;
  - app/src/main/assets/icons/LICENSE, Lucide's, which the ISC license asks to travel with
    every copy.

A theme draws its icons from Lucide or draws them all itself. A theme directory holding an
icons/ folder is of the second kind, and must hold exactly Lucide's names: one missing, or one
Lucide does not have, fails the run. Its SVGs become <theme>_<name>.xml the same way. There is
no mixing, and no placeholder for a missing icon.

The conversion is written here rather than delegated: Lucide's SVGs are a single 24x24
viewport stroked with one style, made of path, circle, ellipse, rect, line, polyline and
polygon, with no transform. Each shape becomes a path; Android's pathData reads SVG path
syntax as is. An element or attribute outside that set stops the run rather than being
dropped, so an update of Lucide that uses something new says so.

Updating Lucide: replace third_party/lucide (icons/, categories/, LICENSE, VERSION), run this
script, commit. It lists the names that disappeared since the last run, since an icon a
zone or a tool stored under such a name now shows as its two first letters -- unless the
name became an alias, in which case the app still finds it.

Usage:
    ./scripts/generate_icons.py
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LUCIDE = ROOT / "third_party" / "lucide"
THEMES = ROOT / "app" / "src" / "main" / "java" / "com" / "assistant" / "themes"
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "icons"
INDEX = ASSETS / "index.json"
SHARED_STRINGS = ROOT / "app" / "src" / "main" / "java" / "com" / "assistant" / "core" / "strings" / "sources" / "shared.xml"

SVG_NS = "{http://www.w3.org/2000/svg}"

# What the root <svg> of every Lucide icon says, and what the drawables therefore assume.
EXPECTED_ROOT = {
    "width": "24", "height": "24", "viewBox": "0 0 24 24", "fill": "none",
    "stroke": "currentColor", "stroke-width": "2",
    "stroke-linecap": "round", "stroke-linejoin": "round",
}

# The attributes each shape may carry. Anything else is a feature this converter does not know.
SHAPE_ATTRIBUTES = {
    "path": {"d"},
    "circle": {"cx", "cy", "r"},
    "ellipse": {"cx", "cy", "rx", "ry"},
    "rect": {"x", "y", "width", "height", "rx", "ry"},
    "line": {"x1", "y1", "x2", "y2"},
    "polyline": {"points"},
    "polygon": {"points"},
}


class UnsupportedSvg(Exception):
    pass


def number(value):
    """A coordinate as the shortest string that keeps its value."""
    text = f"{float(value):.4f}".rstrip("0").rstrip(".")
    return text if text not in ("", "-0") else "0"


def ellipse_path(cx, cy, rx, ry):
    """An ellipse as two half arcs, which pathData draws the way the SVG shape does."""
    cx, cy, rx, ry = map(float, (cx, cy, rx, ry))
    left, right = number(cx - rx), number(cx + rx)
    return (f"M{left},{number(cy)} A{number(rx)},{number(ry)} 0 1,0 {right},{number(cy)} "
            f"A{number(rx)},{number(ry)} 0 1,0 {left},{number(cy)} Z")


def rect_path(x, y, width, height, rx=None, ry=None):
    """A rectangle, with its rounded corners when it has them (SVG rules: one radius sets both)."""
    x, y, w, h = map(float, (x or 0, y or 0, width, height))
    if rx is None and ry is None:
        return f"M{number(x)},{number(y)} h{number(w)} v{number(h)} h{number(-w)} Z"
    rx = float(rx if rx is not None else ry)
    ry = float(ry if ry is not None else rx)
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    a = f"A{number(rx)},{number(ry)} 0 0 1"
    return (f"M{number(x + rx)},{number(y)} H{number(x + w - rx)} {a} {number(x + w)},{number(y + ry)} "
            f"V{number(y + h - ry)} {a} {number(x + w - rx)},{number(y + h)} "
            f"H{number(x + rx)} {a} {number(x)},{number(y + h - ry)} "
            f"V{number(y + ry)} {a} {number(x + rx)},{number(y)} Z")


def points_path(points, closed):
    coordinates = re.split(r"[\s,]+", points.strip())
    pairs = [f"{number(coordinates[i])},{number(coordinates[i + 1])}" for i in range(0, len(coordinates), 2)]
    return "M" + " L".join(pairs) + (" Z" if closed else "")


# Parameters each path command takes, per repetition. An arc's two flags are one character each
# and may be written with nothing between them and the next number ("a2 2 0 0022 17"), which
# Android's path parsers do not all read; every path is therefore rewritten with its numbers
# separated.
COMMAND_ARITY = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
NUMBER = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")


def normalize_path(d):
    """The same path, each command and number written out and separated by spaces."""
    out = []
    i, command = 0, None
    while i < len(d):
        c = d[i]
        if c in " \t\n\r,":
            i += 1
            continue
        if c.lower() in COMMAND_ARITY:
            command = c
            out.append(c)
            i += 1
            if c.lower() == "z":
                command = None
            continue
        if command is None:
            raise UnsupportedSvg(f"path data without a command at {d[i:i + 20]!r}")
        arity = COMMAND_ARITY[command.lower()]
        values = []
        for index in range(arity):
            while i < len(d) and d[i] in " \t\n\r,":
                i += 1
            if command.lower() == "a" and index in (3, 4):
                if i >= len(d) or d[i] not in "01":
                    raise UnsupportedSvg(f"arc flag expected at {d[i:i + 20]!r}")
                values.append(d[i])
                i += 1
                continue
            match = NUMBER.match(d, i)
            if not match:
                raise UnsupportedSvg(f"number expected at {d[i:i + 20]!r}")
            values.append(number(match.group()))
            i = match.end()
        out.append(" ".join(values))
        # Numbers after a moveto's first pair are implicit linetos, of the same case.
        if command in "mM":
            command = "l" if command == "m" else "L"
    return " ".join(out)


def shape_to_path(element):
    """(pathData, filled) for one shape of a Lucide SVG."""
    tag = element.tag.replace(SVG_NS, "")
    if tag not in SHAPE_ATTRIBUTES:
        raise UnsupportedSvg(f"element <{tag}>")
    attributes = dict(element.attrib)
    fill = attributes.pop("fill", None)
    if fill not in (None, "currentColor"):
        raise UnsupportedSvg(f'<{tag}> fill="{fill}"')
    unknown = set(attributes) - SHAPE_ATTRIBUTES[tag]
    if unknown:
        raise UnsupportedSvg(f"<{tag}> attribute(s) {sorted(unknown)}")
    a = attributes
    if tag == "path":
        data = normalize_path(a["d"])
    elif tag == "circle":
        data = ellipse_path(a["cx"], a["cy"], a["r"], a["r"])
    elif tag == "ellipse":
        data = ellipse_path(a["cx"], a["cy"], a["rx"], a["ry"])
    elif tag == "rect":
        data = rect_path(a.get("x"), a.get("y"), a["width"], a["height"], a.get("rx"), a.get("ry"))
    elif tag == "line":
        data = f"M{number(a['x1'])},{number(a['y1'])} L{number(a['x2'])},{number(a['y2'])}"
    else:
        data = points_path(a["points"], closed=(tag == "polygon"))
    return data, fill == "currentColor"


def vector_drawable(svg_file):
    """The vector drawable XML for one Lucide SVG. Stroked in the theme's neutral colour, which
    the icon composable tints per use."""
    root = ET.parse(svg_file).getroot()
    for key, expected in EXPECTED_ROOT.items():
        if root.get(key) != expected:
            raise UnsupportedSvg(f'root {key}="{root.get(key)}", expected "{expected}"')
    paths = []
    for element in root:
        data, filled = shape_to_path(element)
        fill = "?android:attr/colorControlNormal" if filled else "@android:color/transparent"
        paths.append(
            "    <path\n"
            f'        android:pathData="{data}"\n'
            '        android:strokeColor="?android:attr/colorControlNormal"\n'
            '        android:strokeWidth="2"\n'
            '        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round"\n'
            f'        android:fillColor="{fill}" />'
        )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by scripts/generate_icons.py from Lucide (ISC). Do not edit. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        + "\n".join(paths) + "\n</vector>\n"
    )


def resource_name(prefix, icon):
    return f"{prefix}_{icon.replace('-', '_')}"


def write_drawables(prefix, svg_dir, names):
    """Write <prefix>_<name>.xml for every name, and remove the ones no longer in the set."""
    failures = []
    for name in sorted(names):
        try:
            (DRAWABLES / f"{resource_name(prefix, name)}.xml").write_text(
                vector_drawable(svg_dir / f"{name}.svg"), encoding="utf-8")
        except (UnsupportedSvg, ET.ParseError, KeyError) as error:
            failures.append(f"{prefix}: {name}: {error}")
    expected = {f"{resource_name(prefix, name)}.xml" for name in names}
    for stale in DRAWABLES.glob(f"{prefix}_*.xml"):
        if stale.name not in expected:
            stale.unlink()
    return failures


def read_lucide():
    """(icons, categories, aliases) from the vendored copy."""
    icons = {}
    aliases = {}
    for meta_file in sorted((LUCIDE / "icons").glob("*.json")):
        name = meta_file.stem
        meta = json.loads(meta_file.read_text(encoding="utf-8"))
        icons[name] = {
            "name": name,
            "tags": meta.get("tags", []),
            "categories": meta.get("categories", []),
        }
        for alias in meta.get("aliases", []):
            former = alias["name"] if isinstance(alias, dict) else alias
            aliases[former] = name
    missing_svg = [name for name in icons if not (LUCIDE / "icons" / f"{name}.svg").exists()]
    if missing_svg:
        raise SystemExit(f"Lucide metadata without an SVG: {missing_svg}")
    categories = []
    for category_file in sorted((LUCIDE / "categories").glob("*.json")):
        meta = json.loads(category_file.read_text(encoding="utf-8"))
        categories.append({"id": category_file.stem, "icon": meta["icon"]})
    return icons, categories, aliases


def check_category_strings(categories):
    """Every category needs a title in the strings system; the locale check does the rest."""
    shared = SHARED_STRINGS.read_text(encoding="utf-8")
    missing = [c["id"] for c in categories if f'name="icon_category_{c["id"].replace("-", "_")}"' not in shared]
    return [f"no string icon_category_{name.replace('-', '_')} in shared.xml" for name in missing]


def main():
    icons, categories, aliases = read_lucide()
    names = set(icons)
    colliding = sorted(set(aliases) & names)
    if colliding:
        raise SystemExit(f"Names both current and former: {colliding}")

    previous = set()
    if INDEX.exists():
        previous = {icon["name"] for icon in json.loads(INDEX.read_text(encoding="utf-8"))["icons"]}

    DRAWABLES.mkdir(parents=True, exist_ok=True)
    problems = write_drawables("lucide", LUCIDE / "icons", names)

    # Themes that draw their own: all of Lucide, nothing else.
    for theme_dir in sorted(p for p in THEMES.iterdir() if p.is_dir()):
        own = theme_dir / "icons"
        if not own.is_dir():
            continue
        drawn = {svg.stem for svg in own.glob("*.svg")}
        missing, foreign = sorted(names - drawn), sorted(drawn - names)
        if missing or foreign:
            problems.append(f"theme {theme_dir.name}: {len(missing)} icon(s) missing, "
                            f"{len(foreign)} not in Lucide {foreign[:10]}")
            continue
        problems.extend(write_drawables(theme_dir.name, own, names))

    problems.extend(check_category_strings(categories))
    if problems:
        for problem in problems:
            print(problem)
        return 1

    ASSETS.mkdir(parents=True, exist_ok=True)
    version = (LUCIDE / "VERSION").read_text(encoding="utf-8").splitlines()[0]
    INDEX.write_text(json.dumps({
        "version": version,
        "icons": [icons[name] for name in sorted(names)],
        "categories": categories,
        "aliases": dict(sorted(aliases.items())),
    }, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (ASSETS / "LICENSE").write_text((LUCIDE / "LICENSE").read_text(encoding="utf-8"), encoding="utf-8")

    print(f"{version}: {len(names)} icons, {len(categories)} categories, {len(aliases)} former names.")
    gone = sorted(previous - names)
    if gone:
        renamed = [f"{name} -> {aliases[name]}" for name in gone if name in aliases]
        lost = [name for name in gone if name not in aliases]
        if renamed:
            print(f"Renamed since the last run, still found through their alias: {', '.join(renamed)}")
        if lost:
            print(f"Gone since the last run, shown as two letters wherever stored: {', '.join(lost)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
