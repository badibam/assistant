#!/usr/bin/env python3
"""Pixelize Lucide's icons for the retro theme: app/.../themes/retro/icons/<name>.svg.

Each icon is drawn in a box of 22 by 22 pixels, the register's second size (docs/design/
retro-theme.md): rendered eight times larger, then each pixel kept when the icon covers at least
THRESHOLD of it. The rendering is headless Chrome's, the renderer the palette bench uses; the
vendored Lucide copy is the source (third_party/lucide).

About four icons in five come out clean. The others are redrawn by hand in the retouches file
(themes/retro/icon-retouches.txt), applied after the conversion: a block per icon, its name after
an @, then its 22 rows of '.' (empty) and '#' (inked). A retouch replaces the whole icon; it grows
with use.

Each icon is written as an SVG of filled pixels (viewBox 0 0 22 22, one path, a rectangle per run
of inked pixels in a row), which scripts/generate_icons.py turns into the theme's drawables. The
folder is written beside the old one and swapped in whole, so an interrupted run leaves the old
set rather than half of a new one. The output is committed, like the rest of the generated icons.

Usage:
    ./scripts/pixelize_icons.py                   pixelize every icon, apply the retouches
    ./scripts/pixelize_icons.py --plank [NAME...] a PNG of the written icons in tmp/, to judge by
                                                  eye: the names given, or the app's own icons
"""

import html
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LUCIDE = ROOT / "third_party" / "lucide" / "icons"
THEME = ROOT / "app" / "src" / "main" / "java" / "com" / "assistant" / "themes" / "retro"
OUT = THEME / "icons"
RETOUCHES = THEME / "icon-retouches.txt"
SOURCES = ROOT / "app" / "src" / "main" / "java"
TMP = ROOT / "tmp"

SIZE = 22
# How many times larger the icon is rendered before each pixel's coverage is measured.
SUPER = 8
# Coverage over which a pixel is inked: 0.45 kept at the trial of 2026-09-29, 0.30 clogging shapes.
THRESHOLD = 0.45

CHROME = "google-chrome"

PAGE = """<!doctype html><meta charset="utf-8"><pre id="out"></pre><script>
const ICONS = __ICONS__;
const SIZE = __SIZE__, SUPER = __SUPER__, THRESHOLD = __THRESHOLD__;
(async () => {
  const side = SIZE * SUPER;
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = side;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  const out = {};
  for (const [name, svg] of ICONS) {
    const url = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml" }));
    const img = new Image();
    await new Promise((ok, ko) => { img.onload = ok; img.onerror = () => ko(name); img.src = url; });
    ctx.clearRect(0, 0, side, side);
    ctx.drawImage(img, 0, 0, side, side);
    URL.revokeObjectURL(url);
    const data = ctx.getImageData(0, 0, side, side).data;
    const rows = [];
    for (let y = 0; y < SIZE; y++) {
      let row = "";
      for (let x = 0; x < SIZE; x++) {
        let sum = 0;
        for (let dy = 0; dy < SUPER; dy++)
          for (let dx = 0; dx < SUPER; dx++)
            sum += data[((y * SUPER + dy) * side + x * SUPER + dx) * 4 + 3];
        row += sum / (SUPER * SUPER * 255) >= THRESHOLD ? "#" : ".";
      }
      rows.push(row);
    }
    out[name] = rows;
  }
  document.getElementById("out").textContent = JSON.stringify(out);
})().catch(e => { document.getElementById("out").textContent = "ERROR " + e; });
</script>"""


def lucide_names():
    return sorted(svg.stem for svg in LUCIDE.glob("*.svg"))


def render(names):
    """{name: 22 rows of '.' and '#'}, every icon rendered in one page of headless Chrome."""
    icons = [[name, (LUCIDE / f"{name}.svg").read_text(encoding="utf-8").replace("currentColor", "#000")]
             for name in names]
    page = (PAGE.replace("__ICONS__", json.dumps(icons))
            .replace("__SIZE__", str(SIZE)).replace("__SUPER__", str(SUPER))
            .replace("__THRESHOLD__", str(THRESHOLD)))
    with tempfile.TemporaryDirectory() as work:
        path = Path(work) / "pixelize.html"
        path.write_text(page, encoding="utf-8")
        result = subprocess.run(
            [CHROME, "--headless=new", "--disable-gpu", "--no-sandbox", "--virtual-time-budget=600000",
             f"--user-data-dir={work}/profile", "--dump-dom", path.as_uri()],
            capture_output=True, text=True, timeout=600)
    match = re.search(r'<pre id="out">(.*?)</pre>', result.stdout, re.S)
    if not match or not match.group(1).startswith("{"):
        raise SystemExit(f"Chrome gave no icons: {match.group(1)[:200] if match else result.stderr[-2000:]}")
    maps = json.loads(html.unescape(match.group(1)))
    missing = sorted(set(names) - set(maps))
    if missing:
        raise SystemExit(f"{len(missing)} icon(s) not rendered: {missing[:10]}")
    return maps


