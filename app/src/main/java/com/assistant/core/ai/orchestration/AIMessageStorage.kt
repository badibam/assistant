package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.processing.AICommandProcessor
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI Message Storage - manages message storage and reactive updates
 *
 * Responsibilities:
 * - Store all types of messages (AI, System, User enrichments)
 * - Manage reactive messages flow for UI updates
 * - Parse messages from service responses
 * - Execute commands (enrichments, data queries, actions) and create SystemMessages
 *
 * This class centralizes all message storage logic to maintain consistency
 * and provide a single reactive flow for UI updates.
 */
class AIMessageStorage(
    private val context: Context,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope,
    private val getActiveSessionId: () -> String?
) {

    private val s = Strings.`for`(context = context)

    // Reactive messages flow for active session (observable by UI)
    private val _activeSessionMessages = MutableStateFlow<List<SessionMessage>>(emptyList())
    val activeSessionMessages: StateFlow<List<SessionMessage>> = _activeSessionMessages.asStateFlow()

    // ========================================================================================
    // Message Flow Management
    // ========================================================================================

    /**
     * Update reactive messages flow for active session
     * Called after storing any message to trigger UI update
     */
    fun updateMessagesFlow(sessionId: String) {
        // Only update if this is still the active session
        if (getActiveSessionId() != sessionId) {
            LogManager.aiSession("Skipping messages flow update for non-active session $sessionId", "DEBUG")
            return
        }

        // Load messages asynchronously and update flow
        scope.launch {
            try {
                val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))
                if (result.isSuccess) {
                    val messagesData = result.data?.get("messages") as? List<*>
                    val messages = parseMessages(messagesData)

                    // Update flow on main thread
                    withContext(Dispatchers.Main) {
                        _activeSessionMessages.value = messages
                        LogManager.aiSession("Updated messages flow for session $sessionId: ${messages.size} messages", "DEBUG")
                    }
                } else {
                    LogManager.aiSession("Failed to load messages for flow update: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                LogManager.aiSession("Exception updating messages flow: ${e.message}", "ERROR", e)
            }
        }
    }

    /**
     * Clear messages flow (when session is closed)
     */
    fun clearMessagesFlow() {
        _activeSessionMessages.value = emptyList()
    }

    /**
     * Parse messages list from service response
     * Returns empty list if parsing fails completely
     */
    private fun parseMessages(messagesData: List<*>?): List<SessionMessage> {
        if (messagesData == null) return emptyList()

        return messagesData.mapNotNull { msgData ->
            try {
                val msgMap = msgData as? Map<*, *> ?: return@mapNotNull null

                val id = msgMap["id"] as? String ?: return@mapNotNull null
                val timestamp = (msgMap["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val senderStr = msgMap["sender"] as? String ?: return@mapNotNull null
                val sender = try {
                    MessageSender.valueOf(senderStr)
                } catch (e: Exception) {
                    LogManager.aiSession("Invalid sender type: $senderStr", "WARN")
                    return@mapNotNull null
                }

                // Parse richContent from JSON if present
                val richContentJson = msgMap["richContentJson"] as? String
                val richContent = if (!richContentJson.isNullOrEmpty()) {
                    RichMessage.fromJson(richContentJson).also { parsed ->
                        if (parsed == null) {
                            LogManager.aiSession("Failed to parse richContentJson for message $id", "WARN")
                        }
                    }
                } else null

                // Get textContent as-is
                val textContent = (msgMap["textContent"] as? String)?.takeIf { it.isNotEmpty() }

                // Parse aiMessage from aiMessageJson if present
                val aiMessageJsonStr = msgMap["aiMessageJson"] as? String
                val aiMessage = if (!aiMessageJsonStr.isNullOrEmpty()) {
                    AIMessage.fromJson(aiMessageJsonStr).also { parsed ->
                        if (parsed == null) {
                            LogManager.aiSession("Failed to parse aiMessageJson for message $id", "WARN")
                        }
                    }
                } else null

                // Parse systemMessage from JSON if present
                val systemMessageJson = msgMap["systemMessageJson"] as? String
                val systemMessage = if (!systemMessageJson.isNullOrEmpty()) {
                    SystemMessage.fromJson(systemMessageJson).also { parsed ->
                        if (parsed == null) {
                            LogManager.aiSession("Failed to parse systemMessageJson for message $id", "WARN")
                        }
                    }
                } else null

                // executionMetadata = null (TODO: automation)
                val excludeFromPrompt = msgMap["excludeFromPrompt"] as? Boolean ?: false

                SessionMessage(
                    id = id,
                    timestamp = timestamp,
                    sender = sender,
                    richContent = richContent,
                    textContent = textContent,
                    aiMessage = aiMessage,
                    aiMessageJson = aiMessageJsonStr?.takeIf { it.isNotEmpty() }, // Keep original JSON
                    systemMessage = systemMessage,
                    executionMetadata = null, // TODO: Parse when automation implemented
                    excludeFromPrompt = excludeFromPrompt
                )

            } catch (e: Exception) {
                LogManager.aiSession("Failed to parse message: ${e.message}", "WARN", e)
                null // Skip this message
            }
        }
    }

    // ========================================================================================
    // Message Storage Functions
    // ========================================================================================

    /**
     * Store AI message response
     * Note: Content should be already cleaned of markdown markers
     * @return Message ID of the stored message (for validation), or null if storage failed
     */
    suspend fun storeAIMessage(aiResponse: AIResponse, sessionId: String): String? {
        return try {
            val aiMessageJson = aiResponse.content

            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.AI.name,
                "aiMessageJson" to aiMessageJson,
                "timestamp" to System.currentTimeMillis(),
                // Token usage metrics for cost calculation
                "inputTokens" to aiResponse.inputTokens,
                "cacheWriteTokens" to aiResponse.cacheWriteTokens,
                "cacheReadTokens" to aiResponse.cacheReadTokens,
                "outputTokens" to aiResponse.tokensUsed
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store AI message: ${storeResult.error}", "WARN")
                null
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)

                // Return message ID for validation
                storeResult.data?.get("messageId") as? String
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing AI message: ${e.message}", "ERROR", e)
            null
        }
    }

    /**
     * Store SystemMessage in session
     */
    suspend fun storeSystemMessage(systemMessage: SystemMessage, sessionId: String) {
        try {
            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.SYSTEM.name,
                "systemMessage" to systemMessage,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store SystemMessage: ${storeResult.error}", "WARN")
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing SystemMessage: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Store postText as separate AI message (UI only, excluded from prompt)
     * Called after successful actions to display completion message
     */
    suspend fun storePostTextMessage(postText: String, sessionId: String) {
        try {
            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.AI.name,
                "textContent" to postText,
                "excludeFromPrompt" to true,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store postText message: ${storeResult.error}", "WARN")
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing postText message: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Store limit reached message
     */
    suspend fun storeLimitReachedMessage(reason: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.LIMIT_REACHED,
            commandResults = emptyList(),
            summary = reason,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

    /**
     * Store FORMAT_ERROR message for AI response format errors
     */
    suspend fun storeFormatErrorMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.FORMAT_ERROR,
            commandResults = emptyList(),
            summary = summary,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

    /**
     * Store NETWORK_ERROR message for network/HTTP/provider errors
     */
    suspend fun storeNetworkErrorMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.NETWORK_ERROR,
            commandResults = emptyList(),
            summary = summary,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

    /**
     * Store SESSION_TIMEOUT message for watchdog timeout errors
     */
    suspend fun storeSessionTimeoutMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.SESSION_TIMEOUT,
            commandResults = emptyList(),
            summary = summary,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

    /**
     * Store INTERRUPTED message when user stops autonomous loop
     */
    suspend fun storeInterruptedMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.INTERRUPTED,
            commandResults = emptyList(),
            summary = summary,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

    /**
     * Store communication module as text message for UI display (persists in history)
     * This message is NOT deleted after user response, it stays in the history
     *
     * @param moduleText Text representation of the module
     * @param sessionId Session ID
     * @param triggerUIUpdate Whether to trigger updateMessagesFlow (default true for backward compatibility)
     * @return Message ID of the stored text message, or null if storage failed
     */
    suspend fun storeCommunicationModuleTextMessage(
        moduleText: String,
        sessionId: String,
        triggerUIUpdate: Boolean = true
    ): String? {
        return try {
            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.AI.name,
                "textContent" to moduleText,
                "excludeFromPrompt" to true,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store communication module text message: ${storeResult.error}", "WARN")
                null
            } else {
                // Update reactive messages flow for UI only if requested
                if (triggerUIUpdate) {
                    updateMessagesFlow(sessionId)
                }

                // Return message ID
                val messageId = storeResult.data?.get("messageId") as? String
                LogManager.aiSession("Created communication module text message: $messageId (UI update: $triggerUIUpdate)", "DEBUG")
                messageId
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing communication module text message: ${e.message}", "ERROR", e)
            null
        }
    }

    /**
     * Create and store COMMUNICATION_CANCELLED message
     * This message is created as a fallback when no response is provided to a communication module
     * It will be deleted if the user actually provides a response
     *
     * @param sessionId Session ID
     * @param triggerUIUpdate Whether to trigger updateMessagesFlow (default true for backward compatibility)
     * @return Message ID of the stored cancellation message, or null if storage failed
     */
    suspend fun createAndStoreCommunicationCancelledMessage(
        sessionId: String,
        triggerUIUpdate: Boolean = true
    ): String? {
        return try {
            val systemMessage = SystemMessage(
                type = SystemMessageType.COMMUNICATION_CANCELLED,
                commandResults = emptyList(),
                summary = s.shared("ai_system_communication_no_response"),
                formattedData = null
            )

            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.SYSTEM.name,
                "systemMessage" to systemMessage,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store COMMUNICATION_CANCELLED message: ${storeResult.error}", "WARN")
                null
            } else {
                // Update reactive messages flow for UI only if requested
                if (triggerUIUpdate) {
                    updateMessagesFlow(sessionId)
                }

                // Return message ID for potential deletion
                val messageId = storeResult.data?.get("messageId") as? String
                LogManager.aiSession("Created COMMUNICATION_CANCELLED fallback message: $messageId (UI update: $triggerUIUpdate)", "DEBUG")
                messageId
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing COMMUNICATION_CANCELLED message: ${e.message}", "ERROR", e)
            null
        }
    }

    /**
     * Create and store VALIDATION_CANCELLED message
     * This message is created as a fallback when no validation is provided for AI actions
     * It will be deleted if the user actually validates the actions
     *
     * @param sessionId Session ID
     * @param triggerUIUpdate Whether to trigger updateMessagesFlow (default true for backward compatibility)
     * @return Message ID of the stored cancellation message, or null if storage failed
     */
    suspend fun createAndStoreValidationCancelledMessage(
        sessionId: String,
        triggerUIUpdate: Boolean = true
    ): String? {
        return try {
            val systemMessage = SystemMessage(
                type = SystemMessageType.VALIDATION_CANCELLED,
                commandResults = emptyList(),
                summary = s.shared("ai_system_validation_refused"),
                formattedData = null
            )

            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.SYSTEM.name,
                "systemMessage" to systemMessage,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store VALIDATION_CANCELLED message: ${storeResult.error}", "WARN")
                null
            } else {
                // Update reactive messages flow for UI only if requested
                if (triggerUIUpdate) {
                    updateMessagesFlow(sessionId)
                }

                // Return message ID for potential deletion
                val messageId = storeResult.data?.get("messageId") as? String
                LogManager.aiSession("Created VALIDATION_CANCELLED fallback message: $messageId (UI update: $triggerUIUpdate)", "DEBUG")
                messageId
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing VALIDATION_CANCELLED message: ${e.message}", "ERROR", e)
            null
        }
    }

    // ========================================================================================
    // Command Execution Wrappers
    // ========================================================================================

    /**
     * Execute user enrichments and return SystemMessage
     * Uses same pipeline as old Level 4: EnrichmentProcessor → UserCommandProcessor → CommandExecutor
     */
    suspend fun executeEnrichments(commands: List<DataCommand>): SystemMessage {
        val processor = UserCommandProcessor(context)
        val executableCommands = processor.processCommands(commands)

        val executor = CommandExecutor(context)
        val result = executor.executeCommands(
            commands = executableCommands,
            messageType = SystemMessageType.DATA_ADDED,
            level = "enrichments"
        )

        // Format formattedData from promptResults
        val formattedData = result.promptResults.joinToString("\n\n") {
            "# ${it.dataTitle}\n${it.formattedData}"
        }

        return result.systemMessage.copy(
            formattedData = formattedData
        )
    }

    /**
     * Execute AI data commands (queries)
     */
    suspend fun executeDataCommands(commands: List<DataCommand>): SystemMessage {
        val processor = AICommandProcessor(context)
        val executableCommands = processor.processDataCommands(commands)

        val executor = CommandExecutor(context)
        val result = executor.executeCommands(
            commands = executableCommands,
            messageType = SystemMessageType.DATA_ADDED,
            level = "ai_data"
        )

        // Format formattedData from promptResults
        val formattedData = result.promptResults.joinToString("\n\n") {
            "# ${it.dataTitle}\n${it.formattedData}"
        }

        return result.systemMessage.copy(
            formattedData = formattedData
        )
    }

    /**
     * Execute AI action commands (mutations)
     */
    suspend fun executeActionCommands(commands: List<DataCommand>): SystemMessage {
        val processor = AICommandProcessor(context)
        val executableCommands = processor.processActionCommands(commands)

        val executor = CommandExecutor(context)
        val result = executor.executeCommands(
            commands = executableCommands,
            messageType = SystemMessageType.ACTIONS_EXECUTED,
            level = "ai_actions"
        )

        return result.systemMessage
    }
}
