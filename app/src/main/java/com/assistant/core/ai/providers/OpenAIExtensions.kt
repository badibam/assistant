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
 * @param effort The reasoning effort ("none" turns thinking off where a model has it); null leaves
 *   the model's default
 * @param datetimeText The dated closing message (buildDatetimeMessage), built by the caller:
 *   it reads the clock and the strings, which keeps this function pure and testable
 * @param imageData The JPEG of each image the messages carry, as base64 (AttachedImages)
 * @return JsonObject ready for OpenAI API /v1/responses endpoint
 */
internal fun PromptData.toOpenAIJson(
    model: String,
    temperature: Double,
    maxOutputTokens: Int,
    effort: String?,
    datetimeText: String,
    imageData: ImageData = { error("Image $it in a prompt built without image data") }
): JsonObject {
    return buildJsonObject {
        put("model", model)
        put("temperature", temperature)
        put("max_output_tokens", maxOutputTokens)
        if (effort != null) {
            putJsonObject("reasoning") { put("effort", effort) }
        }
        // JSON mode: the answer is one JSON object, as the prompt asks, never text around it
        putJsonObject("text") { putJsonObject("format") { put("type", "json_object") } }

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

            // The conversation, then the dated message
            conversationMessages(datetimeText, ImageParts.RESPONSES, imageData).forEach { add(it) }
        }
    }
}

/**
 * How a message with images is written: the Responses API and Chat Completions name their parts
 * differently, an image going as a `data:` URL in both.
 */
internal enum class ImageParts(val textType: String) {
    /** `{"type": "input_text", "text"}`, `{"type": "input_image", "image_url": "data:…"}` */
    RESPONSES("input_text"),

    /** `{"type": "text", "text"}`, `{"type": "image_url", "image_url": {"url": "data:…"}}` */
    CHAT_COMPLETIONS("text");

    fun image(dataUrl: String): JsonObject = when (this) {
        RESPONSES -> buildJsonObject { put("type", "input_image"); put("image_url", dataUrl) }
        CHAT_COMPLETIONS -> buildJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", dataUrl) } }
    }
}

/**
 * The session's messages as OpenAI role messages, the dated closing message last: SYSTEM messages
 * fused into the user's turn (transformSystemMessagesToUser), an AI message sent as the JSON it
 * answered, an empty message left out. A message with images goes as a list of parts, each image
 * where the user put it; any other as its text. Shared by the Responses API (toOpenAIJson) and
 * Chat Completions (toChatCompletionsJson), whose messages have this same form but for the names
 * of their parts ([format]).
 *
 * @param datetimeText The dated closing message, built by the caller (buildDatetimeMessage)
 * @param imageData The JPEG of each image the messages carry, as base64 (AttachedImages)
 */
internal fun PromptData.conversationMessages(
    datetimeText: String,
    format: ImageParts,
    imageData: ImageData
): List<JsonObject> {
    val messages = transformSystemMessagesToUser(sessionMessages).mapNotNull { msg ->
        val role = when (msg.sender) {
            MessageSender.USER -> "user"
            MessageSender.AI -> "assistant"
            MessageSender.SYSTEM -> "user"  // Should not happen after normalization
        }
        if (msg.promptParts != null) {
            return@mapNotNull buildJsonObject {
                put("role", role)
                putJsonArray("content") {
                    msg.userParts(null).forEach { part ->
                        when (part) {
                            is PromptPart.Text -> addJsonObject { put("type", format.textType); put("text", part.text) }
                            is PromptPart.Image -> add(format.image("data:$IMAGE_MEDIA_TYPE;base64,${imageData(part.imageId)}"))
                        }
                    }
                }
            }
        }
        val content = when {
            msg.sender == MessageSender.AI && msg.aiMessageJson != null -> msg.aiMessageJson
            else -> extractTextContent(msg) ?: ""
        }
        if (content.isBlank()) null
        else buildJsonObject {
            put("role", role)
            put("content", content)
        }
    }
    return messages + buildJsonObject {
        put("role", "user")
        put("content", datetimeText)
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
