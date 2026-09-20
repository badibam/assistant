#!/usr/bin/env python3
"""Ratchet on the project's naming rule for string keys.

The rule: a key written inside a string is snake_case -- service parameter, result key,
schema field, setting name, column. camelCase belongs to Kotlin identifiers only. The one
exception is vocabulary the project did not write: JSON Schema's own keywords.

The rule holds only if something checks it, so this script counts the keys that still break
it and compares them to a versioned baseline. A key absent from the baseline fails the check:
nothing new enters. A baseline key that has disappeared also fails, so the baseline shrinks
as the debt is paid instead of drifting into a list of names nobody has looked at for months.

Usage:
    ./scripts/check_key_case.py            check against the baseline
    ./scripts/check_key_case.py --write    rewrite the baseline from what is there now
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"
BASELINE = Path(__file__).resolve().parent / "key_case_baseline.txt"

# JSON Schema keywords. Not ours to rename, so not violations.
FOREIGN = {
    "additionalProperties", "allOf", "anyOf", "contentEncoding", "contentMediaType",
    "exclusiveMaximum", "exclusiveMinimum", "maxItems", "maxLength", "maxProperties",
    "minItems", "minLength", "minProperties", "multipleOf", "oneOf", "patternProperties",
    "propertyNames", "uniqueItems",
}

# A string literal holding a single identifier with an inner capital: "toolInstanceId".
KEY = re.compile(r'[`"]([a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*)[`"]')


def collect():
    """Every camelCase key found in the sources, mapped to the files holding it."""
    found = {}
    for path in sorted(SOURCES.rglob("*")):
        if path.suffix not in (".kt", ".xml") or not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for key in KEY.findall(text):
            if key in FOREIGN:
                continue
            found.setdefault(key, set()).add(path.relative_to(ROOT).as_posix())
    return found


def read_baseline():
    if not BASELINE.exists():
        return None
    return {
        line.strip()
        for line in BASELINE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }


def write_baseline(keys):
    header = (
        "# Keys still written in camelCase, waiting to be renamed to snake_case.\n"
        "# Checked by scripts/check_key_case.py -- this list may shrink, never grow.\n"
        "# Rewrite with: ./scripts/check_key_case.py --write\n"
    )
    BASELINE.write_text(header + "\n".join(sorted(keys)) + "\n", encoding="utf-8")


def main():
    found = collect()

    if "--write" in sys.argv:
        write_baseline(found)
        print(f"Baseline written: {len(found)} keys.")
        return 0

    baseline = read_baseline()
    if baseline is None:
        print(f"No baseline at {BASELINE.relative_to(ROOT)}. Create it with --write.")
        return 1

    added = sorted(set(found) - baseline)
    gone = sorted(baseline - set(found))

    for key in added:
        where = ", ".join(sorted(found[key])[:3])
        print(f"NEW camelCase key: {key}  ({where})")
    for key in gone:
        print(f"RENAMED, drop it from the baseline: {key}")

    if added or gone:
        print(f"\n{len(found)} camelCase keys present, baseline says {len(baseline)}.")
        print("Run --write once the change is intentional.")
        return 1

    print(f"{len(found)} camelCase keys left to rename. None added.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
