package com.assistant.core.services

import android.content.Context
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
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject
import java.util.*
import com.assistant.core.validation.FieldPatternGrammar
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
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_tool_data_service").format(e.message ?: ""))
        }
    }

    private suspend fun createEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val tooltype = params.optString("tooltype")
        val name = params.optString("name", null)
        val insertPosition = if (params.has("insert_position")) params.optInt("insert_position") else null

        if (toolInstanceId.isEmpty() || tooltype.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, tooltype"))
        }

        // Get app timezone for ISO ↔ timestamp conversion
        val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

        // Convert ISO → timestamps in data (recursive)
        val dataJson = params.optJSONObject("data")?.let { dataObj ->
            DateTimeConverter.isoToTimestamps(dataObj, appTimezone).toString()
        } ?: "{}"

        // Convert ISO → timestamps in custom_fields (recursive)
        val customFieldsJson = params.optJSONObject("custom_fields")?.let { customFieldsObj ->
            DateTimeConverter.isoToTimestamps(customFieldsObj, appTimezone).toString()
        }

        // Milliseconds are the contract. An absent timestamp means now, which is a default
        // written into the contract; any number is taken as milliseconds, Int and Double
        // included, since JSON decides the width on its own. Anything else is refused: a type
        // nobody expected used to be answered with the present moment, and no caller could tell
        // that apart from an entry genuinely recorded now.
        val timestamp = when {
            !params.has("timestamp") -> System.currentTimeMillis()
            params.opt("timestamp") is Number -> (params.opt("timestamp") as Number).toLong()
            params.opt("timestamp") is String -> {
                try {
                    DateTimeConverter.isoToTimestamp(params.optString("timestamp"), appTimezone)
                } catch (e: IllegalArgumentException) {
                    return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.optString("timestamp")))
                }
            }
            else -> return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.opt("timestamp").toString()))
        }

        // Handle position-based insertion
        var finalDataJson = dataJson
        if (insertPosition != null) {
            finalDataJson = handlePositionInsertion(toolInstanceId, tooltype, dataJson, insertPosition)
        }

        // Enrich data with auto-generated fields (e.g., raw display field for tracking)
        finalDataJson = enrichDataIfSupported(tooltype, toolInstanceId, finalDataJson, name)

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
            customFields = customFieldsJson  // Store custom fields separately
        )

        val dao = getToolDataDao()
        dao.insert(entity)

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
     * Handle position-based insertion by shifting existing positions and setting new position
     */
    private suspend fun handlePositionInsertion(
        toolInstanceId: String,
        tooltype: String,
        dataJson: String,
        insertPosition: Int
    ): String {
        val dao = getToolDataDao()
        val existingEntries = dao.getByToolInstance(toolInstanceId)

        // 1. Shift positions >= insertPosition
        existingEntries.forEach { entry ->
            val entryData = JSONObject(entry.data)
            val currentPosition = entryData.optInt("position", -1)

            if (currentPosition >= insertPosition) {
                entryData.put("position", currentPosition + 1)
                // Update in DB
                val updatedEntity = entry.copy(
                    data = entryData.toString(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.update(updatedEntity)
            }
        }

        // 2. Set position for new entry
        val newData = JSONObject(dataJson)
        newData.put("position", insertPosition)
        return newData.toString()
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

        // Get app timezone for ISO ↔ timestamp conversion
        val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

        // Convert ISO → timestamps in data (recursive)
        val dataJson = params.optJSONObject("data")?.let { dataObj ->
            DateTimeConverter.isoToTimestamps(dataObj, appTimezone).toString()
        }

        // Convert ISO → timestamps in custom_fields (recursive)
        val customFieldsJson = params.optJSONObject("custom_fields")?.let { customFieldsObj ->
            DateTimeConverter.isoToTimestamps(customFieldsObj, appTimezone).toString()
        }

        // Milliseconds are the contract. An absent timestamp leaves the recorded one alone;
        // any number is taken as milliseconds. Anything else is refused: an unreadable type used
        // to be dropped here, so an update meant to move an entry in time did nothing and said
        // nothing.
        val timestamp = when {
            !params.has("timestamp") -> null
            params.opt("timestamp") is Number -> (params.opt("timestamp") as Number).toLong()
            params.opt("timestamp") is String -> {
                try {
                    DateTimeConverter.isoToTimestamp(params.optString("timestamp"), appTimezone)
                } catch (e: IllegalArgumentException) {
                    return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.optString("timestamp")))
                }
            }
            else -> return OperationResult.error(s.shared("service_error_invalid_timestamp_format").format(params.opt("timestamp").toString()))
        }

        // Merge JSON data: new fields overwrite, absent fields are preserved (e.g. systemManaged fields)
        // Protection layers: AI commands have systemManaged fields stripped, UI doesn't expose them
        val mergedData = if (dataJson != null) {
            val existingJson = JSONObject(existingEntity.data)
            val newJson = JSONObject(dataJson)

            // Copy all keys from newJson into existingJson (overwrite present, preserve absent)
            newJson.keys().forEach { key ->
                existingJson.put(key, newJson.get(key))
            }

            // Enrich data with auto-generated fields (e.g., raw for tracking)
            enrichDataIfSupported(existingEntity.tooltype, existingEntity.toolInstanceId, existingJson.toString(), name)
        } else {
            existingEntity.data
        }

        // Merge custom fields: new fields overwrite, absent fields are preserved, null values remove fields
        val mergedCustomFields = if (customFieldsJson != null) {
            val existingCustomFields = if (existingEntity.customFields != null) {
                JSONObject(existingEntity.customFields)
            } else {
                JSONObject()
            }
            val newCustomFields = JSONObject(customFieldsJson)

            LogManager.service(
                "DEBUG updateEntry: Merging custom_fields - existing=$existingCustomFields, new=$newCustomFields",
                "DEBUG"
            )

            // Copy all keys from newCustomFields into existingCustomFields
            // - Regular values: overwrite present, preserve absent
            // - null values: remove the field (for migration support)
            newCustomFields.keys().forEach { key ->
                // Use isNull() to detect JSON null values (handles both JSONObject.NULL and parsed null)
                if (newCustomFields.isNull(key)) {
                    // Remove field explicitly set to null
                    LogManager.service("DEBUG updateEntry: Removing field '$key' (null value)", "DEBUG")
                    existingCustomFields.remove(key)
                } else {
                    // Update/add field
                    val value = newCustomFields.get(key)
                    existingCustomFields.put(key, value)
                }
            }

            LogManager.service(
                "DEBUG updateEntry: Result custom_fields=$existingCustomFields",
                "DEBUG"
            )

            existingCustomFields.toString()
        } else {
            existingEntity.customFields
        }

        val updatedEntity = existingEntity.copy(
            data = mergedData,
            customFields = mergedCustomFields,
            timestamp = timestamp ?: existingEntity.timestamp,
            name = name ?: existingEntity.name,
            updatedAt = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

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

        dao.deleteById(entryId)

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

        val (entries, totalCount) = when {
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

        // Get app timezone for timestamp → ISO conversion
        val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

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
                    // Convert timestamps → ISO in data (recursive), and hand out an object:
                    // the string form belongs to the database, not to the callers.
                    val dataWithISO = JsonUtils.toMap(
                        DateTimeConverter.timestampsToISO(JSONObject(entity.data), appTimezone)
                    )

                    // Convert timestamps → ISO in custom_fields (recursive)
                    val customFieldsWithISO = entity.customFields?.let { cf ->
                        JsonUtils.toMap(
                            DateTimeConverter.timestampsToISO(JSONObject(cf), appTimezone)
                        )
                    }

                    val fullEntry = mapOf(
                        "id" to entity.id,
                        "tool_instance_id" to entity.toolInstanceId,
                        "tooltype" to entity.tooltype,
                        "timestamp" to entity.timestamp?.let { DateTimeConverter.timestampToISO(it, appTimezone) },
                        "name" to entity.name,
                        "data" to dataWithISO,
                        "custom_fields" to customFieldsWithISO,  // Use underscore for consistency with DB and configs
                        "created_at" to DateTimeConverter.timestampToISO(entity.createdAt, appTimezone),
                        "updated_at" to DateTimeConverter.timestampToISO(entity.updatedAt, appTimezone)
                    )

                    // Apply fields filter if provided
                    if (fieldsFilter != null) {
                        filterEntryFields(fullEntry, fieldsFilter)  // Return filtered entry
                    } else {
                        fullEntry  // Return full entry if no filter
                    }
                },
                "pagination" to mapOf(
                    "current_page" to page,
                    "total_pages" to totalPages,
                    "total_entries" to totalCount,
                    "entries_per_page" to limit
                )
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

        // Get app timezone for timestamp → ISO conversion
        val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

        // Convert timestamps → ISO in data (recursive)
        val dataWithISO = JSONObject(entity.data).let { dataObj ->
            DateTimeConverter.timestampsToISO(dataObj, appTimezone).toString()
        }

        // Convert timestamps → ISO in custom_fields (recursive)
        val customFieldsWithISO = entity.customFields?.let { cf ->
            JsonUtils.toMap(
                DateTimeConverter.timestampsToISO(JSONObject(cf), appTimezone)
            )
        }

        return OperationResult.success(
            data = mapOf(
                "entry" to mapOf(
                    "id" to entity.id,
                    "tool_instance_id" to entity.toolInstanceId,
                    "tooltype" to entity.tooltype,
                    "timestamp" to entity.timestamp?.let { DateTimeConverter.timestampToISO(it, appTimezone) },
                    "name" to entity.name,
                    "data" to dataWithISO,
                    "custom_fields" to customFieldsWithISO,  // Use underscore for consistency with DB and configs
                    "created_at" to DateTimeConverter.timestampToISO(entity.createdAt, appTimezone),
                    "updated_at" to DateTimeConverter.timestampToISO(entity.updatedAt, appTimezone)
                )
            )
        )
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
     * Params: toolInstanceId, tooltype, entries (array of objects with data, timestamp?, name?)
     */
    private suspend fun batchCreateEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val tooltype = params.optString("tooltype")
        val entriesArray = params.optJSONArray("entries")

        if (toolInstanceId.isEmpty() || tooltype.isEmpty() || entriesArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, tooltype, entries"))
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

                // Build params for single create
                val singleParams = JSONObject().apply {
                    put("tool_instance_id", toolInstanceId)
                    put("tooltype", tooltype)
                    put("data", entryJson.optJSONObject("data") ?: JSONObject())
                    if (entryJson.has("timestamp")) put("timestamp", entryJson.getLong("timestamp"))
                    if (entryJson.has("name")) put("name", entryJson.getString("name"))
                    if (entryJson.has("schema_id")) put("schema_id", entryJson.getString("schema_id"))
                }

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

                // Build params for single update
                val singleParams = JSONObject().apply {
                    put("id", entryId)
                    if (entryJson.has("data")) put("data", entryJson.getJSONObject("data"))
                    if (entryJson.has("custom_fields")) put("custom_fields", entryJson.getJSONObject("custom_fields"))
                    if (entryJson.has("timestamp")) put("timestamp", entryJson.getLong("timestamp"))
                    if (entryJson.has("name")) put("name", entryJson.getString("name"))
                }

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
                SET custom_fields = json_remove(custom_fields, ?),
                    updated_at = ?
                WHERE tool_instance_id = ? AND custom_fields IS NOT NULL
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

    /**
     * Enrich data using ToolType's enrichData() method if supported
     * Returns enriched data, or original data if:
     * - ToolType not found
     * - Config not found
     * - enrichData() not overridden (returns data unchanged)
     *
     * @param tooltype The tooltype name (e.g., "tracking", "notes")
     * @param toolInstanceId The tool instance ID (to fetch config)
     * @param dataJson The data JSON to enrich
     * @param name The entry name (optional)
     * @return Enriched data JSON
     */
    private suspend fun enrichDataIfSupported(
        tooltype: String,
        toolInstanceId: String,
        dataJson: String,
        name: String?
    ): String {
        return try {
            // Get ToolType from ToolTypeManager
            val toolType = ToolTypeManager.getToolType(tooltype) ?: return dataJson

            // Get tool instance config
            val coordinator = Coordinator(context)
            val toolResult = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            val configJson = if (toolResult.status == CommandStatus.SUCCESS) {
                val tool = toolResult.data?.get("tool_instance") as? Map<*, *>
                @Suppress("UNCHECKED_CAST")
                (tool?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it).toString() }
            } else null

            // Call enrichData() - default implementation returns data unchanged
            toolType.enrichData(dataJson, name, configJson)
        } catch (e: Exception) {
            // Log error but return original data (enrichment is not critical for data creation)
            LogManager.service(
                "Failed to enrich data for tooltype=$tooltype: ${e.message}",
                "WARN",
                e
            )
            dataJson
        }
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
        filterJsonField(entry, "custom_fields", parsed.custom)?.let { filtered["custom_fields"] = it }

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