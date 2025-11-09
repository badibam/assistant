package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.domain.AIEvent
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.processing.AIEventProcessor
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.ai.scheduling.AISessionScheduler
import com.assistant.core.ai.scheduling.AutomationScheduler
import com.assistant.core.ai.state.AIMessageRepository
import com.assistant.core.ai.state.AIStateRepository
import com.assistant.core.ai.validation.ValidationResolver
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AI Orchestrator singleton - Public API for AI system.
 *
 * Architecture V2: Event-Driven State Machine
 * - Thin facade delegating to specialized components
 * - All logic in domain/state/processing layers
 * - Observable state via StateFlow
 *
 * Components:
 * - AIStateRepository: State management + DB sync
 * - AIMessageRepository: Message persistence
 * - AIEventProcessor: Event loop + side effects
 * - AISessionScheduler: Session scheduling + interruption
 * - ValidationResolver: Validation hierarchy logic
 * - PromptManager: Prompt generation
 * - AIClient: Provider communication
 * - CommandExecutor: Command execution
 */
object AIOrchestrator {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var aiDao: AIDao

    // Coroutine scope for async operations
    private val orchestratorScope = CoroutineScope(Dispatchers.Default)

    // Core components
    private lateinit var stateRepository: AIStateRepository
    private lateinit var messageRepository: AIMessageRepository
    private lateinit var eventProcessor: AIEventProcessor
    private lateinit var sessionScheduler: AISessionScheduler
    private lateinit var validationResolver: ValidationResolver
    // Note: promptManager is a singleton object, not a class instance
    private lateinit var aiClient: AIClient
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var automationScheduler: AutomationScheduler

    // Session queue (managed by scheduler)
    private val _queuedSessions = kotlinx.coroutines.flow.MutableStateFlow<List<com.assistant.core.ai.scheduling.QueuedSession>>(emptyList())

    // Draft message cache (in-memory only, cleared on app restart)
    // Maps sessionId -> List<MessageSegment> for composer state preservation between screen close/reopen
    private val draftMessage = mutableMapOf<String, List<MessageSegment>>()

    // Initialization flag to prevent multiple initializations
    private var initialized = false

    // ========================================================================================
    // Public Observable State
    // ========================================================================================

    /**
     * Current AI state (observable)
     */
    val currentState: StateFlow<AIState>
        get() = stateRepository.state

    /**
     * Queued sessions waiting for activation (observable)
     */
    val queuedSessions: StateFlow<List<com.assistant.core.ai.scheduling.QueuedSession>>
        get() = _queuedSessions

    /**
     * Observe messages for active session.
     *
     * Returns a Flow that emits messages for the current active session.
     * UI components should collect this Flow to display messages.
     *
     * Automatically loads messages from DB if cache is empty (first observation).
     *
     * @param sessionId Session ID to observe
     * @return Flow of message list
     */
    fun observeMessages(sessionId: String): kotlinx.coroutines.flow.Flow<List<SessionMessage>> {
        // Trigger background load of messages if not already loaded
        // This populates the cache for initial display
        orchestratorScope.launch {
            messageRepository.loadMessages(sessionId)
        }

        return messageRepository.observeMessages(sessionId)
    }

    /**
     * Save draft message for a session.
     * Draft is kept in memory only and cleared on app restart.
     *
     * @param sessionId Session ID
     * @param segments Message segments to save as draft
     */
    fun saveDraftMessage(sessionId: String, segments: List<MessageSegment>) {
        draftMessage[sessionId] = segments
        LogManager.aiSession("Draft saved for session $sessionId: ${segments.size} segments", "DEBUG")
    }

    /**
     * Get draft message for a session.
     *
     * @param sessionId Session ID
     * @return Draft segments, or empty list if no draft exists
     */
    fun getDraftMessage(sessionId: String): List<MessageSegment> {
        return draftMessage[sessionId] ?: emptyList()
    }

