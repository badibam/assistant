package com.assistant.core.ai.providers

import com.assistant.core.ai.data.*
import kotlinx.serialization.json.*

/**
 * Claude-specific extensions for PromptData transformation and response parsing
 *
 * Responsibilities:
 * - Transform PromptData to Claude Messages API JSON format
 * - Fuse consecutive USER messages into multi-block messages
 * - Apply cache_control breakpoints (L1, L2, L3, last message)
 * - Parse Claude API responses with cache metrics
 */

/**
 * Fused message structure for Claude API
 * Represents a message with potentially multiple content blocks
 */
internal data class FusedMessage(
    val role: String,              // "user" or "assistant"
    val contentBlocks: List<String> // List of text content blocks
)

/**
 * Transform PromptData to Claude Messages API JSON format
 *
 * Applies Claude-specific formatting:
 * - System array with L1, L2, L3 (each with cache_control breakpoint)
 * - Messages array with fused USER messages (multi-block for consecutive users)
 * - Current datetime appended at the end (always fresh, never cached)
 * - Cache_control on last block of last history message (4th breakpoint)
 *
 * @param model The model asked for
 * @param maxTokens The longest answer asked for
 * @param effort The reasoning effort, for an endpoint that declares levels (DeepSeek); null otherwise
 * @param datetimeText The dated closing message (buildDatetimeMessage), built by the caller:
 *   it reads the clock and the strings, which keeps this function pure and testable
 * @return JsonObject ready for Claude API /v1/messages endpoint
 */
