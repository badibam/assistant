package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * AI Orchestrator singleton - coordinates the complete AI flow
 *
 * Primary responsibilities:
 * - Component initialization and coordination
 * - Public API for AI operations (sendMessage, executeAIRound, etc.)
 * - Session CRUD operations
 * - Delegation to specialized components
 *
 * Architecture:
 * - AISessionController: Session management and queue
 * - AIMessageStorage: Message storage and reactive updates
 * - AIUserInteractionManager: User validation and communication modules
 * - AIResponseParser: AI response parsing and validation
 * - AIRoundExecutor: AI round execution with autonomous loops
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

    // Specialized components
    private lateinit var sessionController: AISessionController
    private lateinit var messageStorage: AIMessageStorage
    private lateinit var userInteractionManager: AIUserInteractionManager
    private lateinit var responseParser: AIResponseParser
    private lateinit var roundExecutor: AIRoundExecutor

    // ========================================================================================
    // Public StateFlows (exposed from components)
    // ========================================================================================

    /**
     * Waiting state for user interactions (validation, communication modules)
     */
    val waitingState: StateFlow<WaitingState>
        get() = userInteractionManager.waitingState

    /**
     * Reactive messages flow for active session
     */
    val activeSessionMessages: StateFlow<List<SessionMessage>>
        get() = messageStorage.activeSessionMessages

    /**
     * Round execution state
     */
    val isRoundInProgress: StateFlow<Boolean>
        get() = roundExecutor.isRoundInProgress

    /**
     * Active session ID (reactive)
     * Emits new value when session activation/deactivation occurs
     */
    val activeSessionId: StateFlow<String?>
        get() = sessionController.activeSessionId

    /**
     * Active session type (reactive)
     * Emits new value when session activation/deactivation occurs
     * Synchronized with activeSessionId for immediate UI updates
     */
    val activeSessionType: StateFlow<SessionType?>
        get() = sessionController.activeSessionType

    /**
     * Queued sessions (reactive)
     * Emits list of sessions waiting in queue with position information
     */
    val queuedSessions: StateFlow<List<QueuedSessionInfo>>
        get() = sessionController.queuedSessions

    // ========================================================================================
    // Initialization
    // ========================================================================================

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

        // Initialize specialized components
        sessionController = AISessionController(this.context, coordinator, orchestratorScope)
        responseParser = AIResponseParser(this.context)
        messageStorage = AIMessageStorage(
            context = this.context,
            coordinator = coordinator,
            scope = orchestratorScope,
            getActiveSessionId = { sessionController.getActiveSessionId() }
        )
        userInteractionManager = AIUserInteractionManager(
            context = this.context,
            coordinator = coordinator,
            scope = orchestratorScope,
            messageStorage = messageStorage
        )
        roundExecutor = AIRoundExecutor(
            context = this.context,
            coordinator = coordinator,
            scope = orchestratorScope,
            aiClient = aiClient,
            sessionController = sessionController,
            messageStorage = messageStorage,
            userInteractionManager = userInteractionManager,
            responseParser = responseParser
        )

        // Set roundExecutor in sessionController (for automation execution)
        sessionController.setRoundExecutor(roundExecutor)

        // Configure component callbacks
        sessionController.setOnSessionActivatedCallback { sessionId ->
            messageStorage.updateMessagesFlow(sessionId)
        }
        sessionController.setOnSessionClosedCallback {
            // Interrupt ongoing round
            if (roundExecutor.isRoundInProgress.value) {
                userInteractionManager.interruptActiveRound()
            }
            // Resume pending interactions
            userInteractionManager.resumePendingInteractions()
            // Clear messages flow
            messageStorage.clearMessagesFlow()
        }

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
                                // Deactivate CHAT (never resumed automatically after app restart)
                                coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                                LogManager.aiSession("Deactivated CHAT session on app restart: $sessionId", "INFO")
                            }
                            SessionType.AUTOMATION -> {
                                // Deactivate AUTOMATION (finished/interrupted after app restart)
                                coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                                LogManager.aiSession("Deactivated AUTOMATION session on app restart: $sessionId", "INFO")
                            }
                            else -> {
                                // Unknown or null type, deactivate
                                coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                                LogManager.aiSession("Deactivated session with unknown type on app restart: $sessionId", "WARN")
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
     * Request control of a session
     * Delegates to SessionController
     */
    fun requestSessionControl(
        sessionId: String,
        type: SessionType,
        trigger: ExecutionTrigger? = null,
        automationId: String? = null,
        scheduledExecutionTime: Long? = null
    ): AISessionController.SessionControlResult {
        return sessionController.requestSessionControl(sessionId, type, trigger, automationId, scheduledExecutionTime)
    }

    /**
     * Cancel a queued session
     * Removes from queue and deletes from DB
     * Delegates to SessionController
     */
    fun cancelQueuedSession(sessionId: String) {
        LogManager.aiSession("AIOrchestrator.cancelQueuedSession() called for $sessionId", "DEBUG")
        sessionController.cancelQueuedSession(sessionId)
    }

    /**
     * Get active session ID
     */
    fun getActiveSessionId(): String? = sessionController.getActiveSessionId()

    /**
     * Refresh messages flow for active session
     * Used by UI when reconnecting to an already-active session
     */
    fun refreshActiveSessionMessages() {
        val sessionId = sessionController.getActiveSessionId()
        if (sessionId != null) {
            LogManager.aiSession("AIOrchestrator.refreshActiveSessionMessages() called for $sessionId", "DEBUG")
            messageStorage.updateMessagesFlow(sessionId)
        }
    }

    /**
     * Stop current active session
     */
    fun stopActiveSession(): OperationResult {
        LogManager.aiSession("AIOrchestrator.stopActiveSession() called", "DEBUG")
        sessionController.closeActiveSession()
        LogManager.aiSession("Active session stopped successfully", "INFO")
        return OperationResult.success()
    }

    /**
     * Trigger scheduler tick
     * Called by AutomationService after CRUD operations that affect scheduling
     * and by SchedulerWorker for periodic checks
     */
    suspend fun tick() {
        LogManager.aiSession("AIOrchestrator.tick() called", "DEBUG")
        sessionController.tick()
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
    // User Interaction Resume Functions (called from UI)
    // ========================================================================================

    /**
     * Resume execution after user validation
     * Delegates to UserInteractionManager
     */
    fun resumeWithValidation(validated: Boolean) {
        userInteractionManager.resumeWithValidation(validated)
    }

    /**
     * Resume execution after user response to communication module
     * Delegates to UserInteractionManager
     */
    fun resumeWithResponse(response: String?) {
        userInteractionManager.resumeWithResponse(response, sessionController.getActiveSessionId())
    }

    /**
     * Interrupt active AI round
     * Delegates to UserInteractionManager
     */
    fun interruptActiveRound() {
        userInteractionManager.interruptActiveRound()
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
        val sessionId = sessionController.getActiveSessionId() ?: return OperationResult.error("No active session")

        LogManager.aiSession("AIOrchestrator.processUserMessage() for session $sessionId", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // Update activity timestamp
                sessionController.updateActivityTimestamp()

                // 1. Execute enrichments BEFORE storing message user
                val enrichmentSystemMessage = if (richMessage.dataCommands.isNotEmpty()) {
                    messageStorage.executeEnrichments(richMessage.dataCommands)
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
                messageStorage.updateMessagesFlow(sessionId)

                // 3. Store SystemMessage enrichments (if present)
                if (enrichmentSystemMessage != null) {
                    messageStorage.storeSystemMessage(enrichmentSystemMessage, sessionId)
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
     * Delegates to RoundExecutor
     */
    suspend fun executeAIRound(reason: RoundReason): OperationResult {
        return roundExecutor.executeAIRound(reason)
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
        if (sessionController.getActiveSessionId() != sessionId) {
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
     * Send message asynchronously (non-blocking, fire-and-forget)
     * Launches in orchestratorScope to survive UI lifecycle (Dialog close/reopen)
     *
     * This method solves the navigation bug where closing the chat dialog would cancel
     * ongoing coroutines, making communication modules and validation non-functional after reopen.
     *
     * Flow:
     * 1. Get or create session
     * 2. Activate session if needed
     * 3. Process user message (enrichments + store)
     * 4. Clear composer via callback
     * 5. Execute AI round
     */
    fun sendMessageAsync(
        richMessage: RichMessage,
        onSessionCreated: (String) -> Unit = {},
        onComposerClear: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        orchestratorScope.launch {
            try {
                LogManager.aiUI("sendMessageAsync() called with linearText: '${richMessage.linearText}'")

                // 1. Get or create session ID
                val sessionId = sessionController.getActiveSessionId() ?: run {
                    // Create new session on first message
                    LogManager.aiUI("Creating new session for first message")
                    val newSessionId = createSession(s.shared("ai_session_default_name"), SessionType.CHAT)
                    if (newSessionId.isEmpty()) {
                        onError(s.shared("ai_error_create_session").format(""))
                        return@launch
                    }
                    setActiveSession(newSessionId)
                    withContext(Dispatchers.Main) {
                        onSessionCreated(newSessionId)
                    }
                    newSessionId
                }

                // 2. Activate session if not already active
                if (sessionController.getActiveSessionId() != sessionId) {
                    requestSessionControl(sessionId, SessionType.CHAT)
                    withContext(Dispatchers.Main) {
                        onSessionCreated(sessionId)
                    }
                }

                // 3. Process user message (stores message + executes enrichments)
                val processResult = processUserMessage(richMessage)
                if (!processResult.success) {
                    withContext(Dispatchers.Main) {
                        onError(processResult.error ?: s.shared("ai_error_send_message").format(""))
                    }
                    LogManager.aiUI("Failed to process message: ${processResult.error}", "ERROR")
                    return@launch
                }

                // 4. Clear composer (messages will update reactively via StateFlow)
                withContext(Dispatchers.Main) {
                    onComposerClear()
                }
                LogManager.aiUI("User message sent")

                // 5. Execute AI round (messages will update reactively)
                val aiRoundResult = executeAIRound(RoundReason.USER_MESSAGE)
                LogManager.aiUI("AI round finished")

                if (!aiRoundResult.success) {
                    withContext(Dispatchers.Main) {
                        onError(aiRoundResult.error ?: s.shared("ai_error_send_message").format(""))
                    }
                    LogManager.aiUI("Failed AI round: ${aiRoundResult.error}", "ERROR")
                } else {
                    LogManager.aiUI("AI round completed successfully")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(s.shared("ai_error_send_message").format(e.message ?: ""))
                }
                LogManager.aiUI("Exception in sendMessageAsync: ${e.message}", "ERROR", e)
            }
        }
    }

    // ========================================================================================
    // Session CRUD API
    // ========================================================================================

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
                // Parse messages through messageStorage
                // (We need to expose parseMessages or create a public method)
                // For now, just return session without detailed message parsing
                LogManager.aiSession("Loaded session $sessionId", "DEBUG")

                AISession(
                    id = sessionData["id"] as? String ?: sessionId,
                    name = sessionData["name"] as? String ?: "",
                    type = try {
                        SessionType.valueOf(sessionData["type"] as? String ?: "CHAT")
                    } catch (e: Exception) {
                        SessionType.CHAT
                    },
                    requireValidation = sessionData["requireValidation"] as? Boolean ?: false,
                    waitingStateJson = sessionData["waitingStateJson"] as? String,
                    automationId = sessionData["automationId"] as? String,
                    scheduledExecutionTime = (sessionData["scheduledExecutionTime"] as? Number)?.toLong(),
                    providerId = sessionData["providerId"] as? String ?: "claude",
                    providerSessionId = sessionData["providerSessionId"] as? String ?: "",
                    createdAt = (sessionData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    lastActivity = (sessionData["lastActivity"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    messages = emptyList(), // TODO: Parse messages properly
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
    // Automation Execution API
    // ========================================================================================

    /**
     * Execute an automation by creating a new AUTOMATION session and triggering execution
     *
     * Flow:
     * 1. Load automation configuration
     * 2. Load SEED session containing initial message
     * 3. Create new AUTOMATION session (copy USER messages from SEED)
     * 4. Request session control (queues if needed, executes when ready)
     *
     * @param automationId ID of the automation to execute
     * @param trigger Execution trigger type (MANUAL from UI, SCHEDULED from tick, EVENT from triggers)
     * @param scheduledFor Scheduled execution time (current time for MANUAL, calculated time for SCHEDULED)
     */
    suspend fun executeAutomation(
        automationId: String,
        trigger: ExecutionTrigger = ExecutionTrigger.MANUAL,
        scheduledFor: Long = System.currentTimeMillis()
    ): OperationResult {
        LogManager.aiSession("AIOrchestrator.executeAutomation() called for $automationId (trigger=$trigger)", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // 1. Load automation
                val automationResult = coordinator.processUserAction("automations.get", mapOf(
                    "automation_id" to automationId
                ))

                if (!automationResult.isSuccess) {
                    return@withContext OperationResult.error("Failed to load automation: ${automationResult.error}")
                }

                val automationData = automationResult.data?.get("automation") as? Map<*, *>
                    ?: return@withContext OperationResult.error("No automation data found")

                val seedSessionId = automationData["seed_session_id"] as? String
                    ?: return@withContext OperationResult.error("No seed session ID in automation")
                val providerId = automationData["provider_id"] as? String
                    ?: return@withContext OperationResult.error("No provider ID in automation")

                // 2. Load SEED session
                val seedSessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf(
                    "sessionId" to seedSessionId
                ))

                if (!seedSessionResult.isSuccess) {
                    return@withContext OperationResult.error("Failed to load seed session: ${seedSessionResult.error}")
                }

                val seedMessages = seedSessionResult.data?.get("messages") as? List<*>
                    ?: return@withContext OperationResult.error("No messages in seed session")

                // Filter USER messages only
                val userMessages = seedMessages.mapNotNull { msgData ->
                    val msg = msgData as? Map<*, *> ?: return@mapNotNull null
                    val sender = msg["sender"] as? String
                    if (sender == "USER") msg else null
                }

                if (userMessages.isEmpty()) {
                    return@withContext OperationResult.error("No USER messages found in seed session")
                }

                LogManager.aiSession("Found ${userMessages.size} USER messages in seed session", "DEBUG")

                // 3. Create new AUTOMATION session
                val sessionId = java.util.UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val createSessionResult = coordinator.processUserAction("ai_sessions.create_session", mapOf(
                    "name" to "Execution ${automationData["name"]}",
                    "type" to SessionType.AUTOMATION.name,
                    "providerId" to providerId,
                    "automationId" to automationId,
                    "scheduledExecutionTime" to scheduledFor
                ))

                if (!createSessionResult.isSuccess) {
                    return@withContext OperationResult.error("Failed to create automation session: ${createSessionResult.error}")
                }

                val executionSessionId = createSessionResult.data?.get("sessionId") as? String
                    ?: return@withContext OperationResult.error("No session ID returned")

                // 4. Copy USER messages from SEED to new session
                for (userMsg in userMessages) {
                    val copyResult = coordinator.processUserAction("ai_sessions.create_message", mapOf(
                        "sessionId" to executionSessionId,
                        "sender" to MessageSender.USER.name,
                        "richContent" to (userMsg["richContentJson"] ?: ""),
                        "textContent" to (userMsg["textContent"] ?: ""),
                        "timestamp" to now
                    ))

                    if (!copyResult.isSuccess) {
                        LogManager.aiSession("Failed to copy message: ${copyResult.error}", "WARN")
                    }
                }

                LogManager.aiSession("Created AUTOMATION session $executionSessionId for automation $automationId", "INFO")

                // 5. Request session control (this will queue and execute when ready)
                requestSessionControl(
                    sessionId = executionSessionId,
                    type = SessionType.AUTOMATION,
                    trigger = trigger,
                    automationId = automationId,
                    scheduledExecutionTime = scheduledFor
                )

                OperationResult.success(mapOf(
                    "session_id" to executionSessionId,
                    "automation_id" to automationId
                ))

            } catch (e: Exception) {
                LogManager.aiSession("executeAutomation - Error: ${e.message}", "ERROR", e)
                OperationResult.error("Failed to execute automation: ${e.message}")
            }
        }
    }
}
