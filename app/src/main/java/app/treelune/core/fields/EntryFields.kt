package app.treelune.core.fields

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
 *
 * @property nameUnique Whether no two entries of a tool instance share a name, the case and the
 *   spaces around not counted: the name is what finds an entry (a sheet of structured data), and
 *   the service refuses a duplicate, naming the entry that has it
 * @property start What an entry starts as, when the entries live by a status (EntryStart)
 */
data class EntryFields(
    val name: CoreFieldUsage = CoreFieldUsage.REQUIRED,
    val nameUnique: Boolean = false,
    val timestamp: CoreFieldUsage = CoreFieldUsage.OPTIONAL,
    val data: List<FixedField> = emptyList(),
    val state: List<StateField> = emptyList(),
    val start: EntryStart? = null
)

/**
 * What an entry starts as, for a tool type whose entries live by a status in their state
 * (docs/design/entry-start-state.md): whoever creates one gives its status, among [statuses], and
 * the tool type sets the whole state from it with [state]. What the app writes itself keeps the
 * state it gives.
 *
 * With no status at all, the tool type's entries are made by the tool alone (a goal's attempts,
 * a sequence's runs) and every other creation is refused, [refusal] saying what to do instead.
 *
 * @property statuses The statuses an entry may be created with, from outside the app's own work
 * @property refusal Why a creation is refused when [statuses] is empty; null otherwise
 * @property state The whole state of an entry created with a status of [statuses], at [now]
 */
class EntryStart(
    val statuses: List<String>,
    val refusal: String? = null,
    val state: (status: String, now: Long) -> org.json.JSONObject = { status, _ -> org.json.JSONObject().put(STATUS, status) }
) {
    /** What a creation's state comes to (decide). */
    sealed interface Decision {
        /** The entry is written with [state], none when null */
        data class Write(val state: org.json.JSONObject?) : Decision
        /** Refused: no status of [start] was given, [given] being what was, if anything */
        data class NoStatus(val start: EntryStart, val given: String?) : Decision
        /** Refused: fields of the state other than the status were given, which the app writes */
        data class OtherFields(val fields: List<String>) : Decision
    }

    companion object {
        /** The key of the status in an entry's state */
        const val STATUS = "status"

        /**
         * The state of an entry created with [sent] as its state, in a tool type that declares
         * [start] (docs/design/entry-start-state.md). Without a declaration, the state goes as it
         * came. With one, an entry with no status is refused whoever writes it, nothing would ever
         * pick it up; what the app writes itself ([byTheApp]: a scheduler, the tool's own
         * operation, the demo) keeps the state it gives; any other creator, a screen, the AI, a
         * client of the MCP server, gives the status alone, among the declared ones, and the tool
         * type sets the rest.
         */
        fun decide(start: EntryStart?, sent: org.json.JSONObject?, byTheApp: Boolean, now: Long): Decision {
            val given = sent?.takeIf { it.length() > 0 }
            if (start == null) return Decision.Write(given)
            val status = given?.optString(STATUS)?.takeIf { it.isNotEmpty() }
                ?: return Decision.NoStatus(start, null)
            if (byTheApp) return Decision.Write(given)
            if (status !in start.statuses) return Decision.NoStatus(start, status)
            val others = given.keys().asSequence().filter { it != STATUS }.toList()
            if (others.isNotEmpty()) return Decision.OtherFields(others)
            return Decision.Write(start.state(status, now))
        }
    }
}

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

    /**
     * A short text naming the entry; [unique] when no two entries of a tool share it, which a
     * filter on it then compares the same way, the case and the spaces around not counted.
     */
    fun name(text: (String) -> String, unique: Boolean = false) = FieldDefinition(
        name = "name",
        displayName = text("label_name"),
        description = text(if (unique) "tools_base_schema_data_name_unique" else "tools_base_schema_data_name"),
        type = FieldType.TEXT,
        alwaysVisible = false,
        config = mapOf("length" to TextLength.SHORT.name) + (if (unique) mapOf(UNIQUE to true) else emptyMap())
    )

    /** The config key marking a unique name. */
    const val UNIQUE = "unique"

    /** A name as uniqueness compares it: the case and the spaces around not counted. */
    fun uniqueKey(name: String): String = name.trim().lowercase()

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
 * What locates an entry (id, tool_instance_id, tooltype) is described but is not a
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

        // The core's fields. An ABSENT one is not declared, and additionalProperties refuses it.
        coreField(properties, required, CoreFields.name(text), declared.name, text)?.put("minLength", 1)
        coreField(properties, required, CoreFields.timestamp(text), declared.timestamp, text)
        properties.put("created_at", FieldValueSchema.forReader(CoreFields.createdAt(text), text).put("system_managed", true))
        properties.put("updated_at", FieldValueSchema.forReader(CoreFields.updatedAt(text), text).put("system_managed", true))

        // The tool type's fields
        val dataRequired = declared.data.filter { it.required }.map { it.definition.name }
        properties.put("data", objectOf(
            fields = declared.data.associate { fixed ->
                fixed.definition.name to FieldValueSchema.forReader(fixed.definition, text).also {
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
            fields = extra.associate { it.name to FieldValueSchema.forReader(it, text) },
            required = emptyList(),
            description = text("entry_schema_extra")
        ))

        // The entry's state, and the DURATION fields running now. Written by the app, but for the
        // status an entry is created with where the tool type lets its creator give one (EntryStart)
        val start = declared.start
        val givenStatus = start?.statuses?.isNotEmpty() == true
        val state = objectOf(
            fields = declared.state.associate { field ->
                field.definition.name to FieldValueSchema.forReader(field.definition, text).also {
                    if (givenStatus && field.definition.name != EntryStart.STATUS) it.put("system_managed", true)
                }
            },
            required = emptyList(),
            description = when {
                givenStatus -> text("entry_schema_state_start").format(start!!.statuses.joinToString(", "))
                start != null -> text("entry_schema_state") + " " + start.refusal
                else -> text("entry_schema_state")
            }
        )
        runningSchema(declared, extra, text)?.let { state.getJSONObject("properties").put(RUNNING_KEY, it.put("system_managed", true)) }
        if (!givenStatus) state.put("system_managed", true)
        properties.put("state", state)

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
        usage: CoreFieldUsage,
        text: (String) -> String
    ): JSONObject? {
        if (usage == CoreFieldUsage.ABSENT) return null
        if (usage == CoreFieldUsage.REQUIRED) required.add(field.name)
        return FieldValueSchema.forReader(field, text).also { properties.put(field.name, it) }
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
