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
            json.put("formattedData", formattedData)
        }

        val resultsArray = JSONArray()
        for (result in commandResults) {
            val resultJson = JSONObject()
            resultJson.put("command", result.command)
            resultJson.put("status", result.status.name)
            resultJson.put("details", result.details)
            resultsArray.put(resultJson)
        }
        json.put("commandResults", resultsArray)

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
                val formattedData = json.optString("formattedData").takeIf { it.isNotEmpty() }

                val commandResults = mutableListOf<CommandResult>()
                val resultsArray = json.getJSONArray("commandResults")
                for (i in 0 until resultsArray.length()) {
                    val resultJson = resultsArray.getJSONObject(i)
                    commandResults.add(
                        CommandResult(
                            command = resultJson.getString("command"),
                            status = CommandStatus.valueOf(resultJson.getString("status")),
                            details = resultJson.optString("details").takeIf { it.isNotEmpty() }
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
    DATA_ADDED,       // Data requests completed and added to context
    ACTIONS_EXECUTED, // AI actions execution results
    LIMIT_REACHED,    // Autonomous loop limit reached (stops execution, waits for next user message)
    NETWORK_ERROR,    // Network/HTTP/provider errors (stored for audit, FILTERED from prompt)
    SESSION_TIMEOUT   // Session watchdog timeout (stored for audit, FILTERED from prompt)
}

/**
 * Individual command execution result
 */
data class CommandResult(
    val command: String,    // Command executed (e.g., "tool_data.get")
    val status: CommandStatus,
    val details: String?    // Error message or result summary
)

enum class CommandStatus {
    SUCCESS,   // Command executed successfully
    FAILED,    // Command failed due to technical error
    CANCELLED, // Command cancelled (timeout, token limit, OR user refused validation)
    CACHED     // Command was deduplicated, data already available from previous execution
}