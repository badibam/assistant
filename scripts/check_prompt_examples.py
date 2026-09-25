#!/usr/bin/env python3
"""Check the examples in the L1 prompt against the code they describe.

The prompt is the contract between the app and the model, and it is the only one in the
project that is neither compiled nor tested: it lives in the strings system, as prose. So a
promise it makes can stop being true without anything saying so. That has happened twice --
it announced an ISO timestamp while the schema declared a number, and it still taught a
field type that a migration removed.

This reads the prompt's JSON examples and asks five questions of them:

  1. does every example parse as JSON;
  2. does every custom field type it names still exist in FieldType;
  3. does every key the base config schema or the core's entry fields declare carry a value of
     the declared type;
  4. does every object carry the fields its schema requires;
  5. does every icon it names exist -- the icon index is the app's, so a name the model would
     copy from an example is a name the app accepts.

Question 3 compares against the shape the *model* is given, not the stored one. A property
the schema marks `"format": "epoch-millis"` is stored as a number and handed to the model as
an ISO 8601 string -- that is what SchemaModelView does at runtime, and the rule is repeated
here because a Python script cannot call it. If that rule ever changes, this line changes
with it.

Question 4 is the one an example most easily fails while looking right: a CREATE_DATA example
without `name` read fine and could not be executed as written. It reads the `required` lists
of the schemas an example's objects answer to, and needs whole examples to know which object
is which -- a data entry is only a data entry inside CREATE_DATA -- so it works on the fenced
```json blocks rather than on loose braces. The objects it knows:

  - a response (pre_text and the rest): the AI message schema;
  - a communication module's data: that module's schema;
  - a CREATE_DATA entry: the keys every entry schema requires, whatever its tool (the list
    EntrySchemaGenerator starts from), where a key the command's params carry counts as
    present, since the service copies tool_instance_id and tooltype into every entry;
  - a CREATE_TOOL config: the base config schema;
  - a custom field definition: its field type's schema, and that of its config.

A schema's `required` is read from the Kotlin source: the least indented list in the function
that builds it is the top level's, and a field type's config has its own, inside the braces
of its `config` property.

Not everything is covered. Some of the prompt's brace-delimited blocks are shorthand rather
than JSON (`{ name?, timestamp?, data }`), and three fenced examples elide part of themselves
with "..."; both are skipped: they describe a shape in prose, so there is nothing to parse.
The tooltype-specific schemas (what a tracking entry's `data` must hold) are not read either:
they are assembled at runtime from each tool's code. What is checked is what can be.

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
ENTRY_FIELDS = ROOT / "app/src/main/java/com/assistant/core/fields/EntryFields.kt"
MESSAGE_SCHEMAS = ROOT / "app/src/main/java/com/assistant/core/ai/data/AIMessageSchemas.kt"
MODULE_SCHEMAS = ROOT / "app/src/main/java/com/assistant/core/ai/data/CommunicationModuleSchemas.kt"
FIELD_SCHEMAS = ROOT / "app/src/main/java/com/assistant/core/fields/FieldTypeSchemaProvider.kt"
ICON_INDEX = ROOT / "app/src/main/assets/icons/index.json"

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
                .replace("&gt;", ">").replace("&amp;", "&").replace("\\'", "'"))


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


# A field type of the core's entry fields, against the JSON type its value schema declares.
CORE_FIELD_TYPES = {"TEXT": "string", "DATETIME": "number"}


def core_field_types():
    """What the core's entry fields (CoreFields) hold: {key: (json_type, seen_as_iso_string)}.

    Their schema is generated (FieldValueSchema), so it is read from their declaration: a
    DATETIME is epoch-millis at rest and ISO to the model.
    """
    source = ENTRY_FIELDS.read_text(encoding="utf-8")
    declared = {}
    for name, field_type in re.findall(r'name = "(\w+)",.*?type = FieldType\.(\w+)', source, re.S):
        if field_type in CORE_FIELD_TYPES:
            declared[name] = (CORE_FIELD_TYPES[field_type], field_type == "DATETIME")
    return declared


def entry_required():
    """The keys every entry schema requires, whatever its tool."""
    source = ENTRY_FIELDS.read_text(encoding="utf-8")
    match = re.search(r"val required = mutableListOf\(([^)]*)\)", source)
    if not match:
        raise LookupError(f"the required list of the entry schema not found in {ENTRY_FIELDS.name}")
    return re.findall(r'"(\w+)"', match.group(1))


def declared_property_types():
    """What the base config schema says each of its properties holds, as the model sees it.

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


def function_body(path, name):
    """The source of the Kotlin function `name`, up to the next function of the file."""
    source = path.read_text(encoding="utf-8")
    start = re.search(rf"\bfun {name}\(", source)
    if not start:
        raise LookupError(f"fun {name} not found in {path.name}")
    end = re.search(r"\n\s*(?:private |internal |override )?fun ", source[start.end():])
    return source[start.start():start.end() + end.start()] if end else source[start.start():]


def required_lists(path, name):
    """The `required` lists of the schema built by `name`, least indented first."""
    found = []
    for match in re.finditer(r'^(\s*)"required"\s*:\s*\[([^\]]*)\]', function_body(path, name), re.M):
        keys = re.findall(r'"(\w+)"', match.group(2))
        found.append((len(match.group(1)), keys))
    return [keys for _, keys in sorted(found, key=lambda item: item[0])]


