#!/usr/bin/env python3
"""Check that the app's copy of the Cartouche font is the latest, byte for byte.

Cartouche, the pixel font of the retro theme, lives in a project of its own
(cartouche-font, beside this repository) that Saylune and the assistant both copy from:
its glyph maps are the source, its ttf/ the compiled product. The app carries a copy in
res/font, committed, so that the build never reaches outside the repository. A copy nobody
checks ages in silence: a glyph drawn there after the copy never shows here, and nothing
else fails.

The font project is found beside the main checkout, so that the check also runs from a
worktree. Its absence is a failure, not a pass: a check that cannot look has not looked.

Usage:
    ./scripts/check_font_copy.py
"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COPY = ROOT / "app" / "src" / "main" / "res" / "font"

# The files the app takes from the font project's ttf/: the text family, both weights.
FILES = ["cartouche_regular.ttf", "cartouche_thin.ttf"]


def font_project():
    """The font project's directory: beside the main checkout, which a worktree is not."""
    common = subprocess.run(
        ["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.strip()
    return Path(common).parent.parent / "cartouche-font"


def main():
    source = font_project() / "ttf"
    if not source.is_dir():
        print(f"font copy: {source} not found -- the copy cannot be checked")
        return 1
    stale = [name for name in FILES
             if not (COPY / name).is_file() or (COPY / name).read_bytes() != (source / name).read_bytes()]
    for name in stale:
        print(f"font copy: {name} differs from {source / name} -- copy it again")
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