    /**
     * Clear draft message for a session.
     * Called when message is sent successfully.
     *
     * @param sessionId Session ID
     */
    fun clearDraftMessage(sessionId: String) {
        draftMessage.remove(sessionId)
        LogManager.aiSession("Draft cleared for session $sessionId", "DEBUG")
    }

    /**
     * Add session to queue (internal - called by event processor).
     */
    internal fun enqueueSession(sessionId: String, sessionType: SessionType, trigger: ExecutionTrigger, priority: Int) {
        val queuedSession = com.assistant.core.ai.scheduling.QueuedSession(
            sessionId = sessionId,
            sessionType = sessionType,
            trigger = trigger,
            queuedAt = System.currentTimeMillis()
        )

        // Add to queue and sort by priority (lower priority = higher precedence)
        val currentQueue = _queuedSessions.value.toMutableList()
        currentQueue.add(queuedSession)
        currentQueue.sortBy {
            when (it.sessionType) {
                SessionType.CHAT -> 1
                SessionType.AUTOMATION -> if (it.trigger == ExecutionTrigger.MANUAL) 2 else 3
                else -> 999
            }
        }

        _queuedSessions.value = currentQueue

        LogManager.aiSession("Session enqueued: $sessionId (priority $priority), queue size: ${currentQueue.size}", "INFO")
    }

    /**
     * Remove session from queue (internal - called by event processor).
     */
    internal fun dequeueSession(sessionId: String) {
        _queuedSessions.value = _queuedSessions.value.filter { it.sessionId != sessionId }
        LogManager.aiSession("Session dequeued: $sessionId, remaining: ${_queuedSessions.value.size}", "INFO")
    }

    /**
     * Cancel queued session (public API - called by UI).
     */
    suspend fun cancelQueuedSession(sessionId: String) {
        LogManager.aiSession("Canceling queued session: $sessionId", "INFO")

        // Remove from queue
        dequeueSession(sessionId)

        // Delete session from DB
        val session = aiDao.getSession(sessionId)
        if (session != null) {
            aiDao.deleteSession(session)
        }
    }

    // ========================================================================================
    // Initialization
    // ========================================================================================

    /**
     * Initialize orchestrator with context.
     * Must be called at app startup before any usage.
     *
     * Protected against multiple calls: if already initialized, this is a no-op.
     * This prevents duplicate initialization when MainActivity.onCreate() is called
     * multiple times (e.g., during configuration changes or activity recreation).
     */
    suspend fun initialize(context: Context) {
        if (initialized) {
            LogManager.aiSession("AIOrchestrator.initialize() called but already initialized, ignoring", "DEBUG")
            return
        }

        LogManager.aiSession("AIOrchestrator V2 initializing...", "INFO")

        this.context = context.applicationContext
        this.coordinator = Coordinator(this.context)

        val appDatabase = AppDatabase.getDatabase(this.context)
        this.aiDao = appDatabase.aiDao()

        // Initialize components
        stateRepository = AIStateRepository(this.context, aiDao)
        messageRepository = AIMessageRepository(aiDao)
        validationResolver = ValidationResolver(this.context)
        // promptManager is a singleton object, no initialization needed
        aiClient = AIClient(this.context)
        commandExecutor = CommandExecutor(this.context)
        automationScheduler = AutomationScheduler(this.context)

        sessionScheduler = AISessionScheduler(aiDao, automationScheduler)

        eventProcessor = AIEventProcessor(
            context = this.context,
            stateRepository = stateRepository,
            messageRepository = messageRepository,
            coordinator = coordinator,
            aiClient = aiClient,
            promptManager = PromptManager, // Singleton object
            validationResolver = validationResolver,
            commandExecutor = commandExecutor
        )

        // Restore state from DB
        stateRepository.initializeFromDb()

        // Start event processor
        eventProcessor.initialize()

        initialized = true
        LogManager.aiSession("AIOrchestrator V2 initialized", "INFO")
    }

    // ========================================================================================
    // User Message API
    // ========================================================================================

