package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.ai.processing.AICommandProcessor
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * AI Orchestrator singleton - coordinates the complete AI flow
 *
 * Primary responsibilities:
 * - Complete flow orchestration: user message → enrichments → prompt building → AI call → autonomous loops
 * - Autonomous loops with limits (data queries, action retries, communication modules)
 * - User interaction management via StateFlow (validation, communication modules)
 * - Session management via coordinator
 *
 * Singleton pattern allows:
 * - Session persistence across UI lifecycle (Dialog open/close)
 * - State observation via StateFlow for UI reconnection
 * - Global access without dependency injection
 */
object AIOrchestrator {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var aiClient: AIClient
    private lateinit var s: com.assistant.core.strings.StringsContext

    // State for user interaction (observable by UI)
    private val _waitingState = MutableStateFlow<WaitingState>(WaitingState.None)
    val waitingState: StateFlow<WaitingState> = _waitingState.asStateFlow()

    // Continuations for user interaction suspension
    private var validationContinuation: Continuation<Boolean>? = null
    private var responseContinuation: Continuation<String>? = null

    /**
     * Initialize orchestrator with context
     * Must be called at app startup before any usage
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        this.coordinator = Coordinator(this.context)
        this.aiClient = AIClient(this.context)
        this.s = Strings.`for`(context = this.context)
        LogManager.aiSession("AIOrchestrator initialized as singleton")
    }

    // ========================================================================================
    // User Interaction Resume Functions (called from UI)
    // ========================================================================================

    /**
     * Resume execution after user validation
     * Called from UI when user responds to validation request
     */
    fun resumeWithValidation(validated: Boolean) {
        validationContinuation?.resume(validated)
        validationContinuation = null
        _waitingState.value = WaitingState.None
    }

    /**
     * Resume execution after user response to communication module
     * Called from UI when user provides response
     */
    fun resumeWithResponse(response: String) {
        responseContinuation?.resume(response)
        responseContinuation = null
        _waitingState.value = WaitingState.None
    }

    // ========================================================================================
    // Main Public API
    // ========================================================================================

    /**
     * Send user message to AI with autonomous loops
     *
     * Flow:
     * 1. Execute enrichments BEFORE storing message
     * 2. Store user message
     * 3. Store SystemMessage enrichments (if present)
     * 4. Build prompt data (L1-L3 + messages)
     * 5. Call AI with PromptData
     * 6. Store AI response
     * 7. AUTONOMOUS LOOPS (data queries, actions with retries, communication modules)
     */
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult {
        LogManager.aiSession("AIOrchestrator.sendMessage() called for session $sessionId", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // Load session for type and limits
                val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))
                if (!sessionResult.isSuccess) {
                    return@withContext OperationResult.error("Failed to load session: ${sessionResult.error}")
                }

                val sessionData = sessionResult.data?.get("session") as? Map<*, *>
                    ?: return@withContext OperationResult.error("No session data found")

                val sessionTypeStr = sessionData["type"] as? String ?: "CHAT"
                val sessionType = SessionType.valueOf(sessionTypeStr)

                // Get limits for session type
                val limits = getLimitsForSessionType(sessionType)

                // 1. Execute enrichments BEFORE storing message user
                val enrichmentSystemMessage = if (richMessage.dataCommands.isNotEmpty()) {
                    executeEnrichments(richMessage.dataCommands)
                } else null

