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
 * - choices: what is one level down from where the input of a thing stands (ThingBrowser), among
 *   what leads to something of `kinds`, the entries restricted to those of `tool_instances` when
 *   given: at the app, the zones, or when only such restricted entries are taken, those tools
 *   directly; in the zone `zone_id`, its tools and its variables, each with its group (null outside
 *   the zone's groups), and the zone's groups in order; in the tool `tool_instance_id`, its entries,
 *   their label holding `query` when one is given. A tool comes with its type and its zone.
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
        ReferenceKind.VARIABLE -> database.variableDao().getById(reference.id!!)?.name
    }

    private suspend fun choices(params: JSONObject): OperationResult {
        val kinds = params.optJSONArray("kinds")?.let { a -> (0 until a.length()).mapNotNull { i -> ReferenceKind.entries.firstOrNull { it.name == a.optString(i) } } }?.toSet()
            ?: return OperationResult.error(s.shared("field_validation_reference_kinds"))
        val restricted = params.optJSONArray("tool_instances")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
        val toolId = params.optString("tool_instance_id").takeIf { it.isNotEmpty() }
        val query = params.optString("query").trim().lowercase()

        val zones = database.zoneDao().getAllZones()
        val zoneNames = zones.associate { it.id to it.name }
        val tools = database.toolInstanceDao().getAllToolInstances()
        val variables = if (ReferenceKind.VARIABLE in kinds) database.variableDao().getAll() else emptyList()
        // A tool leads somewhere when it is taken itself, or when its entries are
        fun leads(tool: ToolInstance) = ReferenceKind.TOOL_INSTANCE in kinds ||
            (ReferenceKind.ENTRY in kinds && (restricted.isEmpty() || tool.id in restricted))
        fun toolRows(shown: List<ToolInstance>, groups: List<String> = emptyList()) = shown.map { tool ->
            mapOf("id" to tool.id, "name" to toolName(tool), "tooltype" to tool.tooltype, "zone_id" to tool.zone_id, "zone_name" to zoneNames[tool.zone_id],
                "group" to JSONObject(tool.config_json).optString("group").takeIf { it in groups })
        }

        return OperationResult.success(when {
            toolId != null -> {
                val tool = tools.firstOrNull { it.id == toolId }
                val entries = if (tool == null || ReferenceKind.ENTRY !in kinds || (restricted.isNotEmpty() && toolId !in restricted)) emptyList()
                else database.toolDataDao().getByToolInstance(toolId)
                    .map { it.id to entryLabel(it) }
                    .filter { (_, label) -> query.isEmpty() || label.lowercase().contains(query) }
                    .sortedBy { (_, label) -> label.lowercase() }
                mapOf("entries" to entries.map { (id, label) -> mapOf("id" to id, "name" to label) })
            }
            zoneId != null -> {
                // The zone's groups, as its screen orders them; a group it no longer has is none
                val groups = zones.firstOrNull { it.id == zoneId }?.tool_groups?.let { json ->
                    JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } }
                } ?: emptyList()
                mapOf(
                    "groups" to groups,
                    "tool_instances" to toolRows(tools.filter { it.zone_id == zoneId && leads(it) }, groups),
                    "variables" to variables.filter { it.zoneId == zoneId }.map { variable ->
                        mapOf("id" to variable.id, "name" to variable.name, "group" to variable.group?.takeIf { it in groups })
                    }
                )
            }
            // Only the entries of some tools taken: those tools, wherever they are
            kinds == setOf(ReferenceKind.ENTRY) && restricted.isNotEmpty() -> mapOf("tool_instances" to toolRows(tools.filter { it.id in restricted }))
            else -> mapOf("zones" to zones
                .filter { zone -> ReferenceKind.ZONE in kinds || tools.any { it.zone_id == zone.id && leads(it) } || variables.any { it.zoneId == zone.id } }
                .map { mapOf("id" to it.id, "name" to it.name) })
        })
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
