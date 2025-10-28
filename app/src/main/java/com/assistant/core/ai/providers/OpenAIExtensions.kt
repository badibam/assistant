package com.assistant.core.ai.providers

import com.assistant.core.ai.data.*
import kotlinx.serialization.json.*
import org.json.JSONObject

/**
 * OpenAI-specific extensions for PromptData transformation and response parsing
 *
 * Responsibilities:
 * - Transform PromptData to OpenAI Responses API JSON format
 * - Structure messages with roles (system, user, assistant)
 * - Parse OpenAI API responses with token metrics
 *
 * Note: OpenAI's /v1/responses API uses structured messages similar to Claude,
 * with role-based message arrays rather than simple text input.
 */

/**
 * Transform PromptData to OpenAI Responses API JSON format
 *
 * OpenAI format uses structured messages array similar to Claude:
 * - input: array of {role, content} objects
 * - Roles: "system", "user", "assistant"
 * - L1 and L2 content placed as initial system messages
 * - Session messages transformed with proper role mapping
 *
 * @param config Provider configuration (api_key, model, temperature, etc.)
 * @return JsonObject ready for OpenAI API /v1/responses endpoint
 */
internal fun PromptData.toOpenAIJson(config: JSONObject): JsonObject {
    val model = config.getString("model")
    val temperature = config.optDouble("temperature", 1.0)
    val maxTokens = config.optInt("max_output_tokens", 32000)

    return buildJsonObject {
        put("model", model)
        put("temperature", temperature)
        put("max_output_tokens", maxTokens)

        // Build input as array of messages
        putJsonArray("input") {
            // Add L1 as system message
            addJsonObject {
                put("role", "system")
                put("content", level1Content)
            }

            // Add L2 as system message if not empty
            if (level2Content.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", level2Content)
                }
            }

            // Transform session messages
            // Step 1: Transform SYSTEM → USER (common transformation)
            val normalizedMessages = transformSystemMessagesToUser(sessionMessages)

            // Step 2: Add messages with proper roles
            normalizedMessages.forEach { msg ->
                val role = when (msg.sender) {
                    MessageSender.USER -> "user"
                    MessageSender.AI -> "assistant"
                    MessageSender.SYSTEM -> "user"  // Should not happen after normalization
                }

                val content = when {
                    msg.sender == MessageSender.AI && msg.aiMessageJson != null -> msg.aiMessageJson
                    else -> extractTextContent(msg) ?: ""
                }

                // Only add non-empty messages
                if (content.isNotBlank()) {
                    addJsonObject {
                        put("role", role)
                        put("content", content)
                    }
                }
            }
        }
    }
}

/**
 * Extract text content from a SessionMessage
 * Tries different sources in priority order
 */
private fun extractTextContent(message: SessionMessage): String? {
    return when {
        // Rich content has priority (user messages with enrichments)
        message.richContent != null -> message.richContent.linearText

        // Simple text content
        message.textContent != null -> message.textContent

        // AI message preText
        message.aiMessage != null -> message.aiMessage.preText

        // System message summary with command results
        message.systemMessage != null -> {
            val systemMsg = message.systemMessage
            val summary = systemMsg.summary

            buildString {
                appendLine(summary)

                // Include all command results with their details (like in UI)
                if (systemMsg.commandResults.isNotEmpty()) {
                    appendLine()
                    systemMsg.commandResults.forEach { result ->

                        if (result.details != null) {
                            append("- ${result.details}")

                            // Add data (for action commands, like in UI)
                            if (result.isActionCommand && result.data != null && result.data.isNotEmpty()) {
                                val dataText = result.data.entries.joinToString(", ") { (k, v) -> "$k: $v" }
                                append(" ($dataText)")
                            }

                            // Add error message if command failed
                            if (result.status == CommandStatus.FAILED && result.error != null) {
                                append(" → Erreur: ${result.error}")
                            }
                            appendLine()
                        }
                    }
                }

                // Include formattedData if available (for DATA_ADDED queries)
                if (systemMsg.formattedData != null) {
                    appendLine()
                    append(systemMsg.formattedData)
                }
            }
        }

        else -> null
    }
}