                // 2. Store user message
                val userMessageResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                    "sessionId" to sessionId,
                    "sender" to MessageSender.USER.name,
                    "richContent" to richMessage.toJson(),
                    "timestamp" to System.currentTimeMillis()
                ))

                if (!userMessageResult.isSuccess) {
                    return@withContext OperationResult.error("Failed to store user message: ${userMessageResult.error}")
                }

                // 3. Store SystemMessage enrichments (if present)
                if (enrichmentSystemMessage != null) {
                    storeSystemMessage(enrichmentSystemMessage, sessionId)
                }

                // 4. Build prompt data
                val promptData = PromptManager.buildPromptData(sessionId, context)

                // 5. Call AI with PromptData
                var aiResponse = aiClient.query(promptData)

                // 6. Store AI response
                storeAIMessage(aiResponse, sessionId)

                // 7. AUTONOMOUS LOOPS
                var totalRoundtrips = 0
                var consecutiveDataQueries = 0
                var consecutiveActionRetries = 0
                var communicationRoundtrips = 0

                while (totalRoundtrips < limits.maxAutonomousRoundtrips) {

                    // Parse AI message for commands
                    val aiMessage = parseAIMessageFromResponse(aiResponse)

                    if (aiMessage == null) {
                        // No AI message structure - end loop
                        break
                    }

                    // 7a. COMMUNICATION MODULE (prioritaire)
                    if (aiMessage.communicationModule != null) {
                        if (communicationRoundtrips >= limits.maxCommunicationModules) {
                            storeLimitReachedMessage("Communication modules limit reached", sessionId)
                            break
                        }

                        // STOP - Attendre réponse utilisateur
                        val userResponse = waitForUserResponse(aiMessage.communicationModule)

                        // Stocker réponse user (texte simple)
                        coordinator.processUserAction("ai_sessions.create_message", mapOf(
                            "sessionId" to sessionId,
                            "sender" to MessageSender.USER.name,
                            "textContent" to userResponse,
                            "timestamp" to System.currentTimeMillis()
                        ))

                        // Renvoyer automatiquement à l'IA
                        val newPromptData = PromptManager.buildPromptData(sessionId, context)
                        aiResponse = aiClient.query(newPromptData)
                        storeAIMessage(aiResponse, sessionId)

                        communicationRoundtrips++
                        totalRoundtrips++
                        continue
                    }

                    // 7b. DATA COMMANDS (queries)
                    if (aiMessage.dataCommands != null && aiMessage.dataCommands.isNotEmpty()) {
                        if (consecutiveDataQueries >= limits.maxDataQueryIterations) {
                            storeLimitReachedMessage("Data query iterations limit reached", sessionId)
                            break
                        }

                        val dataSystemMessage = executeDataCommands(aiMessage.dataCommands)
                        storeSystemMessage(dataSystemMessage, sessionId)

                        val newPromptData = PromptManager.buildPromptData(sessionId, context)
                        aiResponse = aiClient.query(newPromptData)
                        storeAIMessage(aiResponse, sessionId)

                        consecutiveDataQueries++
                        consecutiveActionRetries = 0  // Reset
                        totalRoundtrips++
                        continue
                    }

                    // 7c. ACTION COMMANDS (mutations)
                    if (aiMessage.actionCommands != null && aiMessage.actionCommands.isNotEmpty()) {

                        // Validation request ?
                        if (aiMessage.validationRequest != null) {
                            // STOP - Attendre validation user
                            val validated = waitForUserValidation(aiMessage.validationRequest)

                            if (!validated) {
                                // Refuser actions
                                val refusedSystemMessage = createRefusedActionsMessage(aiMessage.actionCommands)
                                storeSystemMessage(refusedSystemMessage, sessionId)
                                break  // FIN - attend prochain message user
                            }
                        }

                        // Exécuter actions
                        val actionSystemMessage = executeActionCommands(aiMessage.actionCommands)
                        storeSystemMessage(actionSystemMessage, sessionId)

                        // Check if all actions succeeded
                        val allSuccess = actionSystemMessage.commandResults.all { it.status == CommandStatus.SUCCESS }

                        if (allSuccess) {
                            // Succès total - FIN boucle
                            break
                        } else {
                            // Échecs - retry
                            if (consecutiveActionRetries >= limits.maxActionRetries) {
                                storeLimitReachedMessage("Action retries limit reached", sessionId)
                                break
                            }

                            val newPromptData = PromptManager.buildPromptData(sessionId, context)
                            aiResponse = aiClient.query(newPromptData)
                            storeAIMessage(aiResponse, sessionId)

                            consecutiveActionRetries++
                            consecutiveDataQueries = 0  // Reset
                            totalRoundtrips++
                            continue
                        }
                    }

                    // Rien à faire
                    break
                }

                if (totalRoundtrips >= limits.maxAutonomousRoundtrips) {
                    storeLimitReachedMessage("Total autonomous roundtrips limit reached", sessionId)
                }

                LogManager.aiSession("Message sent and processed successfully", "INFO")
                OperationResult.success()

            } catch (e: Exception) {
                LogManager.aiSession("AIOrchestrator.sendMessage - Error: ${e.message}", "ERROR", e)
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
     * Load session by ID
     * TODO: Implement full session parsing when needed by UI
     */
    suspend fun loadSession(sessionId: String): AISession? {
        LogManager.aiSession("AIOrchestrator.loadSession() called for $sessionId", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))

        return if (result.isSuccess) {
            val sessionData = result.data?.get("session") as? Map<*, *>
            val messagesData = result.data?.get("messages") as? List<*>

            if (sessionData != null) {
                // Stub implementation - return minimal AISession
                AISession(
                    id = sessionData["id"] as? String ?: sessionId,
                    name = sessionData["name"] as? String ?: "",
                    type = try {
                        SessionType.valueOf(sessionData["type"] as? String ?: "CHAT")
                    } catch (e: Exception) {
                        SessionType.CHAT
                    },
                    providerId = sessionData["providerId"] as? String ?: "claude",
                    providerSessionId = sessionData["providerSessionId"] as? String ?: "",
                    schedule = null,
                    createdAt = (sessionData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    lastActivity = (sessionData["lastActivity"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    messages = emptyList(), // TODO: Parse messages when needed
                    isActive = sessionData["isActive"] as? Boolean ?: false
                )
            } else {
                null
            }
        } else {
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
                val sessionId = result.data?.get("sessionId") as? String
                if (sessionId != null) {
                    loadSession(sessionId)
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Stop current active session
     */
    suspend fun stopActiveSession(): OperationResult {
        LogManager.aiSession("AIOrchestrator.stopActiveSession() called", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
        return if (result.isSuccess) {
            LogManager.aiSession("Active session stopped successfully", "INFO")
            OperationResult.success()
        } else {
            OperationResult.error(result.error ?: "Failed to stop session")
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
            OperationResult.error(result.error ?: "Failed to set active session")
        }
    }

    // ========================================================================================
    // Private Helpers - Autonomous Loop Support
    // ========================================================================================

    /**
     * Get session limits based on session type
     */
    private fun getLimitsForSessionType(type: SessionType): SessionLimits {
        val aiLimits = AppConfigManager.getAILimits()

        return when (type) {
            SessionType.CHAT -> SessionLimits(
                maxDataQueryIterations = aiLimits.chatMaxDataQueryIterations,
                maxActionRetries = aiLimits.chatMaxActionRetries,
                maxAutonomousRoundtrips = aiLimits.chatMaxAutonomousRoundtrips,
                maxCommunicationModules = aiLimits.chatMaxCommunicationModulesRoundtrips
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxDataQueryIterations = aiLimits.automationMaxDataQueryIterations,
                maxActionRetries = aiLimits.automationMaxActionRetries,
                maxAutonomousRoundtrips = aiLimits.automationMaxAutonomousRoundtrips,
                maxCommunicationModules = aiLimits.automationMaxCommunicationModulesRoundtrips
            )
        }
    }

    /**
     * Execute user enrichments and return SystemMessage
     * Uses same pipeline as old Level 4: EnrichmentProcessor → UserCommandProcessor → CommandExecutor
     */
    private suspend fun executeEnrichments(
        commands: List<DataCommand>
    ): SystemMessage {
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
    private suspend fun executeDataCommands(commands: List<DataCommand>): SystemMessage {
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
    private suspend fun executeActionCommands(commands: List<DataCommand>): SystemMessage {
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

    /**
     * Create SystemMessage for refused actions
     */
    private fun createRefusedActionsMessage(actions: List<DataCommand>): SystemMessage {
        return SystemMessage(
            type = SystemMessageType.ACTIONS_EXECUTED,
            commandResults = actions.map { action ->
                CommandResult(
                    command = action.type,
                    status = CommandStatus.CANCELLED,
                    details = "User refused this action"
                )
            },
            summary = "User refused ${actions.size} proposed action(s)",
            formattedData = null
        )
    }

    /**
     * Store limit reached message
     */
    private suspend fun storeLimitReachedMessage(reason: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.LIMIT_REACHED,
            commandResults = emptyList(),
            summary = reason,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

    /**
     * Wait for user validation (suspend until UI calls resumeWithValidation)
     */
    private suspend fun waitForUserValidation(request: ValidationRequest): Boolean =
        suspendCancellableCoroutine { cont ->
            _waitingState.value = WaitingState.WaitingValidation(request)
            validationContinuation = cont
        }

    /**
     * Wait for user response to communication module (suspend until UI calls resumeWithResponse)
     */
    private suspend fun waitForUserResponse(module: CommunicationModule): String =
        suspendCancellableCoroutine { cont ->
            _waitingState.value = WaitingState.WaitingResponse(module)
            responseContinuation = cont
        }

    /**
     * Store SystemMessage in session
     */
    private suspend fun storeSystemMessage(systemMessage: SystemMessage, sessionId: String) {
        try {
            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.SYSTEM.name,
                "systemMessage" to systemMessage,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store SystemMessage: ${storeResult.error}", "WARN")
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing SystemMessage: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Store AI message response
     */
    private suspend fun storeAIMessage(aiResponse: AIResponse, sessionId: String) {
        try {
            val aiMessageJson = aiResponse.content

            val storeResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                "sessionId" to sessionId,
                "sender" to MessageSender.AI.name,
                "aiMessageJson" to aiMessageJson,
                "timestamp" to System.currentTimeMillis()
            ))

            if (!storeResult.isSuccess) {
                LogManager.aiSession("Failed to store AI message: ${storeResult.error}", "WARN")
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing AI message: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Parse AIMessage from AIResponse
     * TODO: Implement full JSON parsing when needed
     */
    private fun parseAIMessageFromResponse(aiResponse: AIResponse): AIMessage? {
        if (!aiResponse.success) return null

        return try {
            // TODO: Parse aiResponse.content as JSON and extract AIMessage structure
            // For now, return stub
            AIMessage(
                preText = "",
                validationRequest = null,
                dataCommands = null,
                actionCommands = null,
                postText = null,
                communicationModule = null
            )
        } catch (e: Exception) {
            LogManager.aiSession("Failed to parse AIMessage from response: ${e.message}", "WARN", e)
            null
        }
    }
}

/**
 * Session limits configuration
 */
data class SessionLimits(
    val maxDataQueryIterations: Int,
    val maxActionRetries: Int,
    val maxAutonomousRoundtrips: Int,
    val maxCommunicationModules: Int
)
