package com.assistant.core.services

import android.content.Context
import androidx.room.withTransaction
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Operation
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.AppDatabase
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject
import java.util.*
import com.assistant.core.validation.FieldPatternGrammar
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.SystemManagedFields
import com.assistant.core.validation.Schema
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldContainer
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

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        return try {
            when (operation) {
                "create" -> createEntry(params, token)
                "update" -> updateEntry(params, token)
                "delete" -> deleteEntry(params, token)
                "get" -> getEntries(params, token)       // Standard REST GET
                "get_single" -> getSingleEntry(params, token)  // GET single entry by ID
                "stats" -> getStats(params, token)       // GET /tool_data/stats
                "delete_all" -> deleteAllEntries(params, token)  // POST /tool_data/delete_all
                "batch_create" -> batchCreateEntries(params, token)  // Batch create multiple entries
                "batch_update" -> batchUpdateEntries(params, token)  // Batch update multiple entries
                "batch_delete" -> batchDeleteEntries(params, token)  // Batch delete multiple entries
                "remove_custom_field" -> removeCustomFieldFromAllEntries(params, token)  // Remove custom field from all entries
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
        val timestamp = when {
            !params.has("timestamp") -> System.currentTimeMillis()
            params.opt("timestamp") is Number -> (params.opt("timestamp") as Number).toLong()
            else -> return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.opt("timestamp").toString()))
        }

        val finalDataJson = dataJson

        // An open choice's new values join its options, in the same transaction as the entry
        val grownConfig = configWithNewOptions(target, finalDataJson, extraJson)
        val checked = grownConfig?.let { target.withConfig(it) } ?: target

        validateEntry(checked, name, timestamp, finalDataJson, extraJson, stateJson)
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
            extra = extraJson,
            state = stateJson
        )

        val dao = getToolDataDao()
        storeSettled(tooltype, dao.getByToolInstance(toolInstanceId) + entity, entity.id, checked.grown(grownConfig)) { settled ->
            dao.insert(settled ?: entity)
        }

        // Notify UI of data change in this tool instance
        val zoneId = getZoneIdForTool(toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(toolInstanceId, zoneId)
        }

        return OperationResult.success(
            data = mapOf(
                "id" to entity.id,
                "created_at" to entity.createdAt
            )
        )
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

        val updatedEntity = existingEntity.copy(
            data = mergedData,
            extra = mergedExtra,
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

        val after = dao.getByToolInstance(existingEntity.toolInstanceId)
            .map { if (it.id == updatedEntity.id) updatedEntity else it }
        storeSettled(existingEntity.tooltype, after, updatedEntity.id, checked.grown(grownConfig)) { settled ->
            dao.update(settled ?: updatedEntity)
        }

        // Notify UI of data change in this tool instance
        val zoneId = getZoneIdForTool(existingEntity.toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(existingEntity.toolInstanceId, zoneId)
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
        val startTime = if (params.has("start_time")) params.optLong("start_time") else null
        val endTime = if (params.has("end_time")) params.optLong("end_time") else null

        // Status of the entries to return, for tooltypes whose data has a lifecycle
        // (Messages occurrences and the future active tooltypes). Combines with the time
        // range rather than excluding it: a status filter narrows, it does not replace.
        val status = if (params.has("status")) params.optString("status") else null

        val dao = getToolDataDao()

        // Only the entries with a DURATION field running, for a screen to show and stop them
        val running = params.optBoolean("running", false)

        val (entries, totalCount) = when {
            running -> {
                val data = dao.getRunning(toolInstanceId)
                Pair(data, data.size)
            }
            // Status filter, optionally narrowed further by the time range
            status != null -> {
                val from = startTime ?: 0
                val to = endTime ?: Long.MAX_VALUE
                val count = dao.countByStatusAndTimeRange(toolInstanceId, status, from, to)
                val data = dao.getByStatusAndTimeRangePaginated(toolInstanceId, status, from, to, limit, offset)
                Pair(data, count)
            }
            // Both startTime and endTime specified
            startTime != null && endTime != null -> {
                val count = dao.countByTimeRange(toolInstanceId, startTime, endTime)
                val data = dao.getByTimeRangePaginated(toolInstanceId, startTime, endTime, limit, offset)
                Pair(data, count)
            }
            // Only startTime specified (from timestamp >= startTime)
            startTime != null -> {
                val count = dao.countByTimeRange(toolInstanceId, startTime, Long.MAX_VALUE)
                val data = dao.getByTimeRangePaginated(toolInstanceId, startTime, Long.MAX_VALUE, limit, offset)
                Pair(data, count)
            }
            // Only endTime specified (from timestamp <= endTime)
            endTime != null -> {
                val count = dao.countByTimeRange(toolInstanceId, 0, endTime)
                val data = dao.getByTimeRangePaginated(toolInstanceId, 0, endTime, limit, offset)
                Pair(data, count)
            }
            // No time filtering
            else -> {
                val count = dao.countByToolInstance(toolInstanceId)
                val data = dao.getByToolInstancePaginated(toolInstanceId, limit, offset)
                Pair(data, count)
            }
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

    private suspend fun getStats(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        }

        val dao = getToolDataDao()
        val count = dao.countByToolInstance(toolInstanceId)

        return OperationResult.success(
            mapOf(
                "count" to count
                // TODO: add first_entry and last_entry if necessary
            )
        )
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
     * Removes a custom field from all entries of a tool instance.
     *
     * Called by ToolInstanceService when a custom field is deleted from the tool config.
     * Uses SQLite json_remove() for efficient bulk update without loading entries in memory.
     *
     * @param params Must contain: toolInstanceId (string), fieldName (string)
     * @return OperationResult with updated_count
     */
    private suspend fun removeCustomFieldFromAllEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val fieldName = params.optString("field_name")

        if (toolInstanceId.isEmpty() || fieldName.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, fieldName"))
        }

        try {
            // Use direct SQL query with json_remove() for performance
            // SQLite json_remove() syntax: json_remove(json, path)
            val database = AppDatabase.getDatabase(context).openHelper.writableDatabase

            database.execSQL(
                """
                UPDATE tool_data
                SET extra = json_remove(extra, ?),
                    updated_at = ?
                WHERE tool_instance_id = ? AND extra IS NOT NULL
                """.trimIndent(),
                arrayOf("$.$fieldName", System.currentTimeMillis(), toolInstanceId)
            )

            // Count affected entries for logging
            val affectedCount = database.compileStatement(
                "SELECT changes()"
            ).simpleQueryForLong()

            LogManager.service(
                "Removed custom field '$fieldName' from $affectedCount entries in tool instance $toolInstanceId"
            )

            // Notify UI of data change
            val zoneId = getZoneIdForTool(toolInstanceId)
            if (zoneId != null) {
                DataChangeNotifier.notifyToolDataChanged(toolInstanceId, zoneId)
            }

            return OperationResult.success(mapOf(
                "updated_count" to affectedCount.toInt()
            ))
        } catch (e: Exception) {
            LogManager.service(
                "Failed to remove custom field: ${e.message}",
                "ERROR",
                e
            )
            return OperationResult.error("Failed to remove custom field: ${e.message}")
        }
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
                id = config.optString("data_schema_id"),
                displayName = tool.tooltype,
                description = "",
                category = com.assistant.core.validation.SchemaCategory.TOOL_DATA,
                content = BaseSchemas.getEntrySchema(toolType, config, context)
            )
        )
    }

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
        val declared = ToolTypeManager.getToolType(target.tool.tooltype)
            ?.getEntryFields(target.config, context)?.data?.map { it.definition } ?: emptyList()
        val userFields = target.config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        for ((fields, values) in listOf(declared to data, userFields to extra)) {
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

        filterJsonField(entry, "data", parsed.data)?.let { filtered["data"] = it }
        filterJsonField(entry, "extra", parsed.custom)?.let { filtered["extra"] = it }

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