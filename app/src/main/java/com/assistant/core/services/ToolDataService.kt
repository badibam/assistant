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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.*

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
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_tool_data_service").format(e.message ?: ""))
        }
    }

    private suspend fun createEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        val tooltype = params.optString("tooltype")
        val dataJson = params.optJSONObject("data")?.toString() ?: "{}"
        val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else null
        val name = params.optString("name", null)
        val insertPosition = if (params.has("insert_position")) params.optInt("insert_position") else null

        if (toolInstanceId.isEmpty() || tooltype.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, tooltype"))
        }

        // Handle position-based insertion
        var finalDataJson = dataJson
        if (insertPosition != null) {
            finalDataJson = handlePositionInsertion(toolInstanceId, tooltype, dataJson, insertPosition)
        }

        val now = System.currentTimeMillis()
        val entity = ToolDataEntity(
            id = UUID.randomUUID().toString(),
            toolInstanceId = toolInstanceId,
            tooltype = tooltype,
            timestamp = timestamp,
            name = name,
            data = finalDataJson,
            createdAt = now,
            updatedAt = now
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
                "createdAt" to entity.createdAt
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
        val dataJson = params.optJSONObject("data")?.toString()
        val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else null
        val name = params.optString("name", null)

        if (entryId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_id"))
        }

        val dao = getToolDataDao()
        val existingEntity = dao.getById(entryId)
            ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(entryId))

        val updatedEntity = existingEntity.copy(
            data = dataJson ?: existingEntity.data,
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
                "updatedAt" to updatedEntity.updatedAt
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

        // Get entity before deleting to retrieve toolInstanceId for notification
        val entity = dao.getById(entryId)

        dao.deleteById(entryId)

        // Notify UI of data change in this tool instance
        if (entity != null) {
            val zoneId = getZoneIdForTool(entity.toolInstanceId)
            if (zoneId != null) {
                DataChangeNotifier.notifyToolDataChanged(entity.toolInstanceId, zoneId)
            }
        }

        return OperationResult.success()
    }

    private suspend fun getEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Debug logging
        com.assistant.core.utils.LogManager.service("ToolDataService.getEntries - Received params: $params")

        val toolInstanceId = params.optString("toolInstanceId")
        com.assistant.core.utils.LogManager.service("ToolDataService.getEntries - toolInstanceId='$toolInstanceId' (length=${toolInstanceId.length})")
        com.assistant.core.utils.LogManager.service("ToolDataService.getEntries - params keys: ${params.keys().asSequence().toList()}")

        if (toolInstanceId.isEmpty()) {
            com.assistant.core.utils.LogManager.service("ToolDataService.getEntries - toolInstanceId is empty, returning error", "ERROR")
            return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        }

        // Filtering and pagination parameters
        val limit = if (params.has("limit")) params.optInt("limit") else Int.MAX_VALUE
        val page = params.optInt("page", 1)
        val offset = (page - 1) * limit
        val startTime = if (params.has("startTime")) params.optLong("startTime") else null
        val endTime = if (params.has("endTime")) params.optLong("endTime") else null

        val dao = getToolDataDao()
        
        val (entries, totalCount) = if (startTime != null && endTime != null) {
            // Time range filtering with pagination
            val count = dao.countByTimeRange(toolInstanceId, startTime, endTime)
            val data = dao.getByTimeRangePaginated(toolInstanceId, startTime, endTime, limit, offset)
            Pair(data, count)
        } else {
            // Simple pagination (all entries)
            val count = dao.countByToolInstance(toolInstanceId)
            val data = dao.getByToolInstancePaginated(toolInstanceId, limit, offset)
            Pair(data, count)
        }
        
        val totalPages = if (totalCount == 0) 1 else ((totalCount - 1) / limit) + 1

        return OperationResult.success(
            data = mapOf(
                "entries" to entries.map { entity ->
                    mapOf(
                        "id" to entity.id,
                        "toolInstanceId" to entity.toolInstanceId,
                        "tooltype" to entity.tooltype,
                        "timestamp" to entity.timestamp,
                        "name" to entity.name,
                        "data" to entity.data,
                        "createdAt" to entity.createdAt,
                        "updatedAt" to entity.updatedAt
                    )
                },
                "pagination" to mapOf(
                    "currentPage" to page,
                    "totalPages" to totalPages,
                    "totalEntries" to totalCount,
                    "entriesPerPage" to limit
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

        return OperationResult.success(
            data = mapOf(
                "entry" to mapOf(
                    "id" to entity.id,
                    "toolInstanceId" to entity.toolInstanceId,
                    "tooltype" to entity.tooltype,
                    "timestamp" to entity.timestamp,
                    "name" to entity.name,
                    "data" to entity.data,
                    "createdAt" to entity.createdAt,
                    "updatedAt" to entity.updatedAt
                )
            )
        )
    }

    private suspend fun getStats(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
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

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        }

        val dao = getToolDataDao()
        dao.deleteByToolInstance(toolInstanceId)

        // Notify UI of data change in this tool instance
        val zoneId = getZoneIdForTool(toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(toolInstanceId, zoneId)
        }

        return OperationResult.success()
    }

    /**
     * Batch create multiple tool data entries
     * Params: toolInstanceId, tooltype, entries (array of objects with data, timestamp?, name?)
     */
    private suspend fun batchCreateEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        val tooltype = params.optString("tooltype")
        val entriesArray = params.optJSONArray("entries")

        if (toolInstanceId.isEmpty() || tooltype.isEmpty() || entriesArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, tooltype, entries"))
        }

        val dao = getToolDataDao()
        val createdIds = mutableListOf<String>()
        var successCount = 0
        var failureCount = 0

        // Process each entry
        for (i in 0 until entriesArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val entryJson = entriesArray.getJSONObject(i)

                // Build params for single create
                val singleParams = JSONObject().apply {
                    put("toolInstanceId", toolInstanceId)
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
                    com.assistant.core.utils.LogManager.service("Batch entry $i created successfully", "DEBUG")
                } else {
                    failureCount++
                    com.assistant.core.utils.LogManager.service("Batch entry $i failed: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                failureCount++
                com.assistant.core.utils.LogManager.service("Batch entry $i exception: ${e.message}", "ERROR", e)
            }
        }

        // Note: No notification here - createEntry() already notifies for each entry

        // Return error if all entries failed
        if (successCount == 0 && failureCount > 0) {
            return OperationResult.error("All batch entries failed: $failureCount failed")
        }

        return OperationResult.success(mapOf(
            "created_count" to successCount,
            "failed_count" to failureCount,
            "ids" to createdIds,
            "toolInstanceName" to toolInstanceId // For CommandExecutor system messages
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
        for (i in 0 until entriesArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val entryJson = entriesArray.getJSONObject(i)
                val entryId = entryJson.optString("id")

                if (entryId.isEmpty()) {
                    failureCount++
                    continue
                }

                // Build params for single update
                val singleParams = JSONObject().apply {
                    put("id", entryId)
                    if (entryJson.has("data")) put("data", entryJson.getJSONObject("data"))
                    if (entryJson.has("timestamp")) put("timestamp", entryJson.getLong("timestamp"))
                    if (entryJson.has("name")) put("name", entryJson.getString("name"))
                }

                // Use existing updateEntry logic
                val result = updateEntry(singleParams, token)

                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }
        }

        // Note: No notification here - updateEntry() already notifies for each entry

        // Return error if all entries failed
        if (successCount == 0 && failureCount > 0) {
            return OperationResult.error("All batch entries failed: $failureCount failed")
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

        // Process each ID
        for (i in 0 until idsArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val entryId = idsArray.getString(i)

                if (entryId.isEmpty()) {
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
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }
        }

        // Note: No notification here - deleteEntry() already notifies for each entry

        // Return error if all entries failed
        if (successCount == 0 && failureCount > 0) {
            return OperationResult.error("All batch entries failed: $failureCount failed")
        }

        return OperationResult.success(mapOf(
            "deleted_count" to successCount,
            "failed_count" to failureCount
        ))
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
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)

        return when (operation) {
            "create", "batch_create" -> {
                val toolInstanceId = params.optString("toolInstanceId")
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
                val toolInstanceId = params.optString("toolInstanceId")
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
                val toolInstanceId = params.optString("toolInstanceId")
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
     * Helper data class for tool information
     */
    private data class ToolInfo(val name: String, val zoneName: String)

    /**
     * Helper to retrieve tool and zone information
     * Note: Uses runBlocking since verbalize() is not suspend but needs DB access
     */
    private fun getToolInfo(toolInstanceId: String, context: Context): ToolInfo {
        val s = Strings.`for`(context = context)
        val defaultName = s.shared("content_unnamed")

        if (toolInstanceId.isBlank()) {
            return ToolInfo(defaultName, defaultName)
        }

        return runBlocking {
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

            ToolInfo(toolName, zoneName)
        }
    }
}