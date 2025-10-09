package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.ai.processing.AICommandProcessor
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.ai.validation.ValidationResolver
import com.assistant.core.ai.validation.ValidationResult
import com.assistant.core.ai.validation.ValidationContext
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
 *
 * IMPORTANT: Uses its own CoroutineScope (singleton-scoped) for async operations.
 * This ensures that coroutines are NOT tied to UI lifecycle (ChatScreen navigation).
 * Continuations remain valid as long as the app is open, fixing navigation bugs.
 */
object AIOrchestrator {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var aiClient: AIClient
    private lateinit var s: com.assistant.core.strings.StringsContext

    // Own coroutine scope for singleton lifecycle (not tied to UI)
    // SupervisorJob ensures one child failure doesn't cancel others
    // Dispatchers.Main for UI state updates
    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State for user interaction (observable by UI)
    private val _waitingState = MutableStateFlow<WaitingState>(WaitingState.None)
    val waitingState: StateFlow<WaitingState> = _waitingState.asStateFlow()

    // Reactive messages flow for active session (observable by UI)
    private val _activeSessionMessages = MutableStateFlow<List<SessionMessage>>(emptyList())
    val activeSessionMessages: StateFlow<List<SessionMessage>> = _activeSessionMessages.asStateFlow()

    // Continuations for user interaction suspension
    // These remain valid across ChatScreen navigation since they're in singleton scope
    private var validationContinuation: Continuation<Boolean>? = null
    private var responseContinuation: Continuation<String?>? = null

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

    // Round interruption flag (set by user via UI)
    @Volatile
    private var shouldInterruptRound = false

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
     * Restores active session from DB if present
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        this.coordinator = Coordinator(this.context)
        this.aiClient = AIClient(this.context)
        this.s = Strings.`for`(context = this.context)

        LogManager.aiSession("AIOrchestrator initialized as singleton")

