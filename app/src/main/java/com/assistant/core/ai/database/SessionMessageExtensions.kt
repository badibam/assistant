package com.assistant.core.ai.database

import com.assistant.core.ai.data.SessionMessage

/**
 * Extension function to convert SessionMessageEntity (database) to SessionMessage (domain)
 *
 * Pattern: Inline JSON deserialization using companion object parsers
 * Matches app pattern from AppConfigService and AIMessage.fromJson() etc.
 *
 * Handles:
 * - RichMessage parsing from richContentJson
 * - AIMessage parsing from aiMessageParsedJson
 * - SystemMessage parsing from systemMessageJson
 * - ExecutionMetadata parsing from executionMetadataJson
 */
fun SessionMessageEntity.toDomain(): SessionMessage {
    // Import companion object parsers inline (matches app pattern)
    val richMessage = richContentJson?.let { json ->
        try {
            com.assistant.core.ai.data.RichMessage.fromJson(json)
        } catch (e: Exception) {
            // Graceful fallback - log but don't crash
            null
        }
    }

    val aiMessage = aiMessageParsedJson?.let { json ->
        try {
            com.assistant.core.ai.data.AIMessage.fromJson(json)
        } catch (e: Exception) {
            // Graceful fallback
            null
        }
    }

    val systemMessage = systemMessageJson?.let { json ->
        try {
            com.assistant.core.ai.data.SystemMessage.fromJson(json)
        } catch (e: Exception) {
            // Graceful fallback
            null
        }
    }

    val executionMetadata = executionMetadataJson?.let { json ->
        try {
            val obj = org.json.JSONObject(json)
            com.assistant.core.ai.data.ExecutionMetadata(
                ruleId = obj.getString("ruleId"),
                triggeredAt = obj.getLong("triggeredAt"),
                feedback = obj.optJSONObject("feedback")?.let { feedbackJson ->
                    com.assistant.core.ai.data.ExecutionFeedback(
                        comment = feedbackJson.getString("comment"),
                        rating = feedbackJson.getInt("rating"),
                        timestamp = feedbackJson.getLong("timestamp")
                    )
                }
            )
        } catch (e: Exception) {
            // Graceful fallback
            null
        }
    }

    return SessionMessage(
        id = id,
        timestamp = timestamp,
        sender = sender,
        richContent = richMessage,
        textContent = textContent,
        aiMessage = aiMessage,
        aiMessageJson = aiMessageJson,
        systemMessage = systemMessage,
        executionMetadata = executionMetadata,
        excludeFromPrompt = excludeFromPrompt
    )
}