/**
 * Parse OpenAI API response to AIResponse
 *
 * Extracts:
 * - Content text from output[0].content[0].text
 * - Token usage (input, output, cached)
 * - Error information if present
 *
 * OpenAI response structure:
 * {
 *   "status": "completed",
 *   "output": [{ "content": [{ "type": "output_text", "text": "..." }] }],
 *   "usage": {
 *     "input_tokens": 36,
 *     "input_tokens_details": { "cached_tokens": 0 },
 *     "output_tokens": 87
 *   },
 *   "error": { "message": "..." }  // if error
 * }
 *
 * @return AIResponse with OpenAI token metrics
 */
internal fun JsonElement.toOpenAIResponse(): AIResponse {
    val jsonObj = this.jsonObject

    // Check for error
    // Use safe cast to handle JsonNull elements gracefully
    val errorObj = jsonObj["error"] as? JsonObject
    if (errorObj != null) {
        val errorMessage = errorObj["message"]?.jsonPrimitive?.content ?: "Unknown error"
        return AIResponse(
            success = false,
            content = "",
            errorMessage = errorMessage,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }

    // Check status - reject non-completed responses (incomplete JSON is unusable)
    val status = jsonObj["status"]?.jsonPrimitive?.content
    if (status != "completed") {
        com.assistant.core.utils.LogManager.aiService(
            "OpenAI response status '$status'. This typically means max_output_tokens limit was reached. " +
            "Increase max_output_tokens in provider configuration to allow longer responses.",
            "ERROR"
        )
        return AIResponse(
            success = false,
            content = "",
            errorMessage = "Response incomplete (status: $status). Increase max_output_tokens in provider config.",
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }

    // Extract content from output array
    // OpenAI returns multiple output elements: reasoning (optional) + message
    // We need to find the element with type="message"
    val outputArray = jsonObj["output"]?.jsonArray
    com.assistant.core.utils.LogManager.aiService("OpenAI parsing - outputArray size: ${outputArray?.size}")

    // Find the message element (not reasoning)
    val messageOutput = outputArray?.firstOrNull { element ->
        (element as? JsonObject)?.get("type")?.jsonPrimitive?.content == "message"
    } as? JsonObject
    com.assistant.core.utils.LogManager.aiService("OpenAI parsing - messageOutput found: ${messageOutput != null}")

    val contentArray = messageOutput?.get("content")?.jsonArray
    com.assistant.core.utils.LogManager.aiService("OpenAI parsing - contentArray size: ${contentArray?.size}")

    // Find the text content (type="output_text")
    val textContent = contentArray?.firstOrNull { element ->
        (element as? JsonObject)?.get("type")?.jsonPrimitive?.content == "output_text"
    } as? JsonObject
    com.assistant.core.utils.LogManager.aiService("OpenAI parsing - textContent found: ${textContent != null}")

    val text = textContent?.get("text")?.jsonPrimitive?.content ?: ""
    com.assistant.core.utils.LogManager.aiService("OpenAI parsing - extracted text length: ${text.length}")
    com.assistant.core.utils.LogManager.aiService("OpenAI parsing - text preview: ${text.take(200)}")

    // Extract usage metrics
    // Use safe cast to handle JsonNull elements gracefully
    val usage = jsonObj["usage"] as? JsonObject
    val totalInputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0
    val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0

    // OpenAI provides cached_tokens in input_tokens_details
    val inputTokensDetails = usage?.get("input_tokens_details") as? JsonObject
    val cachedTokens = inputTokensDetails?.get("cached_tokens")?.jsonPrimitive?.int ?: 0

    // OpenAI's input_tokens includes cached tokens, so we subtract to get uncached (new) tokens
    // This matches Claude's semantic where inputTokens = uncached input only
    val uncachedInputTokens = totalInputTokens - cachedTokens

    // OpenAI doesn't distinguish between cache write and cache read like Claude
    // We map cached_tokens to cacheReadTokens for consistency
    return AIResponse(
        success = true,
        content = text,
        errorMessage = null,
        tokensUsed = outputTokens,
        cacheWriteTokens = 0,  // OpenAI doesn't report cache write tokens
        cacheReadTokens = cachedTokens,
        inputTokens = uncachedInputTokens  // Uncached input tokens only
    )
}
