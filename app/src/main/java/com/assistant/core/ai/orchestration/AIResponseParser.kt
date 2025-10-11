package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager

/**
 * AI Response Parser - parses and validates AI responses
 *
 * Responsibilities:
 * - Parse AI response JSON into AIMessage structure
 * - Validate AIMessage format and constraints
 * - Handle parsing fallbacks for invalid responses
 * - Log token usage statistics
 *
 * All providers must return JSON content matching AIMessage structure:
 * {
 *   "preText": "...",
 *   "validationRequest": true,  // boolean, optional
 *   "dataCommands": [...],
 *   "actionCommands": [...],
 *   "postText": "...",
 *   "keepControl": true,  // boolean, optional
 *   "communicationModule": { "type": "...", "data": {...} }
 * }
 *
 * Provider responsibility: Transform their API response to this format
 *
 * FALLBACK: If JSON parsing fails (AI didn't respect format), creates basic AIMessage
 * with error prefix and raw text as preText
 */
class AIResponseParser(private val context: Context) {

    private val s = Strings.`for`(context = context)

    /**
     * Parse AI response content as AIMessage structure
     *
     * Returns ParseResult with:
     * - aiMessage: Parsed AIMessage, or null if response not successful or empty
     * - formatErrors: List of validation errors if any
     */
    fun parseAIMessageFromResponse(aiResponse: AIResponse): ParseResult {
        if (!aiResponse.success) {
            LogManager.aiSession("AI response not successful, skipping parse", "DEBUG")
            return ParseResult(aiMessage = null)
        }

        if (aiResponse.content.isEmpty()) {
            LogManager.aiSession("AI response content is empty", "DEBUG")
            return ParseResult(aiMessage = null)
        }

        // Log raw AI response content (VERBOSE level)
        LogManager.aiSession("AI RAW RESPONSE: ${aiResponse.content}", "VERBOSE")

        val formatErrors = mutableListOf<String>()

        return try {
            val json = org.json.JSONObject(aiResponse.content)

            // preText is required
            val preText = json.optString("preText", "")
            if (preText.isEmpty()) {
                LogManager.aiSession("AIMessage missing required preText field", "WARN")
                val errorMsg = s.shared("ai_error_missing_required_field").format("preText")
                return ParseResult(aiMessage = null, formatErrors = listOf(errorMsg))
            }

            // Parse optional validationRequest (boolean: true = validation required)
            val validationRequest = if (json.has("validationRequest")) {
                json.optBoolean("validationRequest", false)
            } else null

            // Parse optional dataCommands
            val dataCommands = json.optJSONArray("dataCommands")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    try {
                        val cmd = array.getJSONObject(i)
                        val type = cmd.getString("type")
                        val params = parseJsonObjectToMap(cmd.getJSONObject("params"))
                        // Generate deterministic ID from type + params hashcode
                        val id = "${type}_${params.hashCode()}"
                        DataCommand(
                            id = id,
                            type = type,
                            params = params,
                            isRelative = cmd.optBoolean("isRelative", false)
                        )
                    } catch (e: Exception) {
                        LogManager.aiSession("Failed to parse dataCommand at index $i: ${e.message}", "WARN")
                        null
                    }
                }
            }

