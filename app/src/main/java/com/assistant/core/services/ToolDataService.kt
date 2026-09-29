package com.assistant.core.services

import android.content.Context
import androidx.room.withTransaction
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Operation
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.database.AppDatabase
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import com.assistant.core.validation.FieldPatternGrammar
import com.assistant.core.fields.EntryFilters
import androidx.sqlite.db.SimpleSQLiteQuery
import com.assistant.core.fields.NumericPrecision
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.SystemManagedFields
import com.assistant.core.validation.Schema
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FieldValueValidator
import com.assistant.core.fields.RunningDurations
import com.assistant.core.fields.toJsonArray
import com.assistant.core.fields.withOptionsAdded
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.utils.LogManager

/**
 * Centralized service for all tool_data operations
 * Replaces specialized services (TrackingService, etc.)
 */
class ToolDataService(private val context: Context) : ExecutableService {

    companion object {
        /** The parameters of tool_data.get, and the phase the coordinator adds to every call. */
        private val GET_PARAMS = setOf("tool_instance_id", "fields", "filters", "limit", "page", "running", "phase")
        private val VALUES_PARAMS = setOf("tool_instance_id", "field", "limit", "phase")
        /** How many values a text field offers at most, the most frequent. */
        private const val VALUES_LIMIT = 20
    }

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        return try {
            when (operation) {
                "create" -> createEntry(params, token)
                "update" -> updateEntry(params, token)
                "delete" -> deleteEntry(params, token)
                "get" -> getEntries(params, token)       // Standard REST GET
                "values" -> getValues(params, token)     // The values a text field holds, to pick one in a filter
                "get_single" -> getSingleEntry(params, token)  // GET single entry by ID
                "delete_all" -> deleteAllEntries(params, token)  // POST /tool_data/delete_all
                "batch_create" -> batchCreateEntries(params, token)  // Batch create multiple entries
                "batch_update" -> batchUpdateEntries(params, token)  // Batch update multiple entries
                "batch_delete" -> batchDeleteEntries(params, token)  // Batch delete multiple entries
                "start_duration" -> startDuration(params, token)  // A DURATION field starts running
                "stop_duration" -> stopDuration(params, token)    // It stops, and the time elapsed is added to it
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_tool_data_service").format(e.message ?: ""))
        }
    }

