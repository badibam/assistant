package com.assistant.core.fields.settings

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
        FieldValueSchema.forReader(field.definition, text).also { schema ->
            field.default?.let { schema.put("default", it) }
            if (field.secret) schema.put(SECRET, true)
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

    /** The nodes stored in one object: sections opened, since they store nothing of their own. */
    internal fun flatten(nodes: List<SettingNode>): List<SettingNode> = nodes.flatMap { node ->
        if (node is SettingNode.Section) flatten(node.nodes) else listOf(node)
    }
}
