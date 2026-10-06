#!/usr/bin/env python3
"""Make the cosy theme's fonts from Baloo 2, whose source is copied in third_party/baloo2/.

The source is one variable font (weights 400 to 800) that also draws Devanagari. The app
takes five fixed weights of it, the Devanagari left out, as res/font/baloo2_*.ttf: a fixed
weight is what every Android version draws the same, and the Devanagari would weigh half the
file for a script the app never shows in it. The output is committed, as the icons are: the
build never needs this script nor fontTools. The licence goes with the fonts into the app's
assets (fonts/baloo2/OFL.txt), as the SIL Open Font License asks.

Updating Baloo 2 = replacing third_party/baloo2/, running this script again, committing.

Usage:
    ./scripts/make_baloo_fonts.py
"""

import shutil
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "third_party" / "baloo2" / "Baloo2[wght].ttf"
LICENCE = ROOT / "third_party" / "baloo2" / "OFL.txt"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
ASSET_LICENCE = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "baloo2" / "OFL.txt"

# The weights the theme draws in, by the name of their file.
WEIGHTS = {"regular": 400, "medium": 500, "semibold": 600, "bold": 700, "extrabold": 800}

# Devanagari and its extensions: the blocks the font draws that the app never shows in it.
DROPPED = [(0x0900, 0x097F), (0x1CD0, 0x1CFF), (0xA8E0, 0xA8FF)]


def kept_codepoints(font):
    """Every character the font maps, but those of the dropped blocks."""
    return sorted(c for c in font.getBestCmap() if not any(a <= c <= b for a, b in DROPPED))


def main():
    for name, weight in WEIGHTS.items():
        font = TTFont(SOURCE)
        codepoints = kept_codepoints(font)
        font = instantiateVariableFont(font, {"wght": weight}, updateFontNames=True)
        options = Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        subsetter = Subsetter(options)
        subsetter.populate(unicodes=codepoints)
        subsetter.subset(font)
        out = FONTS / f"baloo2_{name}.ttf"
        font.save(out)
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")
    ASSET_LICENCE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(LICENCE, ASSET_LICENCE)
    print(ASSET_LICENCE.relative_to(ROOT))


if __name__ == "__main__":
    main()
