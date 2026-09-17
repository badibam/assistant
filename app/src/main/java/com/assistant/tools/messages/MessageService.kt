package com.assistant.tools.messages

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager
import com.assistant.tools.messages.scheduler.MessageScheduler
import org.json.JSONObject

/**
 * Service for the Messages tool.
 *
 * It exposes one operation, because there is only one thing a message template can be asked to
 * do that is not plain data editing: send now. Reading the occurrences, marking one read or
 * filing it away all go through tool_data like any other entry — an occurrence is ordinary
 * data and needs no service of its own to be read.
 *
 * This is what makes a tooltype "active" rather than passive: it exposes execute, and possibly
 * a scheduler. Tracking, Journal and Note have neither — the user writes their entries. Here
 * the system writes them, driven by the config.
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
            "execute" -> s.tool("verbalize_execute")
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
                "execute" -> executeNow(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            LogManager.service("MessageService.execute($operation) failed: ${e.message}", "ERROR", e)
            OperationResult.error("${s.shared("service_error_operation_failed")}: ${e.message}")
        }
    }

    /**
     * Sends this message now.
     *
     * Creates an occurrence due at this instant and lets the scheduler resolve it, rather than
     * carrying a second copy of the sending logic. Manual and scheduled sends therefore travel
     * the same path and differ only by triggered_by, which is what keeps the two from drifting
     * apart the way the old execution plane drifted from tool_data.
     *
     * An optional title and content can be supplied for this one send; without them the
     * template's common part goes out on its own, which is all a fixed reminder needs.
     *
     * Params:
     * - tool_instance_id: String (required)
     * - title: String (optional) — title for this send only
     * - content: String (optional) — body for this send only
     */
    private suspend fun executeNow(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_missing_required_params").format("tool_instance_id"))
        }

        val now = System.currentTimeMillis()
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()

        val data = JSONObject().apply {
            put("status", "pending")
            put("triggered_by", "MANUAL")
            params.optString("title").takeIf { it.isNotEmpty() }?.let { put("title", it) }
            params.optString("content").takeIf { it.isNotEmpty() }?.let { put("content", it) }
        }

        val createResult = coordinator.processUserAction("tool_data.create", mapOf(
            "toolInstanceId" to toolInstanceId,
            "tooltype" to "messages",
            "schema_id" to "messages_data",
            "name" to params.optString("name", ""),
            "timestamp" to DateTimeConverter.timestampToISO(now, timezone),
            "data" to data
        ))

        if (!createResult.isSuccess) {
            LogManager.service("Failed to create manual occurrence for $toolInstanceId: ${createResult.error}", "ERROR")
            return OperationResult.error(createResult.error ?: s.tool("error_save"))
        }

        // Hand it to the scheduler, which owns the send. A full scan for one occurrence is more
        // work than strictly needed, but a manual send is rare and one sending path is worth
        // more than the saved cycles.
        MessageScheduler.checkScheduled(context)

        return OperationResult.success(
            data = mapOf("id" to (createResult.data?.get("id") ?: ""))
        )
    }
}
