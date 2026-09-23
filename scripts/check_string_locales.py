#!/usr/bin/env python3
"""Check that every translation carries exactly the keys of the default locale.

The app's strings live in source files that name their locale in the file name: `shared.xml`
and a tool's `strings.xml` hold the default (English), `shared-fr.xml` and `strings-fr.xml`
hold the French one. A gradle task aggregates each locale into its own `values*/` file, and
at runtime StringsManager resolves a key through Android's resources, which pick the file
matching the phone.

That resolution is why a missing key is invisible rather than loud: Android falls back to the
default, so a key forgotten in the French file silently shows English on a French phone, and
a key left behind in the French file after its default was deleted is never read at all.
Nothing in a compile or a test run notices either one.

The script also reads the placeholders (%1$s, %2$d...), because a translation that drops or
renumbers one throws IllegalFormatException at the moment the screen draws it -- the kind of
crash that reaches a phone and not a test.

Usage:
    ./scripts/check_string_locales.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "app" / "src" / "main" / "java" / "com" / "assistant" / "tools"
SHARED = ROOT / "app" / "src" / "main" / "java" / "com" / "assistant" / "core" / "strings" / "sources"

# The same shapes the gradle task reads, kept in step with it by hand: a source file is a
# locale of a namespace, and a string is a name with a body.
LOCALE = re.compile(r"^(.+?)(?:-([a-zA-Z]{2}(?:-r[A-Z]{2})?))?\.xml$")
STRING = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)
PLACEHOLDER = re.compile(r"%\d+\$[sd]|%[sd]")

# Sources that are deliberately single-locale: they are sent to the AI, not shown on screen,
# so they have no translation to be out of step with. Named here rather than guessed, so that
# adding one is a decision someone wrote down.
UNTRANSLATED = {"ai_prompt_chunks"}


def groups():
    """Return {namespace: {locale: path}} over every string source in the tree."""
    found = {}

    def add(path, namespace_of_base):
        match = LOCALE.match(path.name)
        if not match:
            return
        base, locale = match.group(1), match.group(2) or ""
        found.setdefault(namespace_of_base(base), {})[locale] = path

    for tool_dir in sorted(p for p in TOOLS.iterdir() if p.is_dir()):
        for path in sorted(tool_dir.glob("strings*.xml")):
            add(path, lambda base, name=tool_dir.name: name)

    for path in sorted(SHARED.glob("*.xml")):
        add(path, lambda base: base)

    return found


def strings_of(path):
    """Return {key: body} for one source file."""
    return {m.group(1): m.group(2).strip() for m in STRING.finditer(path.read_text(encoding="utf-8"))}


def main():
    problems = []

    for namespace, by_locale in sorted(groups().items()):
        if "" not in by_locale:
            problems.append(f"{namespace}: translations but no default locale "
                            f"({', '.join(sorted(by_locale))})")
            continue

        default = strings_of(by_locale[""])
        translations = {loc: p for loc, p in by_locale.items() if loc}

        if not translations and namespace not in UNTRANSLATED:
            problems.append(f"{namespace}: no translation at all; add it to UNTRANSLATED "
                            f"if that is deliberate")

        for locale, path in sorted(translations.items()):
            translated = strings_of(path)

            for key in sorted(set(default) - set(translated)):
                problems.append(f"{namespace}[{locale}]: {key} missing -- the phone will "
                                f"show the default instead")
            for key in sorted(set(translated) - set(default)):
                problems.append(f"{namespace}[{locale}]: {key} has no default; it is never read")

            for key in sorted(set(default) & set(translated)):
                expected = sorted(PLACEHOLDER.findall(default[key]))
                actual = sorted(PLACEHOLDER.findall(translated[key]))
                if expected != actual:
                    problems.append(f"{namespace}[{locale}]: {key} formats "
                                    f"{actual or 'nothing'} where the default formats "
                                    f"{expected or 'nothing'}")

    if problems:
        print(f"{len(problems)} problem(s) in the string sources:\n")
        for problem in problems:
            print(f"  {problem}")
        return 1

    counts = {ns: len(strings_of(by_locale[""])) for ns, by_locale in groups().items()}
    locales = sorted({loc for by_locale in groups().values() for loc in by_locale if loc})
    print(f"string sources: {sum(counts.values())} keys over {len(counts)} namespaces, "
          f"translated into {', '.join(locales)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