        // Restore active session from DB (async)
        orchestratorScope.launch {
            try {
                val result = coordinator.processUserAction("ai_sessions.get_active_session", emptyMap())
                if (result.isSuccess) {
                    val hasActive = result.data?.get("hasActiveSession") as? Boolean ?: false
                    if (hasActive) {
                        val sessionId = result.data?.get("sessionId") as? String
                        val sessionData = result.data?.get("session") as? Map<*, *>
                        val typeStr = sessionData?.get("type") as? String
                        val type = typeStr?.let {
                            try {
                                SessionType.valueOf(it)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        when (type) {
                            SessionType.CHAT -> {
                                // Restore CHAT session in memory (without DB sync to avoid loop)
                                if (sessionId != null) {
                                    activeSessionId = sessionId
                                    activeSessionType = type
                                    lastActivityTimestamp = System.currentTimeMillis()
                                    LogManager.aiSession("Restored active CHAT session: $sessionId", "INFO")

                                    // Initialize messages flow for restored session
                                    updateMessagesFlow(sessionId)
                                }
                            }
                            SessionType.AUTOMATION -> {
                                // Deactivate AUTOMATION (it's finished/interrupted after app restart)
                                coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                                LogManager.aiSession("Deactivated finished AUTOMATION session: $sessionId", "INFO")
                            }
                            else -> {
                                // Unknown or null type, deactivate
                                coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                                LogManager.aiSession("Deactivated session with unknown type: $sessionId", "WARN")
                            }
                        }
                    } else {
                        LogManager.aiSession("No active session to restore", "DEBUG")
                    }
                } else {
                    LogManager.aiSession("Failed to load active session: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                LogManager.aiSession("Exception restoring active session: ${e.message}", "ERROR", e)
            }
        }
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
     * Updates both memory state (immediate) and DB state (async)
     */
    @Synchronized
    fun closeActiveSession() {
        if (activeSessionId == null) {
            LogManager.aiSession("No active session to close", "DEBUG")
            return
        }

        val sessionToClose = activeSessionId
        LogManager.aiSession("Closing active session: $sessionToClose", "INFO")

        // 1. Update memory state (immediate)
        activeSessionId = null
        activeSessionType = null
        lastActivityTimestamp = 0

        // 2. Clear messages flow
        _activeSessionMessages.value = emptyList()

        // 3. Sync DB state (async, non-blocking)
        orchestratorScope.launch {
            try {
                val result = coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                if (result.isSuccess) {
                    LogManager.aiSession("Session deactivated in DB: $sessionToClose", "DEBUG")
                } else {
                    LogManager.aiSession("Failed to deactivate session in DB: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                LogManager.aiSession("Exception deactivating session in DB: ${e.message}", "ERROR", e)
            }
        }

        // Process queue
        processNextInQueue()
    }

    /**
     * Get active session ID
     */
    fun getActiveSessionId(): String? = activeSessionId

    /**
     * Activate a session
     * Updates both memory state (immediate) and DB state (async)
     */
    private fun activateSession(
        sessionId: String,
        type: SessionType,
        automationId: String?,
        scheduledExecutionTime: Long?
    ) {
        // 1. Update memory state (immediate)
        activeSessionId = sessionId
        activeSessionType = type
        lastActivityTimestamp = System.currentTimeMillis()
        LogManager.aiSession("Session activated in memory: $sessionId (type=$type, automationId=$automationId)", "DEBUG")

        // 2. Initialize messages flow for new active session
        updateMessagesFlow(sessionId)

        // 3. Sync DB state (async, non-blocking)
        orchestratorScope.launch {
            try {
                val result = coordinator.processUserAction("ai_sessions.set_active_session", mapOf(
                    "sessionId" to sessionId
                ))
                if (result.isSuccess) {
                    LogManager.aiSession("Session activated in DB: $sessionId", "DEBUG")
                } else {
                    LogManager.aiSession("Failed to activate session in DB: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                LogManager.aiSession("Exception activating session in DB: ${e.message}", "ERROR", e)
            }
        }
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
        // Delete VALIDATION_CANCELLED message if user validated
        if (validated) {
            val currentState = _waitingState.value
            if (currentState is WaitingState.WaitingValidation) {
                val cancelMessageId = currentState.cancelMessageId
                if (cancelMessageId.isNotEmpty()) {
                    orchestratorScope.launch {
                        try {
                            val deleteResult = coordinator.processUserAction("ai_sessions.delete_message", mapOf(
                                "messageId" to cancelMessageId
                            ))
                            if (deleteResult.isSuccess) {
                                LogManager.aiSession("Deleted VALIDATION_CANCELLED fallback message: $cancelMessageId", "DEBUG")
                            } else {
                                LogManager.aiSession("Failed to delete VALIDATION_CANCELLED message: ${deleteResult.error}", "WARN")
                            }
                        } catch (e: Exception) {
                            LogManager.aiSession("Exception deleting VALIDATION_CANCELLED message: ${e.message}", "ERROR", e)
                        }
                    }
                }
            }
        }

        validationContinuation?.resume(validated)
        validationContinuation = null
        _waitingState.value = WaitingState.None
    }

    /**
     * Resume execution after user response to communication module
     * Called from UI when user provides response or cancels
     *
     * @param response User response text, or null if cancelled
     */
    fun resumeWithResponse(response: String?) {
        // Delete COMMUNICATION_CANCELLED message if user provided a response
        if (response != null) {
            val currentState = _waitingState.value
            if (currentState is WaitingState.WaitingResponse) {
                val cancelMessageId = currentState.cancelMessageId
                if (cancelMessageId.isNotEmpty()) {
                    orchestratorScope.launch {
                        try {
                            val deleteResult = coordinator.processUserAction("ai_sessions.delete_message", mapOf(
                                "messageId" to cancelMessageId
                            ))
                            if (deleteResult.isSuccess) {
                                LogManager.aiSession("Deleted COMMUNICATION_CANCELLED fallback message: $cancelMessageId", "DEBUG")
                            } else {
                                LogManager.aiSession("Failed to delete COMMUNICATION_CANCELLED message: ${deleteResult.error}", "WARN")
                            }
                        } catch (e: Exception) {
                            LogManager.aiSession("Exception deleting COMMUNICATION_CANCELLED message: ${e.message}", "ERROR", e)
                        }
                    }
                }
            }
        }

        responseContinuation?.resume(response)
        responseContinuation = null
        _waitingState.value = WaitingState.None
    }

    /**
     * Interrupt active AI round
     * Called from UI when user wants to stop autonomous loops
     * The round will stop after current operation completes
     */
    fun interruptActiveRound() {
        if (_isRoundInProgress.value) {
            shouldInterruptRound = true
            LogManager.aiSession("User requested round interruption", "INFO")
        } else {
            LogManager.aiSession("No active round to interrupt", "DEBUG")
        }
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

                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)

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
                    orchestratorScope.launch {
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
        // Reset interruption flag at start of new round
        shouldInterruptRound = false

        // 1. Build prompt data
        val promptData = PromptManager.buildPromptData(sessionId, context)

        // Check session still active before first AI call
        if (!isSessionStillActive(sessionId)) {
            LogManager.aiSession("Session stopped before first AI call", "INFO")
            return OperationResult.error(s.shared("ai_error_session_stopped"))
        }

        // 2. Call AI with PromptData (with network check and retry)
        var aiResponse = callAIWithRetry(promptData, sessionType)

        // Log token usage stats
        logTokenStats(aiResponse)

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

        // Check 1: Interruption before storing initial AI response
        if (shouldInterruptRound) {
            LogManager.aiSession("Round interrupted before storing initial AI response", "INFO")
            storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
            return OperationResult.success()
        }

        // 3. Store AI response and get message ID for validation
        val initialAIMessageId = storeAIMessage(aiResponse, sessionId)

        // 4. AUTONOMOUS LOOPS
        var totalRoundtrips = 0
        var consecutiveDataQueries = 0
        var consecutiveActionRetries = 0
        var consecutiveFormatErrors = 0

        while (totalRoundtrips < limits.maxAutonomousRoundtrips) {

            // Check if session was stopped
            if (!isSessionStillActive(sessionId)) {
                LogManager.aiSession("Session $sessionId is no longer active, exiting autonomous loop", "INFO")
                break
            }

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
                    storeLimitReachedMessage(s.shared("ai_limit_format_errors_reached"), sessionId)
                    break
                }

                val errorList = parseResult.formatErrors.joinToString("\n") { "- $it" }
                val errorSummary = s.shared("ai_error_format_errors").format(errorList)
                LogManager.aiSession("Format errors detected: $errorSummary", "WARN")
                storeFormatErrorMessage(errorSummary, sessionId)

                // Check session still active before continuing
                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before format error retry", "INFO")
                    break
                }

                // Continue loop to send error back to AI for correction
                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                logTokenStats(aiResponse)
                if (!aiResponse.success) break

                // Check interruption BEFORE storing (ignore response completely)
                if (shouldInterruptRound) {
                    LogManager.aiSession("Round interrupted before storing format error correction response", "INFO")
                    storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                // Store AI response (don't need ID here, validation only on actions)
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
                // 1. Store AI message as text (for UI history display, excludeFromPrompt=true)
                val moduleText = aiMessage.communicationModule.toText(context)
                coordinator.processUserAction("ai_sessions.create_message", mapOf(
                    "sessionId" to sessionId,
                    "sender" to MessageSender.AI.name,
                    "textContent" to moduleText,
                    "excludeFromPrompt" to true,
                    "timestamp" to System.currentTimeMillis()
                ))
                updateMessagesFlow(sessionId)

                // 2. Create fallback COMMUNICATION_CANCELLED message (deleted if user responds)
                val cancelMessageId = createAndStoreCommunicationCancelledMessage(sessionId)

                // 3. STOP - Attendre réponse utilisateur (pass cancelMessageId for WaitingState)
                val userResponse = if (cancelMessageId != null) {
                    waitForUserResponse(aiMessage.communicationModule, cancelMessageId)
                } else {
                    // Fallback if cancel message creation failed (shouldn't happen)
                    waitForUserResponse(aiMessage.communicationModule, "")
                }

                // Check if user cancelled
                if (userResponse == null) {
                    // User cancelled - COMMUNICATION_CANCELLED message already created, just break
                    LogManager.aiSession("User cancelled communication module, keeping fallback COMMUNICATION_CANCELLED message", "INFO")
                    break
                }

                // User provided response - format with prefix and store
                val formattedResponse = "${s.shared("ai_module_response_prefix")} $userResponse"

                coordinator.processUserAction("ai_sessions.create_message", mapOf(
                    "sessionId" to sessionId,
                    "sender" to MessageSender.USER.name,
                    "textContent" to formattedResponse,
                    "timestamp" to System.currentTimeMillis()
                ))

                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)

                // Check session still active before continuing
                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before communication module response retry", "INFO")
                    break
                }

                // Renvoyer automatiquement à l'IA
                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                logTokenStats(aiResponse)
                if (!aiResponse.success) break

                // Check interruption BEFORE storing (ignore response completely)
                if (shouldInterruptRound) {
                    LogManager.aiSession("Round interrupted before storing communication module response", "INFO")
                    storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                // Store AI response (don't need ID here, validation only on actions)
                storeAIMessage(aiResponse, sessionId)

                totalRoundtrips++
                continue
            }

            // 4b. DATA COMMANDS (queries)
            if (aiMessage.dataCommands != null && aiMessage.dataCommands.isNotEmpty()) {
                if (consecutiveDataQueries >= limits.maxDataQueryIterations) {
                    storeLimitReachedMessage(s.shared("ai_limit_data_queries_reached"), sessionId)
                    break
                }

                val dataSystemMessage = executeDataCommands(aiMessage.dataCommands)
                storeSystemMessage(dataSystemMessage, sessionId)

                // Check session still active before continuing
                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before data query retry", "INFO")
                    break
                }

                // Check interruption after executing data commands
                if (shouldInterruptRound) {
                    LogManager.aiSession("Round interrupted after data commands execution", "INFO")
                    storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                logTokenStats(aiResponse)
                if (!aiResponse.success) break

                // Check interruption BEFORE storing (ignore response completely)
                if (shouldInterruptRound) {
                    LogManager.aiSession("Round interrupted before storing data query response", "INFO")
                    storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                // Store AI response and get message ID for potential validation
                val currentAIMessageId = storeAIMessage(aiResponse, sessionId)

                consecutiveDataQueries++
                consecutiveActionRetries = 0  // Reset
                totalRoundtrips++
                continue
            }

            // 4c. ACTION COMMANDS (mutations)
            if (aiMessage.actionCommands != null && aiMessage.actionCommands.isNotEmpty()) {

                // VALIDATION RESOLUTION (Phase 6)
                // Determine if validation is required based on hierarchy: app > zone > tool > session > AI request
                val validationResolver = ValidationResolver(context)
                val currentAIMessageId = initialAIMessageId ?: ""  // Use stored message ID

                val validationResult = validationResolver.shouldValidate(
                    actions = aiMessage.actionCommands,
                    sessionId = sessionId,
                    aiMessageId = currentAIMessageId,
                    aiRequestedValidation = aiMessage.validationRequest == true
                )

                when (validationResult) {
                    is ValidationResult.RequiresValidation -> {
                        // 1. Create fallback VALIDATION_CANCELLED message (deleted if user validates)
                        val cancelMessageId = createAndStoreValidationCancelledMessage(sessionId)

                        // 2. STOP - Attendre validation user (pass cancelMessageId for WaitingState)
                        val validated = if (cancelMessageId != null) {
                            waitForUserValidation(validationResult.context, cancelMessageId)
                        } else {
                            // Fallback if cancel message creation failed (shouldn't happen)
                            waitForUserValidation(validationResult.context, "")
                        }

                        if (!validated) {
                            // User refused - VALIDATION_CANCELLED message already created, just break
                            LogManager.aiSession("User refused validation, keeping fallback VALIDATION_CANCELLED message", "INFO")
                            break  // FIN - attend prochain message user
                        }
                        // Si validated → continuer avec l'exécution
                    }
                    ValidationResult.NoValidation -> {
                        // Pas de validation nécessaire, continuer directement
                    }
                }

                // Exécuter actions
                val actionSystemMessage = executeActionCommands(aiMessage.actionCommands)
                storeSystemMessage(actionSystemMessage, sessionId)

                // Check if all actions succeeded
                val allSuccess = actionSystemMessage.commandResults.all { it.status == CommandStatus.SUCCESS }

                if (allSuccess) {
                    // Succès total - Store postText if present (UI only, excluded from prompt)
                    if (aiMessage.postText != null && aiMessage.postText.isNotEmpty()) {
                        storePostTextMessage(aiMessage.postText, sessionId)
                    }
                    // FIN boucle
                    break
                } else {
                    // Échecs - retry
                    if (consecutiveActionRetries >= limits.maxActionRetries) {
                        storeLimitReachedMessage(s.shared("ai_limit_action_retries_reached"), sessionId)
                        break
                    }

                    // Check session still active before continuing
                    if (!isSessionStillActive(sessionId)) {
                        LogManager.aiSession("Session stopped before action retry", "INFO")
                        break
                    }

                    // Check interruption after executing actions
                    if (shouldInterruptRound) {
                        LogManager.aiSession("Round interrupted after action commands execution", "INFO")
                        storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                        break
                    }

                    val newPromptData = PromptManager.buildPromptData(sessionId, context)
                    aiResponse = callAIWithRetry(newPromptData, sessionType)
                    logTokenStats(aiResponse)
                    if (!aiResponse.success) break

                    // Check interruption BEFORE storing (ignore response completely)
                    if (shouldInterruptRound) {
                        LogManager.aiSession("Round interrupted before storing action retry response", "INFO")
                        storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                        break
                    }

                    // Store AI response (don't need ID here, we already have initialAIMessageId)
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
            storeLimitReachedMessage(s.shared("ai_limit_total_roundtrips_reached"), sessionId)
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
     * Update reactive messages flow for active session
     * Called after storing any message to trigger UI update
     */
    private fun updateMessagesFlow(sessionId: String) {
        // Only update if this is still the active session
        if (activeSessionId != sessionId) {
            LogManager.aiSession("Skipping messages flow update for non-active session $sessionId", "DEBUG")
            return
        }

        // Load messages asynchronously and update flow
        orchestratorScope.launch {
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
     * Delegates to closeActiveSession() which handles both memory and DB sync
     */
    fun stopActiveSession(): OperationResult {
        LogManager.aiSession("AIOrchestrator.stopActiveSession() called", "DEBUG")
        closeActiveSession()
        LogManager.aiSession("Active session stopped successfully", "INFO")
        return OperationResult.success()
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

    /**
     * Toggle validation requirement for a session
     * Updates session.requireValidation flag via coordinator
     */
    suspend fun toggleValidation(sessionId: String, requireValidation: Boolean): OperationResult {
        LogManager.aiSession("AIOrchestrator.toggleValidation() called for $sessionId: $requireValidation", "DEBUG")

        val result = coordinator.processUserAction("ai_sessions.toggle_validation", mapOf(
            "sessionId" to sessionId,
            "requireValidation" to requireValidation
        ))
        return if (result.isSuccess) {
            LogManager.aiSession("Session $sessionId validation toggled: $requireValidation", "INFO")
            OperationResult.success()
        } else {
            OperationResult.error(result.error ?: "Failed to toggle validation")
        }
    }

    // ========================================================================================
    // Private Helpers - Autonomous Loop Support
    // ========================================================================================

    /**
     * Check if session is still active (not closed by user or system)
     * Returns false if session was closed (activeSessionId != sessionId)
     */
    private fun isSessionStillActive(sessionId: String): Boolean {
        return activeSessionId == sessionId
    }

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
                maxAutonomousRoundtrips = aiLimits.chatMaxAutonomousRoundtrips
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxDataQueryIterations = aiLimits.automationMaxDataQueryIterations,
                maxActionRetries = aiLimits.automationMaxActionRetries,
                maxFormatErrorRetries = aiLimits.automationMaxFormatErrorRetries,
                maxAutonomousRoundtrips = aiLimits.automationMaxAutonomousRoundtrips
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

    /**
     * Store INTERRUPTED message when user stops autonomous loop
     */
    private suspend fun storeInterruptedMessage(summary: String, sessionId: String) {
        val systemMessage = SystemMessage(
            type = SystemMessageType.INTERRUPTED,
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
     * Create and store COMMUNICATION_CANCELLED message
     * This message is created as a fallback when no response is provided to a communication module
     * It will be deleted if the user actually provides a response
     *
     * @return Message ID of the stored cancellation message, or null if storage failed
     */
    private suspend fun createAndStoreCommunicationCancelledMessage(sessionId: String): String? {
        try {
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
                return null
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)

                // Return message ID for potential deletion
                val messageId = storeResult.data?.get("messageId") as? String
                LogManager.aiSession("Created COMMUNICATION_CANCELLED fallback message: $messageId", "DEBUG")
                return messageId
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing COMMUNICATION_CANCELLED message: ${e.message}", "ERROR", e)
            return null
        }
    }

    /**
     * Create and store VALIDATION_CANCELLED message
     * This message is created as a fallback when no validation is provided for AI actions
     * It will be deleted if the user actually validates the actions
     *
     * @return Message ID of the stored cancellation message, or null if storage failed
     */
    private suspend fun createAndStoreValidationCancelledMessage(sessionId: String): String? {
        try {
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
                return null
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)

                // Return message ID for potential deletion
                val messageId = storeResult.data?.get("messageId") as? String
                LogManager.aiSession("Created VALIDATION_CANCELLED fallback message: $messageId", "DEBUG")
                return messageId
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing VALIDATION_CANCELLED message: ${e.message}", "ERROR", e)
            return null
        }
    }

    /**
     * Wait for user validation (suspend until UI calls resumeWithValidation)
     * Phase 6: Implemented with ValidationContext
     *
     * @param context ValidationContext avec les actions et métadonnées
     * @param cancelMessageId ID of VALIDATION_CANCELLED message to delete if user validates
     * @return true si user valide, false si user refuse
     */
    private suspend fun waitForUserValidation(context: ValidationContext, cancelMessageId: String): Boolean =
        suspendCancellableCoroutine { cont ->
            _waitingState.value = WaitingState.WaitingValidation(context, cancelMessageId)
            validationContinuation = cont
        }

    /**
     * Wait for user response to communication module (suspend until UI calls resumeWithResponse)
     *
     * @param module Communication module to display
     * @param cancelMessageId ID of COMMUNICATION_CANCELLED message to delete if user responds
     * @return User response text, or null if cancelled
     */
    private suspend fun waitForUserResponse(module: CommunicationModule, cancelMessageId: String): String? =
        suspendCancellableCoroutine { cont ->
            _waitingState.value = WaitingState.WaitingResponse(module, cancelMessageId)
            responseContinuation = cont
        }

    /**
     * Store postText as separate AI message (UI only, excluded from prompt)
     * Called after successful actions to display completion message
     */
    private suspend fun storePostTextMessage(postText: String, sessionId: String) {
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
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing SystemMessage: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Store AI message response
     * @return Message ID of the stored message (for validation), or null if storage failed
     */
    private suspend fun storeAIMessage(aiResponse: AIResponse, sessionId: String): String? {
        try {
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
                return null
            } else {
                // Update reactive messages flow for UI
                updateMessagesFlow(sessionId)

                // Return message ID for validation
                val messageId = storeResult.data?.get("messageId") as? String
                return messageId
            }
        } catch (e: Exception) {
            LogManager.aiSession("Error storing AI message: ${e.message}", "ERROR", e)
            return null
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

    /**
     * Log token usage statistics in one line
     * Format: Total tokens, cache write, cache read, uncached, output (with counts and percentages)
     */
    private fun logTokenStats(aiResponse: AIResponse) {
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
    val maxAutonomousRoundtrips: Int
)
