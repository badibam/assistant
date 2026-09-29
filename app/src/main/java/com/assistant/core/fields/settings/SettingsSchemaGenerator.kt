package com.assistant.core.fields.settings

import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FieldValueSchema
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JSON schema of a config, generated from its declaration, so that no config schema is
 * written by hand.
 *
 * Each shape turns into the JSON Schema construct it stands for: a field into its value's schema
 * (FieldValueSchema.forReader, with its default), a group into an object, a list into an array,
 * a variant into a oneOf whose branches each hold the whole object for one option. Sections
 * vanish: they only group settings on the screen.
 */
object SettingsSchemaGenerator {

    /** Custom keyword marking a secret setting: never sent to the AI, never logged. */
    const val SECRET = "secret"

    /**
     * Custom keyword marking a list of field definitions: what the model reads of it is spelled
     * out once in the prompt, not in every schema that holds one (SchemaNotation).
     */
    const val FIELD_DEFINITIONS = "field_definitions"

    /**
     * @param nodes The declaration
     * @param text The shared string of a key (s::shared), for what the schema says of a value
     * @throws IllegalStateException when two settings of one object share a name: the one
     *   declared last would silently replace the other
     */
    fun generate(nodes: List<SettingNode>, text: (String) -> String): JSONObject = objectOf(nodes, text)

    private fun objectOf(nodes: List<SettingNode>, text: (String) -> String): JSONObject {
        val flat = flatten(nodes)
        val variant = flat.filterIsInstance<SettingNode.Variant>().firstOrNull()
            ?: return plainObject(flat, text)

        // One branch per option: the object as it is when that option is chosen, the selector
        // held to it. Another variant among the nodes branches again inside each.
        val others = flat - variant
        val branches = JSONArray()
        variant.cases.forEach { (option, caseNodes) ->
            val selector = variant.selector.copy(required = true)
            val branch = objectOf(others + selector + caseNodes, text)
            branch.getJSONObject("properties").getJSONObject(selector.definition.name).put("const", option)
            branches.put(branch)
        }
        return JSONObject().put("type", "object").put("oneOf", branches)
    }

    private fun plainObject(nodes: List<SettingNode>, text: (String) -> String): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()

