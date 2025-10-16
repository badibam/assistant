package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.domain.*
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.state.AIMessageRepository
import com.assistant.core.ai.state.AIStateRepository
import com.assistant.core.ai.validation.ValidationResolver
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.*
import java.util.UUID

/**
 * Event processor with side effects for AI execution.
 *
 * Handles the event loop and all side effects based on phase transitions:
 * - Execute enrichments
 * - Call AI provider
 * - Parse AI responses
 * - Execute commands (data queries, actions)
 * - Handle network retry
 * - Manage user interactions (validation, communication)
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Listens to state changes from AIStateRepository
 * - Executes side effects based on phase
 * - Emits new events to continue flow
 * - All side effects are async and cancellable
 */
class AIEventProcessor(
    private val context: Context,
    private val stateRepository: AIStateRepository,
    private val messageRepository: AIMessageRepository,
    private val coordinator: Coordinator,
    private val aiClient: AIClient,
    private val promptManager: PromptManager,
    private val validationResolver: ValidationResolver,
    private val commandExecutor: CommandExecutor
) {
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var networkRetryJob: Job? = null

    /**
     * Initialize processor and start listening to state changes.
     */
    fun initialize() {
        processingScope.launch {
            stateRepository.state.collect { state ->
                handleStateChange(state)
            }
        }
    }

    /**
     * Emit an event to trigger state transition and side effects.
     *
     * This is the main entry point for all events.
     */
    suspend fun emit(event: AIEvent) {
        try {
            // Transition state via repository (atomic memory + DB)
            val newState = stateRepository.emit(event)

            // Side effects are handled by handleStateChange (via StateFlow collector)
            // No need to call explicitly here

        } catch (e: Exception) {
            LogManager.aiSession(
                "Event processing failed: ${event::class.simpleName}, error: ${e.message}",
                "ERROR",
                e
            )

            // Emit system error event
            emit(AIEvent.SystemErrorOccurred(e.message ?: "Unknown error"))
        }
    }

    /**
     * Handle state change and execute side effects based on phase.
     */
    private suspend fun handleStateChange(state: AIState) {
        when (state.phase) {
            Phase.IDLE -> {
                // No side effects - waiting for activation
            }

            Phase.EXECUTING_ENRICHMENTS -> {
                executeEnrichments(state)
            }

            Phase.CALLING_AI -> {
                callAI(state)
            }

            Phase.PARSING_AI_RESPONSE -> {
                // Parsing is done in AIResponseReceived handler
                // This phase is transitional
            }

            Phase.WAITING_VALIDATION -> {
                // UI handles validation display
                // No side effects - waiting for user
            }

            Phase.WAITING_COMMUNICATION_RESPONSE -> {
                // UI handles communication module display
                // No side effects - waiting for user
            }

            Phase.EXECUTING_DATA_QUERIES -> {
                executeDataQueries(state)
            }

            Phase.EXECUTING_ACTIONS -> {
                executeActions(state)
            }

            Phase.WAITING_COMPLETION_CONFIRMATION -> {
                scheduleCompletionConfirmation(state)
            }

            Phase.WAITING_NETWORK_RETRY -> {
                scheduleNetworkRetry(state)
            }

            Phase.RETRYING_AFTER_FORMAT_ERROR -> {
                // Transition directly to CALLING_AI
                emit(AIEvent.RetryScheduled)
            }

            Phase.RETRYING_AFTER_ACTION_FAILURE -> {
                // Transition directly to CALLING_AI
                emit(AIEvent.RetryScheduled)
            }

            Phase.COMPLETED -> {
                handleSessionCompletion(state)
            }
        }
    }

    /**
     * Execute enrichments from user message.
     */
    private suspend fun executeEnrichments(state: AIState) {
        // TODO: Load enrichments from last user message
        // TODO: Process enrichments via EnrichmentProcessor
        // TODO: Execute commands via CommandExecutor
        // For now, emit empty results
        emit(AIEvent.EnrichmentsExecuted(emptyList()))
    }

    /**
     * Call AI provider with current prompt.
     */
    private suspend fun callAI(state: AIState) {
        try {
            val sessionId = state.sessionId ?: return

            // Build prompt data
            val promptData = promptManager.buildPromptData(sessionId)

            // Check network availability
            if (!isNetworkAvailable()) {
                if (state.sessionType == SessionType.AUTOMATION) {
                    // AUTOMATION: infinite retry
                    emit(AIEvent.NetworkErrorOccurred(0))
                } else {
                    // CHAT: immediate failure
                    emit(AIEvent.SystemErrorOccurred("Network unavailable"))
                }
                return
            }

            // Call AI provider
            val response = aiClient.query(promptData)

            if (response.success) {
                // Emit success event with content
                emit(AIEvent.AIResponseReceived(response.content))
            } else {
                // Network error
                emit(AIEvent.NetworkErrorOccurred(0))
            }

        } catch (e: Exception) {
            LogManager.aiSession("AI call failed: ${e.message}", "ERROR", e)
            emit(AIEvent.NetworkErrorOccurred(0))
        }
    }

    /**
     * Schedule network retry with 30s delay.
     */
    private fun scheduleNetworkRetry(state: AIState) {
        // Cancel previous retry if any
        networkRetryJob?.cancel()

        networkRetryJob = processingScope.launch {
            delay(30_000L) // 30 seconds

            // Check if network is now available
            if (isNetworkAvailable()) {
                emit(AIEvent.NetworkAvailable)
            } else {
                // Schedule another retry
                emit(AIEvent.NetworkRetryScheduled)
            }
        }
    }

    /**
     * Execute data query commands.
     */
    private suspend fun executeDataQueries(state: AIState) {
        // TODO: Extract dataCommands from last AI message
        // TODO: Execute via CommandExecutor
        // TODO: Format results
        // For now, emit empty results
        emit(AIEvent.DataQueriesExecuted(emptyList()))
    }

    /**
     * Execute action commands with validation logic.
     */
    private suspend fun executeActions(state: AIState) {
        try {
            val sessionId = state.sessionId ?: return

            // TODO: Extract actionCommands from last AI message
            val actions = emptyList<DataCommand>() // Placeholder
            val aiMessageId = "" // Placeholder

            // Check if validation is required
            val validationResult = validationResolver.shouldValidate(
                actions = actions,
                sessionId = sessionId,
                aiMessageId = aiMessageId,
                aiRequestedValidation = false // TODO: Get from AI message
            )

            when (validationResult) {
                is com.assistant.core.ai.validation.ValidationResult.RequiresValidation -> {
                    // Store validation context and wait for user
                    val waitingContext = WaitingContext.Validation(
                        validationContext = validationResult.context,
                        cancelMessageId = createFallbackMessage(sessionId, "VALIDATION_CANCELLED")
                    )

                    stateRepository.updateWaitingContext(waitingContext)
                }

                is com.assistant.core.ai.validation.ValidationResult.NoValidation -> {
                    // Execute actions directly
                    // TODO: Execute via CommandExecutor
                    val results = emptyList<com.assistant.commands.CommandResult>()
                    val allSuccess = true
                    val keepControl = false // TODO: Get from AI message

                    emit(AIEvent.ActionsExecuted(results, allSuccess, keepControl))
                }
            }

        } catch (e: Exception) {
            LogManager.aiSession("Action execution failed: ${e.message}", "ERROR", e)
            emit(AIEvent.SystemErrorOccurred(e.message ?: "Unknown error"))
        }
    }

    /**
     * Schedule completion confirmation with 1s delay.
     */
    private fun scheduleCompletionConfirmation(state: AIState) {
        processingScope.launch {
            delay(1_000L) // 1 second

            // Auto-confirm completion
            emit(AIEvent.CompletionConfirmed)
        }
    }

    /**
     * Handle session completion cleanup.
     */
    private suspend fun handleSessionCompletion(state: AIState) {
        val sessionId = state.sessionId ?: return

        LogManager.aiSession(
            "Session completed: $sessionId, reason: ${state.phase}",
            "INFO"
        )

        // Clear message cache
        messageRepository.clearCache(sessionId)

        // Cancel any pending jobs
        networkRetryJob?.cancel()

        // Force state to idle
        stateRepository.forceIdle()

        // TODO: Emit SchedulerHeartbeat to process next session
    }

    /**
     * Create fallback message for cancellation scenarios.
     */
    private suspend fun createFallbackMessage(sessionId: String, type: String): String {
        val messageId = UUID.randomUUID().toString()

        val message = SessionMessage(
            id = messageId,
            timestamp = System.currentTimeMillis(),
            sender = MessageSender.SYSTEM,
            richContent = null,
            textContent = type, // Fallback text
            aiMessage = null,
            aiMessageJson = null,
            systemMessage = null,
            executionMetadata = null,
            excludeFromPrompt = false
        )

        messageRepository.storeMessage(sessionId, message)

        return messageId
    }

    /**
     * Check network availability.
     */
    private fun isNetworkAvailable(): Boolean {
        // TODO: Implement network check via NetworkUtils
        return true
    }

    /**
     * Shutdown processor and cancel all jobs.
     */
    fun shutdown() {
        processingScope.cancel()
    }
}