internal fun PromptData.toClaudeJson(model: String, maxTokens: Int, effort: String?, datetimeText: String): JsonObject {
    return buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)

        // No "thinking" field alongside: a 400 seen through Claude Code (anthropics/claude-code#65863)
        // points to the pair being mutually exclusive on DeepSeek (not measured here)
        if (effort != null) {
            putJsonObject("output_config") {
                put("effort", effort)
            }
        }

        // System array with 3 cache breakpoints (L1, L2, L3)
        putJsonArray("system") {
            // L1: System Documentation
            addJsonObject {
                put("type", "text")
                put("text", level1Content)
                putJsonObject("cache_control") {
                    put("type", "ephemeral")
                }
            }

            // L2: User Data
            addJsonObject {
                put("type", "text")
                put("text", level2Content)
                putJsonObject("cache_control") {
                    put("type", "ephemeral")
                }
            }

            // L3: APP_STATE Snapshot (frozen at first message)
            addJsonObject {
                put("type", "text")
                put("text", level3Content)
                putJsonObject("cache_control") {
                    put("type", "ephemeral")
                }
            }
        }

        // Messages array with fusion
        putJsonArray("messages") {
            // Step 1: Transform SYSTEM → USER (common transformation)
            val normalizedMessages = transformSystemMessagesToUser(sessionMessages)

            // Step 2: Fuse consecutive USER messages (Claude-specific)
            val fusedMessages = fuseConsecutiveUserMessages(normalizedMessages)

            // Build messages with cache_control on last block of last message
            fusedMessages.forEachIndexed { index, msg ->
                addJsonObject {
                    put("role", msg.role)

                    val isLastMessage = index == fusedMessages.lastIndex

                    // Last message must use array format to support cache_control
                    if (msg.contentBlocks.size == 1 && !isLastMessage) {
                        // Single block message (not last) - use string content for simplicity
                        put("content", msg.contentBlocks[0])
                    } else {
                        // Multi-block message OR last message - use array of text objects
                        putJsonArray("content") {
                            msg.contentBlocks.forEachIndexed { blockIndex, block ->
                                addJsonObject {
                                    put("type", "text")
                                    put("text", block)

                                    // Cache control on last block of last message (4th breakpoint)
                                    if (isLastMessage && blockIndex == msg.contentBlocks.lastIndex) {
                                        putJsonObject("cache_control") {
                                            put("type", "ephemeral")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // The dated closing message: after the last breakpoint, so its change on every call
            // costs no cache
            addJsonObject {
                put("role", "user")
                put("content", datetimeText)
            }
        }
    }
}

/**
 * Fuse consecutive USER messages into single messages with multiple content blocks
 *
 * This is Step 2 of Claude message preparation (Claude-specific).
 * Claude requires strict USER/ASSISTANT alternation, so consecutive USER messages
 * must be combined into one message with multiple content blocks.
 *
 * @param messages Normalized messages (only USER and AI, no SYSTEM)
 * @return List of fused messages with strict alternation
 *
 * Example:
 * INPUT:  [USER "q1", USER "data", AI "resp", USER "q2"]
 * OUTPUT: [FusedMessage("user", ["q1", "data"]), FusedMessage("assistant", ["resp"]), FusedMessage("user", ["q2"])]
 */
internal fun fuseConsecutiveUserMessages(messages: List<SessionMessage>): List<FusedMessage> {
    val result = mutableListOf<FusedMessage>()
    val currentUserBlocks = mutableListOf<String>()

    messages.forEach { msg ->
        when (msg.sender) {
            MessageSender.USER -> {
                // Accumulate USER content blocks (filter empty/blank blocks)
                extractTextContent(msg)?.let { text ->
                    if (text.isNotBlank()) {
                        currentUserBlocks.add(text)
                    }
                }
            }
            MessageSender.AI -> {
                // An empty AI message is left out (the API refuses an empty block), and it
                // must not close the user turn either: the user blocks on both sides of it
                // belong to one turn, or two user turns would follow each other.
                val aiContent = msg.aiMessageJson ?: extractTextContent(msg) ?: ""
                if (aiContent.isNotBlank()) {
                    if (currentUserBlocks.isNotEmpty()) {
                        result.add(FusedMessage("user", currentUserBlocks.toList()))
                        currentUserBlocks.clear()
                    }
                    result.add(FusedMessage("assistant", listOf(aiContent)))
                }
            }
            MessageSender.SYSTEM -> {
                // Should never happen after transformSystemMessagesToUser
                // But handle gracefully by treating as USER (filter empty/blank blocks)
                extractTextContent(msg)?.let { text ->
                    if (text.isNotBlank()) {
                        currentUserBlocks.add(text)
                    }
                }
            }
        }
    }

    // Flush final USER blocks if any
    if (currentUserBlocks.isNotEmpty()) {
        result.add(FusedMessage("user", currentUserBlocks.toList()))
    }

    return result
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
 * Parse Claude API response to AIResponse
 *
 * Extracts:
 * - Content text
 * - Token usage (input, cache_write, cache_read, output)
 * - Error information if present
 *
 * @return AIResponse with Claude cache metrics
 */
internal fun JsonElement.toClaudeAIResponse(): AIResponse {
    val jsonObj = this.jsonObject

    // Check for error
    val errorObj = jsonObj["error"]?.jsonObject
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

    // A response cut by max_tokens carries incomplete JSON, unusable by the parser.
    val stopReason = jsonObj["stop_reason"]?.jsonPrimitive?.contentOrNull
    if (stopReason == "max_tokens") {
        return AIResponse(
            success = false,
            content = "",
            errorMessage = "Response truncated (stop_reason: max_tokens). Increase max_tokens in provider config.",
            failure = AIFailure.CONFIG,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }

    // Extract content from the text block: with thinking enabled, a "thinking" block comes first
    val contentArray = jsonObj["content"]?.jsonArray
    val textBlock = contentArray?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
    if (textBlock == null) {
        return AIResponse(
            success = false,
            content = "",
            errorMessage = "Provider response has no text block (stop_reason: $stopReason).",
            failure = AIFailure.CONFIG,
            tokensUsed = 0,
            cacheWriteTokens = 0,
            cacheReadTokens = 0,
            inputTokens = 0
        )
    }
    val content = textBlock.jsonObject["text"]?.jsonPrimitive?.content ?: ""

    // Usage: input and output counts are required, since the cost is computed from them and a
    // missing one would show a paid call as free. The cache counts are optional: a provider
    // on this format that does not cache (DeepSeek's documentation is silent on them) sends
    // none, and none means nothing was written to or read from a cache.
    val usage = jsonObj["usage"] as? JsonObject
    val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull
    val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull
    if (inputTokens == null || outputTokens == null) {
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
    val cacheWriteTokens = usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    val cacheReadTokens = usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0

    return AIResponse(
        success = true,
        content = content,
        errorMessage = null,
        tokensUsed = outputTokens,
        cacheWriteTokens = cacheWriteTokens,
        cacheReadTokens = cacheReadTokens,
        inputTokens = inputTokens
    )
}
