package com.assistant.tools.messages.scheduler

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import com.assistant.tools.messages.MessageService
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Scheduler for Messages tool
 *
 * Responsibilities:
 * - Scan all Messages tool instances for scheduled messages
 * - For each message with schedule != null, check if nextExecutionTime is reached
 * - Create execution snapshot and append to executions array
 * - Send notification via NotificationService
 * - Update schedule.nextExecutionTime for next occurrence
 *
 * Architecture:
 * - Called by CoreScheduler.tick() (1 min app-open, 15 min app-closed)
 * - Uses MessageService.appendExecution() (internal method, not exposed to commands)
 * - Best effort notifications (failed = status: "failed", no retry)
 * - Atomic sequence per message: create execution → send notif → update status
 *
 * Flow per scheduled message:
 * 1. Check if nextExecutionTime <= now
 * 2. Create execution entry (status: "pending", snapshots)
 * 3. Call NotificationService.send(title_snapshot, content_snapshot, priority)
 * 4. Update execution status ("sent" or "failed" based on notif result)
 * 5. Calculate next execution time via ScheduleCalculator
 * 6. Update schedule.nextExecutionTime in message data
 */
object MessageScheduler : ToolScheduler {

    override suspend fun checkScheduled(context: Context) {
        LogManager.service("MessageScheduler.checkScheduled() - scanning messages", "DEBUG")

        try {
            val coordinator = Coordinator(context)
            val messageService = MessageService(context)
            val now = System.currentTimeMillis()

            // 1. Get all Messages tool instances
            val instancesResult = coordinator.processUserAction(
                "tools.list_all",
                emptyMap()
            )

            if (!instancesResult.isSuccess) {
                LogManager.service("Failed to list tool instances: ${instancesResult.error}", "ERROR")
                return
            }

            @Suppress("UNCHECKED_CAST")
            val instances = (instancesResult.data?.get("tool_instances") as? List<Map<String, Any>>) ?: emptyList()
            val messageInstances = instances.filter { it["tool_type"] == "messages" }

            if (messageInstances.isEmpty()) {
                LogManager.service("No Messages tool instances found", "DEBUG")
                return
            }

            LogManager.service("Found ${messageInstances.size} Messages tool instance(s)", "DEBUG")

            // 2. For each instance, get all messages (data entries)
            for (instance in messageInstances) {
                val toolInstanceId = instance["id"] as? String ?: continue

                val messagesResult = coordinator.processUserAction(
                    "tool_data.get",
                    mapOf("toolInstanceId" to toolInstanceId)
                )

                if (!messagesResult.isSuccess) {
                    LogManager.service("Failed to get messages for instance $toolInstanceId: ${messagesResult.error}", "WARN")
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val entries = (messagesResult.data?.get("entries") as? List<Map<String, Any>>) ?: emptyList()

                // 3. Check each message for scheduling
                for (entry in entries) {
                    val messageId = entry["id"] as? String ?: continue
                    val messageName = entry["name"] as? String ?: ""
                    val data = entry["data"] as? String ?: continue

                    try {
                        processMessage(context, messageId, messageName, data, now, messageService, coordinator, instance)
                    } catch (e: Exception) {
                        LogManager.service("Failed to process message $messageId: ${e.message}", "ERROR", e)
                        // Continue with other messages
                    }
                }
            }

            LogManager.service("MessageScheduler.checkScheduled() completed", "DEBUG")

        } catch (e: Exception) {
            LogManager.service("MessageScheduler.checkScheduled() failed: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Process a single message for scheduling
     *
     * @param context Android context
     * @param messageId ID of message (ToolDataEntity.id)
     * @param messageName Name/title of the message (from ToolDataEntity.name)
     * @param dataJson JSON string of message data
     * @param now Current timestamp
     * @param messageService MessageService instance for appendExecution
     * @param coordinator Coordinator for updating message
     * @param instance Tool instance data (for external_notifications config)
     */
    private suspend fun processMessage(
        context: Context,
        messageId: String,
        messageName: String,
        dataJson: String,
        now: Long,
        messageService: MessageService,
        coordinator: Coordinator,
        instance: Map<String, Any>
    ) {
        val data = JSONObject(dataJson)

        // Skip if no schedule
        val schedule = data.optJSONObject("schedule")
        if (schedule == null || schedule.toString() == "null") {
            return
        }

        // Check if nextExecutionTime is reached
        val nextExecutionTime = schedule.optLong("nextExecutionTime", -1)
        if (nextExecutionTime <= 0 || nextExecutionTime > now) {
            return  // Not yet time to execute
        }

        // Extract message data for execution
        // Note: title comes from ToolDataEntity.name field, not from data JSON
        LogManager.service("Executing scheduled message: '$messageName' (id=$messageId)", "INFO")
        val content = data.optString("content")
        val priority = data.optString("priority", "default")

        // Get external_notifications setting from config
        val configJson = instance["config_json"] as? String
        val externalNotifications = if (configJson != null) {
            try {
                val config = JSONObject(configJson)
                config.optBoolean("external_notifications", true)
            } catch (e: Exception) {
                true  // Default to true if config parsing fails
            }
        } else {
            true
        }

        // Create execution entry
        val execution = mutableMapOf<String, Any>(
            "scheduled_time" to nextExecutionTime,
            "sent_at" to now,
            "status" to "pending",  // Will be updated after notification attempt
            "title_snapshot" to messageName,
            "content_snapshot" to (content.takeIf { it.isNotEmpty() } ?: ""),
            "read" to false,
            "archived" to false
        )

        // Append execution to message (status: pending)
        val appendResult = messageService.appendExecution(messageId, execution)
        if (!appendResult.success) {
            LogManager.service("Failed to append execution to message $messageId: ${appendResult.error}", "ERROR")
            return
        }

        // Send notification if external_notifications enabled
        var notificationSuccess = true
        if (externalNotifications) {
            val notifResult = coordinator.processUserAction(
                "notifications.send",
                mapOf(
                    "title" to messageName,
                    "content" to content,
                    "priority" to priority
                )
            )

            notificationSuccess = notifResult.isSuccess
            if (!notificationSuccess) {
                LogManager.service("Notification send failed for message $messageId: ${notifResult.error}", "WARN")
            }
        } else {
            LogManager.service("External notifications disabled for instance, skipping notification", "DEBUG")
        }

        // Update execution status based on notification result
        val finalStatus = if (notificationSuccess) "sent" else "failed"
        execution["status"] = finalStatus

        // Re-append with updated status (replaces pending entry)
        // Note: This is a simplification - in production we should update the specific execution index
        // For now, we update the entire message with corrected status
        updateExecutionStatus(messageId, finalStatus, coordinator)

        // Calculate next execution time using ScheduleCalculator
        val scheduleConfig = parseScheduleConfig(schedule)
        if (scheduleConfig != null) {
            val nextExecution = ScheduleCalculator.calculateNextExecution(
                pattern = scheduleConfig.pattern,
                timezone = scheduleConfig.timezone,
                startDate = scheduleConfig.startDate,
                endDate = scheduleConfig.endDate,
                fromTimestamp = now
            )

            if (nextExecution != null) {
                schedule.put("nextExecutionTime", nextExecution)
                LogManager.service("Message '$messageName' next execution: ${formatTimestamp(nextExecution)}", "INFO")
            } else {
                schedule.put("nextExecutionTime", JSONObject.NULL)
                LogManager.service("Message '$messageName' completed (no more scheduled executions)", "INFO")
            }
            data.put("schedule", schedule)
        } else {
            // Failed to parse schedule, disable this message to prevent infinite retries
            LogManager.service("Failed to parse schedule for message '$messageName', disabling", "WARN")
            schedule.put("nextExecutionTime", JSONObject.NULL)
            data.put("schedule", schedule)
        }

        val updateResult = coordinator.processUserAction(
            "tool_data.update",
            mapOf(
                "id" to messageId,
                "data" to data  // JSONObject, not toString()
            )
        )

        if (!updateResult.isSuccess) {
            LogManager.service("Failed to update message $messageId after execution: ${updateResult.error}", "WARN")
        }
    }

    /**
     * Update execution status in the last execution of a message
     *
     * This is a helper to update the status field of the last appended execution.
     * Retrieves the message, modifies the last execution's status, and saves back.
     */
    private suspend fun updateExecutionStatus(
        messageId: String,
        status: String,
        coordinator: Coordinator
    ) {
        try {
            // Get current message
            val getResult = coordinator.processUserAction(
                "tool_data.get_single",
                mapOf("entry_id" to messageId)  // tool_data.get_single expects "entry_id", not "id"
            )

            if (!getResult.isSuccess) {
                LogManager.service("Failed to get message $messageId for status update: ${getResult.error}", "WARN")
                return
            }

            @Suppress("UNCHECKED_CAST")
            val entry = getResult.data?.get("entry") as? Map<String, Any>
            val dataStr = entry?.get("data") as? String ?: return
            val data = JSONObject(dataStr)
            val executions = data.optJSONArray("executions")

            if (executions == null || executions.length() == 0) {
                LogManager.service("No executions found in message $messageId", "WARN")
                return
            }

            // Update last execution status
            val lastIndex = executions.length() - 1
            val lastExecution = executions.getJSONObject(lastIndex)
            lastExecution.put("status", status)

            // Save updated message
            val updateResult = coordinator.processUserAction(
                "tool_data.update",
                mapOf(
                    "id" to messageId,
                    "data" to data.toString()
                )
            )

            if (!updateResult.isSuccess) {
                LogManager.service("Failed to update execution status for message $messageId: ${updateResult.error}", "WARN")
            }

        } catch (e: Exception) {
            LogManager.service("Exception updating execution status for message $messageId: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Parse schedule JSONObject to ScheduleConfig
     *
     * @param scheduleJson JSONObject from message data
     * @return ScheduleConfig object or null if parsing fails
     */
    private fun parseScheduleConfig(scheduleJson: JSONObject): ScheduleConfig? {
        return try {
            // Use kotlinx.serialization to deserialize the schedule JSON
            Json.decodeFromString<ScheduleConfig>(scheduleJson.toString())
        } catch (e: Exception) {
            LogManager.service("Failed to parse schedule config: ${e.message}", "ERROR", e)
            null
        }
    }

    /**
     * Format timestamp to readable date string
     *
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date string (e.g., "2025-01-15 14:30:00")
     */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            dateFormat.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            timestamp.toString()
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
                is org.json.JSONArray -> value.toList()
                else -> value
            }
        }
        return map
    }

    /**
     * Helper to convert JSONArray to List recursively
     */
    private fun org.json.JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until length()) {
            val value = get(i)
            list.add(when (value) {
                is JSONObject -> value.toMap()
                is org.json.JSONArray -> value.toList()
                else -> value
            })
        }
        return list
    }
}
