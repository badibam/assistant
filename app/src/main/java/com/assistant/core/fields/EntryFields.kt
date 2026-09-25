package com.assistant.core.fields

import org.json.JSONArray
import org.json.JSONObject

/**
 * How a tool type uses one of the fields the core declares for every entry, name or timestamp.
 *
 * ABSENT means no form shows it, the AI is not told of it, its column stays empty, and a value
 * sent for it is refused: a name nobody gives would be invented, and so would a date filled in
 * on the entry's behalf.
 */
enum class CoreFieldUsage {
    REQUIRED,
    OPTIONAL,
    ABSENT
}

/**
 * A field a tool type declares in the "data" object of its entries.
 *
 * Fixed: it is neither removed nor renamed by the user, who adds fields of their own in "extra".
 *
 * @property definition The field, described with the same types as a user's field
 * @property required Whether an entry must have a value for it
 * @property systemWritten Whether the app writes it and no caller does, such as a copy of the
 *           config an entry must keep saying (a message's title as it was sent)
 */
data class FixedField(
    val definition: FieldDefinition,
    val required: Boolean = false,
    val systemWritten: Boolean = false
)

/**
 * A field of an entry's "state": what the app and its actions produce on an entry (read,
 * archived, a message's status), described with the field types for its labels and filters,
 * and never entered in a form.
 *
 * @property definition The field; its config gives the labels ("Read" / "Unread")
 * @property filterable Whether it is offered as a filter: read yes, a manual position no
 */
data class StateField(
    val definition: FieldDefinition,
    val filterable: Boolean
)

/**
 * Everything a tool type declares about the fields of its entries, sorted by who writes them:
 * the core's name and timestamp, stored in their columns; the tool type's fixed fields in
 * "data"; the entry's state in "state". The user's own fields, in "extra", come from the tool
 * instance's config and are not part of the declaration.
 */
data class EntryFields(
    val name: CoreFieldUsage = CoreFieldUsage.REQUIRED,
    val timestamp: CoreFieldUsage = CoreFieldUsage.OPTIONAL,
    val data: List<FixedField> = emptyList(),
    val state: List<StateField> = emptyList()
)

/**
 * The fields the core declares for every entry, described with the vocabulary of the fields so
 * that they are entered, shown, checked and described to the AI like any other.
 *
 * They stay in columns shared by every tool: the history sorts by date, the pointer filters by
 * period, and the timestamp is indexed.
 *
 * Each takes the shared string of a key (s::shared) for its label and description.
 */
object CoreFields {

    /** A short text naming the entry. */
    fun name(text: (String) -> String) = FieldDefinition(
        name = "name",
        displayName = text("label_name"),
        description = text("tools_base_schema_data_name"),
        type = FieldType.TEXT,
        alwaysVisible = false,
        config = mapOf("length" to TextLength.SHORT.name)
    )

    /** The moment the entry is about, which can be set in the past. */
    fun timestamp(text: (String) -> String) = FieldDefinition(
        name = "timestamp",
        displayName = text("label_date"),
        description = text("tools_base_schema_data_timestamp"),
        type = FieldType.DATETIME,
        alwaysVisible = false,
        config = null
    )

    /** When the entry was created, written by the system. */
    fun createdAt(text: (String) -> String) = FieldDefinition(
        name = "created_at",
        displayName = text("label_created_at"),
        description = text("tools_base_schema_data_created_at"),
        type = FieldType.DATETIME,
        alwaysVisible = false,
        config = null
    )

    /** When the entry was last changed, written by the system. */
    fun updatedAt(text: (String) -> String) = FieldDefinition(
        name = "updated_at",
        displayName = text("label_updated_at"),
        description = text("tools_base_schema_data_updated_at"),
        type = FieldType.DATETIME,
        alwaysVisible = false,
        config = null
    )
}

/**
 * The data schema of a tool type's entries, generated from its declaration and the user's
 * fields, so that no tool type writes an entry schema by hand.
 *
 * What locates an entry (id, tool_instance_id, tooltype, schema_id) is described but is not a
 * field. Everything else is: the core's fields at the root, then "data", "extra" and "state".
 */
object EntrySchemaGenerator {

    /**
     * The key in "state" under which a running DURATION field keeps the instant it started,
     * as state.running.<data|extra>.<field>: by where the field lives, since a fixed field and
     * a user's field may share a name.
     */
    const val RUNNING_KEY = "running"

