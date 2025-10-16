package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.AIDao
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
import kotlinx.coroutines.flow.StateFlow

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

    // Core components
    private lateinit var stateRepository: AIStateRepository
    private lateinit var messageRepository: AIMessageRepository
    private lateinit var eventProcessor: AIEventProcessor
    private lateinit var sessionScheduler: AISessionScheduler
    private lateinit var validationResolver: ValidationResolver
    private lateinit var promptManager: PromptManager
    private lateinit var aiClient: AIClient
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var automationScheduler: AutomationScheduler

    // ========================================================================================
    // Public Observable State
    // ========================================================================================

    /**
     * Current AI state (observable)
     */
    val currentState: StateFlow<AIState>
        get() = stateRepository.state

    // ========================================================================================
    // Initialization
    // ========================================================================================

    /**
     * Initialize orchestrator with context.
     * Must be called at app startup before any usage.
     */
    suspend fun initialize(context: Context) {
        this.context = context.applicationContext
        this.coordinator = Coordinator(this.context)

        val appDatabase = AppDatabase.getDatabase(this.context)
        this.aiDao = appDatabase.aiDao()

        LogManager.aiSession("AIOrchestrator V2 initializing...")

        // Initialize components
        stateRepository = AIStateRepository(this.context, aiDao)
        messageRepository = AIMessageRepository(aiDao)
        validationResolver = ValidationResolver(this.context)
        promptManager = PromptManager(this.context)
        aiClient = AIClient(this.context)
        commandExecutor = CommandExecutor(this.context)
        automationScheduler = AutomationScheduler(aiDao)

        sessionScheduler = AISessionScheduler(aiDao, automationScheduler)

        eventProcessor = AIEventProcessor(
            context = this.context,
            stateRepository = stateRepository,
            messageRepository = messageRepository,
            coordinator = coordinator,
            aiClient = aiClient,
            promptManager = promptManager,
            validationResolver = validationResolver,
            commandExecutor = commandExecutor
        )

        // Restore state from DB
        stateRepository.initializeFromDb()

        // Start event processor
        eventProcessor.initialize()

        LogManager.aiSession("AIOrchestrator V2 initialized", "INFO")
    }

    // ========================================================================================
    // User Message API
    // ========================================================================================

    /**
     * Send user message to active session.
     *
     * Flow:
     * 1. Store USER message in repository
     * 2. Emit UserMessageSent event
     * 3. Event processor handles enrichments → AI call
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

        // Store USER message
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
     * Request chat session activation.
     * Creates new session if needed.
     *
     * Flow:
     * 1. Check if there's already an active CHAT session
     * 2. If not, create new CHAT session
     * 3. Emit SessionActivationRequested event
     * 4. SessionScheduler handles queue/interruption logic
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
            // Create new CHAT session
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
                consecutiveFormatErrors = 0,
                consecutiveActionFailures = 0,
                consecutiveDataQueries = 0,
                totalRoundtrips = 0,
                lastEventTime = now,
                lastUserInteractionTime = now,
                automationId = null,
                scheduledExecutionTime = null,
                providerId = providerId,
                providerSessionId = java.util.UUID.randomUUID().toString(),
                createdAt = now,
                lastActivity = now,
                isActive = false, // Will be activated by SessionActivationRequested
                endReason = null,
                tokensUsed = null
            )

            aiDao.insertSession(session)
            LogManager.aiSession("Created new CHAT session: $newSessionId", "INFO")
            newSessionId
        }

        // Emit activation request
        eventProcessor.emit(AIEvent.SessionActivationRequested(sessionId))
    }

    /**
     * Stop active session.
     */
    suspend fun stopActiveSession() {
        eventProcessor.emit(AIEvent.SessionCompleted(SessionEndReason.CANCELLED))
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
        kotlinx.coroutines.GlobalScope.launch {
            eventProcessor.emit(AIEvent.ValidationReceived(approved))
        }
    }

    /**
     * Resume execution after communication module response.
     */
    fun resumeWithResponse(response: String) {
        LogManager.aiSession("resumeWithResponse: $response", "INFO")

        // Emit response event asynchronously
        kotlinx.coroutines.GlobalScope.launch {
            eventProcessor.emit(AIEvent.CommunicationResponseReceived(response))
        }
    }

    // ========================================================================================
    // Automation API
    // ========================================================================================

    /**
     * Execute automation manually.
     *
     * Flow:
     * 1. Load automation configuration
     * 2. Load SEED session messages
     * 3. Create new AUTOMATION session
     * 4. Copy USER messages from SEED
     * 5. Emit SessionActivationRequested event
     */
    suspend fun executeAutomation(automationId: String) {
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
                consecutiveFormatErrors = 0,
                consecutiveActionFailures = 0,
                consecutiveDataQueries = 0,
                totalRoundtrips = 0,
                lastEventTime = now,
                lastUserInteractionTime = now,
                automationId = automationId,
                scheduledExecutionTime = now, // For MANUAL trigger
                providerId = providerId,
                providerSessionId = java.util.UUID.randomUUID().toString(),
                createdAt = now,
                lastActivity = now,
                isActive = false, // Will be activated by SessionActivationRequested
                endReason = null,
                tokensUsed = null
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

            // Emit activation request
            eventProcessor.emit(AIEvent.SessionActivationRequested(newSessionId))

        } catch (e: Exception) {
            LogManager.aiSession("executeAutomation failed: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Scheduler heartbeat (called by SchedulerWorker every 5 min).
     */
    suspend fun tick() {
        eventProcessor.emit(AIEvent.SchedulerHeartbeat)
    }

    // ========================================================================================
    // Shutdown
    // ========================================================================================

    /**
     * Shutdown orchestrator and cleanup resources.
     */
    fun shutdown() {
        eventProcessor.shutdown()
        LogManager.aiSession("AIOrchestrator V2 shutdown", "INFO")
    }
}
