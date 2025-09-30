package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Operation
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.AppDatabase
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.strings.Strings
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

        // Validation via ToolType
        val toolType = ToolTypeManager.getToolType(tooltype)
        if (toolType != null) {
            // Build complete structure for validation (base fields + data field)
            val fullDataMap = mutableMapOf<String, Any>().apply {
                // Base fields required by schema
                put("tool_instance_id", toolInstanceId)
                put("tooltype", tooltype)
                timestamp?.let { put("timestamp", it) }
                name?.let { put("name", it) }
                
                // Data field containing tool type specific data
                val dataContent = JSONObject(dataJson).let { json ->
                    mutableMapOf<String, Any>().apply {
                        json.keys().forEach { key ->
                            put(key, json.get(key))
                        }
                    }
                }
                put("data", dataContent)
            }

            // Use schema_id from params for validation
            val schemaId = params.optString("schema_id")
            if (schemaId.isNotEmpty()) {
                fullDataMap["schema_id"] = schemaId  // Add schema_id to root level for validation
            }

            val validation = if (schemaId.isNotEmpty()) {
                val schema = toolType.getSchema(schemaId, context)
                if (schema != null) {
                    SchemaValidator.validate(schema, fullDataMap, context)
                } else {
                    com.assistant.core.validation.ValidationResult.error("Schema not found: $schemaId")
                }
            } else {
                com.assistant.core.validation.ValidationResult.error("Missing schema_id in data")
            }
            if (!validation.isValid) {
                return OperationResult.error(s.shared("service_error_validation_failed").format(validation.errorMessage ?: ""))
            }
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

        // Validation if new data provided
        if (dataJson != null) {
            val toolType = ToolTypeManager.getToolType(existingEntity.tooltype)
            if (toolType != null) {
                // Build complete structure for validation (base fields + data field)
                val fullDataMap = mutableMapOf<String, Any>().apply {
                    // Base fields required by schema
                    put("tool_instance_id", existingEntity.toolInstanceId)
                    put("tooltype", existingEntity.tooltype)
                    put("timestamp", timestamp ?: existingEntity.timestamp ?: 0L)
                    put("name", name ?: existingEntity.name ?: "")
                    
                    // Data field containing tool type specific data
                    val dataContent = JSONObject(dataJson).let { json ->
                        mutableMapOf<String, Any>().apply {
                            json.keys().forEach { key ->
                                put(key, json.get(key))
                            }
                        }
                    }
                    put("data", dataContent)
                }
                
                // Use schema_id from data for validation
            val dataContent = JSONObject(dataJson)
            val schemaId = dataContent.optString("schema_id")

            val validation = if (schemaId.isNotEmpty()) {
                val schema = toolType.getSchema(schemaId, context)
                if (schema != null) {
                    SchemaValidator.validate(schema, fullDataMap, context)
                } else {
                    com.assistant.core.validation.ValidationResult.error("Schema not found: $schemaId")
                }
            } else {
                com.assistant.core.validation.ValidationResult.error("Missing schema_id in data")
            }
                if (!validation.isValid) {
                    return OperationResult.error(s.shared("service_error_validation_failed").format(validation.errorMessage ?: ""))
                }
            }
        }

        val updatedEntity = existingEntity.copy(
            data = dataJson ?: existingEntity.data,
            timestamp = timestamp ?: existingEntity.timestamp,
            name = name ?: existingEntity.name,
            updatedAt = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

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
        dao.deleteById(entryId)

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
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }
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
}