def read_retouches(names):
    """{name: rows} from the retouches file; a block that is not a known icon of 22 rows of 22 fails."""
    if not RETOUCHES.exists():
        return {}
    retouches, current, problems = {}, None, []
    for number, line in enumerate(RETOUCHES.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip() or line.startswith("#") and not set(line) <= {".", "#"}:
            continue
        if line.startswith("@"):
            current = line[1:].strip()
            if current not in names:
                problems.append(f"line {number}: @{current} is not a Lucide icon")
            if current in retouches:
                problems.append(f"line {number}: @{current} retouched twice")
            retouches[current] = []
        elif current is None:
            problems.append(f"line {number}: rows before any @name")
        else:
            retouches[current].append(line)
    for name, rows in retouches.items():
        if len(rows) != SIZE or any(len(row) != SIZE or set(row) - {".", "#"} for row in rows):
            problems.append(f"@{name}: {SIZE} rows of {SIZE} '.' or '#' expected")
    if problems:
        raise SystemExit("icon-retouches.txt:\n  " + "\n  ".join(problems))
    return retouches


def svg(rows):
    """An SVG of filled pixels: one path, a rectangle per run of inked pixels in a row."""
    parts = []
    for y, row in enumerate(rows):
        for run in re.finditer(r"#+", row):
            parts.append(f"M{run.start()} {y}h{len(run.group())}v1h-{len(run.group())}z")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">'
            f'<path fill="currentColor" d="{"".join(parts)}"/></svg>\n')


def rows_of(svg_file):
    """The rows an icon's SVG holds, read back from its runs."""
    rows = [["."] * SIZE for _ in range(SIZE)]
    d = re.search(r' d="([^"]*)"', svg_file.read_text(encoding="utf-8")).group(1)
    for x, y, w in re.findall(r"M(\d+) (\d+)h(\d+)", d):
        for i in range(int(w)):
            rows[int(y)][int(x) + i] = "#"
    return ["".join(row) for row in rows]


def write(maps):
    """Writes every icon beside the current folder, then swaps the folder in whole."""
    partial = OUT.with_name("icons.part")
    if partial.exists():
        shutil.rmtree(partial)
    partial.mkdir()
    for name, rows in maps.items():
        (partial / f"{name}.svg").write_text(svg(rows), encoding="utf-8")
    if OUT.exists():
        shutil.rmtree(OUT)
    partial.rename(OUT)


def app_icons():
    """The icons the app's own code names, by the sources IconNamesInCodeTest reads: the icons of
    ButtonAction and EnrichmentType, the notifications' own (APP_ICON), every tool type's default
    and suggested icons, and the iconName = "..." the screens write."""
    found = set()
    for path in SOURCES.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for enum in ("ButtonAction", "EnrichmentType"):
            block = re.search(rf"enum class {enum}\(val iconName: String\) \{{(.*?)\}}", text, re.S)
            if block:
                found.update(re.findall(r'[A-Z_]+\("([^"]+)"\)', block.group(1)))
        found.update(re.findall(r'APP_ICON = "([^"]+)"', text))
        for function in ("getDefaultIconName", "getSuggestedIcons"):
            # A body, as an expression (its first line holding something) or as a block opening
            # right after the type; the contract's declaration has none
            body = re.search(rf"fun {function}\(\)\s*:\s*[\w<>]+\s*(?:=\s*([^\n]+)|\{{(.*?)\n\s*\}})", text, re.S)
            if body:
                found.update(re.findall(r'"([^"]+)"', body.group(1) if body.group(1) is not None else body.group(2)))
        found.update(re.findall(r'iconName = "([^"]+)"', text))
    unknown = sorted(n for n in found if not (LUCIDE / f"{n}.svg").exists())
    if unknown:
        raise SystemExit(f"names in the code that are no Lucide icon: {unknown}")
    return sorted(found)


def plank(names):
    """A PNG of [names] as written, three screen pixels per pixel, on the retro dark ground."""
    from PIL import Image, ImageDraw
    scale, pad, columns = 3, 4, 12
    cell = SIZE + 2 * pad
    lines = (len(names) + columns - 1) // columns
    image = Image.new("RGB", (columns * cell * scale, lines * cell * scale), (25, 17, 39))
    draw = ImageDraw.Draw(image)
    for i, name in enumerate(names):
        ox, oy = (i % columns) * cell + pad, (i // columns) * cell + pad
        for y, row in enumerate(rows_of(OUT / f"{name}.svg")):
            for x, pixel in enumerate(row):
                if pixel == "#":
                    draw.rectangle([(ox + x) * scale, (oy + y) * scale, (ox + x + 1) * scale - 1, (oy + y + 1) * scale - 1],
                                   fill=(180, 173, 194))
    TMP.mkdir(exist_ok=True)
    target = TMP / "retro-icons-plank.png"
    image.save(target)
    (TMP / "retro-icons-plank.txt").write_text(
        "\n".join(" ".join(names[i:i + columns]) for i in range(0, len(names), columns)) + "\n", encoding="utf-8")
    print(f"{target.relative_to(ROOT)}: {len(names)} icons, {columns} per row (names in retro-icons-plank.txt)")


def main(argv):
    if argv and argv[0] == "--plank":
        names = argv[1:] or app_icons()
        unknown = [n for n in names if not (OUT / f"{n}.svg").exists()]
        if unknown:
            raise SystemExit(f"not written: {unknown}")
        plank(names)
        return 0
    names = lucide_names()
    maps = render(names)
    retouches = read_retouches(set(names))
    maps.update(retouches)
    write(maps)
    print(f"{len(maps)} icons written to {OUT.relative_to(ROOT)}, {len(retouches)} retouched by hand.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
