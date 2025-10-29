package com.assistant.tools.messages

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Service for Messages tool operations
 *
 * Responsibilities:
 * - Execution history queries (get_history with filters)
 * - Execution flag updates (mark_read, mark_archived)
 * - Statistics aggregation (including execution counts)
 *
 * Architecture:
 * - Messages use ToolDataEntity for templates (config data with schedule)
 * - Executions stored in tool_executions table (unified execution history)
 * - CRUD operations handled by ToolDataService (enrichData() calculates nextExecutionTime)
 * - Execution operations delegate to tool_executions service
 */
class MessageService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "messages", context = context)
    private val coordinator = Coordinator(context)

    override fun verbalize(
        operation: String,
        params: JSONObject,
        context: Context
    ): String {
        return when (operation) {
            "get_history" -> s.tool("verbalize_get_history")
            "mark_read" -> s.tool("verbalize_mark_read")
            "mark_archived" -> s.tool("verbalize_mark_archived")
            "stats" -> s.tool("verbalize_stats")
            else -> s.shared("verbalize_unknown_operation").format(operation)
        }
    }

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        return try {
            when (operation) {
                // Messages-specific operations
                "get_history" -> getHistory(params, token)
                "mark_read" -> markRead(params, token)
                "mark_archived" -> markArchived(params, token)
                "stats" -> getStats(params, token)

                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            LogManager.service("MessageService.execute($operation) failed: ${e.message}", "ERROR", e)
            OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
    }

    // ========================================
    // Messages-specific operations
    // ========================================

    /**
     * Get execution history with filters
     *
     * Returns all executions from tool_executions table for this instance,
     * with optional filters for read/archived status.
     *
     * Params:
     * - toolInstanceId: String (required)
     * - filters: JSONObject (optional)
     *   - read: Boolean? (null = ignore filter)
     *   - archived: Boolean? (null = ignore filter)
     * - limit: Int (optional) - max executions to return
     * - page: Int (optional) - page number for pagination
     *
     * Returns:
     * - executions: Array of execution records from tool_executions
     *   Sorted by execution_time DESC (most recent first)
     */
    private suspend fun getHistory(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId"))
        }

        // Extract filters
        val filters = params.optJSONObject("filters")
        val readFilter = filters?.opt("read") as? Boolean
        val archivedFilter = filters?.opt("archived") as? Boolean

        // Delegate to tool_executions service
        val executionsParams = JSONObject().apply {
            put("toolInstanceId", toolInstanceId)
            if (params.has("limit")) put("limit", params.getInt("limit"))
            if (params.has("page")) put("page", params.getInt("page"))
        }

        val result = coordinator.processUserAction("tool_executions.get", executionsParams.toMap())

        if (!result.isSuccess) {
            return OperationResult.error(result.error ?: s.shared("service_error_operation_failed"))
        }

        // Get executions and apply client-side filters (read/archived from execution_result)
        @Suppress("UNCHECKED_CAST")
        val allExecutions = (result.data?.get("executions") as? List<Map<String, Any>>) ?: emptyList()

        val filteredExecutions = allExecutions.filter { execution ->
            @Suppress("UNCHECKED_CAST")
            val executionResultStr = execution["executionResult"] as? String
            if (executionResultStr != null) {
                try {
                    val executionResult = JSONObject(executionResultStr)
                    val read = executionResult.optBoolean("read", false)
                    val archived = executionResult.optBoolean("archived", false)

                    val matchesReadFilter = readFilter == null || read == readFilter
                    val matchesArchivedFilter = archivedFilter == null || archived == archivedFilter

                    matchesReadFilter && matchesArchivedFilter
                } catch (e: Exception) {
                    true // Include if can't parse
                }
            } else {
                true // Include if no executionResult
            }
        }

        return OperationResult.success(data = mapOf(
            "executions" to filteredExecutions,
            "pagination" to (result.data?.get("pagination") ?: mapOf<String, Any>())
        ))
    }

    /**
     * Helper to convert JSONObject to Map recursively
     */
    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            val value = get(key)
            map[key] = when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value
            }
        }
        return map
    }

    /**
     * Helper to convert JSONArray to List recursively
     */
    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until length()) {
            val value = get(i)
            list.add(when (value) {
                is JSONObject -> value.toMap()
                is JSONArray -> value.toList()
                else -> value
            })
        }
        return list
    }

    /**
     * Mark execution as read/unread
     *
     * Params:
     * - execution_id: String (required) - ID of the execution in tool_executions table
     * - read: Boolean (required) - New read status
     *
     * Returns:
     * - success: Boolean
     */
    private suspend fun markRead(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val executionId = params.optString("execution_id")
        val read = params.optBoolean("read")

        if (executionId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("execution_id"))
        }

        return updateExecutionFlag(executionId, "read", read)
    }

    /**
     * Mark execution as archived/unarchived
     *
     * Params:
     * - execution_id: String (required) - ID of the execution in tool_executions table
     * - archived: Boolean (required)
     *
     * Returns:
     * - success: Boolean
     */
    private suspend fun markArchived(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val executionId = params.optString("execution_id")
        val archived = params.optBoolean("archived")

        if (executionId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("execution_id"))
        }

        return updateExecutionFlag(executionId, "archived", archived)
    }

    /**
     * Update a flag in a specific execution's execution_result
     *
     * @param executionId ID of execution in tool_executions table
     * @param flagName Name of flag to update ("read" or "archived")
     * @param value New value for flag
     */
    private suspend fun updateExecutionFlag(
        executionId: String,
        flagName: String,
        value: Boolean
    ): OperationResult {
        try {
            // Get current execution
            val getResult = coordinator.processUserAction(
                "tool_executions.get_single",
                mapOf("execution_id" to executionId)
            )

            if (!getResult.isSuccess) {
                return OperationResult.error(getResult.error ?: s.shared("service_error_entry_not_found").format(executionId))
            }

            @Suppress("UNCHECKED_CAST")
            val execution = getResult.data?.get("execution") as? Map<String, Any>
                ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(executionId))

            // Parse execution_result JSON
            val executionResultStr = execution["executionResult"] as? String ?: "{}"
            val executionResult = JSONObject(executionResultStr)

            // Update flag
            executionResult.put(flagName, value)

            // Update execution via tool_executions service
            val updateResult = coordinator.processUserAction(
                "tool_executions.update",
                mapOf(
                    "id" to executionId,
                    "executionResult" to executionResult
                )
            )

            if (!updateResult.isSuccess) {
                return OperationResult.error(updateResult.error ?: s.shared("service_error_operation_failed"))
            }

            LogManager.service("Updated execution $executionId flag $flagName=$value", "DEBUG")
            return OperationResult.success()

        } catch (e: Exception) {
            LogManager.service("Failed to update execution flag: ${e.message}", "ERROR", e)
            return OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
    }

    /**
     * Get statistics for tool instance
     *
     * Params:
     * - toolInstanceId: String (required)
     *
     * Returns:
     * - total_messages: Int - Number of message templates
     * - total_executions: Int - Total number of executions from tool_executions
     * - unread: Int - Count of unread executions (from execution_result.read)
     * - archived: Int - Count of archived executions (from execution_result.archived)
     * - pending: Int - Count of pending executions (status = "pending")
     * - completed: Int - Count of completed executions (status = "completed")
     * - failed: Int - Count of failed executions (status = "failed")
     */
    private suspend fun getStats(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId"))
        }

        try {
            // Get message templates count
            val dao = AppDatabase.getDatabase(context).toolDataDao()
            val messages = dao.getByToolInstance(toolInstanceId)
            val totalMessages = messages.size

            // Get execution stats from tool_executions
            val statsResult = coordinator.processUserAction(
                "tool_executions.stats",
                mapOf("toolInstanceId" to toolInstanceId)
            )

            if (!statsResult.isSuccess) {
                return OperationResult.error(statsResult.error ?: s.shared("service_error_operation_failed"))
            }

            val totalExecutions = statsResult.data?.get("total_count") as? Int ?: 0
            val pendingCount = statsResult.data?.get("pending_count") as? Int ?: 0
            val completedCount = statsResult.data?.get("completed_count") as? Int ?: 0
            val failedCount = statsResult.data?.get("failed_count") as? Int ?: 0

            // Calculate unread/archived by fetching all executions (TODO: optimize with dedicated query)
            val executionsResult = coordinator.processUserAction(
                "tool_executions.get",
                mapOf("toolInstanceId" to toolInstanceId)
            )

            var unread = 0
            var archived = 0

            if (executionsResult.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                val executions = (executionsResult.data?.get("executions") as? List<Map<String, Any>>) ?: emptyList()

                for (execution in executions) {
                    val executionResultStr = execution["executionResult"] as? String
                    if (executionResultStr != null) {
                        try {
                            val executionResult = JSONObject(executionResultStr)
                            if (!executionResult.optBoolean("read", false)) unread++
                            if (executionResult.optBoolean("archived", false)) archived++
                        } catch (e: Exception) {
                            // Skip if can't parse
                        }
                    }
                }
            }

            return OperationResult.success(data = mapOf(
                "total_messages" to totalMessages,
                "total_executions" to totalExecutions,
                "unread" to unread,
                "archived" to archived,
                "pending" to pendingCount,
                "completed" to completedCount,
                "failed" to failedCount
            ))

        } catch (e: Exception) {
            LogManager.service("MessageService.getStats failed: ${e.message}", "ERROR", e)
            return OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
    }

}
