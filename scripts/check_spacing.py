#!/usr/bin/env python3
"""Check that no screen writes a space in dp: every space is named, and the theme sizes it.

A screen puts space between and around its elements with UI.Space (XS, S, M, L, XL), which
the theme turns into a size: dp for the default theme, whole cells for the retro theme. A
space written in dp looks right in the default theme and falls between two cells in the
retro one, where nothing else would say so.

What counts as a space: the arguments of padding(), Arrangement.spacedBy() and
PaddingValues(), a Spacer's height or width, and a `spacing =` argument. A zero is not a
space. The themes' own code is left out: a theme sizes its components as it likes.

Usage:
    ./scripts/check_spacing.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"
THEMES = SOURCES / "app" / "treelune" / "themes"

DP = re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\.dp\b")
# Calls whose whole argument list is space.
CALLS = re.compile(r"\b(?:padding|spacedBy|PaddingValues)\(|\bSpacer\(\s*(?:modifier\s*=\s*)?Modifier\.(?:height|width)\(")
NAMED = re.compile(r"\bspacing\s*=\s*(\d+(?:\.\d+)?)\.dp\b")


def arguments_end(text, start):
    """The index just past the parenthesis that closes the one opened before [start]."""
    depth = 1
    i = start
    while i < len(text) and depth:
        depth += {"(": 1, ")": -1}.get(text[i], 0)
        i += 1
    return i


def spaces_in_dp(text):
    """Offsets of every non-zero dp written as a space in [text]."""
    found = []
    for call in CALLS.finditer(text):
        span = text[call.end():arguments_end(text, call.end())]
        found += [call.end() + m.start() for m in DP.finditer(span) if float(m.group(1)) != 0]
    found += [m.start() for m in NAMED.finditer(text) if float(m.group(1)) != 0]
    return sorted(set(found))


def main():
    problems = []
    for path in sorted(SOURCES.rglob("*.kt")):
        if THEMES in path.parents:
            continue
        text = path.read_text(encoding="utf-8")
        for offset in spaces_in_dp(text):
            line = text.count("\n", 0, offset) + 1
            problems.append(f"{path.relative_to(ROOT)}:{line}: a space in dp -- use UI.Space")
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
