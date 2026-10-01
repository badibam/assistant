package com.assistant.core.services

import android.content.Context
import com.assistant.core.ai.database.getById
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import androidx.room.withTransaction
import com.assistant.core.fields.CoreFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.ToolFields
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.fields.toJson
import com.assistant.core.imports.ColumnTarget
import com.assistant.core.imports.CsvReader
import com.assistant.core.imports.ImportPlanner
import com.assistant.core.imports.ImportTable
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * The core's import (docs/DATA.md, « Import »), into any tool, the same for the screen and, later,
 * the AI and an API: lines of text values named by their column.
 *
 * The lines come from `csv`, the file's text, or from `file`, the id of a file joined to a message
 * (the files service), which the AI imports from without reading its lines.
 *
 * - detect: `tool_instance_id`, `csv` or `file`: a declaration to confirm, column by column (target, type,
 *   writing, an example read), with the ambiguities to settle and the lines that would be refused
 * - apply: `tool_instance_id`, `csv` or `file`, `columns` (a complete declaration): the new fields created
 *   in the order of the columns, then each line written — an entry found by its key updated, the
 *   others created; a cell that does not read, or a line the service refuses, refuses that line
 *   alone. Refused whole: an incomplete declaration, naming what is missing, a key twice in the file.
 */
class ImportService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val toolInstanceId = params.optString("tool_instance_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        val text = params.optString("file").takeIf { it.isNotEmpty() }?.let { fileId ->
            AppDatabase.getDatabase(context).attachedFileDao().getById(fileId)?.content
                ?: return OperationResult.error(s.shared("file_error_not_found").format(fileId))
        } ?: params.optString("csv")
        val table = try {
            CsvReader.read(text) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(s.shared("import_error_file").format(e.message ?: ""))
        }
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = tool.data?.get("tool_instance") as? Map<*, *> ?: return OperationResult.error(tool.error ?: s.shared("service_error_tool_instance_not_found"))
        @Suppress("UNCHECKED_CAST")
        val config = JsonUtils.toJSONObject(toolInstance["config"] as Map<String, Any?>)
        val toolType = ToolTypeManager.getToolType(toolInstance["tooltype"] as String)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))
        val uniqueName = toolType.getEntryFields(config, context).nameUnique
        // What a column may fill: the name, the date, the tool type's fields and the user's
        val fields = ToolFields.filterable(toolInstanceId, context, s).filterKeys { !it.startsWith("state.") && it != "created_at" && it != "updated_at" }
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()

        return when (operation) {
            "detect" -> OperationResult.success(mapOf(
                "columns" to ImportPlanner.detect(table, fields, uniqueName, zone).map { proposal ->
                    JsonUtils.toMap(proposal.declaration.toJson()) + mapOf(
                        "ambiguous" to proposal.ambiguous.map { it.name },
                        "example" to proposal.example?.let { (cell, read) -> mapOf("cell" to cell, "read" to read) },
                        "unreadable_lines" to proposal.unreadable
                    )
                },
                "lines" to table.rows.size
            ))
            "apply" -> apply(toolInstanceId, config, table, params.optJSONArray("columns") ?: JSONArray(), fields, uniqueName, zone)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun apply(
        toolInstanceId: String,
        config: JSONObject,
        table: ImportTable,
        columns: JSONArray,
        fields: Map<String, FieldDefinition>,
        uniqueName: Boolean,
        zone: java.time.ZoneId
    ): OperationResult {
        val declarations = try {
            ImportPlanner.declarationsOf(columns)
        } catch (e: Exception) {
            return OperationResult.error(s.shared("import_error_declaration").format(e.message ?: ""))
        }
        ImportPlanner.missing(table, declarations, fields, uniqueName) { s.shared(it) }.takeIf { it.isNotEmpty() }
            ?.let { return OperationResult.error(s.shared("import_error_declaration").format(it.joinToString("; "))) }
        ImportPlanner.duplicateKeys(table, declarations).takeIf { it.isNotEmpty() }
            ?.let { return OperationResult.error(s.shared("import_error_duplicate_keys").format(it.joinToString(", "))) }
        val plan = ImportPlanner.plan(table, declarations, zone)

        // The new fields and every line in one transaction: a failure on the way writes nothing.
        // A line the service refuses is an answer, counted, and the others go on
        val report = try {
            AppDatabase.getDatabase(context).withTransaction {
                // The new fields, in the order of their columns, before the lines that fill them
                val added = declarations.filter { it.target == ColumnTarget.NEW }
                val newPaths = if (added.isEmpty()) emptyMap() else {
                    val before = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
                    val extra = JSONArray().apply { before.forEach { put(it.toJson()) }; added.forEach { put(it.newField!!.toJson()) } }
                    val updated = coordinator.processUserAction("tools.update", mapOf(
                        "tool_instance_id" to toolInstanceId,
                        "config" to JsonUtils.toMap(JSONObject(config.toString()).put("extra_fields", extra))
                    ))
                    if (!updated.isSuccess) throw Refused(updated.error ?: "")
                    // The names the service gave them, the new fields being the last ones, in order
                    val reread = ToolFields.filterable(toolInstanceId, context, s).keys.filter { it.startsWith("extra.") }
                    val created = reread.takeLast(added.size)
                    added.map { it.column }.zip(created).toMap()
                }

                // The entries already there, found by their name when names are unique
                val existing = if (!uniqueName) emptyMap() else {
                    val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "fields" to listOf("id", "name")))
                    (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
                        .mapNotNull { e -> (e["name"] as? String)?.let { CoreFields.uniqueKey(it) to e["id"] as String } }.toMap()
                }

                var created = 0
                var updated = 0
                val refused = plan.refused.map { mapOf("line" to it.line, "column" to it.column, "cell" to it.cell, "reason" to s.shared("import_refused_cell").format(it.cell, s.shared("import_writing_${it.reason.lowercase()}"))) }.toMutableList()
                for (line in plan.lines) {
                    val data = mutableMapOf<String, Any>()
                    val extra = mutableMapOf<String, Any>()
                    var timestamp: Any? = null
                    (line.values + line.newValues.mapKeys { newPaths.getValue(it.key) }).forEach { (path, value) ->
                        when {
                            path == "timestamp" -> timestamp = value
                            path.startsWith("data.") -> data[path.removePrefix("data.")] = value
                            path.startsWith("extra.") -> extra[path.removePrefix("extra.")] = value
                        }
                    }
                    val params = mutableMapOf<String, Any>("tool_instance_id" to toolInstanceId, "data" to data)
                    line.name?.let { params["name"] = it }
                    if (extra.isNotEmpty()) params["extra"] = extra
                    timestamp?.let { params["timestamp"] = it }
                    val found = line.name?.let { existing[CoreFields.uniqueKey(it)] }
                    val result = if (found != null) coordinator.processUserAction("tool_data.update", params + ("id" to found))
                        else coordinator.processUserAction("tool_data.create", params)
                    when {
                        !result.isSuccess -> refused.add(mapOf("line" to line.line, "reason" to (result.error ?: "")))
                        found != null -> updated++
                        else -> created++
                    }
                }
                mapOf(
                    "created" to created,
                    "updated" to updated,
                    "refused" to refused.sortedBy { it["line"] as Int },
                    "new_fields" to newPaths.values.toList()
                )
            }
        } catch (refused: Refused) {
            return OperationResult.error(refused.message ?: "")
        }
        return OperationResult.success(report)
    }

    /** A refusal that undoes the whole import. */
    private class Refused(message: String) : Exception(message)

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        if (operation == "detect") return s.shared("action_verbalize_imports_detect")
        // A joined file is named, with its lines and the tool it goes into, for the validation card
        val file = params.optString("file").takeIf { it.isNotEmpty() }?.let { AppDatabase.getDatabase(context).attachedFileDao().getInfo(it) }
            ?: return s.shared("action_verbalize_imports_apply")
        val tool = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to params.optString("tool_instance_id")))
        @Suppress("UNCHECKED_CAST")
        val toolName = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.get("name") as? String
            ?: return s.shared("action_verbalize_imports_apply")
        return s.shared("action_verbalize_imports_apply_file").format(file.name, file.lineCount, toolName)
    }
}
