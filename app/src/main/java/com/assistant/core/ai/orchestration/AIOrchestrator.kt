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
import kotlinx.coroutines.*
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

    // ========================================================================================
    // Session Control State
    // ========================================================================================

    // Active session state (one session active at a time)
    private var activeSessionId: String? = null
    private var activeSessionType: SessionType? = null
    private var lastActivityTimestamp: Long = 0

    // Round execution state
    private val _isRoundInProgress = MutableStateFlow(false)
    val isRoundInProgress: StateFlow<Boolean> = _isRoundInProgress.asStateFlow()

    // Session queue (FIFO with priority rules)
    private val sessionQueue = mutableListOf<QueuedSession>()

    /**
     * Queued session data
     */
    data class QueuedSession(
        val sessionId: String,
        val type: SessionType,
        val automationId: String?,
        val scheduledExecutionTime: Long?,
        val enqueuedAt: Long
    )

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
    // Session Control API
    // ========================================================================================

    /**
     * Result of session control request
     */
    sealed class SessionControlResult {
        object ACTIVATED : SessionControlResult()
        object ALREADY_ACTIVE : SessionControlResult()
        data class QUEUED(val position: Int) : SessionControlResult()
    }

    /**
     * Request control of a session
     * Returns ACTIVATED if session activated immediately, ALREADY_ACTIVE if already active, or QUEUED with position
     */
    @Synchronized
    fun requestSessionControl(
        sessionId: String,
        type: SessionType,
        automationId: String? = null,
        scheduledExecutionTime: Long? = null
    ): SessionControlResult {
        LogManager.aiSession("Requesting session control: sessionId=$sessionId, type=$type", "DEBUG")

        // Already active?
        if (activeSessionId == sessionId) {
            LogManager.aiSession("Session already active: $sessionId", "DEBUG")
            return SessionControlResult.ALREADY_ACTIVE
        }

        // No active session → activate immediately
        if (activeSessionId == null) {
            activateSession(sessionId, type, automationId, scheduledExecutionTime)
            LogManager.aiSession("Session activated immediately: $sessionId", "INFO")
            return SessionControlResult.ACTIVATED
        }

        // CHAT logic: one CHAT at a time
        if (type == SessionType.CHAT) {
            // Remove any other CHAT from queue
            sessionQueue.removeAll { it.type == SessionType.CHAT }

            // If other CHAT is active, close it and activate new one
            if (activeSessionType == SessionType.CHAT) {
                LogManager.aiSession("Closing active CHAT and switching to new CHAT: $sessionId", "INFO")
                closeActiveSession()
                activateSession(sessionId, type, automationId, scheduledExecutionTime)
                return SessionControlResult.ACTIVATED
            }

            // AUTOMATION active → queue CHAT with priority (position 1)
            enqueueSession(sessionId, type, automationId, scheduledExecutionTime, priority = true)
            LogManager.aiSession("CHAT queued with priority: $sessionId", "INFO")
            return SessionControlResult.QUEUED(1)
        }

        // AUTOMATION logic: Check if active CHAT can be evicted
        if (type == SessionType.AUTOMATION && activeSessionType == SessionType.CHAT) {
            val limits = AppConfigManager.getAILimits()
            val now = System.currentTimeMillis()
            val inactivityDuration = now - lastActivityTimestamp

            if (inactivityDuration > limits.chatMaxInactivityBeforeAutomationEviction) {
                LogManager.aiSession(
                    "Active CHAT inactive for ${inactivityDuration / 1000}s (limit: ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - evicting for AUTOMATION: $sessionId",
                    "INFO"
                )
                closeActiveSession()
                activateSession(sessionId, type, automationId, scheduledExecutionTime)
                return SessionControlResult.ACTIVATED
            } else {
                LogManager.aiSession(
                    "Active CHAT still active (inactive for ${inactivityDuration / 1000}s < ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - queuing AUTOMATION: $sessionId",
                    "INFO"
                )
            }
        }

        // AUTOMATION queued normally (FIFO)
        enqueueSession(sessionId, type, automationId, scheduledExecutionTime, priority = false)
        val position = sessionQueue.indexOfFirst { it.sessionId == sessionId } + 1
        LogManager.aiSession("AUTOMATION queued at position $position: $sessionId", "INFO")
        return SessionControlResult.QUEUED(position)
    }

    /**
     * Close active session manually
     */
    @Synchronized
    fun closeActiveSession() {
        if (activeSessionId == null) {
            LogManager.aiSession("No active session to close", "DEBUG")
            return
        }

        LogManager.aiSession("Closing active session: $activeSessionId", "INFO")
        activeSessionId = null
        activeSessionType = null
        lastActivityTimestamp = 0

        // Process queue
        processNextInQueue()
    }

    /**
     * Get active session ID
     */
    fun getActiveSessionId(): String? = activeSessionId

    /**
     * Activate a session
     */
    private fun activateSession(
        sessionId: String,
        type: SessionType,
        automationId: String?,
        scheduledExecutionTime: Long?
    ) {
        activeSessionId = sessionId
        activeSessionType = type
        lastActivityTimestamp = System.currentTimeMillis()
        LogManager.aiSession("Session activated: $sessionId (type=$type, automationId=$automationId)", "DEBUG")
    }

    /**
     * Enqueue a session with optional priority
     */
    private fun enqueueSession(
        sessionId: String,
        type: SessionType,
        automationId: String?,
        scheduledExecutionTime: Long?,
        priority: Boolean
    ) {
        val queued = QueuedSession(
            sessionId = sessionId,
            type = type,
            automationId = automationId,
            scheduledExecutionTime = scheduledExecutionTime,
            enqueuedAt = System.currentTimeMillis()
        )

        // CHAT: remove any other CHAT (already done in requestSessionControl, but defensive)
        if (type == SessionType.CHAT) {
            sessionQueue.removeAll { it.type == SessionType.CHAT }
        }

        if (priority) {
            sessionQueue.add(0, queued) // Position 1 (CHAT prioritaire)
        } else {
            sessionQueue.add(queued) // FIFO normal
        }

        LogManager.aiSession("Session enqueued: $sessionId at position ${sessionQueue.size} (priority=$priority)", "DEBUG")
    }

    /**
     * Process next session in queue
     */
    private fun processNextInQueue() {
        if (sessionQueue.isEmpty()) {
            LogManager.aiSession("Queue empty, no session to process", "DEBUG")
            return
        }

        val next = sessionQueue.removeAt(0)
        activateSession(next.sessionId, next.type, next.automationId, next.scheduledExecutionTime)
        LogManager.aiSession("Processing next in queue: ${next.sessionId} (type=${next.type})", "INFO")

        // TODO: Trigger session execution (for AUTOMATION, will be done when scheduler is implemented)
        // For CHAT, UI will detect active session change and open chat interface
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
     * Process user message: execute enrichments and store message
     * Does NOT execute AI round (use executeAIRound for that)
     *
     * Flow:
     * 1. Execute enrichments BEFORE storing message
     * 2. Store user message
     * 3. Store SystemMessage enrichments (if present)
     */
    suspend fun processUserMessage(richMessage: RichMessage): OperationResult {
        val sessionId = activeSessionId ?: return OperationResult.error("No active session")

        LogManager.aiSession("AIOrchestrator.processUserMessage() for session $sessionId", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // Update activity timestamp
                lastActivityTimestamp = System.currentTimeMillis()

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

                LogManager.aiSession("User message processed successfully", "INFO")
                OperationResult.success()

            } catch (e: Exception) {
                LogManager.aiSession("processUserMessage - Error: ${e.message}", "ERROR", e)
                OperationResult.error("Failed to process user message: ${e.message}")
            }
        }
    }

    /**
     * Execute AI round with autonomous loops
     * Uses active session, requires processUserMessage() or similar to have been called first
     *
     * Flow:
     * 1. Build prompt data (L1-L3 + messages)
     * 2. Call AI with PromptData (with network checks, retry for AUTOMATION)
     * 3. Store AI response
     * 4. AUTONOMOUS LOOPS (data queries, actions with retries, communication modules)
     */
    suspend fun executeAIRound(reason: RoundReason): OperationResult {
        val sessionId = activeSessionId ?: return OperationResult.error("No active session")

        // Prevent concurrent rounds
        if (_isRoundInProgress.value) {
            LogManager.aiSession("AI round already in progress, rejecting concurrent call", "WARN")
            return OperationResult.error("AI round already in progress")
        }

        LogManager.aiSession("AIOrchestrator.executeAIRound($reason) for session $sessionId", "DEBUG")

        _isRoundInProgress.value = true

        return withContext(Dispatchers.IO) {
            try {
                // Update activity timestamp
                lastActivityTimestamp = System.currentTimeMillis()

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

                // Watchdog for AUTOMATION (timeout occupation)
                var shouldTerminateRound = false
                val watchdogJob = if (sessionType == SessionType.AUTOMATION) {
                    CoroutineScope(Dispatchers.Default).launch {
                        val aiLimits = AppConfigManager.getAILimits()
                        delay(aiLimits.automationMaxSessionDuration)
                        shouldTerminateRound = true
                        LogManager.aiSession("AUTOMATION timeout reached, forcing termination", "WARN")
                    }
                } else null

                try {
                    // Execute round with watchdog
                    val result = executeRoundWithAutonomousLoops(sessionId, sessionType, limits, reason) { shouldTerminateRound }
                    result
                } finally {
                    watchdogJob?.cancel()
                }

            } catch (e: Exception) {
                LogManager.aiSession("executeAIRound - Error: ${e.message}", "ERROR", e)
                OperationResult.error("AI round error: ${e.message}")
            } finally {
                _isRoundInProgress.value = false
                processNextInQueue()
            }
        }
    }

    /**
     * Send user message to AI with autonomous loops (wrapper for compatibility)
     *
     * Flow:
     * 1. Process user message (enrichments + store)
     * 2. Execute AI round
     */
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult {
        // Activate session if not already active
        if (activeSessionId != sessionId) {
            val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))
            if (!sessionResult.isSuccess) {
                return OperationResult.error("Failed to load session: ${sessionResult.error}")
            }

            val sessionData = sessionResult.data?.get("session") as? Map<*, *>
                ?: return OperationResult.error("No session data found")

            val sessionTypeStr = sessionData["type"] as? String ?: "CHAT"
            val sessionType = SessionType.valueOf(sessionTypeStr)

            requestSessionControl(sessionId, sessionType)
        }

        // Process message
        val processResult = processUserMessage(richMessage)
        if (!processResult.success) return processResult

        // Execute AI round
        return executeAIRound(RoundReason.USER_MESSAGE)
    }

    /**
     * Execute AI round with autonomous loops (internal implementation)
     */
    private suspend fun executeRoundWithAutonomousLoops(
        sessionId: String,
        sessionType: SessionType,
        limits: SessionLimits,
        reason: RoundReason,
        shouldTerminate: () -> Boolean
    ): OperationResult {
        // 1. Build prompt data
        val promptData = PromptManager.buildPromptData(sessionId, context)

        // 2. Call AI with PromptData (with network check and retry)
        var aiResponse = callAIWithRetry(promptData, sessionType)

        if (!aiResponse.success) {
            // Network error or timeout - store NETWORK_ERROR and potentially requeue
            storeNetworkErrorMessage(aiResponse.errorMessage ?: s.shared("ai_error_network_call_failed"), sessionId)

            if (sessionType == SessionType.AUTOMATION) {
                // Requeue AUTOMATION at priority position
                enqueueSession(sessionId, sessionType, null, null, priority = true)
                closeActiveSession()
            }

            return OperationResult.error(aiResponse.errorMessage ?: s.shared("ai_error_network_call_failed"))
        }

        // 3. Store AI response
        storeAIMessage(aiResponse, sessionId)

        // 4. AUTONOMOUS LOOPS
        var totalRoundtrips = 0
        var consecutiveDataQueries = 0
        var consecutiveActionRetries = 0
        var consecutiveFormatErrors = 0
        var communicationRoundtrips = 0

        while (totalRoundtrips < limits.maxAutonomousRoundtrips) {

            // Check watchdog termination flag
            if (shouldTerminate()) {
                val aiLimits = AppConfigManager.getAILimits()
                val timeoutMinutes = aiLimits.automationMaxSessionDuration / 60000
                storeSessionTimeoutMessage(s.shared("ai_error_session_timeout_automation").format(timeoutMinutes), sessionId)
                break
            }

            // Parse AI message for commands
            val parseResult = parseAIMessageFromResponse(aiResponse)

            // Check for format errors
            if (parseResult.formatErrors.isNotEmpty()) {
                // Check limit
                if (consecutiveFormatErrors >= limits.maxFormatErrorRetries) {
                    storeLimitReachedMessage("Format errors limit reached", sessionId)
                    break
                }

                val errorList = parseResult.formatErrors.joinToString("\n") { "- $it" }
                val errorSummary = s.shared("ai_error_format_errors").format(errorList)
                LogManager.aiSession("Format errors detected: $errorSummary", "WARN")
                storeFormatErrorMessage(errorSummary, sessionId)

                // Continue loop to send error back to AI for correction
                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                if (!aiResponse.success) break
                storeAIMessage(aiResponse, sessionId)

                consecutiveFormatErrors++
                totalRoundtrips++
                continue
            }

            // Reset format errors counter when message is correctly parsed
            consecutiveFormatErrors = 0

            val aiMessage = parseResult.aiMessage
            if (aiMessage == null) {
                // No AI message structure - end loop
                break
            }

            // 4a. COMMUNICATION MODULE (prioritaire)
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
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                if (!aiResponse.success) break
                storeAIMessage(aiResponse, sessionId)

                communicationRoundtrips++
                totalRoundtrips++
                continue
            }

            // 4b. DATA COMMANDS (queries)
            if (aiMessage.dataCommands != null && aiMessage.dataCommands.isNotEmpty()) {
                if (consecutiveDataQueries >= limits.maxDataQueryIterations) {
                    storeLimitReachedMessage("Data query iterations limit reached", sessionId)
                    break
                }

                val dataSystemMessage = executeDataCommands(aiMessage.dataCommands)
                storeSystemMessage(dataSystemMessage, sessionId)

                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                if (!aiResponse.success) break
                storeAIMessage(aiResponse, sessionId)

                consecutiveDataQueries++
                consecutiveActionRetries = 0  // Reset
                totalRoundtrips++
                continue
            }

            // 4c. ACTION COMMANDS (mutations)
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
                    aiResponse = callAIWithRetry(newPromptData, sessionType)
                    if (!aiResponse.success) break
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

        LogManager.aiSession("AI round completed successfully", "INFO")
        return OperationResult.success()
    }

    /**
     * Call AI with retry logic for AUTOMATION, network checks for both
     */
    private suspend fun callAIWithRetry(promptData: PromptData, sessionType: SessionType): AIResponse {
        // Check network before call
        if (!com.assistant.core.utils.NetworkUtils.isNetworkAvailable(context)) {
            val errorMsg = s.shared("ai_error_network_unavailable")
            LogManager.aiSession(errorMsg, "WARN")
            return AIResponse(success = false, content = "", errorMessage = errorMsg)
        }

        if (sessionType == SessionType.AUTOMATION) {
            // Retry logic for AUTOMATION (3x with backoff)
            val delays = listOf(5_000L, 15_000L, 30_000L)
            var lastError: String? = null

            for (attempt in 0..2) {
                val response = aiClient.query(promptData)
                if (response.success) {
                    return response
                }

                lastError = response.errorMessage
                LogManager.aiSession("AI call attempt ${attempt + 1} failed: $lastError", "WARN")

                if (attempt < 2) {
                    delay(delays[attempt])
                }
            }

            LogManager.aiSession("All 3 AI call attempts failed", "ERROR")
            val errorMsg = s.shared("ai_error_network_timeout_retries").format(3, lastError ?: "")
            return AIResponse(success = false, content = "", errorMessage = errorMsg)

        } else {
            // CHAT: no retry, single attempt
            return aiClient.query(promptData)
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
     * Load session by ID with full message parsing
     */
    suspend fun loadSession(sessionId: String): AISession? {
        LogManager.aiSession("AIOrchestrator.loadSession() called for $sessionId", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))

        return if (result.isSuccess) {
            val sessionData = result.data?.get("session") as? Map<*, *>
            val messagesData = result.data?.get("messages") as? List<*>

            if (sessionData != null) {
                // Parse messages
                val messages = parseMessages(messagesData)

                LogManager.aiSession("Loaded session $sessionId with ${messages.size} messages", "DEBUG")

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
                    schedule = null, // TODO: Parse schedule when needed
                    createdAt = (sessionData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    lastActivity = (sessionData["lastActivity"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    messages = messages,
                    isActive = sessionData["isActive"] as? Boolean ?: false
                )
            } else {
                null
            }
        } else {
            LogManager.aiSession("Failed to load session $sessionId: ${result.error}", "ERROR")
            null
        }
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

                SessionMessage(
                    id = id,
                    timestamp = timestamp,
                    sender = sender,
                    richContent = richContent,
                    textContent = textContent,
                    aiMessage = aiMessage,
                    aiMessageJson = aiMessageJsonStr?.takeIf { it.isNotEmpty() }, // Keep original JSON
                    systemMessage = systemMessage,
                    executionMetadata = null // TODO: Parse when automation implemented
                )

            } catch (e: Exception) {
                LogManager.aiSession("Failed to parse message: ${e.message}", "WARN", e)
                null // Skip this message
            }
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
                maxFormatErrorRetries = aiLimits.chatMaxFormatErrorRetries,
                maxAutonomousRoundtrips = aiLimits.chatMaxAutonomousRoundtrips,
                maxCommunicationModules = aiLimits.chatMaxCommunicationModulesRoundtrips
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxDataQueryIterations = aiLimits.automationMaxDataQueryIterations,
                maxActionRetries = aiLimits.automationMaxActionRetries,
                maxFormatErrorRetries = aiLimits.automationMaxFormatErrorRetries,
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
    /**
     * Store NETWORK_ERROR message for network/HTTP/provider errors
     */
    private suspend fun storeNetworkErrorMessage(summary: String, sessionId: String) {
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
    private suspend fun storeSessionTimeoutMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.SESSION_TIMEOUT,
            commandResults = emptyList(),
            summary = summary,
            formattedData = null
        )
        storeSystemMessage(systemMessage, sessionId)
    }

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
     * Store FORMAT_ERROR message for AI response format errors
     */
    private suspend fun storeFormatErrorMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.FORMAT_ERROR,
            commandResults = emptyList(),
            summary = summary,
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
     * Parse AI response content as AIMessage structure
     *
     * All providers must return JSON content matching AIMessage structure:
     * {
     *   "preText": "...",
     *   "validationRequest": { "message": "...", "status": "..." },
     *   "dataCommands": [...],
     *   "actionCommands": [...],
     *   "postText": "...",
     *   "communicationModule": { "type": "...", "data": {...} }
     * }
     *
     * Provider responsibility: Transform their API response to this format
     *
     * FALLBACK: If JSON parsing fails (AI didn't respect format), creates basic AIMessage
     * with error prefix and raw text as preText
     *
     * Returns null only if response is not successful
     */
    private fun parseAIMessageFromResponse(aiResponse: AIResponse): ParseResult {
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

            // Parse optional validationRequest
            val validationRequest = json.optJSONObject("validationRequest")?.let { vr ->
                val message = vr.getString("message")
                val statusStr = vr.optString("status")
                val status = if (statusStr.isNotEmpty()) {
                    try { ValidationStatus.valueOf(statusStr) } catch (e: Exception) { null }
                } else null
                ValidationRequest(message, status)
            }

            // Parse optional dataCommands
            val dataCommands = json.optJSONArray("dataCommands")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    try {
                        val cmd = array.getJSONObject(i)
                        DataCommand(
                            id = cmd.getString("id"),
                            type = cmd.getString("type"),
                            params = parseJsonObjectToMap(cmd.getJSONObject("params")),
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
                        DataCommand(
                            id = cmd.getString("id"),
                            type = cmd.getString("type"),
                            params = parseJsonObjectToMap(cmd.getJSONObject("params")),
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

            // Parse optional communicationModule
            val communicationModule = json.optJSONObject("communicationModule")?.let { module ->
                try {
                    val type = module.getString("type")
                    val data = parseJsonObjectToMap(module.getJSONObject("data"))

                    when (type) {
                        "MultipleChoice" -> CommunicationModule.MultipleChoice(type, data)
                        "Validation" -> CommunicationModule.Validation(type, data)
                        else -> {
                            val errorMsg = "Unknown communication module type: $type"
                            LogManager.aiSession(errorMsg, "WARN")
                            formatErrors.add("communicationModule: $errorMsg")
                            null
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = "Failed to parse communicationModule: ${e.message}"
                    LogManager.aiSession(errorMsg, "WARN")
                    formatErrors.add("communicationModule: ${e.message}")
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
                communicationModule = communicationModule
            )

            // Log parsed structure (DEBUG level)
            LogManager.aiSession(
                "AI PARSED MESSAGE:\n" +
                "  preText: ${preText.take(100)}${if (preText.length > 100) "..." else ""}\n" +
                "  validationRequest: ${validationRequest?.message?.take(50) ?: "null"}\n" +
                "  dataCommands: ${dataCommands?.size ?: 0} commands\n" +
                "  actionCommands: ${actionCommands?.size ?: 0} commands\n" +
                "  postText: ${postText?.take(50) ?: "null"}\n" +
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

/**
 * Session limits configuration
 */
data class SessionLimits(
    val maxDataQueryIterations: Int,
    val maxActionRetries: Int,
    val maxFormatErrorRetries: Int,
    val maxAutonomousRoundtrips: Int,
    val maxCommunicationModules: Int
)
