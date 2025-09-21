package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.utils.TokenCalculator
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
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
        LogManager.aiSession("AIOrchestrator.sendMessage() called for session $sessionId")

        return withContext(Dispatchers.IO) {
            try {
                // 1. Add enrichment queries to Level 4 if any
                if (richMessage.dataQueries.isNotEmpty()) {
                    val enrichmentResult = addEnrichmentsToSession(sessionId, richMessage.dataQueries)
                    if (!enrichmentResult.success) {
                        return@withContext enrichmentResult
                    }
                }

                // 2. Store user message via coordinator
                val userMessageResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                    "sessionId" to sessionId,
                    "sender" to MessageSender.USER.name,
                    "richContent" to richMessage.linearText, // Simplified for now
                    "timestamp" to System.currentTimeMillis()
                ))

                if (!userMessageResult.isSuccess) {
                    return@withContext OperationResult.error("Failed to store user message: ${userMessageResult.error}")
                }

                // 3. Build prompt via PromptManager - TODO: Load actual session
                // For now, create a mock session for compilation
                val mockSession = AISession(
                    id = sessionId,
                    name = "Chat Session",
                    type = SessionType.CHAT,
                    providerId = "claude",
                    providerSessionId = "",
                    schedule = null, // No schedule for CHAT sessions
                    createdAt = System.currentTimeMillis(),
                    lastActivity = System.currentTimeMillis(),
                    messages = emptyList(),
                    isActive = true
                )
                val promptResult = PromptManager.buildPrompt(mockSession, context)

                // 4. Token validation
                val tokenLimit = TokenCalculator.getTokenLimit(context)
                if (promptResult.totalTokens > tokenLimit) {
                    LogManager.aiSession("Token limit exceeded: ${promptResult.totalTokens} > $tokenLimit", "WARN")
                    return@withContext OperationResult.error("Token limit exceeded: ${promptResult.totalTokens} tokens")
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
                        OperationResult.success(mapOf("aiMessage" to aiMessageJson as Any))
                    } else {
                        OperationResult.error("Failed to store AI response: ${aiMessageResult.error}")
                    }
                } else {
                    OperationResult.error("AI query failed: ${aiResponse.error}")
                }

            } catch (e: Exception) {
                LogManager.aiSession("AIOrchestrator.sendMessage - Error: ${e.message}", "ERROR")
                OperationResult.error("Orchestration error: ${e.message}")
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
        LogManager.aiSession("AIOrchestrator.createSession() called: name=$name, type=$type")

        val result = coordinator.processUserAction("ai_sessions.create_session", mapOf(
            "name" to name,
            "type" to type.name,
            "providerId" to providerId
        ))

        return if (result.isSuccess) {
            result.data?.get("sessionId") as? String ?: ""
        } else {
            LogManager.aiSession("Failed to create session: ${result.error}", "ERROR")
            ""
        }
    }

    /**
     * Load AI session via coordinator
     */
    suspend fun loadSession(sessionId: String): AISession? {
        LogManager.aiSession("AIOrchestrator.loadSession() called for $sessionId")

        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf(
            "sessionId" to sessionId
        ))

        return if (result.isSuccess) {
            // TODO: Parse AISession from result.data
            null // Placeholder
        } else {
            LogManager.aiSession("Failed to load session: ${result.error}", "ERROR")
            null
        }
    }

    /**
     * Set active session via coordinator
     */
    suspend fun setActiveSession(sessionId: String): OperationResult {
        LogManager.aiSession("AIOrchestrator.setActiveSession() called for $sessionId")

        val result = coordinator.processUserAction("ai_sessions.set_active_session", mapOf(
            "sessionId" to sessionId
        ))
        return if (result.isSuccess) {
            OperationResult.success()
        } else {
            OperationResult.error(result.error ?: "Failed to set active session")
        }
    }

    /**
     * Add enrichment queries to session Level 4 via coordinator
     */
    suspend fun addEnrichmentsToSession(sessionId: String, dataQueries: List<DataQuery>): OperationResult {
        LogManager.aiSession("AIOrchestrator.addEnrichmentsToSession() called with ${dataQueries.size} queries")

        // TODO: Implement enrichment addition via coordinator
        // This should update the session's Level 4 queries
        return OperationResult.success()
    }
}

