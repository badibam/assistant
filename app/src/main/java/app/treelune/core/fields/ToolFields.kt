package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.JsonUtils

/** The fields of a tool instance's entries, read from its current config through tools.get. */
object ToolFields {

    /**
     * The fields the entries of [toolInstanceId] can be filtered and read on, by path
     * (EntryFilters.filterableFields).
     *
     * @throws IllegalStateException when the tool cannot be read
     */
    suspend fun filterable(toolInstanceId: String, context: Context, s: StringsContext): Map<String, FieldDefinition> {
        val result = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
        }
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        return EntryFilters.filterableFields(toolType.getEntryFields(config, context), extra) { s.shared(it) }
    }
}
