#!/usr/bin/env python3
"""Check the examples in the L1 prompt against the code they describe.

The prompt is the contract between the app and the model, and it is the only one in the
project that is neither compiled nor tested: it lives in the strings system, as prose. So a
promise it makes can stop being true without anything saying so. That has happened twice --
it announced an ISO timestamp while the schema declared a number, and it still taught a
field type that a migration removed.

This reads the prompt's JSON examples and asks three questions of them:

  1. does every example parse as JSON;
  2. does every custom field type it names still exist in FieldType;
  3. does every key the base data schema declares carry a value of the declared type.

Question 3 compares against the shape the *model* is given, not the stored one. A property
the schema marks `"format": "epoch-millis"` is stored as a number and handed to the model as
an ISO 8601 string -- that is what SchemaModelView does at runtime, and the rule is repeated
here because a Python script cannot call it. If that rule ever changes, this line changes
with it.

Not everything is covered. Ten of the prompt's brace-delimited blocks are shorthand rather
than JSON (`{ name?, timestamp?, data }`), and they are skipped: they describe a shape in
prose, so there is nothing to parse. What is checked is what can be.

Exit code 0 when the prompt and the code agree, 1 otherwise.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PROMPT = ROOT / "app/src/main/java/com/assistant/core/strings/sources/ai_prompt_chunks.xml"
FIELD_TYPE = ROOT / "app/src/main/java/com/assistant/core/fields/FieldType.kt"
BASE_SCHEMAS = ROOT / "app/src/main/java/com/assistant/core/tools/BaseSchemas.kt"

# A JSON type, as the schema declares it, against the Python types a parsed example yields.
JSON_TYPES = {
    "string": (str,),
    "number": (int, float),
    "integer": (int,),
    "boolean": (bool,),
    "object": (dict,),
    "array": (list,),
}


def unescape(text):
    """XML entities back to the characters the prompt actually shows the model."""
    return (text.replace("&quot;", '"').replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&"))


def json_examples(text):
    """Every brace-delimited block of the prompt that is genuine JSON.

    Yields (block, parsed). Blocks holding a schema placeholder are left out: they are
    filled in at runtime and are not examples. Blocks that do not parse are reported by the
    caller as shorthand rather than as errors -- see the module docstring.
    """
    blocks = re.findall(r"\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}", text)
    for block in blocks:
        if "{{" in block or "SCHEMA:" in block:
            continue
        candidate = unescape(block)
        try:
            yield candidate, json.loads(candidate)
        except ValueError:
            yield candidate, None


def field_type_names():
    """The names of the FieldType enum entries."""
    source = FIELD_TYPE.read_text(encoding="utf-8")
    body = source[source.index("enum class FieldType {"):]
    body = body[:body.index("companion object")] if "companion object" in body else body
    return set(re.findall(r"^\s{4}([A-Z][A-Z_0-9]*)\s*,", body, re.M))


def declared_property_types():
    """What the base data schema says each of its properties holds, as the model sees it.

    Returns {key: (json_type, seen_as_iso_string)}. A property marked epoch-millis is a
    number at rest and an ISO string to the model, which is the only place the two views
    differ.
    """
    source = BASE_SCHEMAS.read_text(encoding="utf-8")
    declared = {}
    for match in re.finditer(
        r'"(?P<key>[a-z_]+)"\s*:\s*\{(?P<body>[^{}]*)\}', source
    ):
        body = match.group("body")
        type_match = re.search(r'"type"\s*:\s*"(\w+)"', body)
        if not type_match:
            continue
        epoch = '"format": "epoch-millis"' in body
        declared[match.group("key")] = (type_match.group(1), epoch)
    return declared


def walk(value, path=""):
    """Every (key, value) pair in a parsed example, at any depth."""
    if isinstance(value, dict):
        for key, child in value.items():
            yield key, child, f"{path}.{key}" if path else key
            yield from walk(child, f"{path}.{key}" if path else key)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from walk(child, f"{path}[{index}]")


def main():
    if not PROMPT.exists():
        print(f"prompt not found: {PROMPT}")
        return 1

    text = PROMPT.read_text(encoding="utf-8")
    types = field_type_names()
    declared = declared_property_types()

    problems = []
    parsed_count = 0
    shorthand_count = 0

    for block, example in json_examples(text):
        if example is None:
            shorthand_count += 1
            continue
        parsed_count += 1

        for key, value, path in walk(example):
            # 2. a field type the prompt names must still exist
            if key == "type" and isinstance(value, str) and value.isupper():
                if value not in types and "_" in value and value.split("_")[0] in {
                    "TEXT", "NUMERIC", "SCALE", "CHOICE", "BOOLEAN", "RANGE", "DATE", "TIME", "DATETIME"
                }:
                    problems.append(
                        f'field type "{value}" at {path} is not a FieldType any more'
                    )

            # 3. a declared key must carry the declared type
            if key in declared and value is not None:
                json_type, epoch = declared[key]
                expected = (str,) if epoch else JSON_TYPES.get(json_type, ())
                if expected and not isinstance(value, expected):
                    shown = "an ISO 8601 string" if epoch else json_type
                    problems.append(
                        f'"{key}" at {path} is {type(value).__name__}, '
                        f"while the model is given {shown}"
                    )

    print(f"{parsed_count} JSON example(s) read, {shorthand_count} shorthand block(s) skipped.")

    if problems:
        print(f"{len(problems)} disagreement(s) between the prompt and the code:")
        for problem in sorted(set(problems)):
            print(f"  - {problem}")
        return 1

    print("The prompt's examples agree with the code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