def property_block(body, key):
    """The braces of the property `key` in a schema's source, or "" when it has none."""
    start = re.search(rf'"{key}"\s*:\s*\{{', body)
    if not start:
        return ""
    depth = 0
    for index in range(start.end() - 1, len(body)):
        depth += {"{": 1, "}": -1}.get(body[index], 0)
        if depth == 0:
            return body[start.end() - 1:index + 1]
    return ""


def field_type_schemas():
    """For each field type, (its required keys, its config's required keys)."""
    source = FIELD_SCHEMAS.read_text(encoding="utf-8")
    schemas = {}
    for field_type, builder in re.findall(r"FieldType\.(\w+) -> create(\w+)Schema\(", source):
        name = f"build{builder}SchemaJson"
        config = re.search(r'"required"\s*:\s*\[([^\]]*)\]', property_block(function_body(FIELD_SCHEMAS, name), "config"))
        schemas[field_type] = (
            required_lists(FIELD_SCHEMAS, name)[0],
            re.findall(r'"(\w+)"', config.group(1)) if config else [],
        )
    return schemas


def fenced_examples(text):
    """Every fenced ```json block of the prompt, whole, parsed -- or None when it elides.

    A block that starts with a key is a fragment of an object, and is read as one.
    """
    for block in re.findall(r"```json\n(.*?)```", text, re.S):
        candidate = block.strip()
        if candidate.startswith('"'):
            candidate = "{" + candidate + "}"
        try:
            yield json.loads(candidate)
        except ValueError:
            yield None


def missing(obj, required, supplied=()):
    """The required keys `obj` lacks, counting those `supplied` from elsewhere as present."""
    return [key for key in required if key not in obj and key not in supplied]


def required_problems(example, schemas):
    """Every object of one whole example that lacks a field its schema requires."""
    problems = []
    response_required, module_required, data_required, config_required, field_types = schemas

    def at(path, key):
        return f"{path}.{key}" if path else key

    def report(path, what, keys):
        problems.append(f"{what} at {path or 'top level'} lacks required {', '.join(keys)}")

    def visit(value, path):
        if isinstance(value, list):
            for index, child in enumerate(value):
                visit(child, f"{path}[{index}]")
            return
        if not isinstance(value, dict):
            return

        response_keys = {"data_commands", "action_commands", "communication_module", "post_text"}
        if "pre_text" in value or response_keys & value.keys():
            keys = missing(value, response_required)
            if keys:
                report(path, "response", keys)

        module = value.get("communication_module")
        if isinstance(module, dict) and module.get("type") in module_required:
            keys = missing(module.get("data", {}), module_required[module["type"]])
            if keys:
                report(at(path, "communication_module.data"), f"{module['type']} module", keys)

        params = value.get("params")
        if isinstance(params, dict):
            if value.get("type") == "CREATE_DATA":
                for index, entry in enumerate(params.get("entries", [])):
                    keys = missing(entry, data_required, supplied=params.keys())
                    if keys:
                        report(at(path, f"params.entries[{index}]"), "CREATE_DATA entry", keys)
            if value.get("type") == "CREATE_TOOL" and isinstance(params.get("config"), dict):
                keys = missing(params["config"], config_required)
                if keys:
                    report(at(path, "params.config"), "CREATE_TOOL config", keys)

        field_type = value.get("type")
        if "display_name" in value and field_type in field_types:
            own, config = field_types[field_type]
            keys = missing(value, own)
            if keys:
                report(path, f"{field_type} field", keys)
            if isinstance(value.get("config"), dict):
                keys = missing(value["config"], config)
                if keys:
                    report(at(path, "config"), f"{field_type} field config", keys)

        for key, child in value.items():
            visit(child, at(path, key))

    visit(example, "")
    return problems


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
    declared = {**declared_property_types(), **core_field_types()}
    icon_names = {icon["name"] for icon in json.loads(ICON_INDEX.read_text(encoding="utf-8"))["icons"]}

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

            # 5. an icon the prompt names must exist
            if key == "icon_name" and isinstance(value, str) and value not in icon_names:
                problems.append(f'icon "{value}" at {path} is not in the icon index')

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

    # 4. every object carries what its schema requires
    schemas = (
        required_lists(MESSAGE_SCHEMAS, "getAIMessageResponseSchemaContent")[0],
        {module: required_lists(MODULE_SCHEMAS, f"get{module}Schema")[0]
         for module in ("MultipleChoice", "Validation")},
        entry_required(),
        required_lists(BASE_SCHEMAS, "getBaseConfigSchema")[0],
        field_type_schemas(),
    )
    whole_count = 0
    elided_count = 0
    for example in fenced_examples(unescape(text)):
        if example is None:
            elided_count += 1
            continue
        whole_count += 1
        problems.extend(required_problems(example, schemas))

    print(f"{parsed_count} JSON example(s) read, {shorthand_count} shorthand block(s) skipped.")
    print(f"{whole_count} whole example(s) read for required fields, {elided_count} elided one(s) skipped.")

    if problems:
        print(f"{len(problems)} disagreement(s) between the prompt and the code:")
        for problem in sorted(set(problems)):
            print(f"  - {problem}")
        return 1

    print("The prompt's examples agree with the code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
