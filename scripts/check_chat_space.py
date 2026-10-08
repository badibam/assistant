#!/usr/bin/env python3
"""Check that every screen the chat's button floats over leaves room for it at its end.

The chat's button floats at the bottom right of the places one reads (Place.chatButton): the
home screen, a zone, a tool, the Guide, an automation. A screen of such a place puts
`.chatButtonSpace()` after its `verticalScroll`, or `chatButtonEnd()` in a lazy list's bottom
content padding; without it, its last line hides under the button, and only a phone shows it.

The places marked in Place.kt are mapped to their screens' files below; a place marked and not
mapped fails, so that a new place gets its screen named here. A tool place stands for every tool
type's screen, found from the tool type's getUsageScreen.

Usage:
    ./scripts/check_chat_space.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
PLACE = APP / "core" / "navigation" / "Place.kt"
TOOLS = APP / "tools"

# A place's kind, and the files of the screens drawn for it
SCREENS = {
    "home": [APP / "core" / "ui" / "screens" / "MainScreen.kt"],
    "zone": [APP / "core" / "ui" / "screens" / "ZoneScreen.kt"],
    "guide": [APP / "core" / "guide" / "ui" / "GuideScreens.kt"],
    "chapter": [APP / "core" / "guide" / "ui" / "GuideScreens.kt"],
    "automation": [APP / "core" / "ai" / "ui" / "automation" / "AutomationScreen.kt"],
    "execution": [APP / "core" / "ai" / "ui" / "automation" / "ExecutionDetailScreen.kt"],
}

ROOM = re.compile(r"\.chatButtonSpace\(\)|chatButtonEnd\(\)")


def tool_screens():
    """Each tool type's usage screen file, from the composable its getUsageScreen calls."""
    files = []
    for tool_type in sorted(TOOLS.glob("*/*ToolType.kt")):
        text = tool_type.read_text(encoding="utf-8")
        body = text[text.index("override fun getUsageScreen"):]
        called = re.search(r"\b([A-Z]\w+Screen)\(", body).group(1)
        found = [p for p in tool_type.parent.rglob("*.kt") if re.search(rf"^fun {called}\(", p.read_text(encoding="utf-8"), re.M)]
        if not found:
            raise SystemExit(f"check_chat_space: no file holds {called}, the screen of {tool_type.parent.name}")
        files.append(found[0])
    return files


def marked_kinds():
    """The kinds of the places Place.kt marks with the chat's button."""
    text = PLACE.read_text(encoding="utf-8")
    kinds = []
    for block in re.split(r"\n    (?=(?:data class|object) )", text):
        kind = re.search(r'override val kind = "([a-z_]+)"', block)
        if kind and "override val chatButton = true" in block:
            kinds.append(kind.group(1))
    return kinds


def main():
    problems = []
    checked = set()
    for kind in marked_kinds():
        files = tool_screens() if kind == "tool" else SCREENS.get(kind)
        if files is None:
            problems.append(f"place '{kind}' has the chat's button and no screen named in check_chat_space.py")
            continue
        for path in files:
            if path in checked:
                continue
            checked.add(path)
            if not ROOM.search(path.read_text(encoding="utf-8")):
                problems.append(f"{path.relative_to(ROOT)}: no room for the chat's button (chatButtonSpace / chatButtonEnd)")
    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} screen(s) without room for the chat's button")
        return 1
    print(f"Every screen under the chat's button leaves it room ({len(checked)} files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