    /**
     * @param declared What the tool type declares
     * @param extra The user's fields, from the tool instance's config
     * @param text The shared string of a key (s::shared), for the descriptions the AI reads
     * @return The JSON schema of one entry
     */
    fun generate(declared: EntryFields, extra: List<FieldDefinition>, text: (String) -> String): String {
        val properties = JSONObject()
        val required = mutableListOf("tool_instance_id", "tooltype")

        // Locators: described so the AI can read them, written by the system
        properties.put("id", locator(text("tools_base_schema_data_id"), systemManaged = false))
        properties.put("tool_instance_id", locator(text("tools_base_schema_data_tool_instance_id"), systemManaged = false))
        properties.put("tooltype", locator(text("tools_base_schema_data_tooltype"), systemManaged = true))
        properties.put("schema_id", locator(text("tools_base_schema_data_schema_id"), systemManaged = true))

        // The core's fields. An ABSENT one is not declared, and additionalProperties refuses it.
        coreField(properties, required, CoreFields.name(text), declared.name)?.put("minLength", 1)
        coreField(properties, required, CoreFields.timestamp(text), declared.timestamp)
        properties.put("created_at", FieldValueSchema.of(CoreFields.createdAt(text)).put("system_managed", true))
        properties.put("updated_at", FieldValueSchema.of(CoreFields.updatedAt(text)).put("system_managed", true))

        // The tool type's fields
        val dataRequired = declared.data.filter { it.required }.map { it.definition.name }
        properties.put("data", objectOf(
            fields = declared.data.associate { fixed ->
                fixed.definition.name to FieldValueSchema.of(fixed.definition).also {
                    if (fixed.systemWritten) it.put("system_managed", true)
                    // A required text is a text with something in it: an empty one is no answer
                    if (fixed.required && fixed.definition.type == FieldType.TEXT) it.put("minLength", 1)
                }
            },
            required = dataRequired,
            description = text("tools_base_schema_data_data")
        ))
        if (dataRequired.isNotEmpty()) required.add("data")

        // The user's fields, all optional
        properties.put("extra", objectOf(
            fields = extra.associate { it.name to FieldValueSchema.of(it) },
            required = emptyList(),
            description = text("entry_schema_extra")
        ))

        // The entry's state, and the DURATION fields running now
        val state = objectOf(
            fields = declared.state.associate { it.definition.name to FieldValueSchema.of(it.definition) },
            required = emptyList(),
            description = text("entry_schema_state")
        )
        runningSchema(declared, extra, text)?.let { state.getJSONObject("properties").put(RUNNING_KEY, it) }
        properties.put("state", state.put("system_managed", true))

        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
            .put("additionalProperties", false)
            .toString()
    }

    /** Declares a core field at the root as its usage says, and returns its schema if declared. */
    private fun coreField(
        properties: JSONObject,
        required: MutableList<String>,
        field: FieldDefinition,
        usage: CoreFieldUsage
    ): JSONObject? {
        if (usage == CoreFieldUsage.ABSENT) return null
        if (usage == CoreFieldUsage.REQUIRED) required.add(field.name)
        return FieldValueSchema.of(field).also { properties.put(field.name, it) }
    }

    private fun locator(description: String, systemManaged: Boolean): JSONObject =
        JSONObject().put("type", "string").put("description", description).also {
            if (systemManaged) it.put("system_managed", true)
        }

    private fun objectOf(fields: Map<String, JSONObject>, required: List<String>, description: String): JSONObject {
        val properties = JSONObject()
        fields.forEach { (name, schema) -> properties.put(name, schema) }
        return JSONObject()
            .put("type", "object")
            .put("description", description)
            .put("properties", properties)
            .put("additionalProperties", false)
            .also { if (required.isNotEmpty()) it.put("required", JSONArray(required)) }
    }

    /**
     * The schema of the start instants of the DURATION fields running now, by where the field
     * lives and its name, or null when the entry has no DURATION field.
     */
    private fun runningSchema(declared: EntryFields, extra: List<FieldDefinition>, text: (String) -> String): JSONObject? {
        val byContainer = mapOf(
            "data" to declared.data.map { it.definition }.filter { it.type == FieldType.DURATION },
            "extra" to extra.filter { it.type == FieldType.DURATION }
        ).filterValues { it.isNotEmpty() }
        if (byContainer.isEmpty()) return null

        val containers = JSONObject()
        byContainer.forEach { (container, fields) ->
            containers.put(container, objectOf(
                fields = fields.associate { field ->
                    field.name to JSONObject()
                        .put("type", "integer")
                        .put("minimum", 0)
                        .put("format", FieldValueSchema.EPOCH_MILLIS)
                },
                required = emptyList(),
                description = text("entry_schema_running_container").format(container)
            ))
        }
        return JSONObject()
            .put("type", "object")
            .put("description", text("entry_schema_running"))
            .put("properties", containers)
            .put("additionalProperties", false)
    }
}
