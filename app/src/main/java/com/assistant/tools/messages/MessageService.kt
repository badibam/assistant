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
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Service for Messages tool operations
 *
 * Responsibilities:
 * - Standard CRUD operations (delegated to ToolDataService)
 * - Execution history queries (get_history with filters)
 * - Execution flag updates (mark_read, mark_archived)
 * - Statistics aggregation (including execution counts)
 * - Internal scheduler operations (appendExecution - not exposed via execute)
 *
 * Architecture:
 * - Messages use ToolDataEntity with JSON value containing template + executions array
 * - Executions are immutable snapshots (systemManaged, stripped from AI commands)
 * - Standard CRUD handled by ToolDataService for consistency
 * - Specific operations implemented directly for execution management
 */
class MessageService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "messages", context = context)
    private val coordinator = Coordinator(context)
    private val toolDataService = com.assistant.core.services.ToolDataService(context)

    override fun verbalize(
        operation: String,
        params: JSONObject,
        context: Context
    ): String {
        // For CRUD operations, delegate to ToolDataService verbalization
        return when (operation) {
            "create", "update", "delete", "get", "get_single" -> {
                toolDataService.verbalize(operation, params, context)
            }
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
                // Standard CRUD - with schedule nextExecutionTime calculation
                "create" -> handleCreate(params, token)
                "update" -> handleUpdate(params, token)
                "delete" -> delegateToToolDataService("delete", params, token)
                "get" -> delegateToToolDataService("get", params, token)
                "get_single" -> delegateToToolDataService("get_single", params, token)

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
    // Create/Update with Schedule Handling
    // ========================================

    /**
     * Handle create operation with automatic nextExecutionTime calculation
     * If message has a schedule, calculates nextExecutionTime before saving
     */
    private suspend fun handleCreate(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Process schedule if present (calculates nextExecutionTime)
        val processedParams = processScheduleInParams(params)

        // Delegate to ToolDataService
        return delegateToToolDataService("create", processedParams, token)
    }

    /**
     * Handle update operation with automatic nextExecutionTime calculation
     * If message schedule is modified, recalculates nextExecutionTime
     */
    private suspend fun handleUpdate(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Process schedule if present (calculates nextExecutionTime)
        val processedParams = processScheduleInParams(params)

        // Delegate to ToolDataService
        return delegateToToolDataService("update", processedParams, token)
    }

    /**
     * Process schedule in params: calculate and set nextExecutionTime if schedule exists
     *
     * @param params Original params (may contain data with schedule)
     * @return Modified params with nextExecutionTime calculated
     */
    private fun processScheduleInParams(params: JSONObject): JSONObject {
        try {
            // Get data field (contains the message JSON)
            val dataJson = params.optJSONObject("data") ?: return params

            // Check if schedule exists
            val scheduleJson = dataJson.optJSONObject("schedule")
            if (scheduleJson == null || scheduleJson.toString() == "null") {
                return params // No schedule, return as-is
            }

            // Parse schedule to ScheduleConfig
            val scheduleConfig = try {
                Json.decodeFromString<ScheduleConfig>(scheduleJson.toString())
            } catch (e: Exception) {
                LogManager.service("Failed to parse schedule config: ${e.message}", "WARN", e)
                return params // Invalid schedule, return as-is (validation will catch it)
            }

            // Calculate nextExecutionTime
            val now = System.currentTimeMillis()
            val nextExecutionTime = ScheduleCalculator.calculateNextExecution(
                pattern = scheduleConfig.pattern,
                timezone = scheduleConfig.timezone,
                startDate = scheduleConfig.startDate,
                endDate = scheduleConfig.endDate,
                fromTimestamp = now
            )

            // Update schedule with calculated nextExecutionTime
            if (nextExecutionTime != null) {
                scheduleJson.put("nextExecutionTime", nextExecutionTime)
            } else {
                scheduleJson.put("nextExecutionTime", JSONObject.NULL)
                LogManager.service("No future executions for schedule (end date passed or invalid pattern)", "WARN")
            }

            // Update data with modified schedule
            // dataJson is already a reference to params["data"], so modifications are already applied
            dataJson.put("schedule", scheduleJson)

            return params

        } catch (e: Exception) {
            LogManager.service("Error processing schedule in params: ${e.message}", "ERROR", e)
            return params // On error, return original params
        }
    }

    /**
     * Format timestamp to readable date string
     */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            dateFormat.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            timestamp.toString()
        }
    }

    // ========================================
    // Delegation to ToolDataService
    // ========================================

    /**
     * Delegate standard CRUD operations to ToolDataService directly
     * Ensures consistency with other tools and reuses validation logic
     *
     * Pattern: MessageService → ToolDataService (no Coordinator intermediary)
     */
    private suspend fun delegateToToolDataService(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        try {
            return toolDataService.execute(operation, params, token)
        } catch (e: Exception) {
            LogManager.service("MessageService delegation to ToolDataService.$operation failed: ${e.message}", "ERROR", e)
            return OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
    }

    // ========================================
    // Messages-specific operations
    // ========================================

    /**
     * Get execution history with filters
     *
     * Returns all executions from all messages of the tool instance,
     * filtered by read/archived status. Includes message title for display.
     *
     * Params:
     * - toolInstanceId: String (required)
     * - filters: JSONObject (optional)
     *   - read: Boolean? (null = ignore filter)
     *   - archived: Boolean? (null = ignore filter)
     *
     * Returns:
     * - executions: Array of {messageId, messageTitle, execution: {...}}
     *   Sorted by sent_at DESC (most recent first)
     */
    private suspend fun getHistory(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId"))
        }

        try {
            val dao = AppDatabase.getDatabase(context).toolDataDao()
            val messages = dao.getByToolInstance(toolInstanceId)

            // Extract filters
            val filters = params.optJSONObject("filters")
            val readFilter = filters?.opt("read") as? Boolean
            val archivedFilter = filters?.opt("archived") as? Boolean

            // Flatten all executions from all messages with join on message title
            val allExecutions = mutableListOf<Map<String, Any>>()

            for (message in messages) {
                val data = JSONObject(message.data)
                val messageTitle = data.optString("title", s.shared("content_unnamed"))
                val executions = data.optJSONArray("executions") ?: continue

                for (i in 0 until executions.length()) {
                    val execution = executions.getJSONObject(i)

                    // Apply filters
                    val read = execution.optBoolean("read", false)
                    val archived = execution.optBoolean("archived", false)

                    val matchesReadFilter = readFilter == null || read == readFilter
                    val matchesArchivedFilter = archivedFilter == null || archived == archivedFilter

                    if (matchesReadFilter && matchesArchivedFilter) {
                        allExecutions.add(mapOf(
                            "messageId" to message.id,
                            "messageTitle" to messageTitle,
                            "executionIndex" to i,
                            "execution" to execution.toMap()
                        ))
                    }
                }
            }

            // Sort by sent_at DESC (most recent first)
            val sortedExecutions = allExecutions.sortedByDescending { entry ->
                @Suppress("UNCHECKED_CAST")
                val execution = entry["execution"] as Map<String, Any>
                execution["sent_at"] as? Long ?: 0L
            }

            return OperationResult.success(data = mapOf("executions" to sortedExecutions))

        } catch (e: Exception) {
            LogManager.service("MessageService.getHistory failed: ${e.message}", "ERROR", e)
            return OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
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
     * - message_id: String (required) - ID of the message (ToolDataEntity.id)
     * - execution_index: Int (required) - Index in executions array
     * - read: Boolean (required) - New read status
     *
     * Returns:
     * - success: Boolean
     */
    private suspend fun markRead(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id")
        val executionIndex = params.optInt("execution_index", -1)
        val read = params.optBoolean("read")

        if (messageId.isEmpty() || executionIndex < 0) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("message_id, execution_index"))
        }

        return updateExecutionFlag(messageId, executionIndex, "read", read)
    }

    /**
     * Mark execution as archived/unarchived
     *
     * Params:
     * - message_id: String (required)
     * - execution_index: Int (required)
     * - archived: Boolean (required)
     *
     * Returns:
     * - success: Boolean
     */
    private suspend fun markArchived(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id")
        val executionIndex = params.optInt("execution_index", -1)
        val archived = params.optBoolean("archived")

        if (messageId.isEmpty() || executionIndex < 0) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("message_id, execution_index"))
        }

        return updateExecutionFlag(messageId, executionIndex, "archived", archived)
    }

    /**
     * Update a flag in a specific execution entry
     *
     * @param messageId ID of message (ToolDataEntity.id)
     * @param executionIndex Index in executions array
     * @param flagName Name of flag to update ("read" or "archived")
     * @param value New value for flag
     */
    private suspend fun updateExecutionFlag(
        messageId: String,
        executionIndex: Int,
        flagName: String,
        value: Boolean
    ): OperationResult {
        try {
            val dao = AppDatabase.getDatabase(context).toolDataDao()
            val message = dao.getById(messageId)
                ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(messageId))

            val data = JSONObject(message.data)
            val executions = data.optJSONArray("executions")
                ?: return OperationResult.error("No executions array found in message $messageId")

            if (executionIndex >= executions.length()) {
                return OperationResult.error("Execution index $executionIndex out of bounds (length: ${executions.length()})")
            }

            // Update flag
            val execution = executions.getJSONObject(executionIndex)
            execution.put(flagName, value)

            // Save updated message
            val updatedMessage = message.copy(
                data = data.toString(),
                updatedAt = System.currentTimeMillis()
            )
            dao.update(updatedMessage)

            // Notify UI
            val zoneId = getZoneIdForTool(message.toolInstanceId)
            if (zoneId != null) {
                DataChangeNotifier.notifyToolDataChanged(message.toolInstanceId, zoneId)
            }

            LogManager.service("Updated execution $executionIndex flag $flagName=$value for message $messageId", "DEBUG")
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
     * - total_executions: Int - Total number of executions across all messages
     * - unread: Int - Count of unread executions
     * - archived: Int - Count of archived executions
     * - pending: Int - Count of pending executions
     * - sent: Int - Count of sent executions
     * - failed: Int - Count of failed executions
     */
    private suspend fun getStats(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("toolInstanceId"))
        }

        try {
            val dao = AppDatabase.getDatabase(context).toolDataDao()
            val messages = dao.getByToolInstance(toolInstanceId)

            var totalExecutions = 0
            var unread = 0
            var archived = 0
            var pending = 0
            var sent = 0
            var failed = 0

            for (message in messages) {
                val data = JSONObject(message.data)
                val executions = data.optJSONArray("executions") ?: continue

                for (i in 0 until executions.length()) {
                    val execution = executions.getJSONObject(i)
                    totalExecutions++

                    // Count by status
                    when (execution.optString("status")) {
                        "pending" -> pending++
                        "sent" -> sent++
                        "failed" -> failed++
                    }

                    // Count flags
                    if (!execution.optBoolean("read", false)) unread++
                    if (execution.optBoolean("archived", false)) archived++
                }
            }

            return OperationResult.success(data = mapOf(
                "total_messages" to messages.size,
                "total_executions" to totalExecutions,
                "unread" to unread,
                "archived" to archived,
                "pending" to pending,
                "sent" to sent,
                "failed" to failed
            ))

        } catch (e: Exception) {
            LogManager.service("MessageService.getStats failed: ${e.message}", "ERROR", e)
            return OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
    }

    // ========================================
    // Internal scheduler operations
    // ========================================

    /**
     * Append execution to message (internal - not exposed via execute)
     *
     * Called only by MessageScheduler when scheduled_time is reached.
     * Creates a new execution entry with snapshots and appends to executions array.
     *
     * @param messageId ID of message to append execution to
     * @param execution Map containing execution data (scheduled_time, sent_at, status, snapshots, flags)
     * @return OperationResult with success/error
     */
    suspend fun appendExecution(messageId: String, execution: Map<String, Any>): OperationResult {
        try {
            val dao = AppDatabase.getDatabase(context).toolDataDao()
            val message = dao.getById(messageId)
                ?: return OperationResult.error("Message not found: $messageId")

            val data = JSONObject(message.data)
            val executions = data.optJSONArray("executions") ?: JSONArray()

            // Append new execution
            val executionJson = JSONObject(execution)
            executions.put(executionJson)

            // Update message
            data.put("executions", executions)
            val updatedMessage = message.copy(
                data = data.toString(),
                updatedAt = System.currentTimeMillis()
            )
            dao.update(updatedMessage)

            // Notify UI
            val zoneId = getZoneIdForTool(message.toolInstanceId)
            if (zoneId != null) {
                DataChangeNotifier.notifyToolDataChanged(message.toolInstanceId, zoneId)
            }

            LogManager.service("Appended execution to message $messageId (status: ${execution["status"]})", "INFO")
            return OperationResult.success()

        } catch (e: Exception) {
            LogManager.service("Failed to append execution to message $messageId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to append execution: ${e.message}")
        }
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Get zone ID for a tool instance
     * Used for DataChangeNotifier to trigger UI updates
     */
    private suspend fun getZoneIdForTool(toolInstanceId: String): String? {
        return try {
            val result = coordinator.processUserAction(
                "tools.get",
                mapOf("tool_instance_id" to toolInstanceId)
            )

            if (result.isSuccess) {
                val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
                toolInstance?.get("zone_id") as? String
            } else {
                null
            }
        } catch (e: Exception) {
            LogManager.service("Failed to get zone ID for tool $toolInstanceId: ${e.message}", "WARN")
            null
        }
    }
}
