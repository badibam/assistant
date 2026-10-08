package app.treelune.core.ai.validation

import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import app.treelune.core.tools.BaseSchemas
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/** One value the AI proposes, with the field that says how to show it. */
data class ProposedValue(val field: FieldDefinition, val value: Any?)

/** One entry the AI proposes to write: the values it gives, in the order of the tool's fields. */
data class ProposedEntry(val values: List<ProposedValue>)

/**
 * The entries an action of the AI writes, read into stored values and matched with their
 * fields, so that a validation request shows what is proposed and not only a sentence
 * (docs/DATA.md: a value is shown by its field type, everywhere).
 *
 * Only what the AI gives is shown: an update lists the values it changes.
 */
object ProposedEntries {

    /** The actions whose params carry entries to write. */
    private val WRITES = setOf("CREATE_DATA", "UPDATE_DATA")

    /**
     * The entries [action] writes, or an empty list for an action that writes none.
     *
     * @param tooltype The tool's type, [config] its stored config
     * @throws IllegalArgumentException on a date or a duration that does not read: the same
     *   value would fail the action itself
     */
    fun of(action: DataCommand, tooltype: String, config: JSONObject, context: Context): List<ProposedEntry> {
        if (action.type !in WRITES) return emptyList()
        val toolType = ToolTypeManager.getToolType(tooltype) ?: return emptyList()
        val s = Strings.`for`(context = context)

        val declared = toolType.getEntryFields(config, context)
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        val schema = JSONObject(BaseSchemas.getEntrySchema(toolType, config, context))
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()

        return (action.params["entries"] as? List<*>).orEmpty().map { entry ->
            val stored = JsonUtils.toMap(ModelValues.fromModel(entry, schema, zone))
            ProposedEntry(valuesOf(stored, declared, extra, s::shared))
        }
    }

    /**
     * The entries [action] writes, read with the fields of its tool as it is now: what a
     * validation request proposes, and what the chat shows an action wrote.
     *
     * @throws IllegalStateException when the tool cannot be read
     */
    suspend fun read(action: DataCommand, context: Context): List<ProposedEntry> {
        if (action.type !in WRITES || action.params["entries"] == null) return emptyList()
        val toolInstanceId = action.params["tool_instance_id"] as? String ?: action.params["id"] as? String ?: ""
        val result = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val tooltype = toolInstance?.get("tooltype") as? String
        if (!result.isSuccess || config == null || tooltype == null) {
            throw IllegalStateException("Tool $toolInstanceId not read for the entries it is written: ${result.error}")
        }
        return of(action, tooltype, config, context)
    }

    /** The values of one entry: the core's fields, then the tool type's, then the user's. */
    internal fun valuesOf(
        entry: Map<String, Any?>,
        declared: EntryFields,
        extra: List<FieldDefinition>,
        text: (String) -> String
    ): List<ProposedValue> {
        val values = mutableListOf<ProposedValue>()
        fun add(field: FieldDefinition, from: Map<*, *>?) {
            if (from != null && from.containsKey(field.name)) values.add(ProposedValue(field, from[field.name]))
        }
        if (declared.name != CoreFieldUsage.ABSENT) add(CoreFields.name(text), entry)
        if (declared.timestamp != CoreFieldUsage.ABSENT) add(CoreFields.timestamp(text), entry)
        declared.data.filterNot { it.systemWritten }.forEach { add(it.definition, entry["data"] as? Map<*, *>) }
        extra.forEach { add(it, entry["extra"] as? Map<*, *>) }
        return values
    }
}