            // Parse optional actionCommands
            val actionCommands = json.optJSONArray("actionCommands")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    try {
                        val cmd = array.getJSONObject(i)
                        val type = cmd.getString("type")
                        val params = parseJsonObjectToMap(cmd.getJSONObject("params"))
                        // Generate deterministic ID from type + params hashcode
                        val id = "${type}_${params.hashCode()}"
                        DataCommand(
                            id = id,
                            type = type,
                            params = params,
                            isRelative = cmd.optBoolean("isRelative", false)
                        )
                    } catch (e: Exception) {
                        LogManager.aiSession("Failed to parse actionCommand at index $i: ${e.message}", "WARN")
                        null
                    }
                }
            }

            // Parse optional postText
            val postText = json.optString("postText").takeIf { it.isNotEmpty() }

            // Parse optional keepControl (boolean: true = keep control after successful actions)
            val keepControl = if (json.has("keepControl")) {
                json.optBoolean("keepControl", false)
            } else null

            // Parse optional communicationModule
            val communicationModule = json.optJSONObject("communicationModule")?.let { module ->
                try {
                    val type = module.getString("type")
                    val data = parseJsonObjectToMap(module.getJSONObject("data"))

                    when (type) {
                        "MultipleChoice" -> CommunicationModule.MultipleChoice(type, data)
                        "Validation" -> CommunicationModule.Validation(type, data)
                        else -> {
                            val errorMsg = s.shared("ai_error_communication_module_unknown_type").format(type)
                            LogManager.aiSession(errorMsg, "WARN")
                            formatErrors.add(errorMsg)
                            null
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = s.shared("ai_error_communication_module_parse").format(e.message ?: "unknown error")
                    LogManager.aiSession(errorMsg, "WARN")
                    formatErrors.add(errorMsg)
                    null
                }
            }

            // Create AIMessage
            val aiMessage = AIMessage(
                preText = preText,
                validationRequest = validationRequest,
                dataCommands = dataCommands,
                actionCommands = actionCommands,
                postText = postText,
                keepControl = keepControl,
                communicationModule = communicationModule
            )

            // Manual validation: check mutual exclusivity and field constraints
            val hasDataCommands = dataCommands != null && dataCommands.isNotEmpty()
            val hasActionCommands = actionCommands != null && actionCommands.isNotEmpty()
            val hasCommunicationModule = communicationModule != null

            // Count how many "action types" are present
            val actionTypesCount = listOf(hasDataCommands, hasActionCommands, hasCommunicationModule).count { it }

            // Rule 1: At most one action type (can be none for simple text response)
            if (actionTypesCount > 1) {
                val presentTypes = mutableListOf<String>()
                if (hasDataCommands) presentTypes.add("dataCommands")
                if (hasActionCommands) presentTypes.add("actionCommands")
                if (hasCommunicationModule) presentTypes.add("communicationModule")
                formatErrors.add(s.shared("ai_error_validation_multiple_action_types").format(presentTypes.joinToString(", ")))
            }

            // Rule 2: validationRequest only valid with actionCommands
            if (validationRequest != null && !hasActionCommands) {
                formatErrors.add(s.shared("ai_error_validation_request_without_actions"))
            }

            // Rule 3: postText only valid with actionCommands
            if (postText != null && !hasActionCommands) {
                formatErrors.add(s.shared("ai_error_posttext_without_actions"))
            }

            // If validation errors, return null aiMessage with errors
            if (formatErrors.isNotEmpty()) {
                LogManager.aiSession("Validation errors detected: ${formatErrors.joinToString("; ")}", "WARN")
                return ParseResult(aiMessage = null, formatErrors = formatErrors)
            }

            // Log parsed structure (DEBUG level)
            LogManager.aiSession(
                "AI PARSED MESSAGE:\n" +
                "  preText: ${preText.take(100)}${if (preText.length > 100) "..." else ""}\n" +
                "  validationRequest: ${validationRequest ?: "null"}\n" +
                "  dataCommands: ${dataCommands?.size ?: 0} commands\n" +
                "  actionCommands: ${actionCommands?.size ?: 0} commands\n" +
                "  postText: ${postText?.take(50) ?: "null"}\n" +
                "  keepControl: ${keepControl ?: "null"}\n" +
                "  communicationModule: ${communicationModule?.type ?: "null"}",
                "DEBUG"
            )

            ParseResult(aiMessage, formatErrors)

        } catch (e: Exception) {
            LogManager.aiSession("Failed to parse AIMessage from response: ${e.message}", "WARN", e)
            LogManager.aiSession("Falling back to raw text display with error prefix", "WARN")

            // FALLBACK: Create basic AIMessage with error prefix
            val errorPrefix = s.shared("ai_response_invalid_format")
            val fallbackMessage = AIMessage(
                preText = "$errorPrefix ${aiResponse.content}",
                validationRequest = null,
                dataCommands = null,
                actionCommands = null,
                postText = null,
                keepControl = null,
                communicationModule = null
            )

            // Log fallback message (DEBUG level)
            LogManager.aiSession(
                "AI FALLBACK MESSAGE (invalid JSON):\n" +
                "  preText: ${fallbackMessage.preText.take(100)}${if (fallbackMessage.preText.length > 100) "..." else ""}",
                "DEBUG"
            )

            ParseResult(fallbackMessage, listOf("General JSON parsing error: ${e.message}"))
        }
    }

    /**
     * Log token usage statistics in one line
     * Format: Total tokens, cache write, cache read, uncached, output (with counts and percentages)
     */
    fun logTokenStats(aiResponse: AIResponse) {
        if (!aiResponse.success) return

        // Calculate total input tokens (all sources)
        val totalInput = aiResponse.inputTokens + aiResponse.cacheWriteTokens + aiResponse.cacheReadTokens
        val totalTokens = totalInput + aiResponse.tokensUsed

        // Avoid division by zero
        if (totalTokens == 0) {
            LogManager.aiSession("Tokens: 0 total", "INFO")
            return
        }

        // Calculate percentages
        val cacheWritePct = (aiResponse.cacheWriteTokens * 100.0 / totalTokens)
        val cacheReadPct = (aiResponse.cacheReadTokens * 100.0 / totalTokens)
        val uncachedPct = (aiResponse.inputTokens * 100.0 / totalTokens)
        val outputPct = (aiResponse.tokensUsed * 100.0 / totalTokens)

        // Format one-line log
        val logMessage = "Tokens: $totalTokens total, " +
                "cache_write: ${aiResponse.cacheWriteTokens} (%.1f%%), ".format(cacheWritePct) +
                "cache_read: ${aiResponse.cacheReadTokens} (%.1f%%), ".format(cacheReadPct) +
                "uncached: ${aiResponse.inputTokens} (%.1f%%), ".format(uncachedPct) +
                "output: ${aiResponse.tokensUsed} (%.1f%%)".format(outputPct)

        LogManager.aiSession(logMessage, "INFO")
    }

    /**
     * Helper to parse JSONObject to Map<String, Any>
     * Handles nested objects and arrays
     */
    private fun parseJsonObjectToMap(jsonObject: org.json.JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        jsonObject.keys().forEach { key ->
            val value = jsonObject.get(key)
            map[key] = when (value) {
                is org.json.JSONObject -> parseJsonObjectToMap(value)
                is org.json.JSONArray -> {
                    (0 until value.length()).map { i ->
                        val item = value.get(i)
                        when (item) {
                            is org.json.JSONObject -> parseJsonObjectToMap(item)
                            else -> item
                        }
                    }
                }
                else -> value
            }
        }
        return map
    }
}

/**
 * Result of parsing AI response
 */
data class ParseResult(
    val aiMessage: AIMessage?,
    val formatErrors: List<String> = emptyList()
)