    private suspend fun createEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val name = params.optString("name", null)

        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("tool_instance_id"))
        }

        val target = when (val loaded = loadWriteTarget(toolInstanceId)) {
            is WriteTarget.Refused -> return OperationResult.error(loaded.error)
            is WriteTarget.Ready -> loaded
        }
        // System-managed at the root of an entry: taken from the tool, never from the caller
        val tooltype = target.tool.tooltype

        // Payloads arrive in milliseconds from every caller, so they are stored as they come.
        // Fields the schema marks system-managed are the app's to produce, not the caller's.
        val dataJson = SystemManagedFields.dropFromData(params.optJSONObject("data") ?: JSONObject(), target.schema.content).toString()
        val extraJson = params.optJSONObject("extra")?.takeIf { it.length() > 0 }?.toString()
        // State is written by the app and the entry's actions (a note's position, a message's
        // status), never entered in a form
        val stateJson = params.optJSONObject("state")?.takeIf { it.length() > 0 }?.toString()

        // Milliseconds are the contract. An absent timestamp means now, which is a default
        // written into the contract; any number is taken as milliseconds, Int and Double
        // included, since JSON decides the width on its own. Anything else is refused.
        val timestampAbsent = ToolTypeManager.getToolType(tooltype)?.getEntryFields(target.config, context)?.timestamp == CoreFieldUsage.ABSENT
        val timestamp = when {
            // A tool type whose entries have no date gets none; one sent is refused by the schema
            !params.has("timestamp") && timestampAbsent -> null
            !params.has("timestamp") -> System.currentTimeMillis()
            params.opt("timestamp") is Number -> (params.opt("timestamp") as Number).toLong()
            else -> return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.opt("timestamp").toString()))
        }

        // A NUMERIC value holds the decimals its field says: rounded, not refused
        val finalDataJson = NumericPrecision.roundAll(dataJson, declaredFields(target))!!
        val finalExtraJson = NumericPrecision.roundAll(extraJson, userFields(target))

        // An open choice's new values join its options, in the same transaction as the entry
        val grownConfig = configWithNewOptions(target, finalDataJson, finalExtraJson)
        val checked = grownConfig?.let { target.withConfig(it) } ?: target

        validateEntry(checked, name, timestamp, finalDataJson, finalExtraJson, stateJson)
            ?.let { return OperationResult.error(it) }
        checkReferences(checked, finalDataJson, finalExtraJson, before = null)
            ?.let { return OperationResult.error(it) }

        val now = System.currentTimeMillis()
        val entity = ToolDataEntity(
            id = UUID.randomUUID().toString(),
            toolInstanceId = toolInstanceId,
            tooltype = tooltype,
            timestamp = timestamp,
            name = name,
            data = finalDataJson,
            createdAt = now,
            updatedAt = now,
            extra = finalExtraJson,
            state = stateJson
        )

        val dao = getToolDataDao()
        try {
            storeSettled(tooltype, dao.getByToolInstance(toolInstanceId) + entity, entity.id, checked.grown(grownConfig)) { settled ->
                refuseTakenName(checked, entity)
                dao.insert(settled ?: entity)
            }
        } catch (taken: NameTaken) {
            return OperationResult.error(taken.message ?: "")
        }

        // Notify UI of data change in this tool instance, and of its config when the entry grew it
        val zoneId = getZoneIdForTool(toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(toolInstanceId, zoneId)
            if (grownConfig != null) DataChangeNotifier.notifyToolsChanged(zoneId)
        }

        return OperationResult.success(
            data = mapOf(
                "id" to entity.id,
                "created_at" to entity.createdAt
            )
        )
    }

    /** A name another entry of the tool already has, where names are unique. */
    private class NameTaken(message: String) : Exception(message)

    /**
     * Refuses [entry] when its tool type keeps names unique (EntryFields.nameUnique) and another
     * entry of the tool has the same one, the case and the spaces around not counted, naming it.
     * Run inside the write's transaction.
     */
    private suspend fun refuseTakenName(target: WriteTarget.Ready, entry: ToolDataEntity) {
        val name = entry.name ?: return
        val declared = ToolTypeManager.getToolType(target.tool.tooltype)?.getEntryFields(target.config, context) ?: return
        if (!declared.nameUnique) return
        val key = com.assistant.core.fields.CoreFields.uniqueKey(name)
        getToolDataDao().getByToolInstance(entry.toolInstanceId)
            .firstOrNull { it.id != entry.id && it.name != null && com.assistant.core.fields.CoreFields.uniqueKey(it.name) == key }
            ?.let { throw NameTaken(s.shared("service_error_name_taken").format(it.name, it.id)) }
    }

    /**
     * Store one write together with what its tool type rewrites around it, all or nothing.
     *
     * [after] is the tool instance's entries as they stand once the write is done, [writtenId] the
     * entry created or updated (null after a delete). [write] stores the write itself, handed the
     * version the tool type settled it to, or null when it left it as it was. [toolConfig] is the
     * tool with its config grown by an open choice, stored in the same transaction.
     */
    private suspend fun storeSettled(
        tooltype: String,
        after: List<ToolDataEntity>,
        writtenId: String?,
        toolConfig: ToolInstance? = null,
        write: suspend (settled: ToolDataEntity?) -> Unit
    ) {
        val settled = ToolTypeManager.getToolType(tooltype)
            ?.settleEntries(after, writtenId)
            ?.associateBy { it.id }
            ?: emptyMap()
        val dao = getToolDataDao()
        val now = System.currentTimeMillis()

        AppDatabase.getDatabase(context).withTransaction {
            toolConfig?.let { AppDatabase.getDatabase(context).toolInstanceDao().updateToolInstance(it) }
            write(writtenId?.let { settled[it] })
            settled.values
                .filter { it.id != writtenId }
                .forEach { dao.update(it.copy(updatedAt = now)) }
        }
    }

    private suspend fun updateEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val entryId = params.optString("id")
        val name = params.optString("name", null)

        if (entryId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_id"))
        }

        val dao = getToolDataDao()
        val existingEntity = dao.getById(entryId)
            ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(entryId))

        val target = when (val loaded = loadWriteTarget(existingEntity.toolInstanceId)) {
            is WriteTarget.Refused -> return OperationResult.error(loaded.error)
            is WriteTarget.Ready -> loaded
        }

        // Payloads arrive in milliseconds from every caller, so they are stored as they come.
        // Fields the schema marks system-managed are the app's to produce, not the caller's.
        val dataJson = params.optJSONObject("data")
            ?.let { SystemManagedFields.dropFromData(it, target.schema.content).toString() }
        val extraJson = params.optJSONObject("extra")
        val stateJson = params.optJSONObject("state")

        // Milliseconds are the contract. An absent timestamp leaves the recorded one alone;
        // any number is taken as milliseconds. Anything else is refused.
        val timestamp = when {
            !params.has("timestamp") -> null
            params.opt("timestamp") is Number -> (params.opt("timestamp") as Number).toLong()
            else -> return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.opt("timestamp").toString()))
        }

        // Merge JSON data: new fields overwrite, absent fields are preserved (system-managed ones
        // included, the incoming data having lost them above)
        val mergedData = if (dataJson != null) {
            val existingJson = JSONObject(existingEntity.data)
            val newJson = JSONObject(dataJson)

            // Copy all keys from newJson into existingJson (overwrite present, preserve absent)
            newJson.keys().forEach { key ->
                existingJson.put(key, newJson.get(key))
            }

            existingJson.toString()
        } else {
            existingEntity.data
        }

        // extra and state are merged the same way: a key sent overwrites, a key absent is kept,
        // a key sent as null is removed (how a value is cleared)
        val mergedExtra = mergeObject(existingEntity.extra, extraJson)
        val mergedState = mergeObject(existingEntity.state, stateJson)

        // A NUMERIC value holds the decimals its field says: rounded, not refused
        val updatedEntity = existingEntity.copy(
            data = NumericPrecision.roundAll(mergedData, declaredFields(target))!!,
            extra = NumericPrecision.roundAll(mergedExtra, userFields(target)),
            state = mergedState,
            timestamp = timestamp ?: existingEntity.timestamp,
            name = name ?: existingEntity.name,
            updatedAt = System.currentTimeMillis()
        )

        // An open choice's new values join its options, in the same transaction as the entry
        val grownConfig = configWithNewOptions(target, updatedEntity.data, updatedEntity.extra)
        val checked = grownConfig?.let { target.withConfig(it) } ?: target

        // The whole entry is checked, not only the fields sent: after the merge it is what will be stored
        validateEntry(
            checked, updatedEntity.name, updatedEntity.timestamp, updatedEntity.data, updatedEntity.extra, updatedEntity.state
        )?.let { return OperationResult.error(it) }
        checkReferences(checked, updatedEntity.data, updatedEntity.extra, before = existingEntity)
            ?.let { return OperationResult.error(it) }

        val after = dao.getByToolInstance(existingEntity.toolInstanceId)
            .map { if (it.id == updatedEntity.id) updatedEntity else it }
        try {
            storeSettled(existingEntity.tooltype, after, updatedEntity.id, checked.grown(grownConfig)) { settled ->
                refuseTakenName(checked, updatedEntity)
                dao.update(settled ?: updatedEntity)
            }
        } catch (taken: NameTaken) {
            return OperationResult.error(taken.message ?: "")
        }

        // Notify UI of data change in this tool instance, and of its config when the entry grew it
        val zoneId = getZoneIdForTool(existingEntity.toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(existingEntity.toolInstanceId, zoneId)
            if (grownConfig != null) DataChangeNotifier.notifyToolsChanged(zoneId)
        }

        return OperationResult.success(
            data = mapOf(
                "id" to updatedEntity.id,
                "updated_at" to updatedEntity.updatedAt
            )
        )
    }

    private suspend fun deleteEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val entryId = params.optString("id")
        if (entryId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_id"))
        }

        val dao = getToolDataDao()

        // Verify entry exists before deletion (CRITICAL: AI must know if entry doesn't exist)
        val entity = dao.getById(entryId)
            ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(entryId))

        val after = dao.getByToolInstance(entity.toolInstanceId).filter { it.id != entryId }
        storeSettled(entity.tooltype, after, null) {
            dao.deleteById(entryId)
        }

        // Notify UI of data change in this tool instance
        val zoneId = getZoneIdForTool(entity.toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(entity.toolInstanceId, zoneId)
        }

        return OperationResult.success(mapOf(
            "id" to entryId,
            "deleted_at" to System.currentTimeMillis()
        ))
    }

    private suspend fun getEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Debug logging
        LogManager.service("ToolDataService.getEntries - Received params: $params")

        val toolInstanceId = params.optString("tool_instance_id")
        LogManager.service("ToolDataService.getEntries - toolInstanceId='$toolInstanceId' (length=${toolInstanceId.length})")
        LogManager.service("ToolDataService.getEntries - params keys: ${params.keys().asSequence().toList()}")

        if (toolInstanceId.isEmpty()) {
            LogManager.service("ToolDataService.getEntries - toolInstanceId is empty, returning error", "ERROR")
            return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        }

        // A parameter this read does not take would be ignored, and the read widened in silence:
        // a period set with a key it does not know would return the whole history
        params.keys().asSequence().firstOrNull { it !in GET_PARAMS }?.let { param ->
            return OperationResult.error(s.shared("service_error_param_unknown").format(param, (GET_PARAMS - "phase").joinToString(", ")))
        }

        // Filtering and pagination parameters
        val hasLimit = params.has("limit")
        val limit = if (hasLimit) params.optInt("limit") else Int.MAX_VALUE
        val page = params.optInt("page", 1)

        // Without a limit everything is on page 1, so asking for another one is a contradiction.
        // It used to be answered: (page - 1) * Int.MAX_VALUE gives an offset past any table on
        // page 2, and overflows to a negative one on page 3, which SQLite reads as no offset --
        // so page 3 returned page 1 and page 2 returned nothing, both without a word.
        if (!hasLimit && page != 1) {
            return OperationResult.error(s.shared("service_error_page_without_limit").format(page))
        }
        val offset = (page - 1) * limit

        val dao = getToolDataDao()

        // Only the entries with a DURATION field running, for a screen to show and stop them
        val running = params.optBoolean("running", false)

        val (entries, totalCount) = if (running) {
            val data = dao.getRunning(toolInstanceId)
            Pair(data, data.size)
        } else {
            // The value filters, a period being one on timestamp, checked against the tool's
            // fields: the tool type's, the user's, and the state keys offered as filters
            val target = when (val loaded = loadWriteTarget(toolInstanceId)) {
                is WriteTarget.Refused -> return OperationResult.error(loaded.error)
                is WriteTarget.Ready -> loaded
            }
            val declared = ToolTypeManager.getToolType(target.tool.tooltype)?.getEntryFields(target.config, context)
                ?: return OperationResult.error(s.shared("service_error_data_schema_not_found").format("", target.tool.tooltype))
            val fields = EntryFilters.filterableFields(declared, userFields(target)) { s.shared(it) }
            val filters = when (val parsed = EntryFilters.parse(params.optJSONArray("filters") ?: JSONArray(), fields) { s.shared(it) }) {
                is EntryFilters.Parsed.Refused -> return OperationResult.error(parsed.error)
                is EntryFilters.Parsed.Ready -> parsed.filters
            }

            val select = EntryFilters.select(toolInstanceId, filters, fields, limit.takeIf { hasLimit }, offset)
            val count = EntryFilters.count(toolInstanceId, filters, fields)
            Pair(
                dao.getFiltered(SimpleSQLiteQuery(select.clause, select.args.toTypedArray())),
                dao.countFiltered(SimpleSQLiteQuery(count.clause, count.args.toTypedArray()))
            )
        }
        
        val totalPages = if (totalCount == 0) 1 else ((totalCount - 1) / limit) + 1

        // Parse fields filter if provided (optional for backward compatibility)
        val fieldsFilter = params.optJSONArray("fields")?.let { fieldsArray ->
            val list = mutableListOf<String>()
            for (i in 0 until fieldsArray.length()) {
                list.add(fieldsArray.getString(i))
            }
            LogManager.service("ToolDataService.get: fieldsFilter = $list", "DEBUG")
            list
        }

        if (fieldsFilter == null) {
            LogManager.service("ToolDataService.get: No fields filter provided (backward compatibility mode)", "DEBUG")
        }

        return OperationResult.success(
            data = mapOf(
                "entries" to entries.map { entity ->
                    val fullEntry = ToolDataEntries.toMap(entity)

                    // Apply fields filter if provided
                    if (fieldsFilter != null) {
                        filterEntryFields(fullEntry, fieldsFilter)  // Return filtered entry
                    } else {
                        fullEntry  // Return full entry if no filter
                    }
                },
                // Without a limit there is no page size to report: Int.MAX_VALUE is how the
                // query reads "no limit", not a number anyone asked for.
                "pagination" to buildMap {
                    put("current_page", page)
                    put("total_pages", totalPages)
                    put("total_entries", totalCount)
                    if (hasLimit) put("entries_per_page", limit)
                }
            )
        )
    }

    /**
     * The values a text field holds among a tool's entries, the most frequent first: what a filter
     * on it offers to pick, besides typing. Params: tool_instance_id, field (a path, as in
     * filters), limit (default [VALUES_LIMIT]).
     */
    private suspend fun getValues(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        params.keys().asSequence().firstOrNull { it !in VALUES_PARAMS }?.let { param ->
            return OperationResult.error(s.shared("service_error_param_unknown").format(param, (VALUES_PARAMS - "phase").joinToString(", ")))
        }
        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isEmpty()) return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        val path = params.optString("field")

        val target = when (val loaded = loadWriteTarget(toolInstanceId)) {
            is WriteTarget.Refused -> return OperationResult.error(loaded.error)
            is WriteTarget.Ready -> loaded
        }
        val declared = ToolTypeManager.getToolType(target.tool.tooltype)?.getEntryFields(target.config, context)
            ?: return OperationResult.error(s.shared("service_error_data_schema_not_found").format("", target.tool.tooltype))
        val fields = EntryFilters.filterableFields(declared, userFields(target)) { s.shared(it) }
        val field = fields[path]
            ?: return OperationResult.error(s.shared("service_error_filter_unknown_field").format(path, fields.keys.joinToString(", ")))
        // A number, a date or a choice is entered with its own input: only a text has values to offer
        if (field.type != FieldType.TEXT) return OperationResult.error(s.shared("service_error_values_not_text").format(field.displayName))

        val query = EntryFilters.values(toolInstanceId, path, params.optInt("limit", VALUES_LIMIT))
        val values = getToolDataDao().distinctValues(SimpleSQLiteQuery(query.clause, query.args.toTypedArray()))
        return OperationResult.success(mapOf("values" to values))
    }

    private suspend fun getSingleEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val entryId = params.optString("entry_id")
        if (entryId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_entry_id"))
        }

        val dao = getToolDataDao()
        val entity = dao.getById(entryId)
            ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(entryId))

        return OperationResult.success(data = mapOf("entry" to ToolDataEntries.toMap(entity)))
    }

    private suspend fun deleteAllEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        }

        // Verify tool instance exists (MAJOR: AI must know if toolInstanceId is invalid)
        val database = AppDatabase.getDatabase(context)
        val toolInstanceDao = database.toolInstanceDao()
        val toolInstance = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found").format(toolInstanceId))

        val dao = getToolDataDao()
        val deletedCount = dao.countByToolInstance(toolInstanceId) // Count before deletion
        dao.deleteByToolInstance(toolInstanceId)

        // Notify UI of data change in this tool instance
        val zoneId = getZoneIdForTool(toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(toolInstanceId, zoneId)
        }

        return OperationResult.success(mapOf(
            "deleted_count" to deletedCount,
            "tool_instance_id" to toolInstanceId
        ))
    }

    /**
     * Batch create multiple tool data entries
     * Params: tool_instance_id, entries (see BatchEntryParams.forCreate)
     */
    private suspend fun batchCreateEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val entriesArray = params.optJSONArray("entries")

        if (toolInstanceId.isEmpty() || entriesArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("tool_instance_id, entries"))
        }

        val dao = getToolDataDao()
        val createdIds = mutableListOf<String>()
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<String>() // Track individual failure messages

        // Process each entry
        for (i in 0 until entriesArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val entryJson = entriesArray.getJSONObject(i)

                val singleParams = BatchEntryParams.forCreate(entryJson, toolInstanceId)

                // Use existing createEntry logic
                val result = createEntry(singleParams, token)

                if (result.success) {
                    result.data?.get("id")?.let { createdIds.add(it.toString()) }
                    successCount++
                    LogManager.service("Batch entry $i created successfully", "DEBUG")
                } else {
                    val error = "Entry $i: ${result.error ?: "unknown error"}"
                    failures.add(error)
                    failureCount++
                    LogManager.service("Batch create failed for entry $i: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                val error = "Entry $i: ${e.message}"
                failures.add(error)
                failureCount++
                LogManager.service("Batch create exception for entry $i: ${e.message}", "ERROR", e)
            }
        }

        // Note: No notification here - createEntry() already notifies for each entry

        // MAJOR: Return error if ALL entries failed (AI must know about total failure)
        // Return success with visible counts if partial success (AI can parse failed_count)
        if (successCount == 0 && failureCount > 0) {
            val detailedError = if (failures.isNotEmpty()) {
                "All batch entries failed ($failureCount): ${failures.joinToString("; ")}"
            } else {
                "All batch entries failed: $failureCount failed"
            }
            return OperationResult.error(detailedError)
        }

        // Log warning if partial failures occurred
        if (failureCount > 0) {
            LogManager.service(
                "Batch create completed with partial failures: $successCount succeeded, $failureCount failed",
                "WARN"
            )
        }

        return OperationResult.success(mapOf(
            "created_count" to successCount,
            "failed_count" to failureCount,
            "ids" to createdIds,
            "tool_instance_name" to toolInstanceId // For CommandExecutor system messages
        ))
    }

    /**
     * Batch update multiple tool data entries
     * Params: entries (array of objects with id, data?, timestamp?, name?)
     */
    private suspend fun batchUpdateEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val entriesArray = params.optJSONArray("entries")

        if (entriesArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("entries"))
        }

        val dao = getToolDataDao()
        var successCount = 0
        var failureCount = 0

        // Process each entry
        val failures = mutableListOf<String>() // Track individual failure messages

        for (i in 0 until entriesArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val entryJson = entriesArray.getJSONObject(i)
                val entryId = entryJson.optString("id")

                if (entryId.isEmpty()) {
                    val error = "Entry $i: missing id"
                    failures.add(error)
                    LogManager.service(
                        "Batch update failed for entry $i: missing id",
                        "WARN"
                    )
                    failureCount++
                    continue
                }

                val singleParams = BatchEntryParams.forUpdate(entryJson, entryId)

                // Use existing updateEntry logic
                val result = updateEntry(singleParams, token)

                if (result.success) {
                    successCount++
                } else {
                    val error = "Entry $i (id=$entryId): ${result.error ?: "unknown error"}"
                    failures.add(error)
                    LogManager.service(
                        "Batch update failed for entry $i (id=$entryId): ${result.error}",
                        "WARN"
                    )
                    failureCount++
                }
            } catch (e: Exception) {
                val error = "Entry $i: ${e.message}"
                failures.add(error)
                LogManager.service(
                    "Batch update exception for entry $i: ${e.message}",
                    "ERROR",
                    e
                )
                failureCount++
            }
        }

        // Note: No notification here - updateEntry() already notifies for each entry

        // MAJOR: Return error if ALL entries failed (AI must know about total failure)
        // Return success with visible counts if partial success (AI can parse failed_count)
        if (successCount == 0 && failureCount > 0) {
            val detailedError = if (failures.isNotEmpty()) {
                "All batch entries failed ($failureCount): ${failures.joinToString("; ")}"
            } else {
                "All batch entries failed: $failureCount failed"
            }
            return OperationResult.error(detailedError)
        }

        // Log warning if partial failures occurred
        if (failureCount > 0) {
            LogManager.service(
                "Batch update completed with partial failures: $successCount succeeded, $failureCount failed",
                "WARN"
            )
        }

        return OperationResult.success(mapOf(
            "updated_count" to successCount,
            "failed_count" to failureCount
        ))
    }

    /**
     * Batch delete multiple tool data entries
     * Params: ids (array of entry IDs to delete)
     */
    private suspend fun batchDeleteEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val idsArray = params.optJSONArray("ids")

        if (idsArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("ids"))
        }

        val dao = getToolDataDao()
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<String>() // Track individual failure messages

        // Process each ID
        for (i in 0 until idsArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val entryId = idsArray.getString(i)

                if (entryId.isEmpty()) {
                    val error = "Entry $i: missing id"
                    failures.add(error)
                    LogManager.service(
                        "Batch delete failed for entry $i: missing id",
                        "WARN"
                    )
                    failureCount++
                    continue
                }

                // Build params for single delete
                val singleParams = JSONObject().apply {
                    put("id", entryId)
                }

                // Use existing deleteEntry logic
                val result = deleteEntry(singleParams, token)

                if (result.success) {
                    successCount++
                } else {
                    val error = "Entry $i (id=$entryId): ${result.error ?: "unknown error"}"
                    failures.add(error)
                    LogManager.service(
                        "Batch delete failed for entry $i (id=$entryId): ${result.error}",
                        "WARN"
                    )
                    failureCount++
                }
            } catch (e: Exception) {
                val error = "Entry $i: ${e.message}"
                failures.add(error)
                LogManager.service(
                    "Batch delete exception for entry $i: ${e.message}",
                    "ERROR",
                    e
                )
                failureCount++
            }
        }

        // Note: No notification here - deleteEntry() already notifies for each entry

        // MAJOR: Return error if ALL entries failed (AI must know about total failure)
        // Return success with visible counts if partial success (AI can parse failed_count)
        if (successCount == 0 && failureCount > 0) {
            val detailedError = if (failures.isNotEmpty()) {
                "All batch entries failed ($failureCount): ${failures.joinToString("; ")}"
            } else {
                "All batch entries failed: $failureCount failed"
            }
            return OperationResult.error(detailedError)
        }

        // Log warning if partial failures occurred
        if (failureCount > 0) {
            LogManager.service(
                "Batch delete completed with partial failures: $successCount succeeded, $failureCount failed",
                "WARN"
            )
        }

        return OperationResult.success(mapOf(
            "deleted_count" to successCount,
            "failed_count" to failureCount
        ))
    }

    /**
     * Starts a DURATION field of an entry: the instant goes into the entry's state, which is
     * where a stopwatch survives the app being killed.
     *
     * Params: id (the entry), container ("data" or "extra"), field (its name)
     */
    private suspend fun startDuration(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val located = locateDuration(params) ?: return OperationResult.error(durationError(params))
        val (entity, target, container, field) = located
        if (RunningDurations.startedAt(entity.state?.let { JSONObject(it) }, container, field) != null) {
            return OperationResult.error(s.shared("service_error_duration_running").format(field))
        }

        val now = System.currentTimeMillis()
        val state = RunningDurations.start(entity.state?.let { JSONObject(it) }, container, field, now)
        val updated = entity.copy(state = state.toString(), updatedAt = now)
        return storeDurationChange(target, updated, mapOf("id" to entity.id, "started_at" to now))
    }

    /**
     * Stops a running DURATION field: the time since it started is added to its value, and its
     * start leaves the state.
     *
     * Params: id (the entry), container ("data" or "extra"), field (its name)
     */
    private suspend fun stopDuration(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val located = locateDuration(params) ?: return OperationResult.error(durationError(params))
        val (entity, target, container, field) = located
        val state = entity.state?.let { JSONObject(it) }
        if (RunningDurations.startedAt(state, container, field) == null) {
            return OperationResult.error(s.shared("service_error_duration_not_running").format(field))
        }

        val now = System.currentTimeMillis()
        val values = JSONObject(when (container) {
            FieldContainer.DATA -> entity.data
            FieldContainer.EXTRA -> entity.extra ?: "{}"
        })
        val stored = if (values.has(field)) values.getLong(field) else null
        val stopped = RunningDurations.stop(state, container, field, stored, now)
        values.put(field, stopped.value)

        val updated = entity.copy(
            data = if (container == FieldContainer.DATA) values.toString() else entity.data,
            extra = if (container == FieldContainer.EXTRA) values.toString() else entity.extra,
            state = stopped.state.takeIf { it.length() > 0 }?.toString(),
            updatedAt = now
        )
        return storeDurationChange(target, updated, mapOf("id" to entity.id, "value" to stopped.value))
    }

    /** An entry, its tool, and one of its DURATION fields, as a start or stop names them. */
    private data class LocatedDuration(
        val entity: ToolDataEntity,
        val target: WriteTarget.Ready,
        val container: FieldContainer,
        val field: String
    )

    /** The DURATION field [params] name, or null when there is none (durationError says why). */
    private suspend fun locateDuration(params: JSONObject): LocatedDuration? {
        val entity = getToolDataDao().getById(params.optString("id")) ?: return null
        val container = FieldContainer.entries.firstOrNull { it.key == params.optString("container") } ?: return null
        val field = params.optString("field").takeIf { it.isNotEmpty() } ?: return null
        val target = loadWriteTarget(entity.toolInstanceId) as? WriteTarget.Ready ?: return null

        val fields = when (container) {
            FieldContainer.DATA -> ToolTypeManager.getToolType(entity.tooltype)
                ?.getEntryFields(target.config, context)?.data?.map { it.definition } ?: emptyList()
            FieldContainer.EXTRA -> target.config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        }
        if (fields.none { it.name == field && it.type == FieldType.DURATION }) return null
        return LocatedDuration(entity, target, container, field)
    }

    private fun durationError(params: JSONObject): String =
        s.shared("service_error_duration_unknown").format(
            params.optString("id"), params.optString("container"), params.optString("field")
        )

    /** Checks and stores an entry a start or a stop rewrote, and notifies its tool. */
    private suspend fun storeDurationChange(
        target: WriteTarget.Ready,
        updated: ToolDataEntity,
        result: Map<String, Any>
    ): OperationResult {
        validateEntry(target, updated.name, updated.timestamp, updated.data, updated.extra, updated.state)
            ?.let { return OperationResult.error(it) }

        val dao = getToolDataDao()
        val after = dao.getByToolInstance(updated.toolInstanceId).map { if (it.id == updated.id) updated else it }
        storeSettled(updated.tooltype, after, updated.id) { settled -> dao.update(settled ?: updated) }

        getZoneIdForTool(updated.toolInstanceId)?.let { DataChangeNotifier.notifyToolDataChanged(updated.toolInstanceId, it) }
        return OperationResult.success(result)
    }

    /**
     * Gets the unified tool_data DAO
     */
    private fun getToolDataDao(): BaseToolDataDao {
        return AppDatabase.getDatabase(context).toolDataDao()
    }

    /**
     * Generates human-readable description of tool data action
     * Format: substantive form (e.g., "Utilisation de l'outil \"Poids\" (zone \"Santé\") : ajout de 10 entrée(s)")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)

        return when (operation) {
            "create", "batch_create" -> {
                val toolInstanceId = params.optString("tool_instance_id")
                val toolInfo = getToolInfo(toolInstanceId, context)

                val count = if (operation == "batch_create") {
                    params.optJSONArray("entries")?.length() ?: 1
                } else 1

                s.shared("action_verbalize_create_data").format(
                    toolInfo.name,
                    toolInfo.zoneName,
                    count
                )
            }
            "update", "batch_update" -> {
                val toolInstanceId = params.optString("tool_instance_id")
                val toolInfo = getToolInfo(toolInstanceId, context)

                val count = if (operation == "batch_update") {
                    params.optJSONArray("entries")?.length() ?: 1
                } else 1

                s.shared("action_verbalize_update_data").format(
                    toolInfo.name,
                    toolInfo.zoneName,
                    count
                )
            }
            "start_duration", "stop_duration" -> {
                val toolInfo = getToolInfo(params.optString("tool_instance_id"), context)
                s.shared("action_verbalize_${operation}").format(toolInfo.name, toolInfo.zoneName)
            }
            "delete", "batch_delete" -> {
                val toolInstanceId = params.optString("tool_instance_id")
                val toolInfo = getToolInfo(toolInstanceId, context)

                val count = if (operation == "batch_delete") {
                    params.optJSONArray("ids")?.length() ?: 1
                } else 1

                s.shared("action_verbalize_delete_data").format(
                    toolInfo.name,
                    toolInfo.zoneName,
                    count
                )
            }
            else -> s.shared("action_verbalize_unknown")
        }
    }

    /**
     * Helper to get zone_id from tool_instance_id
     */
    private suspend fun getZoneIdForTool(toolInstanceId: String): String? {
        val database = AppDatabase.getDatabase(context)
        val toolInstanceDao = database.toolInstanceDao()
        val toolInstance = toolInstanceDao.getToolInstanceById(toolInstanceId)
        return toolInstance?.zone_id
    }

    /** The tool an entry is written to, as the write path needs it, or why it cannot be written to. */
    private sealed interface WriteTarget {
        /** [schema] is the entry schema generated for the tool: its type's fields and the user's. */
        class Ready(val tool: ToolInstance, val config: JSONObject, val schema: Schema) : WriteTarget {
            /** The same tool with [config], its schema generated anew. */
            fun withConfig(config: JSONObject, context: Context): Ready = Ready(
                tool, config, schema.copy(content = BaseSchemas.getEntrySchema(ToolTypeManager.getToolType(tool.tooltype)!!, config, context))
            )

            /** The tool to store with [grownConfig], or null when the config did not grow. */
            fun grown(grownConfig: JSONObject?): ToolInstance? =
                grownConfig?.let { tool.copy(config_json = it.toString(), updated_at = System.currentTimeMillis()) }
        }
        class Refused(val error: String) : WriteTarget
    }

    private fun WriteTarget.Ready.withConfig(config: JSONObject) = withConfig(config, context)

    /** A value read from JSON as the fields read it: a list for an array, as is otherwise. */
    private fun plainValue(value: Any): Any = if (value is org.json.JSONArray) JsonUtils.toList(value) else value

    /**
     * The tool's config once the new values an entry gives to its open CHOICE fields have joined
     * their options, or null when there are none. The user's fields keep their options in
     * extra_fields; a tool type's own open field, in the config it says (configWithOptionsAdded).
     */
    private fun configWithNewOptions(target: WriteTarget.Ready, dataJson: String, extraJson: String?): JSONObject? {
        var config = target.config
        var grown = false

        val extraValues = extraJson?.let { JSONObject(it) }
        val userFields = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        val grownFields = userFields.map { field ->
            if (field.type != FieldType.CHOICE) return@map field
            val added = ChoiceSettings.fromConfig(field.config).newOptionsIn(extraValues?.opt(field.name)?.let { plainValue(it) })
            if (added.isEmpty()) field else field.withOptionsAdded(added).also { grown = true }
        }
        if (grown) config = JSONObject(config.toString()).put("extra_fields", grownFields.toJsonArray())

        val toolType = ToolTypeManager.getToolType(target.tool.tooltype) ?: return if (grown) config else null
        val dataValues = JSONObject(dataJson)
        toolType.getEntryFields(target.config, context).data.map { it.definition }
            .filter { it.type == FieldType.CHOICE }
            .forEach { field ->
                val added = ChoiceSettings.fromConfig(field.config).newOptionsIn(dataValues.opt(field.name)?.let { plainValue(it) })
                if (added.isNotEmpty()) {
                    config = toolType.configWithOptionsAdded(config, field.name, added)
                    grown = true
                }
            }

        return if (grown) config else null
    }



    /** Load a tool and its entry schema, read straight from the database and its config. */
    private suspend fun loadWriteTarget(toolInstanceId: String): WriteTarget {
        val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)
            ?: return WriteTarget.Refused(s.shared("service_error_tool_instance_not_found"))
        val config = JSONObject(tool.config_json)
        val toolType = ToolTypeManager.getToolType(tool.tooltype)
            ?: return WriteTarget.Refused(s.shared("service_error_data_schema_not_found").format("", tool.tooltype))
        return WriteTarget.Ready(
            tool, config, Schema(
                id = "entries:${tool.id}",
                displayName = tool.tooltype,
                description = "",
                category = com.assistant.core.validation.SchemaCategory.TOOL_DATA,
                content = BaseSchemas.getEntrySchema(toolType, config, context)
            )
        )
    }

    /** The fields the tool type declares in the entries' data, for [target]'s config. */
    private fun declaredFields(target: WriteTarget.Ready): List<FieldDefinition> =
        ToolTypeManager.getToolType(target.tool.tooltype)?.getEntryFields(target.config, context)?.data?.map { it.definition } ?: emptyList()

    /** The user's fields of [target], from its config's extra_fields. */
    private fun userFields(target: WriteTarget.Ready): List<FieldDefinition> =
        target.config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()

    /**
     * Check an entry exactly as it is about to be stored: against its tool's data schema, custom
     * fields included, then against the value rules a schema cannot state (a RANGE's start <= end).
     *
     * Every write goes through here, whoever makes it -- a screen, the AI, the scheduler, a batch
     * -- so nothing the schema refuses can be stored.
     *
     * @return The error to hand back, or null when the entry is valid
     */
    private fun validateEntry(
        target: WriteTarget.Ready,
        name: String?,
        timestamp: Long?,
        dataJson: String,
        extraJson: String?,
        stateJson: String?
    ): String? {
        val data = JsonUtils.toMap(dataJson)
        val extra = extraJson?.let { JsonUtils.toMap(it) }?.takeIf { it.isNotEmpty() }
        val state = stateJson?.let { JsonUtils.toMap(it) }?.takeIf { it.isNotEmpty() }
        val entry = mutableMapOf<String, Any?>(
            "tool_instance_id" to target.tool.id,
            "tooltype" to target.tool.tooltype,
            "data" to data
        )
        timestamp?.let { entry["timestamp"] = it }
        name?.let { entry["name"] = it }
        extra?.let { entry["extra"] = it }
        state?.let { entry["state"] = it }

        val result = SchemaValidator.validate(target.schema, entry, context)
        if (!result.isValid) return result.errorMessage ?: s.shared("service_error_validation_failed").format("")

        // What the schema cannot say, field by field: the tool type's fields and the user's
        for ((fields, values) in listOf(declaredFields(target) to data, userFields(target) to extra)) {
            for (field in fields) {
                val fieldResult = FieldValueValidator.validate(field, values?.get(field.name), context)
                if (!fieldResult.isValid) {
                    return s.shared("error_custom_field_validation_failed").format(field.displayName, fieldResult.errorMessage ?: "")
                }
            }
        }
        return null
    }

    /**
     * Check that each REFERENCE value an entry is given designates something that exists, and for
     * an entry, one of a tool instance the field takes. Only the values that change are checked:
     * a reference whose target was deleted since keeps its address, and the entry holding it stays
     * writable ([before], the entry as stored, null for a new one).
     *
     * @return The error to hand back, or null when every reference holds
     */
    private suspend fun checkReferences(target: WriteTarget.Ready, dataJson: String, extraJson: String?, before: ToolDataEntity?): String? {
        val database = AppDatabase.getDatabase(context)
        val containers = listOf(
            Triple(declaredFields(target), JsonUtils.toMap(dataJson), before?.data?.let { JsonUtils.toMap(it) }),
            Triple(userFields(target), extraJson?.let { JsonUtils.toMap(it) } ?: emptyMap<String, Any?>(), before?.extra?.let { JsonUtils.toMap(it) })
        )
        for ((fields, values, previous) in containers) {
            for (field in fields.filter { it.type == FieldType.REFERENCE }) {
                val reference = ReferenceTarget.referenceOf(values[field.name]) ?: continue
                if (ReferenceTarget.referenceOf(previous?.get(field.name)) == reference) continue
                val error = when (reference.kind) {
                    ReferenceKind.APP -> null
                    ReferenceKind.ZONE -> s.shared("field_value_reference_not_found").takeIf { database.zoneDao().getZoneById(reference.id!!) == null }
                    ReferenceKind.TOOL_INSTANCE -> s.shared("field_value_reference_not_found").takeIf { database.toolInstanceDao().getToolInstanceById(reference.id!!) == null }
                    ReferenceKind.ENTRY -> when (val entry = getToolDataDao().getById(reference.id!!)) {
                        null -> s.shared("field_value_reference_not_found")
                        else -> s.shared("field_value_reference_wrong_tool").takeIf { !ReferenceTarget.fromConfig(field.config).acceptsEntryOf(entry.toolInstanceId) }
                    }
                }
                if (error != null) {
                    return s.shared("error_custom_field_validation_failed").format(field.displayName, error.format(reference.kind.name, reference.id))
                }
            }
        }
        return null
    }

    /**
     * [stored] with the keys of [sent] merged in: a key sent overwrites, a key absent is kept,
     * a key sent as null is removed. Null when nothing is left, an empty object saying nothing
     * a missing one does not.
     */
    private fun mergeObject(stored: String?, sent: JSONObject?): String? {
        if (sent == null) return stored
        val merged = JSONObject(stored ?: "{}")
        sent.keys().forEach { key ->
            if (sent.isNull(key)) merged.remove(key) else merged.put(key, sent.get(key))
        }
        return merged.takeIf { it.length() > 0 }?.toString()
    }

    /**
     * Helper data class for tool information
     */
    private data class ToolInfo(val name: String, val zoneName: String)

    /**
     * Helper to retrieve tool and zone information
     */
    private suspend fun getToolInfo(toolInstanceId: String, context: Context): ToolInfo {
        val s = Strings.`for`(context = context)
        val defaultName = s.shared("content_unnamed")

        if (toolInstanceId.isBlank()) {
            return ToolInfo(defaultName, defaultName)
        }

        val coordinator = Coordinator(context)

        // Get tool instance
        val toolResult = coordinator.processUserAction("tools.get", mapOf(
            "tool_instance_id" to toolInstanceId
        ))

        val toolName = if (toolResult.status == CommandStatus.SUCCESS) {
            val tool = toolResult.data?.get("tool_instance") as? Map<*, *>
            tool?.get("name") as? String ?: defaultName
        } else defaultName

        // Get zone name
        val zoneId = if (toolResult.status == CommandStatus.SUCCESS) {
            val tool = toolResult.data?.get("tool_instance") as? Map<*, *>
            tool?.get("zone_id") as? String
        } else null

        val zoneName = if (zoneId != null) {
            val zoneResult = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
            if (zoneResult.status == CommandStatus.SUCCESS) {
                val zone = zoneResult.data?.get("zone") as? Map<*, *>
                zone?.get("name") as? String ?: defaultName
            } else defaultName
        } else defaultName

        return ToolInfo(toolName, zoneName)
    }

    /**
     * Filter entry fields according to requested fields list
     *
     * Where each path points is decided by FieldPatternGrammar, which is also what the
     * validation reads, so both ends agree on what a path means.
     *
     * @param entry Full entry map with all fields
     * @param requestedFields List of field paths to include
     * @return Filtered entry map with only requested fields
     */
    private fun filterEntryFields(entry: Map<String, Any?>, requestedFields: List<String>): Map<String, Any?> {
        val filtered = mutableMapOf<String, Any?>()
        val parsed = FieldPatternGrammar.parse(requestedFields)

        // Include requested root fields
        for (field in parsed.root) {
            if (entry.containsKey(field)) {
                filtered[field] = entry[field]
            }
        }

        parsed.inside.forEach { (container, keys) ->
            filterJsonField(entry, container, keys)?.let { filtered[container] = it }
        }

        return filtered
    }

    /**
     * Keep only the requested keys of one object field of an entry.
     *
     * Returns null when nothing was requested inside that field or the entry does not carry it,
     * so the caller leaves the field out of the result entirely.
     */
    private fun filterJsonField(
        entry: Map<String, Any?>,
        fieldName: String,
        requestedKeys: List<String>
    ): Map<String, Any?>? {
        if (requestedKeys.isEmpty()) return null

        @Suppress("UNCHECKED_CAST")
        val source = entry[fieldName] as? Map<String, Any?> ?: return null

        return source.filterKeys { it in requestedKeys }
    }
}