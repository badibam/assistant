package com.assistant.core.ai.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * System message structure for AI operation results
 * Used for data requests and action execution feedback
 */
data class SystemMessage(
    val type: SystemMessageType,
    val commandResults: List<CommandResult>,  // Metadata (success/failure per command)
    val summary: String,                       // Human-readable summary for UI display
    val formattedData: String? = null         // Complete JSON results for prompt inclusion
) {
    /**
     * Serialize SystemMessage to JSON string
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type.name)
        json.put("summary", summary)
        if (formattedData != null) {
            json.put("formatted_data", formattedData)
        }

        val resultsArray = JSONArray()
        for (result in commandResults) {
            val resultJson = JSONObject()
            resultJson.put("command", result.command)
            resultJson.put("status", result.status.name)
            if (result.details != null) {
                resultJson.put("details", result.details)
            }
            if (result.data != null) {
                resultJson.put("data", JSONObject(result.data as Map<*, *>))
            }
            if (result.error != null) {
                resultJson.put("error", result.error)
            }
            resultJson.put("is_action_command", result.isActionCommand)
            resultsArray.put(resultJson)
        }
        json.put("command_results", resultsArray)

        return json.toString()
    }

    companion object {
        /**
         * Deserialize SystemMessage from JSON string
         */
        fun fromJson(jsonString: String): SystemMessage? {
            return try {
                val json = JSONObject(jsonString)

                val type = SystemMessageType.valueOf(json.getString("type"))
                val summary = json.getString("summary")
                val formattedData = json.optString("formatted_data").takeIf { it.isNotEmpty() }

                val commandResults = mutableListOf<CommandResult>()
                val resultsArray = json.getJSONArray("command_results")
                for (i in 0 until resultsArray.length()) {
                    val resultJson = resultsArray.getJSONObject(i)

                    // Parse data field if present
                    val data = resultJson.optJSONObject("data")?.let { dataJson ->
                        val map = mutableMapOf<String, Any>()
                        dataJson.keys().forEach { key ->
                            map[key] = dataJson.get(key)
                        }
                        map
                    }

                    val error = resultJson.optString("error").takeIf { it.isNotEmpty() }
                    val isActionCommand = resultJson.optBoolean("is_action_command", false)

                    commandResults.add(
                        CommandResult(
                            command = resultJson.getString("command"),
                            status = CommandStatus.valueOf(resultJson.getString("status")),
                            details = resultJson.optString("details").takeIf { it.isNotEmpty() },
                            data = data,
                            error = error,
                            isActionCommand = isActionCommand
                        )
                    )
                }

                SystemMessage(
                    type = type,
                    commandResults = commandResults,
                    summary = summary,
                    formattedData = formattedData
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class SystemMessageType {
    DATA_ADDED,              // Data requests completed and added to context
    ACTIONS_EXECUTED,        // AI actions execution results
    LIMIT_REACHED,           // Autonomous loop limit reached (stops execution, waits for next user message)
    FORMAT_ERROR,            // AI response format error (sent to AI for correction)
    NETWORK_ERROR,           // Network/HTTP/provider errors (stored for audit, FILTERED from prompt)
    SESSION_TIMEOUT,         // Session watchdog timeout (stored for audit, FILTERED from prompt)
    INTERRUPTED,             // User interrupted autonomous loop (stored for audit, FILTERED from prompt)
    COMMUNICATION_CANCELLED, // User did not respond to communication module (sent to AI prompt for context)
    VALIDATION_CANCELLED,    // User did not validate AI actions or explicitly refused (sent to AI prompt for context)
    COMPLETED_CONFIRMATION,  // AI used completed flag, asking for confirmation (sent to AI prompt for double-check)
    PROVIDER_ERROR,          // AI provider not configured or not found (stored for audit, FILTERED from prompt)
    SCHEMA_REQUIRED,         // Entries schemas a query or a write waits on, sent to the AI; the commands were not carried out
    DATA_AWAITING_CONFIRMATION, // Data above the CHAT size threshold, kept out of the prompt until the user sends it
    DATA_REFUSED,            // Data above the size threshold not sent: refused by the user or by an automation (sent to AI to narrow its request)
    TEXT_OUTSIDE_JSON,       // Text the AI wrote around its JSON, set aside: quoted to the user in summary; the AI gets only the notice in formattedData (PromptManager)
    EMPTY_ANSWER,            // The provider answered with no text, the request asked again once (stored for audit and cost, FILTERED from prompt)
    ALWAYS_SEND_AWAITING_CONFIRMATION, // The tools sent always above their threshold, a CHAT waiting for the user's choice (out of the prompt)
    ALWAYS_SEND_ACCEPTED,    // That choice, sent: they go with every call of the session (out of the prompt; read by PromptManager.alwaysSendChoice)
    ALWAYS_SEND_REFUSED      // That choice, not sent: the AI gets their list (out of the prompt; read by PromptManager.alwaysSendChoice)
}

/**
 * Individual command execution result
 */
data class CommandResult(
    val command: String,         // Command executed (e.g., "tool_data.get")
    val status: CommandStatus,
    val details: String?,        // Verbalized action description (e.g., "Création de la zone \"Santé\"")
    val data: Map<String, Any>?, // Structured result data (filtered for actions, complete for queries)
    val error: String?,          // Technical error message if failed (e.g., "nom déjà existant")
    val isActionCommand: Boolean = false  // true = action (create/update/delete), false = query (get/list/stats)
)

enum class CommandStatus {
    SUCCESS,   // Command executed successfully
    FAILED,    // Command failed due to technical error
    CANCELLED, // Command cancelled (timeout, token limit, OR user refused validation)
    CACHED     // Command was deduplicated, data already available from previous execution
}