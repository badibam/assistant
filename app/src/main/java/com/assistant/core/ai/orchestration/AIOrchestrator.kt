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
     * @param richMessage User message with enrichments
     */
    suspend fun sendMessage(richMessage: RichMessage) {
        // Emit UserMessageSent event (placeholder - will be implemented)
        // Event processor will handle enrichments and AI call
        LogManager.aiSession("sendMessage called", "INFO")

        // TODO: Implement UserMessageSent event
        // eventProcessor.emit(AIEvent.UserMessageSent(richMessage))
    }

    // ========================================================================================
    // Session Control API
    // ========================================================================================

    /**
     * Request chat session activation.
     * Creates new session if needed.
     */
    suspend fun requestChatSession() {
        LogManager.aiSession("requestChatSession called", "INFO")

        // TODO: Implement session creation + activation
        // val sessionId = createChatSession()
        // eventProcessor.emit(AIEvent.SessionActivationRequested(sessionId))
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
     */
    suspend fun executeAutomation(automationId: String) {
        LogManager.aiSession("executeAutomation called: $automationId", "INFO")

        // TODO: Implement automation execution flow
        // 1. Create AUTOMATION session from SEED
        // 2. Request activation
        // eventProcessor.emit(AIEvent.SessionActivationRequested(sessionId))
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
