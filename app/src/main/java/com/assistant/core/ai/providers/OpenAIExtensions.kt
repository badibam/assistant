package com.assistant.core.ai.providers

import com.assistant.core.ai.data.*
import kotlinx.serialization.json.*
import com.assistant.core.utils.LogManager

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
 * - L1, L2, L3 content placed as initial system messages
 * - Session messages transformed with proper role mapping
 * - Current datetime appended at the end
 *
 * @param model The model asked for
 * @param temperature The sampling temperature
 * @param maxOutputTokens The longest answer asked for
 * @param datetimeText The dated closing message (buildDatetimeMessage), built by the caller:
 *   it reads the clock and the strings, which keeps this function pure and testable
 * @return JsonObject ready for OpenAI API /v1/responses endpoint
 */
internal fun PromptData.toOpenAIJson(model: String, temperature: Double, maxOutputTokens: Int, datetimeText: String): JsonObject {
    return buildJsonObject {
        put("model", model)
        put("temperature", temperature)
        put("max_output_tokens", maxOutputTokens)

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

            // Add L3 as system message if not empty
            if (level3Content.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", level3Content)
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

            // Add current datetime as final message
            addJsonObject {
                put("role", "user")
                put("content", datetimeText)
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
        // Simple text content, a user message's included: PromptManager writes it from its
        // segments, pointers named as they are now
        message.textContent != null -> message.textContent

        // AI message preText
        message.aiMessage != null -> message.aiMessage.preText

        // System message: its summary, each command result, and the data it added
        message.systemMessage != null -> message.systemMessage.toPromptText()

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
        // An error object inside a 200 body: the call was answered, so it is a refusal, not a
        // network problem. The status-based classification does not apply here.
        return AIResponse(
            success = false,
            content = "",
            errorMessage = errorMessage,
            failure = AIFailure.REFUSED,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }

    // Check status - reject non-completed responses (incomplete JSON is unusable)
    val status = jsonObj["status"]?.jsonPrimitive?.content
    if (status != "completed") {
        LogManager.aiService(
            "OpenAI response status '$status'. This typically means max_output_tokens limit was reached. " +
            "Increase max_output_tokens in provider configuration to allow longer responses.",
            "ERROR"
        )
        return AIResponse(
            success = false,
            content = "",
            errorMessage = "Response incomplete (status: $status). Increase max_output_tokens in provider config.",
            failure = AIFailure.CONFIG,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }

    // Extract content from output array: an optional reasoning element, then the message,
    // whose text is the output_text block
    val messageOutput = (jsonObj["output"] as? JsonArray)?.firstOrNull { element ->
        (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "message"
    } as? JsonObject
    val textContent = (messageOutput?.get("content") as? JsonArray)?.firstOrNull { element ->
        (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "output_text"
    } as? JsonObject
    val text = textContent?.get("text")?.jsonPrimitive?.contentOrNull
        ?: return AIResponse(
            success = false,
            content = "",
            errorMessage = "Provider response has no output_text in a message element.",
            failure = AIFailure.CONFIG,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )

    // Usage: input and output counts are required, since the cost is computed from them and a
    // missing one would show a paid call as free. The cached count is optional: none reported
    // means none was read from the cache.
    val usage = jsonObj["usage"] as? JsonObject
    val totalInputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull
    val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull
    if (totalInputTokens == null || outputTokens == null) {
        return AIResponse(
            success = false,
            content = "",
            errorMessage = "Provider response has no input_tokens or output_tokens in its usage.",
            failure = AIFailure.CONFIG,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }
    val inputTokensDetails = usage["input_tokens_details"] as? JsonObject
    val cachedTokens = inputTokensDetails?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0

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
