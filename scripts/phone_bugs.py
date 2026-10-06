#!/usr/bin/env python3
"""Bring the bugs noted on the phone into TODO.md, then delete them from the phone.

The bugs are the entries of the tool "Assistant" in the zone "Dev" of the release build, where
they are noted. They are read and deleted through the app's MCP server, as Claude reaches it: the
claude.ai connector "Assistant", whose tools Claude Code names mcp__claude_ai_Assistant__<command>.
The relay hands each request to whichever build polls it, so the external access must be open in
the release alone (docs/design/mcp-server.md), and the connector authorised once against it.

Claude only reads and deletes, and answers in JSON; this script shows the list, asks once, writes
TODO.md, and checks that every entry it wrote is the one Claude deleted. TODO.md is written before
anything is deleted, so an interruption leaves a bug in both places, never in neither.
"""

import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TODO = ROOT / "TODO.md"

ZONE = "Dev"
TOOL = "Assistant"

# Where the bugs land, created after "En cours" when it is not there yet.
SECTION = "## Bugs du téléphone"
NEXT_SECTION = "## En attente d'un déclencheur"

CONNECTOR = "mcp__claude_ai_Assistant__"
# ToolSearch: headless, the connectors are still connecting when the model starts, and only
# ToolSearch waits for them and loads their tools.
READ_TOOLS = ["ToolSearch"] + [CONNECTOR + name for name in ("app_context", "zones", "tool_instances", "tool_data")]
DELETE_TOOLS = ["ToolSearch"] + [CONNECTOR + name for name in ("app_context", "delete_data")]

MODEL = "sonnet"
EFFORT = "low"
TIMEOUT = 300

READ_SCHEMA = {
    "type": "object",
    "properties": {
        "error": {"type": ["string", "null"]},
        "tool_instance_id": {"type": ["string", "null"]},
        "entries": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"id": {"type": "string"}, "text": {"type": "string"}},
                "required": ["id", "text"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["error", "tool_instance_id", "entries"],
    "additionalProperties": False,
}

DELETE_SCHEMA = {
    "type": "object",
    "properties": {
        "error": {"type": ["string", "null"]},
        "deleted_ids": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["error", "deleted_ids"],
    "additionalProperties": False,
}

READ_PROMPT = f"""Use only the tools of the "Assistant" MCP connector; they may still be loading, so load them first with ToolSearch (query "+Assistant"). Call app_context first, as its description asks.

Find the zone named "{ZONE}", and in it the tool named "{TOOL}". Read all of its entries.

Answer with:
- tool_instance_id: the id of that tool;
- entries: one item per entry, its id and its text exactly as written — every field the user typed (a title and a body, for instance) joined by " — ", nothing rephrased, summarised, translated or left out;
- error: null, or what went wrong (connector unreachable, zone or tool not found, a refused call). When error is set, entries is empty.

Change nothing in the app."""


def delete_prompt(tool_instance_id, ids):
    return f"""Use only the tools of the "Assistant" MCP connector; they may still be loading, so load them first with ToolSearch (query "+Assistant"). Call app_context first, as its description asks.

Delete, with delete_data, these entries of the tool {tool_instance_id}, and nothing else:
{json.dumps(ids)}

Answer with:
- deleted_ids: the ids the app confirmed deleted;
- error: null, or what went wrong (connector unreachable, a refused call, an id the app did not delete)."""


def ask_claude(prompt, tools, schema):
    """Run claude headless on the connector's tools alone; return its structured answer."""
    process = subprocess.run(
        ["claude", "-p", "--model", MODEL, "--effort", EFFORT,
         "--allowedTools", *tools, "--json-schema", json.dumps(schema), "--output-format", "json"],
        input=prompt, capture_output=True, text=True, timeout=TIMEOUT, cwd=ROOT,
    )
    if process.returncode != 0:
        raise RuntimeError(f"claude failed ({process.returncode}): {process.stderr.strip() or process.stdout.strip()}")
    # A list of the session's events, the last one being its result
    events = json.loads(process.stdout)
    result = events[-1] if isinstance(events, list) and events else {}
    if result.get("type") != "result" or result.get("is_error"):
        raise RuntimeError(f"claude ended without a result: {result.get('result', process.stdout[-500:])}")
    answer = result.get("structured_output")
    if not isinstance(answer, dict):
        raise RuntimeError(f"claude gave no structured answer: {result.get('result', process.stdout)}")
    if answer.get("error"):
        raise RuntimeError(answer["error"])
    return answer


def one_line(text):
    """An entry as one TODO line: TODO.md holds one item per line."""
    return " ".join(text.split())


def add_to_todo(texts):
    """Append the items to the bugs section, creating it before NEXT_SECTION; written whole, then renamed."""
    content = TODO.read_text(encoding="utf-8")
    items = "".join(f"- {one_line(text)}\n" for text in texts)
    if SECTION in content:
        start = content.index(SECTION) + len(SECTION)
        following = content.find("\n## ", start)
        end = len(content) if following == -1 else following + 1
        existing = content[start:end].rstrip("\n") or "\n"
        block = existing + "\n" + items + "\n"
        content = content[:start] + block + content[end:]
    else:
        if NEXT_SECTION not in content:
            raise RuntimeError(f"TODO.md has no '{NEXT_SECTION}' to put '{SECTION}' before")
        at = content.index(NEXT_SECTION)
        content = content[:at] + f"{SECTION}\n\n{items}\n" + content[at:]
    partial = TODO.with_suffix(".md.part")
    partial.write_text(content, encoding="utf-8")
    os.replace(partial, TODO)


def confirm(question):
    try:
        return input(f"{question} [y/N] ").strip().lower() in ("y", "yes", "o", "oui")
    except (EOFError, KeyboardInterrupt):
        print()
        return False


def main():
    print(f"Reading {ZONE} › {TOOL} on the phone…", flush=True)
    found = ask_claude(READ_PROMPT, READ_TOOLS, READ_SCHEMA)
    entries = found["entries"]
    if not entries:
        print("No bug on the phone.")
        return 0
    tool_instance_id = found["tool_instance_id"]
    if not tool_instance_id:
        raise RuntimeError("entries found but no tool id given")

    print(f"\n{len(entries)} bug(s):")
    for entry in entries:
        print(f"  - {one_line(entry['text'])}")
    if not confirm(f"\nAdd them to TODO.md ({SECTION}), then delete them from the phone?"):
        print("Nothing done.")
        return 0

    add_to_todo([entry["text"] for entry in entries])
    print(f"Written to {TODO.relative_to(ROOT)}.")

    ids = [entry["id"] for entry in entries]
    print("Deleting them on the phone…", flush=True)
    deleted = set(ask_claude(delete_prompt(tool_instance_id, ids), DELETE_TOOLS, DELETE_SCHEMA)["deleted_ids"])
    left = [entry for entry in entries if entry["id"] not in deleted]
    if left:
        print(f"{len(left)} still on the phone, already in TODO.md:", file=sys.stderr)
        for entry in left:
            print(f"  - {one_line(entry['text'])}", file=sys.stderr)
        return 1
    print(f"{len(ids)} deleted on the phone.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print()
        sys.exit(130)
    except (RuntimeError, subprocess.TimeoutExpired, json.JSONDecodeError) as error:
        print(f"Failed: {error}", file=sys.stderr)
        sys.exit(1)
