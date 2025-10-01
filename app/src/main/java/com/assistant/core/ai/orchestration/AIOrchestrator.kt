package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.utils.TokenCalculator
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Orchestrator - coordinates the complete AI flow without being an ExecutableService
 *
 * Primary responsibilities:
 * - Complete flow orchestration: user message → prompt building → AI call → response processing
 * - Enrichment queries integration to Level 4
 * - Token validation and limits checking
 * - Coordinates AISessionService (DB operations) and AIProviderService (AI calls)
 * - Used directly by UI components for complex AI interactions
 */
class AIOrchestrator(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val aiClient = AIClient(context)
    private val s = Strings.`for`(context = context)

    // ========================================================================================
    // Main Public API - Pure orchestration, no direct DB access
    // ========================================================================================

    /**
     * Send user message to AI - complete flow orchestration
     * 1. Add enrichment queries to Level 4 (with validation)
     * 2. Store user message in session via coordinator
     * 3. Build prompt via PromptManager
     * 4. Send to AI service via coordinator
     * 5. Process response and store AI message via coordinator
     */
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult {
        LogManager.aiSession("AIOrchestrator.sendMessage() called for session $sessionId", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // Level 4 enrichments are now extracted from message history during prompt generation

                // 2. Store user message via coordinator with full RichMessage serialization
                val userMessageResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                    "sessionId" to sessionId,
                    "sender" to MessageSender.USER.name,
                    "richContent" to richMessage.toJson(), // Store complete RichMessage as JSON
                    "timestamp" to System.currentTimeMillis()
                ))

                if (!userMessageResult.isSuccess) {
                    return@withContext OperationResult.error(s.shared("ai_error_store_user_message").format(userMessageResult.error ?: ""))
                }

                // 3. Load actual session with messages to build prompt
                val session = loadSession(sessionId)
                if (session == null) {
                    return@withContext OperationResult.error(s.shared("ai_error_load_session").format("Session not found"))
                }

                val promptResult = PromptManager.buildPrompt(session, context)

                // 4. Token validation
                val tokenLimit = TokenCalculator.getTokenLimit(context)
                if (promptResult.totalTokens > tokenLimit) {
                    LogManager.aiSession("Token limit exceeded: ${promptResult.totalTokens} > $tokenLimit", "WARN")
                    return@withContext OperationResult.error(s.shared("ai_error_token_limit_exceeded").format(promptResult.totalTokens))
                }

                // 5. Send to AI client directly (no coordinator needed for non-DB operations)
                val aiResponse = aiClient.query(promptResult, "claude") // TODO: Get providerId from session

                if (aiResponse.success) {
                    // 6. Process successful AI response and store via coordinator
                    val responseData = aiResponse.data as Map<String, Any>
                    val aiMessageJson = responseData["aiMessageJson"] as? String ?: ""

                    val aiMessageResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                        "sessionId" to sessionId,
                        "sender" to MessageSender.AI.name,
                        "aiMessageJson" to aiMessageJson as Any,
                        "timestamp" to System.currentTimeMillis() as Any
                    ))

                    if (aiMessageResult.isSuccess) {
                        LogManager.aiSession("Message sent and AI response stored successfully", "INFO")
                        OperationResult.success(mapOf("aiMessage" to aiMessageJson as Any))
                    } else {
                        OperationResult.error(s.shared("ai_error_store_ai_response").format(aiMessageResult.error ?: ""))
                    }
                } else {
                    OperationResult.error(s.shared("ai_error_ai_query_failed").format(aiResponse.error ?: ""))
                }

            } catch (e: Exception) {
                LogManager.aiSession("AIOrchestrator.sendMessage - Error: ${e.message}", "ERROR", e)
                OperationResult.error(s.shared("ai_error_orchestration").format(e.message ?: ""))
            }
        }
    }

    /**
     * Create new AI session via coordinator
     */
    suspend fun createSession(
        name: String,
        type: SessionType = SessionType.CHAT,
        providerId: String = "claude"
    ): String {
        LogManager.aiSession("AIOrchestrator.createSession() called: name=$name, type=$type", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.create_session", mapOf(
            "name" to name,
            "type" to type.name,
            "providerId" to providerId
        ))

        return if (result.isSuccess) {
            val sessionId = result.data?.get("sessionId") as? String ?: ""
            LogManager.aiSession("Session created successfully: $sessionId", "INFO")
            sessionId
        } else {
            LogManager.aiSession("Failed to create session: ${result.error}", "ERROR")
            ""
        }
    }

    /**
     * Load AI session via coordinator
     */
    suspend fun loadSession(sessionId: String): AISession? {
        LogManager.aiSession("AIOrchestrator.loadSession() called for $sessionId", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf(
            "sessionId" to sessionId
        ))

        return if (result.isSuccess) {
            parseAISessionFromResult(result.data)
        } else {
            LogManager.aiSession(s.shared("ai_error_load_session").format(result.error ?: ""), "ERROR")
            null
        }
    }

    // ========================================================================================
    // Private Parsing Logic - Complete implementation with stub/fallbacks for JSON parsing
    // ========================================================================================

    /**
     * Parse AISession from coordinator result data
     * Complete parsing logic with graceful fallbacks for complex JSON fields
     */
    private fun parseAISessionFromResult(data: Map<String, Any>?): AISession? {
        if (data == null) {
            LogManager.aiSession("No data returned from coordinator", "WARN")
            return null
        }

        return try {
            val sessionData = data["session"] as? Map<String, Any>
                ?: throw IllegalArgumentException("Missing session data")

            val messagesData = data["messages"] as? List<Map<String, Any>>
                ?: emptyList()

            // Parse basic session fields
            val sessionId = sessionData["id"] as? String
                ?: throw IllegalArgumentException("Missing session id")
            val name = sessionData["name"] as? String
                ?: throw IllegalArgumentException("Missing session name")
            val typeString = sessionData["type"] as? String
                ?: throw IllegalArgumentException("Missing session type")
            val providerId = sessionData["providerId"] as? String
                ?: throw IllegalArgumentException("Missing providerId")
            val providerSessionId = sessionData["providerSessionId"] as? String ?: ""
            val createdAt = (sessionData["createdAt"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("Missing createdAt")
            val lastActivity = (sessionData["lastActivity"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("Missing lastActivity")
            val isActive = sessionData["isActive"] as? Boolean ?: false

            // Parse session type with fallback
            val sessionType = try {
                SessionType.valueOf(typeString)
            } catch (e: Exception) {
                LogManager.aiSession("Invalid session type: $typeString, defaulting to CHAT", "WARN")
                SessionType.CHAT
            }

            // Parse messages with complete logic but stub JSON parsing
            val messages = messagesData.mapNotNull { messageData ->
                parseSessionMessageFromData(messageData)
            }

            // TODO: Parse schedule config for AUTOMATION sessions (currently null)
            val scheduleConfig: ScheduleConfig? = null

            LogManager.aiSession("Successfully parsed AISession: $sessionId with ${messages.size} messages", "DEBUG")

            AISession(
                id = sessionId,
                name = name,
                type = sessionType,
                providerId = providerId,
                providerSessionId = providerSessionId,
                schedule = scheduleConfig,
                createdAt = createdAt,
                lastActivity = lastActivity,
                messages = messages,
                isActive = isActive
            )

        } catch (e: Exception) {
            LogManager.aiSession("Failed to parse AISession: ${e.message}", "ERROR", e)
            null
        }
    }

    /**
     * Parse SessionMessage from message data
     * Complete parsing logic with graceful JSON deserialization fallbacks
     */
    private fun parseSessionMessageFromData(messageData: Map<String, Any>): SessionMessage? {
        return try {
            val id = messageData["id"] as? String
                ?: throw IllegalArgumentException("Missing message id")
            val timestamp = (messageData["timestamp"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("Missing message timestamp")
            val senderString = messageData["sender"] as? String
                ?: throw IllegalArgumentException("Missing message sender")

            // Parse sender with fallback
            val sender = try {
                MessageSender.valueOf(senderString)
            } catch (e: Exception) {
                LogManager.aiSession("Invalid message sender: $senderString, defaulting to USER", "WARN")
                MessageSender.USER
            }

            // Parse rich content JSON with fallback
            val richContent = messageData["richContentJson"]?.let { jsonString ->
                if (jsonString is String && jsonString.isNotEmpty()) {
                    parseRichMessageFromJson(jsonString)
                } else null
            }

            // Parse simple text content
            val textContent = messageData["textContent"] as? String

            // Parse AI message JSON with fallback
            val aiMessage = messageData["aiMessageJson"]?.let { jsonString ->
                if (jsonString is String && jsonString.isNotEmpty()) {
                    parseAIMessageFromJson(jsonString)
                } else null
            }

            // Store original AI JSON for prompt history consistency
            val aiMessageJson = messageData["aiMessageJson"] as? String

            // TODO: Parse system messages when implemented
            val systemMessage: SystemMessage? = null

            // TODO: Parse execution metadata for automation messages
            val executionMetadata: ExecutionMetadata? = null

            SessionMessage(
                id = id,
                timestamp = timestamp,
                sender = sender,
                richContent = richContent,
                textContent = textContent,
                aiMessage = aiMessage,
                aiMessageJson = aiMessageJson,
                systemMessage = systemMessage,
                executionMetadata = executionMetadata
            )

        } catch (e: Exception) {
            LogManager.aiSession("Failed to parse SessionMessage: ${e.message}", "WARN", e)
            null // Return null to filter out invalid messages
        }
    }

    /**
     * Parse RichMessage from JSON string
     * Uses RichMessage.fromJson() for complete deserialization
     */
    private fun parseRichMessageFromJson(jsonString: String): RichMessage? {
        return try {
            val richMessage = RichMessage.fromJson(jsonString)
            if (richMessage == null) {
                LogManager.aiSession("Failed to deserialize RichMessage from JSON: ${jsonString.take(50)}...", "WARN")
            }
            richMessage
        } catch (e: Exception) {
            LogManager.aiSession("Failed to parse RichMessage JSON: ${e.message}", "WARN", e)
            null
        }
    }

    /**
     * Parse AIMessage from JSON string
     * TODO: Implement complete JSON deserialization with all AIMessage fields
     * Currently returns stub implementation for compilation
     */
    private fun parseAIMessageFromJson(jsonString: String): AIMessage? {
        return try {
            // TODO: Implement proper JSON parsing for AIMessage
            // Should parse preText, validationRequest, dataCommands, actionCommands, postText, communicationModule
            LogManager.aiSession("TODO: Implement AIMessage JSON parsing for: ${jsonString.take(50)}...", "DEBUG")

            // Stub implementation for now
            AIMessage(
                preText = "AI response content", // TODO: Extract from JSON
                validationRequest = null, // TODO: Parse ValidationRequest from JSON
                dataCommands = null, // TODO: Parse DataCommand list from JSON
                actionCommands = null, // TODO: Parse DataCommand list from JSON
                postText = null, // TODO: Extract from JSON
                communicationModule = null // TODO: Parse CommunicationModule from JSON
            )
        } catch (e: Exception) {
            LogManager.aiSession("Failed to parse AIMessage JSON: ${e.message}", "WARN", e)
            null
        }
    }

    /**
     * Get active session via coordinator
     */
    suspend fun getActiveSession(): AISession? {
        LogManager.aiSession("AIOrchestrator.getActiveSession() called", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.get_active_session", emptyMap())

        return if (result.isSuccess) {
            val hasActiveSession = result.data?.get("hasActiveSession") as? Boolean ?: false
            if (hasActiveSession) {
                parseAISessionFromResult(result.data)
            } else {
                LogManager.aiSession("No active session found", "DEBUG")
                null
            }
        } else {
            LogManager.aiSession(s.shared("ai_error_get_active_session").format(result.error ?: ""), "ERROR")
            null
        }
    }

    /**
     * Stop current active session (deactivate all sessions)
     */
    suspend fun stopActiveSession(): OperationResult {
        LogManager.aiSession("AIOrchestrator.stopActiveSession() called", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
        return if (result.isSuccess) {
            LogManager.aiSession("Active session stopped successfully", "INFO")
            OperationResult.success()
        } else {
            OperationResult.error(result.error ?: s.shared("ai_error_stop_session"))
        }
    }

    /**
     * Set active session via coordinator
     */
    suspend fun setActiveSession(sessionId: String): OperationResult {
        LogManager.aiSession("AIOrchestrator.setActiveSession() called for $sessionId", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.set_active_session", mapOf(
            "sessionId" to sessionId
        ))
        return if (result.isSuccess) {
            LogManager.aiSession("Session $sessionId set as active", "INFO")
            OperationResult.success()
        } else {
            OperationResult.error(result.error ?: s.shared("ai_error_set_active_session").format(""))
        }
    }

}

