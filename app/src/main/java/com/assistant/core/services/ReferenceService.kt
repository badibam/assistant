package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DateUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a reference designates, read in the database: the core's service for the things a
 * REFERENCE field or a selection points to (docs/DATA.md).
 *
 * - names: the current name of each reference in `references` ([{kind, id}]), or that it was
 *   deleted: the screen and the AI show a reference by it, never by a stored name.
 * - choices: what a reference of `kinds` may designate, for its input: the zones, the tool
 *   instances (with their zone), and the entries of the tool instances `tool_instances`, or of the
 *   one `tool_instance_id` browsed, their label holding `query` when one is given.
 */
class ReferenceService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database = AppDatabase.getDatabase(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "names" -> names(params)
            "choices" -> choices(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun names(params: JSONObject): OperationResult {
        val array = params.optJSONArray("references")
            ?: return OperationResult.error(s.shared("service_error_references_missing"))
        val references = (0 until array.length()).map { i ->
            ReferenceTarget.referenceOf(array.opt(i))
                ?: return OperationResult.error(s.shared("field_value_reference_invalid").format(array.opt(i).toString()))
        }
        return OperationResult.success(mapOf("references" to references.distinct().map { reference ->
            val name = nameOf(reference)
            mapOf("kind" to reference.kind.name, "id" to reference.id, "name" to name, "deleted" to (name == null))
        }))
    }

    /** The current name of what [reference] designates, null when it no longer exists. */
    private suspend fun nameOf(reference: Reference): String? = when (reference.kind) {
        ReferenceKind.APP -> s.shared("pointer_level_app")
        ReferenceKind.ZONE -> database.zoneDao().getZoneById(reference.id!!)?.name
        ReferenceKind.TOOL_INSTANCE -> database.toolInstanceDao().getToolInstanceById(reference.id!!)?.let { toolName(it) }
        ReferenceKind.ENTRY -> database.toolDataDao().getById(reference.id!!)?.let { entryLabel(it) }
    }

    private suspend fun choices(params: JSONObject): OperationResult {
        val kinds = params.optJSONArray("kinds")?.let { a -> (0 until a.length()).mapNotNull { i -> ReferenceKind.entries.firstOrNull { it.name == a.optString(i) } } }
            ?: return OperationResult.error(s.shared("field_validation_reference_kinds"))
        val restricted = params.optJSONArray("tool_instances")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val browsed = params.optString("tool_instance_id").takeIf { it.isNotEmpty() }
        val query = params.optString("query").trim().lowercase()

        val zones = database.zoneDao().getAllZones()
        val zoneNames = zones.associate { it.id to it.name }
        val tools = database.toolInstanceDao().getAllToolInstances()
        val result = mutableMapOf<String, Any>()

        if (ReferenceKind.ZONE in kinds) {
            result["zones"] = zones.map { mapOf("id" to it.id, "name" to it.name) }
        }
        // The tools, to designate one, or to browse to the entries of one when any entry is taken
        if (ReferenceKind.TOOL_INSTANCE in kinds || (ReferenceKind.ENTRY in kinds && restricted.isEmpty())) {
            result["tool_instances"] = tools.map { tool ->
                mapOf("id" to tool.id, "name" to toolName(tool), "zone_name" to zoneNames[tool.zone_id], "tooltype" to tool.tooltype)
            }
        }
        if (ReferenceKind.ENTRY in kinds) {
            val shown = if (restricted.isNotEmpty()) restricted else listOfNotNull(browsed)
            val byId = tools.associateBy { it.id }
            result["entries"] = shown.mapNotNull { id -> byId[id] }.map { tool ->
                val entries = database.toolDataDao().getByToolInstance(tool.id)
                    .map { it.id to entryLabel(it) }
                    .filter { (_, label) -> query.isEmpty() || label.lowercase().contains(query) }
                    .sortedBy { (_, label) -> label.lowercase() }
                mapOf(
                    "tool_instance_id" to tool.id,
                    "tool_name" to toolName(tool),
                    "entries" to entries.map { (id, label) -> mapOf("id" to id, "name" to label) }
                )
            }
        }
        return OperationResult.success(result)
    }

    private fun toolName(tool: ToolInstance): String = JSONObject(tool.config_json).optString("name")

    /** An entry as a reference shows it: its name, or when it has none, when it was recorded. */
    private fun entryLabel(entry: ToolDataEntity): String = entry.name?.takeIf { it.isNotBlank() }
        ?: entry.timestamp?.let { DateUtils.formatFullDateTime(it) }
        ?: DateUtils.formatFullDateTime(entry.createdAt)

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "names" -> s.shared("action_verbalize_references_names")
            "choices" -> s.shared("action_verbalize_references_choices")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
