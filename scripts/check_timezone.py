#!/usr/bin/env python3
"""Check that no code reads the device's timezone behind the app's back.

The app has one timezone: the override the user sets, or the device's when there is none.
DateTimeConfig.getZoneId() is where that choice is made, and every date the app computes or
shows goes through it -- DateUtils and DateTimeConverter take it as a parameter defaulting to
it, and DateUtils.calendarAt() hands out the Calendar that the period code works in.

Java's date API makes the other path the easy one: Calendar.getInstance(), a SimpleDateFormat,
LocalDate.now() with no zone all silently take the device's timezone. With no override the two
are the same and nothing shows. With one, days start at a different moment from the dates
shown beside them, and the AI is told a time the screen does not show -- which is the bug this
check keeps from coming back, one call at a time.

Two files are allowed: the one that makes the choice, and the settings screen that shows the
device's timezone as the choice the override departs from.

Usage:
    ./scripts/check_timezone.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"

ALLOWED = {
    "app/treelune/core/config/AppConfigStructures.kt",
    "app/treelune/core/ui/screens/settings/FormatSettingsScreen.kt",
}

# Each takes the device's timezone without saying so.
FORBIDDEN = [
    (re.compile(r"\bCalendar\.getInstance\(\s*\)"), "Calendar.getInstance() -- use DateUtils.calendarAt()"),
    (re.compile(r"\bSimpleDateFormat\("), "SimpleDateFormat -- use DateUtils.format() or DateTimeConverter"),
    (re.compile(r"\bZoneId\.systemDefault\(\)"), "ZoneId.systemDefault() -- use DateTimeConfig.getZoneId()"),
    (re.compile(r"\bTimeZone\.getDefault\(\)"), "TimeZone.getDefault() -- use DateTimeConfig.getZoneId()"),
    (re.compile(r"\b(?:LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\(\s*\)"),
     "now() without a zone -- pass the app's zone"),
]


def main():
    found = []
    for path in sorted(SOURCES.rglob("*.kt")):
        relative = path.relative_to(SOURCES).as_posix()
        if relative in ALLOWED:
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if line.lstrip().startswith(("//", "*", "/*")):
                continue
            for pattern, advice in FORBIDDEN:
                if pattern.search(line):
                    found.append(f"{relative}:{number}: {advice}")

    for entry in found:
        print(entry)
    if found:
        print(f"{len(found)} read(s) of the device's timezone outside DateTimeConfig.getZoneId().")
        return 1
    print("Every date goes through the app's timezone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