    /**
     * Send user message to active session.
     *
     * Flow:
     * 1. Validate message is not empty (has text or enrichments)
     * 2. Store USER message in repository
     * 3. Emit UserMessageSent event
     * 4. Event processor handles enrichments → AI call
     *
     * @param richMessage User message with enrichments
     */
    suspend fun sendMessage(richMessage: RichMessage) {
        LogManager.aiSession("sendMessage called", "INFO")

        val sessionId = stateRepository.state.value.sessionId
        if (sessionId == null) {
            LogManager.aiSession("sendMessage: No active session", "ERROR")
            return
        }

        // Check if message is empty (no text and no enrichments)
        val hasText = richMessage.linearText.trim().isNotEmpty()
        val hasEnrichments = richMessage.segments.any { it is MessageSegment.EnrichmentBlock }

        if (!hasText && !hasEnrichments) {
            // Empty message: don't store in history, but still call AI (like implicit "continue")
            LogManager.aiSession("sendMessage: Empty message, skipping storage but continuing sequence", "INFO")
            // Emit UserMessageSent event to trigger AI call without storing empty message
            eventProcessor.emit(AIEvent.UserMessageSent)
            return
        }

        // Store USER message (non-empty)
        val userMessage = SessionMessage(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sender = MessageSender.USER,
            richContent = richMessage,
            textContent = null,
            aiMessage = null,
            aiMessageJson = null,
            systemMessage = null,
            executionMetadata = null,
            excludeFromPrompt = false
        )

        messageRepository.storeMessage(sessionId, userMessage)

        // Emit UserMessageSent event
        // Event processor will transition to EXECUTING_ENRICHMENTS
        eventProcessor.emit(AIEvent.UserMessageSent)
    }

    // ========================================================================================
    // Session Control API
    // ========================================================================================

    /**
     * Create a new CHAT session (always creates, never reuses).
     *
     * @param seedId Optional ID of SEED session to pre-fill composer from
     * @return The created session ID
     */
    private suspend fun createNewChatSession(seedId: String? = null): String {
        val newSessionId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Get active provider
        val provider = aiDao.getActiveProviderConfig()
        val providerId = provider?.providerId ?: "claude"

        val session = AISessionEntity(
            id = newSessionId,
            name = "Chat ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now))}",
            type = SessionType.CHAT,
            requireValidation = false,
            phase = "IDLE",
            waitingContextJson = null,
            totalRoundtrips = 0,
            lastEventTime = now,
            lastUserInteractionTime = now,
            automationId = null,
            seedId = seedId,  // ID of SEED session to pre-fill from (null if normal chat)
            scheduledExecutionTime = null,
            providerId = providerId,
            providerSessionId = java.util.UUID.randomUUID().toString(),
            createdAt = now,
            lastActivity = now,
            isActive = false, // Will be activated by SessionActivationRequested
            endReason = null,
            tokensJson = null,
            costJson = null
        )

        aiDao.insertSession(session)
        LogManager.aiSession("Created new CHAT session: $newSessionId (seedId=$seedId)", "INFO")

        return newSessionId
    }

