package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Operation
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.AppDatabase
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import org.json.JSONObject
import java.util.*

/**
 * Centralized service for all tool_data operations
 * Replaces specialized services (TrackingService, etc.)
 */
class ToolDataService(private val context: Context) : ExecutableService {

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
                else -> OperationResult.error("Unknown operation: $operation")
            }
        } catch (e: Exception) {
            OperationResult.error("ToolDataService error: ${e.message}")
        }
    }

    private suspend fun createEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        val tooltype = params.optString("tooltype")
        val dataJson = params.optJSONObject("data")?.toString() ?: "{}"
        val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else null
        val name = params.optString("name", null)

        if (toolInstanceId.isEmpty() || tooltype.isEmpty()) {
            return OperationResult.error("Missing required parameters: toolInstanceId, tooltype")
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
            
            val validation = SchemaValidator.validate(toolType, fullDataMap, context, schemaType = "data")
            if (!validation.isValid) {
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }

        val now = System.currentTimeMillis()
        val dataVersion = toolType?.getCurrentDataVersion() ?: 1
        val entity = ToolDataEntity(
            id = UUID.randomUUID().toString(),
            toolInstanceId = toolInstanceId,
            tooltype = tooltype,
            dataVersion = dataVersion,
            timestamp = timestamp,
            name = name,
            data = dataJson,
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

    private suspend fun updateEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val entryId = params.optString("id")
        val dataJson = params.optJSONObject("data")?.toString()
        val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else null
        val name = params.optString("name", null)

        if (entryId.isEmpty()) {
            return OperationResult.error("Missing required parameter: id")
        }

        val dao = getToolDataDao()
        val existingEntity = dao.getById(entryId)
            ?: return OperationResult.error("Entry not found: $entryId")

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
                
                val validation = SchemaValidator.validate(toolType, fullDataMap, context, schemaType = "data")
                if (!validation.isValid) {
                    return OperationResult.error("Validation failed: ${validation.errorMessage}")
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
            return OperationResult.error("Missing required parameter: id")
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
            return OperationResult.error("Missing required parameter: toolInstanceId")
        }

        // Filtering and pagination parameters
        val limit = params.optInt("limit", 100)
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
                        "dataVersion" to entity.dataVersion,
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
            return OperationResult.error("Missing required parameter: entry_id")
        }

        val dao = getToolDataDao()
        val entity = dao.getById(entryId)
            ?: return OperationResult.error("Entry not found: $entryId")

        return OperationResult.success(
            data = mapOf(
                "entry" to mapOf(
                    "id" to entity.id,
                    "toolInstanceId" to entity.toolInstanceId,
                    "tooltype" to entity.tooltype,
                    "dataVersion" to entity.dataVersion,
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
            return OperationResult.error("Missing required parameter: toolInstanceId")
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
            return OperationResult.error("Missing required parameter: toolInstanceId")
        }

        val dao = getToolDataDao()
        dao.deleteByToolInstance(toolInstanceId)

        return OperationResult.success()
    }

    /**
     * Gets the unified tool_data DAO
     */
    private fun getToolDataDao(): BaseToolDataDao {
        return AppDatabase.getDatabase(context).toolDataDao()
    }
}

/**
 * Result of a service operation
 */
data class OperationResult(
    val success: Boolean,
    val data: Map<String, Any>? = null,
    val error: String? = null,
    val cancelled: Boolean = false,
    // Multi-step operation support
    val requiresBackground: Boolean = false,     // Phase 1 → 2: needs background processing
    val requiresContinuation: Boolean = false    // Phase 2 → 3: needs final step
) {
    companion object {
        fun success(
            data: Map<String, Any>? = null, 
            requiresBackground: Boolean = false,
            requiresContinuation: Boolean = false
        ) = OperationResult(true, data, requiresBackground = requiresBackground, requiresContinuation = requiresContinuation)
        
        fun error(message: String) = OperationResult(false, error = message)
        fun cancelled() = OperationResult(false, cancelled = true)
    }
}