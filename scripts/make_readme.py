#!/usr/bin/env python3
"""Fill the READMEs' generated blocks from the sources, or check that they are up to date.

Each README (README.md in English, README.fr.md in French) is written by hand, except for a few
blocks between HTML comments that say what the code says and would go stale otherwise:

    <!-- tools -->      the tool types, in the registry's order, each with its name and tagline
    <!-- providers -->  the AI providers one brings a key for, by their name
    <!-- themes -->     the themes, in the registry's order, each with its name and tagline
    <!-- status -->     the version, the lowest Android, the languages

A block ends at its closing comment (<!-- /tools -->). Names and taglines come from the tools'
and the themes' own strings, in the README's language; a tool or a theme added to its registry
appears without touching the README.

The OpenAI-compatible provider is left out of the providers' block: it is a way of talking, not
a vendor, and the README's text names it.

Usage:
    ./scripts/make_readme.py           write the blocks
    ./scripts/make_readme.py --check   say which blocks are stale, and fail if any is; ./run test
                                       runs this, and never writes a README on its own
"""

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune"
TOOL_REGISTRY = SOURCES / "core" / "tools" / "ToolTypeScanner.kt"
THEME_REGISTRY = SOURCES / "core" / "themes" / "ThemeScanner.kt"
PROVIDER_REGISTRY = SOURCES / "core" / "ai" / "providers" / "AIProviderRegistry.kt"
PROVIDERS = SOURCES / "core" / "ai" / "providers"
GRADLE = ROOT / "app" / "build.gradle.kts"
RESOURCES = ROOT / "app" / "src" / "main" / "res"

# A README and the language its strings are read in: the suffix of the strings file.
READMES = {
    ROOT / "README.md": "",
    ROOT / "README.fr.md": "-fr",
}

# The Android release each API level the app may require as its lowest.
ANDROID_RELEASES = {
    24: "7.0", 25: "7.1", 26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11", 31: "12",
    32: "12L", 33: "13", 34: "14", 35: "15", 36: "16",
}

# The words the status block is made of, in each README's language.
WORDS = {
    "": {
        "version": "Version", "android": "Android", "or_later": "or later",
        "languages": "Languages:", "lang": {"en": "English", "fr": "French"},
    },
    "-fr": {
        "version": "Version", "android": "Android", "or_later": "ou plus récent",
        "languages": "Langues :", "lang": {"en": "anglais", "fr": "français"},
    },
}

STRING = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)
REGISTRY_ENTRY = re.compile(r'"([a-z_]+)" to \w+')


def strings(folder, suffix):
    """The strings of a tool's or a theme's folder in one language, unescaped as Android does."""
    text = (folder / f"strings{suffix}.xml").read_text(encoding="utf-8")
    return {name: value.replace("\\'", "'").replace('\\"', '"') for name, value in STRING.findall(text)}


def registry(path):
    """The ids a registry lists, in its order."""
    return REGISTRY_ENTRY.findall(path.read_text(encoding="utf-8"))


def named_lines(folder_root, ids, suffix):
    """One line per id: its name in bold and its tagline."""
    lines = []
    for id_ in ids:
        texts = strings(folder_root / id_, suffix)
        name = texts.get("display_name") or texts["name"]
        lines.append(f"- **{name}** — {texts['tagline']}")
    return lines


def providers():
    """The vendors' names, from the standard provider of each one the registry adds."""
    registry_text = PROVIDER_REGISTRY.read_text(encoding="utf-8")
    names = []
    for cls in re.findall(r"add\((\w+StandardProvider)\(", registry_text):
        if cls.startswith("OpenAICompatible"):
            continue
        for path in PROVIDERS.glob("*.kt"):
            text = path.read_text(encoding="utf-8")
            start = text.find(f"class {cls}(")
            if start < 0:
                continue
            found = re.search(r'getDisplayName\(\): String = "([^"]+)"', text[start:])
            names.append(found.group(1))
            break
        else:
            raise SystemExit(f"make_readme: no class {cls} under {PROVIDERS}")
    return names


def status(suffix):
    """The version, the lowest Android and the languages, one line each."""
    gradle = GRADLE.read_text(encoding="utf-8")
    version = re.search(r'versionName = "([^"]+)"', gradle).group(1)
    min_sdk = int(re.search(r"minSdk = (\d+)", gradle).group(1))
    words = WORDS[suffix]
    # English is the default resources' language; every values-xx folder adds one.
    codes = ["en"] + sorted(p.name.split("-")[1] for p in RESOURCES.glob("values-??"))
    languages = ", ".join(words["lang"][code] for code in codes)
    return [
        f"- {words['version']} {version}",
        f"- {words['android']} {ANDROID_RELEASES[min_sdk]} {words['or_later']}",
        f"- {words['languages']} {languages}",
    ]


def blocks(suffix):
    """Each block's content in one language."""
    return {
        "tools": named_lines(SOURCES / "tools", registry(TOOL_REGISTRY), suffix),
        "providers": [", ".join(f"**{name}**" for name in providers())],
        "themes": named_lines(SOURCES / "themes", registry(THEME_REGISTRY), suffix),
        "status": status(suffix),
    }


def fill(text, contents):
    """The README with each block's content replaced; the names of the blocks it lacks."""
    missing = []
    for name, lines in contents.items():
        pattern = re.compile(rf"(<!-- {name} -->\n)(.*?)(<!-- /{name} -->)", re.S)
        if not pattern.search(text):
            missing.append(name)
            continue
        body = "\n".join(lines) + "\n"
        text = pattern.sub(lambda m: m.group(1) + body + m.group(3), text)
    return text, missing


def main():
    check = "--check" in sys.argv[1:]
    failed = False
    for readme, suffix in READMES.items():
        current = readme.read_text(encoding="utf-8")
        contents = blocks(suffix)
        filled, missing = fill(current, contents)
        for name in missing:
            print(f"{readme.name}: no block <!-- {name} -->")
            failed = True
        if filled == current:
            continue
        if check:
            stale = [name for name in contents if name not in missing and fill(current, {name: contents[name]})[0] != current]
            print(f"{readme.name}: stale blocks {', '.join(stale)} — run ./scripts/make_readme.py, then read the result")
            failed = True
        else:
            # Written aside then renamed: an interrupted write never leaves half a README
            partial = readme.with_name(readme.name + ".part")
            partial.write_text(filled, encoding="utf-8")
            os.replace(partial, readme)
            print(f"{readme.name}: written")
    if check and not failed:
        print("READMEs: generated blocks up to date")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