        nodes.forEach { node ->
            val (name, isRequired, schema) = when (node) {
                is SettingNode.Field -> Triple(node.definition.name, node.required, fieldSchema(node, text))
                is SettingNode.Group -> Triple(node.name, node.required, objectOf(node.nodes, text).put("title", node.label))
                is SettingNode.ListOf -> Triple(node.name, node.required, listSchema(node, text))
                is SettingNode.Condition -> Triple(node.name, node.required, conditionSchema(node, text))
                is SettingNode.Term -> Triple(node.name, node.required, termSchema(node, text))
                is SettingNode.Selection -> Triple(node.name, node.required, selectionSchema(node, text))
                is SettingNode.Period -> Triple(node.name, node.required, periodSchema(node, text))
                is SettingNode.Variant, is SettingNode.Section -> error("flattened before")
            }
            check(!properties.has(name)) { "Setting '$name' is declared twice in one object" }
            properties.put(name, schema)
            if (isRequired) required.put(name)
        }

        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("additionalProperties", false)
            .also { if (required.length() > 0) it.put("required", required) }
    }

    private fun fieldSchema(field: SettingNode.Field, text: (String) -> String): JSONObject =
        valueOfDefinedSchema(field)
            ?: FieldValueSchema.forReader(field.definition, text).also { schema ->
            field.default?.let { schema.put("default", it) }
            if (field.secret) schema.put(SECRET, true)
        }

    /**
     * A value of the field being defined, of its type whatever its config: a choice's options are
     * set beside it, so either shape of choice is taken here and the service checks the value
     * against the field itself (FieldConfigValidator).
     */
    private fun valueOfDefinedSchema(field: SettingNode.Field): JSONObject? {
        if (!field.valueOfDefined) return null
        val schema = if (field.definition.type == FieldType.CHOICE) {
            // One option, or a list of them for a multiple choice or a ranking
            JSONObject().put("type", org.json.JSONArray().put("string").put("array"))
        } else {
            // A number's decimals are a precision rounded at the write, which its schema does
            // not hold: any will do to write it
            val decimals = if (field.definition.type == FieldType.NUMERIC || field.definition.type == FieldType.RANGE) mapOf("decimals" to 0) else null
            FieldValueSchema.of(field.definition.copy(description = null, config = decimals))
        }
        schema.put("title", field.definition.displayName)
        field.definition.description?.let { schema.put("description", it) }
        return schema
    }

    private fun listSchema(list: SettingNode.ListOf, text: (String) -> String): JSONObject {
        val items = when (val item = list.item) {
            is SettingNode.Item.Of -> objectOf(item.nodes, text)
            is SettingNode.Item.Value -> FieldValueSchema.forReader(item.definition, text)
        }
        return JSONObject()
            .put("type", "array")
            .put("title", list.label)
            .put("items", items)
            .also {
                if (list.minItems > 0) it.put("minItems", list.minItems)
                if (list.distinct) it.put("uniqueItems", true)
                if (list.fieldDefinitions) it.put(FIELD_DEFINITIONS, true)
            }
    }

    /**
     * A condition: its operator among the known ones, each side an object the service reads as a
     * field or a term (Condition.fromJson), which the description spells out.
     */
    private fun conditionSchema(node: SettingNode.Condition, text: (String) -> String): JSONObject = JSONObject()
        .put("type", "object")
        .put("title", node.label)
        .put("description", text(when {
            node.onRow -> "condition_schema_row_description"
            node.enteredField != null -> "condition_schema_entered_description"
            else -> "condition_schema_description"
        }))
        .put("properties", JSONObject()
            .put("left", JSONObject().put("type", "object"))
            .put("op", JSONObject().put("type", "string").put("enum", JSONArray(com.assistant.core.fields.FilterOperator.entries.map { it.key })))
            .put("right", JSONObject().put("type", JSONArray().put("object").put("array"))))
        .put("required", JSONArray().put("op"))
        .put("additionalProperties", false)

    /**
     * A term: one key, the kind among those offered, its value as the brick stores it (Term);
     * the description spells out each kind's form.
     */
    private fun termSchema(node: SettingNode.Term, text: (String) -> String): JSONObject {
        val properties = JSONObject()
        val forms = node.kinds.sortedBy { it.ordinal }.map { kind ->
            properties.put(kind.key, when (kind) {
                com.assistant.core.terms.Term.Kind.CONSTANT -> JSONObject().put("type", JSONArray().put("number").put("string").put("boolean").put("array"))
                com.assistant.core.terms.Term.Kind.VARIABLE -> JSONObject().put("type", "string")
                com.assistant.core.terms.Term.Kind.READING -> JSONObject().put("type", "object")
            })
            text("term_schema_${kind.key}")
        }
        return JSONObject()
            .put("type", "object")
            .put("title", node.label)
            .put("description", text("term_schema_description").format(forms.joinToString(" ; ")))
            .put("properties", properties)
            .put("additionalProperties", false)
    }

    /** Entries of one tool, without a period: its target, its filters, the fields kept. */
    private fun selectionSchema(node: SettingNode.Selection, text: (String) -> String): JSONObject = JSONObject()
        .put("type", "object")
        .put("title", node.label)
        .put("description", text("selection_schema_description"))
        .put("properties", JSONObject()
            .put("target", JSONObject().put("type", "object"))
            .put("filters", JSONObject().put("type", "array").put("items", JSONObject().put("type", "object")))
            .put("fields", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
        .put("required", JSONArray().put("target"))
        .put("additionalProperties", false)

    /**
     * A period: each bound a relative date, an object, or a fixed instant, which the model writes
     * in ISO 8601 like every instant (FieldValueSchema.EPOCH_MILLIS).
     */
    private fun periodSchema(node: SettingNode.Period, text: (String) -> String): JSONObject {
        fun bound() = JSONObject().put("type", JSONArray().put("object").put("integer")).put("format", FieldValueSchema.EPOCH_MILLIS)
        return JSONObject()
            .put("type", "object")
            .put("title", node.label)
            .put("description", text("period_schema_description"))
            .put("properties", JSONObject().put("start", bound()).put("end", bound()))
            .put("additionalProperties", false)
    }

    /** The nodes stored in one object: sections opened, since they store nothing of their own. */
    internal fun flatten(nodes: List<SettingNode>): List<SettingNode> = nodes.flatMap { node ->
        if (node is SettingNode.Section) flatten(node.nodes) else listOf(node)
    }
}
