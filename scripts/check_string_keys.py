#!/usr/bin/env python3
"""Check that every string key the code asks for exists.

A key the resources do not hold does not fail anywhere: StringsManager answers "[key]", and
the screen shows it -- a yes/no field read "[label_yes]" and "[label_no]". Nothing in a
compile or a test run notices, which is how 41 of them accumulated.

This reads every literal key passed to s.shared("...") and s.tool("...") and looks it up:

  - a shared key in shared.xml or ai_prompt_chunks.xml, the two sources the shared strings
    are made of;
  - a tool key in the strings.xml of the tool whose directory the call sits in. A call from
    outside tools/ is looked up in every tool's file.

The default locale is enough: check_string_locales.py already makes every translation carry
the same keys.

Not covered: keys built at run time ("day_of_week_$day", "icon_category_${id}"), which only
the screen that composes them can check. Comments are skipped, since they quote keys as
examples.

Usage:
    ./scripts/check_string_keys.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"
SHARED = SOURCES / "app" / "treelune" / "core" / "strings" / "sources"
TOOLS = SOURCES / "app" / "treelune" / "tools"
THEMES = SOURCES / "app" / "treelune" / "themes"

KEY = re.compile(r'<string name="([^"]+)"')
CALL = re.compile(r'\.(shared|tool|theme)\("([A-Za-z0-9_]+)"\)')


def keys_in(path):
    return set(KEY.findall(path.read_text(encoding="utf-8")))


def code_lines(text):
    """(number, line) for the lines that are code, block and line comments left out."""
    in_block = False
    for number, line in enumerate(text.splitlines(), start=1):
        stripped = line.strip()
        if in_block:
            if "*/" in stripped:
                in_block = False
            continue
        if stripped.startswith("/*"):
            in_block = "*/" not in stripped
            continue
        if stripped.startswith(("//", "*")):
            continue
        yield number, line.split("//")[0] if '"' not in line.split("//")[0][-1:] else line


def main():
    shared = keys_in(SHARED / "shared.xml") | keys_in(SHARED / "ai_prompt_chunks.xml")
    tool_keys = {d.name: keys_in(d / "strings.xml") for d in TOOLS.iterdir() if (d / "strings.xml").exists()}
    every_tool_key = set().union(*tool_keys.values())
    theme_keys = {d.name: keys_in(d / "strings.xml") for d in THEMES.iterdir() if (d / "strings.xml").exists()}

    missing = []
    for path in sorted(SOURCES.rglob("*.kt")):
        relative = path.relative_to(SOURCES)
        parts = relative.parts
        own_tool = parts[3] if len(parts) > 3 and parts[:3] == ("app", "treelune", "tools") else None
        own_theme = parts[3] if len(parts) > 3 and parts[:3] == ("app", "treelune", "themes") else None
        for number, line in code_lines(path.read_text(encoding="utf-8")):
            for kind, key in CALL.findall(line):
                if kind == "shared":
                    known = key in shared
                elif kind == "theme":
                    # A theme asks only for its own strings
                    known = own_theme is not None and key in theme_keys.get(own_theme, set())
                else:
                    known = key in (tool_keys.get(own_tool, set()) if own_tool else every_tool_key)
                if not known:
                    missing.append(f"{relative}:{number}: s.{kind}(\"{key}\")")

    for entry in missing:
        print(entry)
    if missing:
        print(f"{len(missing)} string key(s) asked for and not defined: the screen would show them as [key].")
        return 1
    print("Every string key the code asks for exists.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
