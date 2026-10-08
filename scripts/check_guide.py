#!/usr/bin/env python3
"""Check the Guide's chapters (assets/guide/chapters.json) against the code they name.

A tutorial waits for operations and sends the user to places: an operation renamed, a place
gone, a text forgotten, and the step never ends, with nothing else in the build to say so. For
each chapter and step:

- an operation waited for is `resource.operation`, the resource registered (ServiceRegistry)
  and the operation answered by its service (a `"operation" ->` branch in its file);
- an origin is a Source;
- a place's address begins with a kind Place.of reads, a name in braces kept by an earlier step;
- every text the Guide reads by name exists in English and in French (guide.xml, guide-fr.xml):
  a chapter's title and introduction, a tutorial's end, each step's goal and explanation;
- every demo id a step names, in its place or in what it waits for, is a zone or a tool the demo
  installs (assets/demo/structure.json), and a tool's place names the zone the demo puts it in:
  the demo rewritten, a tutorial would send the user to a place that is not there.

Usage:
    ./scripts/check_guide.py
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHAPTERS = ROOT / "app" / "src" / "main" / "assets" / "guide" / "chapters.json"
CORE = ROOT / "app" / "src" / "main" / "java" / "app" / "treelune" / "core"
REGISTRY = CORE / "coordinator" / "ServiceRegistry.kt"
PLACE = CORE / "navigation" / "Place.kt"
SOURCE = CORE / "coordinator" / "Source.kt"
TEXTS = {"English": CORE / "strings" / "sources" / "guide.xml", "French": CORE / "strings" / "sources" / "guide-fr.xml"}
SOURCES = ROOT / "app" / "src" / "main" / "java"
DEMO = ROOT / "app" / "src" / "main" / "assets" / "demo" / "structure.json"
DEMO_ID = re.compile(r"demo-[a-z0-9-]+")

KEPT = re.compile(r"\{([a-z_]+)\}")


def services():
    """Each registered resource and the file of its service."""
    registry = REGISTRY.read_text(encoding="utf-8")
    found = {}
    for resource, cls in re.findall(r'"([a-z_]+)" to ::(\w+)', registry):
        files = [p for p in SOURCES.rglob("*.kt") if re.search(rf"\bclass {cls}\b", p.read_text(encoding="utf-8"))]
        found[resource] = files[0] if files else None
    return found


def main():
    chapters = json.loads(CHAPTERS.read_text(encoding="utf-8"))["chapters"]
    registered = services()
    kinds = set(re.findall(r'^\s+"([a-z_]+)" ->', PLACE.read_text(encoding="utf-8"), re.M))
    origins = set(re.findall(r"^\s+([A-Z]+),?$", SOURCE.read_text(encoding="utf-8"), re.M))
    texts = {lang: set(re.findall(r'<string name="([^"]+)"', path.read_text(encoding="utf-8"))) for lang, path in TEXTS.items()}
    demo = json.loads(DEMO.read_text(encoding="utf-8"))
    demo_zones = {z["id"] for z in demo["zones"]}
    demo_tools = {t["id"]: t["zone_id"] for t in demo["tools"]}
    problems = []

    for chapter in chapters:
        cid = chapter["id"]
        keys = [f"guide_{cid}_title", f"guide_{cid}_intro"]
        kept = set()
        for n, step in enumerate(chapter["steps"], start=1):
            where = f"{cid}, step {n}"
            keys += [f"guide_{cid}_{n}_goal", f"guide_{cid}_{n}_text"]
            target = step.get("target")
            named = [target or ""] + list((step.get("await") or {}).get("params", {}).values())
            for demo_id in (i for text in named for i in DEMO_ID.findall(text)):
                if demo_id not in demo_zones and demo_id not in demo_tools:
                    problems.append(f"{where}: '{demo_id}' is no zone or tool of the demo")
            if target is not None and target.startswith("tool/"):
                parts = target.split("/")
                if parts[1] in demo_tools and demo_tools[parts[1]] != parts[2]:
                    problems.append(f"{where}: the demo puts '{parts[1]}' in '{demo_tools[parts[1]]}', not '{parts[2]}'")
            if target is not None:
                if target.split("/")[0] not in kinds:
                    problems.append(f"{where}: no place of kind '{target.split('/')[0]}'")
                for name in KEPT.findall(target):
                    if name not in kept:
                        problems.append(f"{where}: '{{{name}}}' kept by no earlier step")
            wait = step.get("await")
            if (step["kind"] == "do") != (wait is not None):
                problems.append(f"{where}: a do step waits for an operation, and only it")
            if wait:
                resource, _, operation = wait["operation"].partition(".")
                service = registered.get(resource)
                if resource not in registered:
                    problems.append(f"{where}: no service for resource '{resource}'")
                elif service is None or f'"{operation}" ->' not in service.read_text(encoding="utf-8"):
                    problems.append(f"{where}: '{resource}' answers no operation '{operation}'")
                if wait["origin"] not in origins:
                    problems.append(f"{where}: no origin '{wait['origin']}'")
                for value in wait.get("params", {}).values():
                    for name in KEPT.findall(value):
                        if name not in kept:
                            problems.append(f"{where}: '{{{name}}}' kept by no earlier step")
            kept |= set(step.get("keep", {}).keys())
        if chapter["steps"]:
            keys.append(f"guide_{cid}_outro")
        for lang, have in texts.items():
            for key in keys:
                if key not in have:
                    problems.append(f"{cid}: no {lang} text '{key}'")

    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} problem(s) in the Guide's chapters")
        return 1
    print(f"The Guide's {len(chapters)} chapters agree with the code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
