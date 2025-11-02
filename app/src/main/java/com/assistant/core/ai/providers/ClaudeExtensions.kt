package com.assistant.core.ai.providers

import com.assistant.core.ai.data.*
import kotlinx.serialization.json.*
import org.json.JSONObject

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
 * @param config Provider configuration (api_key, model, max_tokens, etc.)
 * @param context Android context for i18n strings
 * @return JsonObject ready for Claude API /v1/messages endpoint
 */
internal fun PromptData.toClaudeJson(config: JSONObject, context: android.content.Context): JsonObject {
    val model = config.getString("model")
    val maxTokens = config.optInt("max_tokens", 32000)

    return buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)

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

            // Add current datetime as final message (always fresh, no cache)
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val now = System.currentTimeMillis()

            // Use app's configured locale
            val locale = com.assistant.core.utils.LocaleUtils.getAppLocale(context)
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
            val dateStr = dateFormat.format(java.util.Date(now))
            val datetimeText = s.shared("ai_prompt_current_datetime").format(dateStr, now)

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
                // Flush accumulated USER blocks
                if (currentUserBlocks.isNotEmpty()) {
                    result.add(FusedMessage("user", currentUserBlocks.toList()))
                    currentUserBlocks.clear()
                }

                // Add AI message (use aiMessageJson if available, otherwise construct)
                // Filter empty content to avoid API errors
                val aiContent = msg.aiMessageJson ?: extractTextContent(msg) ?: ""
                if (aiContent.isNotBlank()) {
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
        // Rich content has priority (user messages with enrichments)
        message.richContent != null -> message.richContent.linearText

        // Simple text content
        message.textContent != null -> message.textContent

        // AI message preText
        message.aiMessage != null -> message.aiMessage.preText

        // System message summary (for SYSTEM → USER transformed messages)
        message.systemMessage != null -> {
            val systemMsg = message.systemMessage
            val summary = systemMsg.summary

            buildString {
                appendLine(summary)

                // Include all command results with their details (like in UI)
                if (systemMsg.commandResults.isNotEmpty()) {
                    appendLine()
                    systemMsg.commandResults.forEach { result ->

                        // Show details (verbalized description) if available
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

    // Extract content
    val contentArray = jsonObj["content"]?.jsonArray
    val content = contentArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

    // Extract usage metrics
    val usage = jsonObj["usage"]?.jsonObject
    val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0
    val cacheWriteTokens = usage?.get("cache_creation_input_tokens")?.jsonPrimitive?.int ?: 0
    val cacheReadTokens = usage?.get("cache_read_input_tokens")?.jsonPrimitive?.int ?: 0
    val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0

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