    /**
     * Request chat session activation.
     * Reuses existing CHAT session if active, otherwise creates new one.
     *
     * Flow:
     * 1. Check if there's already an active CHAT session (reuse if yes)
     * 2. If not, create new CHAT session
     * 3. Request activation via scheduler (handles queue/eviction logic)
     * 4. Handle activation result (immediate, enqueue, or evict)
     */
    suspend fun requestChatSession() {
        LogManager.aiSession("requestChatSession called", "INFO")

        // Check if there's already an active CHAT session
        val currentState = stateRepository.state.value
        val currentSessionId = currentState.sessionId

        val sessionId = if (currentSessionId != null && currentState.sessionType == SessionType.CHAT) {
            // Reuse existing CHAT session
            LogManager.aiSession("Reusing existing CHAT session: $currentSessionId", "DEBUG")
            currentSessionId
        } else {
            // Create new CHAT session (no seedId for normal chat flow)
            createNewChatSession(seedId = null)
        }

        // Request activation via scheduler
        val activationResult = sessionScheduler.requestSession(
            sessionId = sessionId,
            sessionType = SessionType.CHAT,
            trigger = ExecutionTrigger.MANUAL,
            currentState = currentState
        )

        // Handle result
        when (activationResult) {
            is com.assistant.core.ai.scheduling.ActivationResult.ActivateImmediate -> {
                eventProcessor.emit(AIEvent.SessionActivationRequested(sessionId, SessionType.CHAT))
            }
            is com.assistant.core.ai.scheduling.ActivationResult.EvictAndActivate -> {
                // Evict current session
                eventProcessor.emit(AIEvent.SessionCompleted(activationResult.evictionReason))
                // Then activate new session
                eventProcessor.emit(AIEvent.SessionActivationRequested(sessionId, SessionType.CHAT))
            }
            is com.assistant.core.ai.scheduling.ActivationResult.Enqueue -> {
                enqueueSession(sessionId, SessionType.CHAT, ExecutionTrigger.MANUAL, activationResult.priority)
            }
            is com.assistant.core.ai.scheduling.ActivationResult.Skip -> {
                LogManager.aiSession("Session activation skipped: ${activationResult.reason}", "INFO")
            }
        }
    }

    /**
     * Stop active session with CANCELLED reason.
     */
    suspend fun stopActiveSession() {
        eventProcessor.emit(AIEvent.SessionCompleted(SessionEndReason.CANCELLED))
    }

    /**
     * Interrupt current AI round (CHAT only).
     *
     * Cancels the current AI call/processing but keeps session active.
     * If AI response arrives, it will be ignored.
     * Session remains active and waits for next user message.
     *
     * Session continues automatically when user sends next message.
     */
    suspend fun interruptActiveRound() {
        LogManager.aiSession("interruptActiveRound called", "INFO")
        eventProcessor.emit(AIEvent.AIRoundInterrupted)
    }

    /**
     * Start a new CHAT session, evicting any currently active session.
     *
     * Behavior:
     * - AUTOMATION active: Suspended (can resume later)
     * - CHAT active: Cancelled (permanently replaced)
     * - IDLE: Creates new chat directly
     *
     * Flow:
     * 1. Create NEW CHAT session (always new, never reuses)
     * 2. Close current session with appropriate reason (frees the slot)
     * 3. Request activation of new CHAT session
     *
     * Used when:
     * - User clicks "Interrupt" in ChatOptionsDialog
     * - User clicks chat button on automation card (with seedId pre-fill)
     *
     * @param seedId Optional ID of SEED session to pre-fill composer from (for automation button)
     *
     * Note: AUTOMATION sessions will be resumed automatically by the scheduler
     * when the slot becomes free again (after CHAT ends or becomes inactive).
     */
    suspend fun startNewChatSession(seedId: String? = null) {
        LogManager.aiSession("startNewChatSession called (seedId=$seedId)", "INFO")

        val currentState = stateRepository.state.value

        // Step 1: Create NEW CHAT session (always creates, never reuses)
        val sessionId = createNewChatSession(seedId)

        // Step 2: Request activation via scheduler (will enqueue if slot occupied)
        val activationResult = sessionScheduler.requestSession(
            sessionId = sessionId,
            sessionType = SessionType.CHAT,
            trigger = ExecutionTrigger.MANUAL,
            currentState = currentState
        )

        // Step 3: Handle activation result
        when (activationResult) {
            is com.assistant.core.ai.scheduling.ActivationResult.ActivateImmediate -> {
                // Slot free - activate immediately
                eventProcessor.emit(AIEvent.SessionActivationRequested(sessionId, SessionType.CHAT))
                LogManager.aiSession("startNewChatSession: No active session, new CHAT activated directly", "INFO")
            }
            is com.assistant.core.ai.scheduling.ActivationResult.EvictAndActivate -> {
                // Enqueue new session first (will be auto-activated when slot free)
                enqueueSession(sessionId, SessionType.CHAT, ExecutionTrigger.MANUAL, priority = 1)
                // Then evict current session - processNextSessionActivation() will activate queued session
                eventProcessor.emit(AIEvent.SessionCompleted(activationResult.evictionReason))
                LogManager.aiSession("startNewChatSession: Current session evicted, new CHAT enqueued (will auto-activate)", "INFO")
            }
            is com.assistant.core.ai.scheduling.ActivationResult.Enqueue -> {
                // AUTOMATION active - enqueue and auto-suspend (for seedId-based chat from automation button)
                enqueueSession(sessionId, SessionType.CHAT, ExecutionTrigger.MANUAL, activationResult.priority)

                // Auto-suspend AUTOMATION (specific behavior for automation button chat)
                if (currentState.sessionType == SessionType.AUTOMATION) {
                    eventProcessor.emit(AIEvent.SessionCompleted(SessionEndReason.SUSPENDED))
                    LogManager.aiSession("startNewChatSession: AUTOMATION auto-suspended, new CHAT enqueued", "INFO")
                }
            }
            is com.assistant.core.ai.scheduling.ActivationResult.Skip -> {
                LogManager.aiSession("startNewChatSession: Session activation skipped: ${activationResult.reason}", "WARN")
            }
        }
    }

