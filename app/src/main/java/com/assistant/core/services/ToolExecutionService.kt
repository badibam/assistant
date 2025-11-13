package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.database.entities.ToolExecutionEntity
import com.assistant.core.database.dao.BaseToolExecutionDao
import com.assistant.core.database.AppDatabase
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.*

/**
 * Centralized service for all tool_executions operations
 * Manages execution history for tooltypes that schedule or trigger executions
 * (Messages, Goals, Alerts, Questionnaires, etc.)
 */
class ToolExecutionService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        return try {
            when (operation) {
                "create" -> createExecution(params, token)
                "update" -> updateExecution(params, token)
                "delete" -> deleteExecution(params, token)
                "get" -> getExecutions(params, token)
                "get_single" -> getSingleExecution(params, token)
                "stats" -> getStats(params, token)
                "batch_create" -> batchCreateExecutions(params, token)
                "batch_update" -> batchUpdateExecutions(params, token)
                "batch_delete" -> batchDeleteExecutions(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_tool_execution_service").format(e.message ?: ""))
        }
    }

    private suspend fun createExecution(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        val tooltype = params.optString("tooltype")
        val templateDataId = params.optString("templateDataId")
        val executionTime = params.optLong("executionTime", System.currentTimeMillis())
        val status = params.optString("status", "completed")
        val triggeredBy = params.optString("triggeredBy", "MANUAL")
        val scheduledTime = if (params.has("scheduledTime")) params.optLong("scheduledTime") else null
        val snapshotDataJson = params.optJSONObject("snapshotData") ?: JSONObject()
        val executionResult = params.optJSONObject("executionResult")?.toString() ?: "{}"
        val metadata = params.optJSONObject("metadata")?.toString() ?: "{}"

        if (toolInstanceId.isEmpty() || tooltype.isEmpty() || templateDataId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, tooltype, templateDataId"))
        }

        // Enrich snapshotData with custom_fields_metadata from tool instance config
        // This makes executions self-contained: they preserve the field definitions at execution time
        try {
            val coordinator = Coordinator(context)
            val configResult = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            if (configResult.status == CommandStatus.SUCCESS) {
                val toolInstance = configResult.data?.get("tool_instance") as? Map<*, *>
                val configJson = toolInstance?.get("config_json") as? String

                if (configJson != null && configJson.isNotEmpty()) {
                    try {
                        val config = JSONObject(configJson)
                        val customFieldsArray = config.optJSONArray("custom_fields")

                        // Add custom_fields_metadata to snapshot if custom_fields exist in config
                        if (customFieldsArray != null && customFieldsArray.length() > 0) {
                            snapshotDataJson.put("custom_fields_metadata", customFieldsArray)
                            com.assistant.core.utils.LogManager.service(
                                "Enriched execution snapshot with ${customFieldsArray.length()} custom field definitions",
                                "DEBUG"
                            )
                        }
                    } catch (e: Exception) {
                        com.assistant.core.utils.LogManager.service(
                            "Failed to parse config_json when enriching snapshot: ${e.message}",
                            "WARN",
                            e
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail execution creation
            // Worst case: snapshot won't have metadata, will fall back to current config
            com.assistant.core.utils.LogManager.service(
                "Failed to enrich snapshot with custom_fields_metadata for execution: ${e.message}",
                "WARN",
                e
            )
        }

        val now = System.currentTimeMillis()
        val entity = ToolExecutionEntity(
            id = UUID.randomUUID().toString(),
            toolInstanceId = toolInstanceId,
            tooltype = tooltype,
            templateDataId = templateDataId,
            scheduledTime = scheduledTime,
            executionTime = executionTime,
            status = status,
            snapshotData = snapshotDataJson.toString(),
            executionResult = executionResult,
            triggeredBy = triggeredBy,
            metadata = metadata,
            createdAt = now,
            updatedAt = now
        )

        val dao = getToolExecutionDao()
        dao.insert(entity)

        // Notify UI of execution change in this tool instance
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

    private suspend fun updateExecution(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val executionId = params.optString("id")
        if (executionId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_id"))
        }

        val dao = getToolExecutionDao()
        val existingEntity = dao.getById(executionId)
            ?: return OperationResult.error(s.shared("service_error_execution_not_found").format(executionId))

        // Merge JSON fields: snapshotData, executionResult, metadata
        val mergedSnapshotData = if (params.has("snapshotData")) {
            params.optJSONObject("snapshotData")?.toString() ?: existingEntity.snapshotData
        } else {
            existingEntity.snapshotData
        }

        val mergedExecutionResult = if (params.has("executionResult")) {
            params.optJSONObject("executionResult")?.toString() ?: existingEntity.executionResult
        } else {
            existingEntity.executionResult
        }

        val mergedMetadata = if (params.has("metadata")) {
            params.optJSONObject("metadata")?.toString() ?: existingEntity.metadata
        } else {
            existingEntity.metadata
        }

        val updatedEntity = existingEntity.copy(
            status = params.optString("status", existingEntity.status),
            executionTime = params.optLong("executionTime", existingEntity.executionTime),
            scheduledTime = if (params.has("scheduledTime")) params.optLong("scheduledTime") else existingEntity.scheduledTime,
            snapshotData = mergedSnapshotData,
            executionResult = mergedExecutionResult,
            metadata = mergedMetadata,
            updatedAt = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        // Notify UI of execution change in this tool instance
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

    private suspend fun deleteExecution(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val executionId = params.optString("id")
        if (executionId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_id"))
        }

        val dao = getToolExecutionDao()

        // Verify execution exists before deletion
        val entity = dao.getById(executionId)
            ?: return OperationResult.error(s.shared("service_error_execution_not_found").format(executionId))

        dao.deleteById(executionId)

        // Notify UI of execution change in this tool instance
        val zoneId = getZoneIdForTool(entity.toolInstanceId)
        if (zoneId != null) {
            DataChangeNotifier.notifyToolDataChanged(entity.toolInstanceId, zoneId)
        }

        return OperationResult.success(mapOf(
            "id" to executionId,
            "deleted_at" to System.currentTimeMillis()
        ))
    }

    private suspend fun getExecutions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        }

        // Filtering and pagination parameters
        val limit = if (params.has("limit")) params.optInt("limit") else Int.MAX_VALUE
        val page = params.optInt("page", 1)
        val offset = (page - 1) * limit
        val startTime = if (params.has("startTime")) params.optLong("startTime") else null
        val endTime = if (params.has("endTime")) params.optLong("endTime") else null
        val status = params.optString("status", "")
        val templateDataId = params.optString("templateDataId", "")

        val dao = getToolExecutionDao()

        // Build query based on filters
        val (executions, totalCount) = when {
            // Filter by template
            templateDataId.isNotEmpty() -> {
                val data = dao.getByTemplate(templateDataId)
                Pair(data, data.size)
            }
            // Filter by status
            status.isNotEmpty() -> {
                val data = dao.getByStatus(toolInstanceId, status)
                Pair(data, data.size)
            }
            // Filter by time range
            startTime != null && endTime != null -> {
                val count = dao.countByTimeRange(toolInstanceId, startTime, endTime)
                val data = dao.getByTimeRangePaginated(toolInstanceId, startTime, endTime, limit, offset)
                Pair(data, count)
            }
            // All executions with pagination
            else -> {
                val count = dao.countByToolInstance(toolInstanceId)
                val data = dao.getByToolInstancePaginated(toolInstanceId, limit, offset)
                Pair(data, count)
            }
        }

        val totalPages = if (totalCount == 0) 1 else ((totalCount - 1) / limit) + 1

        return OperationResult.success(
            data = mapOf(
                "executions" to executions.map { entity ->
                    mapOf(
                        "id" to entity.id,
                        "toolInstanceId" to entity.toolInstanceId,
                        "tooltype" to entity.tooltype,
                        "templateDataId" to entity.templateDataId,
                        "scheduledTime" to entity.scheduledTime,
                        "executionTime" to entity.executionTime,
                        "status" to entity.status,
                        "snapshotData" to entity.snapshotData,
                        "executionResult" to entity.executionResult,
                        "triggeredBy" to entity.triggeredBy,
                        "metadata" to entity.metadata,
                        "createdAt" to entity.createdAt,
                        "updatedAt" to entity.updatedAt
                    )
                },
                "pagination" to mapOf(
                    "currentPage" to page,
                    "totalPages" to totalPages,
                    "totalExecutions" to totalCount,
                    "executionsPerPage" to limit
                )
            )
        )
    }

    private suspend fun getSingleExecution(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val executionId = params.optString("execution_id")
        if (executionId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_execution_id"))
        }

        val dao = getToolExecutionDao()
        val entity = dao.getById(executionId)
            ?: return OperationResult.error(s.shared("service_error_execution_not_found").format(executionId))

        return OperationResult.success(
            data = mapOf(
                "execution" to mapOf(
                    "id" to entity.id,
                    "toolInstanceId" to entity.toolInstanceId,
                    "tooltype" to entity.tooltype,
                    "templateDataId" to entity.templateDataId,
                    "scheduledTime" to entity.scheduledTime,
                    "executionTime" to entity.executionTime,
                    "status" to entity.status,
                    "snapshotData" to entity.snapshotData,
                    "executionResult" to entity.executionResult,
                    "triggeredBy" to entity.triggeredBy,
                    "metadata" to entity.metadata,
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

        val dao = getToolExecutionDao()
        val totalCount = dao.countByToolInstance(toolInstanceId)
        val pendingCount = dao.getByStatus(toolInstanceId, "pending").size
        val completedCount = dao.getByStatus(toolInstanceId, "completed").size
        val failedCount = dao.getByStatus(toolInstanceId, "failed").size

        return OperationResult.success(
            mapOf(
                "total_count" to totalCount,
                "pending_count" to pendingCount,
                "completed_count" to completedCount,
                "failed_count" to failedCount
            )
        )
    }

    /**
     * Batch create multiple executions
     * Params: toolInstanceId, tooltype, executions (array of objects)
     */
    private suspend fun batchCreateExecutions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        val tooltype = params.optString("tooltype")
        val executionsArray = params.optJSONArray("executions")

        if (toolInstanceId.isEmpty() || tooltype.isEmpty() || executionsArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId, tooltype, executions"))
        }

        val createdIds = mutableListOf<String>()
        var successCount = 0
        var failureCount = 0

        // Process each execution
        for (i in 0 until executionsArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val executionJson = executionsArray.getJSONObject(i)

                // Build params for single create
                val singleParams = JSONObject().apply {
                    put("toolInstanceId", toolInstanceId)
                    put("tooltype", tooltype)
                    put("templateDataId", executionJson.getString("templateDataId"))
                    if (executionJson.has("executionTime")) put("executionTime", executionJson.getLong("executionTime"))
                    if (executionJson.has("scheduledTime")) put("scheduledTime", executionJson.getLong("scheduledTime"))
                    if (executionJson.has("status")) put("status", executionJson.getString("status"))
                    if (executionJson.has("triggeredBy")) put("triggeredBy", executionJson.getString("triggeredBy"))
                    if (executionJson.has("snapshotData")) put("snapshotData", executionJson.getJSONObject("snapshotData"))
                    if (executionJson.has("executionResult")) put("executionResult", executionJson.getJSONObject("executionResult"))
                    if (executionJson.has("metadata")) put("metadata", executionJson.getJSONObject("metadata"))
                }

                // Use existing createExecution logic
                val result = createExecution(singleParams, token)

                if (result.success) {
                    result.data?.get("id")?.let { createdIds.add(it.toString()) }
                    successCount++
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
                com.assistant.core.utils.LogManager.service("Batch execution $i exception: ${e.message}", "ERROR", e)
            }
        }

        // Return error if ALL executions failed
        if (successCount == 0 && failureCount > 0) {
            return OperationResult.error("All batch executions failed: $failureCount failed")
        }

        return OperationResult.success(mapOf(
            "created_count" to successCount,
            "failed_count" to failureCount,
            "ids" to createdIds
        ))
    }

    /**
     * Batch update multiple executions
     * Params: executions (array of objects with id + fields to update)
     */
    private suspend fun batchUpdateExecutions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val executionsArray = params.optJSONArray("executions")

        if (executionsArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("executions"))
        }

        var successCount = 0
        var failureCount = 0

        // Process each execution
        for (i in 0 until executionsArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val executionJson = executionsArray.getJSONObject(i)
                val executionId = executionJson.optString("id")

                if (executionId.isEmpty()) {
                    failureCount++
                    continue
                }

                // Build params for single update
                val singleParams = JSONObject().apply {
                    put("id", executionId)
                    if (executionJson.has("status")) put("status", executionJson.getString("status"))
                    if (executionJson.has("executionTime")) put("executionTime", executionJson.getLong("executionTime"))
                    if (executionJson.has("scheduledTime")) put("scheduledTime", executionJson.getLong("scheduledTime"))
                    if (executionJson.has("snapshotData")) put("snapshotData", executionJson.getJSONObject("snapshotData"))
                    if (executionJson.has("executionResult")) put("executionResult", executionJson.getJSONObject("executionResult"))
                    if (executionJson.has("metadata")) put("metadata", executionJson.getJSONObject("metadata"))
                }

                // Use existing updateExecution logic
                val result = updateExecution(singleParams, token)

                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }
        }

        // Return error if ALL executions failed
        if (successCount == 0 && failureCount > 0) {
            return OperationResult.error("All batch executions failed: $failureCount failed")
        }

        return OperationResult.success(mapOf(
            "updated_count" to successCount,
            "failed_count" to failureCount
        ))
    }

    /**
     * Batch delete multiple executions
     * Params: ids (array of execution IDs to delete)
     */
    private suspend fun batchDeleteExecutions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val idsArray = params.optJSONArray("ids")

        if (idsArray == null) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("ids"))
        }

        var successCount = 0
        var failureCount = 0

        // Process each ID
        for (i in 0 until idsArray.length()) {
            if (token.isCancelled) return OperationResult.cancelled()

            try {
                val executionId = idsArray.getString(i)

                if (executionId.isEmpty()) {
                    failureCount++
                    continue
                }

                // Build params for single delete
                val singleParams = JSONObject().apply {
                    put("id", executionId)
                }

                // Use existing deleteExecution logic
                val result = deleteExecution(singleParams, token)

                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }
        }

        // Return error if ALL executions failed
        if (successCount == 0 && failureCount > 0) {
            return OperationResult.error("All batch executions failed: $failureCount failed")
        }

        return OperationResult.success(mapOf(
            "deleted_count" to successCount,
            "failed_count" to failureCount
        ))
    }

    /**
     * Gets the unified tool_executions DAO
     */
    private fun getToolExecutionDao(): BaseToolExecutionDao {
        return AppDatabase.getDatabase(context).toolExecutionDao()
    }

    /**
     * Generates human-readable description of tool execution action
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)

        return when (operation) {
            "create", "batch_create" -> {
                val toolInstanceId = params.optString("toolInstanceId")
                val toolInfo = getToolInfo(toolInstanceId, context)

                val count = if (operation == "batch_create") {
                    params.optJSONArray("executions")?.length() ?: 1
                } else 1

                s.shared("action_verbalize_create_execution").format(
                    toolInfo.name,
                    toolInfo.zoneName,
                    count
                )
            }
            "update", "batch_update" -> {
                val toolInstanceId = params.optString("toolInstanceId")
                val toolInfo = getToolInfo(toolInstanceId, context)

                val count = if (operation == "batch_update") {
                    params.optJSONArray("executions")?.length() ?: 1
                } else 1

                s.shared("action_verbalize_update_execution").format(
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

                s.shared("action_verbalize_delete_execution").format(
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