    /**
     * Load messages from a SEED session for pre-filling chat composer.
     *
     * @param seedId ID of the SEED session
     * @return List of SessionMessage from the SEED session (typically one USER message with enrichments)
     */
    suspend fun loadSeedMessages(seedId: String): List<com.assistant.core.ai.data.SessionMessage> {
        LogManager.aiSession("loadSeedMessages called for seedId=$seedId", "INFO")

        return try {
            val messages = messageRepository.loadMessages(seedId)
            LogManager.aiSession("Loaded ${messages.size} messages from SEED session $seedId", "INFO")
            messages
        } catch (e: Exception) {
            LogManager.aiSession("Failed to load SEED messages: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // ========================================================================================
    // User Interaction API
    // ========================================================================================

    /**
     * Resume execution after user validation.
     */
    fun resumeWithValidation(approved: Boolean) {
        LogManager.aiSession("resumeWithValidation: $approved", "INFO")

        // Emit validation event asynchronously
        orchestratorScope.launch {
            eventProcessor.emit(AIEvent.ValidationReceived(approved))
        }
    }

    /**
     * Resume execution after communication module response.
     */
    fun resumeWithResponse(response: String) {
        LogManager.aiSession("resumeWithResponse: $response", "INFO")

        // Emit response event asynchronously
        orchestratorScope.launch {
            eventProcessor.emit(AIEvent.CommunicationResponseReceived(response))
        }
    }

    /**
     * Cancel communication module (CHAT only).
     *
     * Creates COMMUNICATION_CANCELLED system message and transitions to IDLE.
     */
    fun cancelCommunication() {
        LogManager.aiSession("cancelCommunication", "INFO")

        // Emit cancellation event asynchronously
        orchestratorScope.launch {
            eventProcessor.emit(AIEvent.CommunicationCancelled)
        }
    }

    // ========================================================================================
    // Automation API
    // ========================================================================================

    /**
     * Execute automation.
     *
     * Flow:
     * 1. Load automation configuration
     * 2. Load SEED session messages
     * 3. Create new AUTOMATION session
     * 4. Copy USER messages from SEED
     * 5. Emit SessionActivationRequested event
     *
     * @param automationId Automation to execute
     * @param scheduledFor Scheduled execution timestamp (MANUAL: click time, SCHEDULED: planned time)
     */
    suspend fun executeAutomation(automationId: String, scheduledFor: Long) {
        LogManager.aiSession("executeAutomation called: $automationId", "INFO")

        try {
            // Load automation
            val automation = aiDao.getAutomationById(automationId)
            if (automation == null) {
                LogManager.aiSession("Automation not found: $automationId", "ERROR")
                return
            }

            val seedSessionId = automation.seedSessionId
            val providerId = automation.providerId

            // Load SEED session messages
            val seedMessages = aiDao.getMessagesForSession(seedSessionId)
            val userMessages = seedMessages.filter { it.sender == com.assistant.core.ai.data.MessageSender.USER }

            if (userMessages.isEmpty()) {
                LogManager.aiSession("No USER messages in SEED session: $seedSessionId", "ERROR")
                return
            }

            // Create new AUTOMATION session
            val newSessionId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val session = AISessionEntity(
                id = newSessionId,
                name = "Execution ${automation.name}",
                type = SessionType.AUTOMATION,
                requireValidation = false,
                phase = "IDLE",
                waitingContextJson = null,
                totalRoundtrips = 0,
                lastEventTime = now,
                lastUserInteractionTime = now,
                automationId = automationId,
                scheduledExecutionTime = scheduledFor, // Scheduled timestamp (MANUAL: click time, SCHEDULED: planned time)
                providerId = providerId,
                providerSessionId = java.util.UUID.randomUUID().toString(),
                createdAt = now,
                lastActivity = now,
                isActive = false, // Will be activated by SessionActivationRequested
                endReason = null,
                tokensJson = null,
                costJson = null
            )

            aiDao.insertSession(session)
            LogManager.aiSession("Created AUTOMATION session: $newSessionId", "INFO")

            // Copy USER messages from SEED to new session
            for (userMsg in userMessages) {
                aiDao.insertMessage(userMsg.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = newSessionId,
                    timestamp = now
                ))
            }

            LogManager.aiSession("Copied ${userMessages.size} USER messages from SEED", "DEBUG")

            // Request activation via scheduler (handles inactivity check + queue logic)
            val currentState = stateRepository.state.value
            val activationResult = sessionScheduler.requestSession(
                sessionId = newSessionId,
                sessionType = SessionType.AUTOMATION,
                trigger = ExecutionTrigger.MANUAL,
                currentState = currentState
            )

            // Handle result
            when (activationResult) {
                is com.assistant.core.ai.scheduling.ActivationResult.ActivateImmediate -> {
                    eventProcessor.emit(AIEvent.SessionActivationRequested(newSessionId, SessionType.AUTOMATION))
                }
                is com.assistant.core.ai.scheduling.ActivationResult.EvictAndActivate -> {
                    // Evict current session
                    eventProcessor.emit(AIEvent.SessionCompleted(activationResult.evictionReason))
                    // Then activate new session
                    eventProcessor.emit(AIEvent.SessionActivationRequested(newSessionId, SessionType.AUTOMATION))
                }
                is com.assistant.core.ai.scheduling.ActivationResult.Enqueue -> {
                    enqueueSession(newSessionId, SessionType.AUTOMATION, ExecutionTrigger.MANUAL, activationResult.priority)
                }
                is com.assistant.core.ai.scheduling.ActivationResult.Skip -> {
                    LogManager.aiSession("Automation activation skipped: ${activationResult.reason}", "INFO")
                }
            }

        } catch (e: Exception) {
            LogManager.aiSession("executeAutomation failed: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Scheduler heartbeat
     * Called by: internal coroutine (1 min, app-open) + WorkManager (15 min, app-closed)
     */
    suspend fun tick() {
        eventProcessor.emit(AIEvent.SchedulerHeartbeat)
    }

    // ========================================================================================
    // Shutdown
    // ========================================================================================

    /**
     * Shutdown orchestrator and cleanup resources.
     *
     * After shutdown, initialize() can be called again to restart the orchestrator.
     */
    fun shutdown() {
        LogManager.aiSession("AIOrchestrator V2 shutdown", "INFO")
        eventProcessor.shutdown()
        initialized = false
    }
}
