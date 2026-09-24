package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.domain.*
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.ai.providers.AIFailure
import com.assistant.core.ai.providers.aiFailureOf
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.prompts.toPromptSection
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.state.AIMessageRepository
import com.assistant.core.ai.state.AIStateRepository
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.ai.validation.ValidationResolver
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.*
import org.json.JSONObject
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
    private var sessionClosureJob: Job? = null
    private var initialized = false
    private var stateCollectorJob: Job? = null

    /**
     * Initialize processor and start listening to state changes.
     *
     * Protected against multiple calls: if already initialized, this is a no-op.
     * This prevents duplicate event handlers when MainActivity.onCreate() is called
     * multiple times (e.g., during configuration changes or activity recreation).
     */
    fun initialize() {
        if (initialized) {
            LogManager.aiSession(
                "AIEventProcessor.initialize() called but already initialized, ignoring",
                "DEBUG"
            )
            return
        }

        LogManager.aiSession(
            "AIEventProcessor.initialize() starting event loop",
            "INFO"
        )

        stateCollectorJob = processingScope.launch {
            stateRepository.state.collect { state ->
                handleStateChange(state)
            }
        }

        initialized = true
    }

    /**
     * Emit an event to trigger state transition and side effects.
     *
     * This is the main entry point for all events.
     */
    suspend fun emit(event: AIEvent) {
        try {
            // Special handling for SchedulerHeartbeat before state transition
            if (event is AIEvent.SchedulerHeartbeat) {
                handleSchedulerHeartbeat()
                return // Don't transition state for heartbeat
            }

            // Special handling for CommunicationCancelled - create message BEFORE transition
            if (event is AIEvent.CommunicationCancelled) {
                val currentState = stateRepository.currentState
                val sessionId = currentState.sessionId

                if (sessionId != null) {
                    val s = com.assistant.core.strings.Strings.`for`(context = context)

                    // Create COMMUNICATION_CANCELLED system message
                    val cancelMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = s.shared("ai_module_cancelled"),
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = null,
                        executionMetadata = null,
                        excludeFromPrompt = false // Included in prompt so AI knows user cancelled
                    )

                    messageRepository.storeMessage(sessionId, cancelMessage)
                    LogManager.aiSession("Communication cancelled message created for session $sessionId", "DEBUG")
                }
            }

            // DataConfirmationReceived: the pending data joins the prompt, or is replaced by the
            // refusal, BEFORE the transition calls the AI
            if (event is AIEvent.DataConfirmationReceived) {
                val currentState = stateRepository.currentState
                val sessionId = currentState.sessionId
                val waiting = currentState.waitingContext as? WaitingContext.DataConfirmation
                if (sessionId != null && waiting != null) {
                    val pending = messageRepository.loadMessages(sessionId).firstOrNull { it.id == waiting.messageId }
                    val systemMessage = pending?.systemMessage
                    if (pending != null && systemMessage != null) {
                        val resolved = if (event.approved) {
                            pending.copy(
                                systemMessage = systemMessage.copy(type = SystemMessageType.DATA_ADDED),
                                excludeFromPrompt = false
                            )
                        } else {
                            val s = com.assistant.core.strings.Strings.`for`(context = context)
                            pending.copy(
                                systemMessage = systemMessage.copy(
                                    type = SystemMessageType.DATA_REFUSED,
                                    summary = s.shared("ai_system_data_refused_by_user").format(waiting.dataChars),
                                    formattedData = null
                                ),
                                excludeFromPrompt = false
                            )
                        }
                        messageRepository.updateMessage(sessionId, resolved)
                    }
                }
            }

            // Special handling for ValidationReceived - create message BEFORE transition
            if (event is AIEvent.ValidationReceived) {
                val currentState = stateRepository.currentState
                val sessionId = currentState.sessionId

                if (sessionId != null && !event.approved) {
                    val s = com.assistant.core.strings.Strings.`for`(context = context)

                    // Create VALIDATION_REFUSED system message when user refuses
                    val refusalMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = s.shared("ai_system_validation_refused"),
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = null,
                        executionMetadata = null,
                        excludeFromPrompt = false // Included in prompt so AI knows user refused
                    )

                    messageRepository.storeMessage(sessionId, refusalMessage)
                    LogManager.aiSession("Validation refused message created for session $sessionId", "DEBUG")
                }
            }

            // Special handling for CommunicationResponseReceived - create message BEFORE transition
            if (event is AIEvent.CommunicationResponseReceived) {
                val currentState = stateRepository.currentState
                val sessionId = currentState.sessionId

                if (sessionId != null) {
                    val s = com.assistant.core.strings.Strings.`for`(context = context)

                    // Create system message with user response
                    val responsePrefix = s.shared("ai_module_response_prefix")
                    val responseMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = "$responsePrefix ${event.response}",
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = null,
                        executionMetadata = null,
                        excludeFromPrompt = false // Included in prompt so AI sees user response
                    )

                    messageRepository.storeMessage(sessionId, responseMessage)
                    LogManager.aiSession("Communication response message created for session $sessionId", "DEBUG")
                }
            }

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
     * Say so when the roundtrip limit is what sent the session back to idle.
     *
     * A CHAT session never closes, so reaching the limit puts it back to IDLE and the end
     * reason is dropped -- which left nothing at all on screen. Every message sent afterwards
     * went the same way: stored, IDLE to EXECUTING_ENRICHMENTS, limit, back to IDLE, in
     * silence. The session looked broken rather than stopped, and the only trace was a log
     * line saying "Roundtrip limit reached".
     *
     * Reported on each return to idle rather than once, so the answer arrives with the
     * message it answers. Kept out of the prompt: the model never sees these rounds, and if
     * the limit is later raised the session resumes without a pile of notices in its history.
     */
    private suspend fun reportRoundtripLimit(state: AIState) {
        val sessionId = state.sessionId ?: return
        val sessionType = state.sessionType ?: return
        val limits = AppConfigManager.getAILimits().getLimitsForSessionType(sessionType)
        if (state.totalRoundtrips < limits.maxAutonomousRoundtrips) return

        val s = com.assistant.core.strings.Strings.`for`(context = context)

        messageRepository.storeMessage(sessionId, SessionMessage(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sender = MessageSender.SYSTEM,
            richContent = null,
            textContent = null,
            aiMessage = null,
            aiMessageJson = null,
            systemMessage = com.assistant.core.ai.data.SystemMessage(
                type = SystemMessageType.LIMIT_REACHED,
                commandResults = emptyList(),
                summary = s.shared("ai_limit_total_roundtrips_reached"),
                formattedData = null
            ),
            executionMetadata = null,
            excludeFromPrompt = true
        ))
    }

    /**
     * Handle state change and execute side effects based on phase.
     */
    private suspend fun handleStateChange(state: AIState) {
        when (state.phase) {
            Phase.IDLE -> {
                // Check if just transitioned from WAITING_COMMUNICATION_RESPONSE
                // This happens when CommunicationCancelled event is emitted
                // We need to handle the side effect here since the event itself doesn't have a dedicated phase
                // (No explicit handling needed - message creation is done in emit() before transition)
                reportRoundtripLimit(state)
            }

            Phase.EXECUTING_ENRICHMENTS -> {
                executeEnrichments(state)
            }

            Phase.CALLING_AI -> {
                callAI(state)
            }

            Phase.PARSING_AI_RESPONSE -> {
                parseAIResponse(state)
            }

            Phase.PREPARING_CONTINUATION -> {
                prepareContinuation(state)
            }

            Phase.WAITING_VALIDATION -> {
                // Check if validation actually required via ValidationResolver
                // If yes: create WaitingContext and wait for user
                // If no: emit ValidationNotRequired to proceed directly to EXECUTING_ACTIONS
                checkValidationRequired(state)
            }

            Phase.WAITING_COMMUNICATION_RESPONSE -> {
                // Create WaitingContext with communication module for UI display
                createCommunicationWaitingContext(state)
            }

            Phase.WAITING_DATA_CONFIRMATION -> {
                // Rebuilt from the stored pending message, so the wait survives a restart
                createDataConfirmationWaitingContext(state)
            }

            Phase.EXECUTING_DATA_QUERIES -> {
                executeDataQueries(state)
            }

            Phase.EXECUTING_ACTIONS -> {
                executeActions(state)
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

            Phase.INTERRUPTED -> {
                // Cancel any pending network retry
                networkRetryJob?.cancel()
                networkRetryJob = null

                // Create interruption system message (audit only, excluded from prompt)
                createInterruptionMessage(state)
            }

            Phase.AWAITING_SESSION_CLOSURE -> {
                scheduleSessionClosure(state)
            }

            Phase.CLOSED -> {
                // Handle session completion cleanup
                handleSessionCompletion(state)

                // Try to activate next session AFTER cleanup is complete
                // This ensures endReason is persisted before scheduler checks DB
                processNextSessionActivation()
            }
        }
    }

    /**
     * Execute enrichments from user message.
     *
     * Flow:
     * 1. Load messages and find last USER message
     * 2. Check if enrichments already executed (SystemMessage follows USER message)
     * 3. Extract RichMessage and regenerate DataCommands from EnrichmentBlocks
     * 4. Transform commands via UserCommandProcessor
     * 5. Execute via CommandExecutor
     * 6. Store SystemMessage with results
     * 7. Emit EnrichmentsExecuted event with CommandResults
     *
     * Note: When user sends empty message (implicit "continue"), we skip enrichments
     * because they were already executed for the previous USER message.
     */
    private suspend fun executeEnrichments(state: AIState) {
        try {
            val sessionId = state.sessionId ?: run {
                LogManager.aiSession("executeEnrichments: No session ID in state", "ERROR")
                emit(AIEvent.SystemErrorOccurred("No session ID"))
                return
            }

            // 1. Load messages and find last USER message
            val messages = messageRepository.loadMessages(sessionId)
            val lastUserMessage = messages.lastOrNull { it.sender == MessageSender.USER }

            if (lastUserMessage == null) {
                LogManager.aiSession("executeEnrichments: No USER message found", "DEBUG")
                emit(AIEvent.EnrichmentsExecuted(emptyList()))
                return
            }

            // 2. Check if enrichments already executed for this USER message
            // If there's already a SYSTEM message after this USER message, enrichments were executed
            val lastUserMessageIndex = messages.indexOfLast { it.id == lastUserMessage.id }
            val hasSystemMessageAfter = messages.drop(lastUserMessageIndex + 1)
                .any { it.sender == MessageSender.SYSTEM }

            if (hasSystemMessageAfter) {
                LogManager.aiSession("executeEnrichments: Enrichments already executed for last USER message (implicit continue), skipping", "DEBUG")
                emit(AIEvent.EnrichmentsExecuted(emptyList()))
                return
            }

            val richContent = lastUserMessage.richContent
            if (richContent == null) {
                LogManager.aiSession("executeEnrichments: USER message has no richContent", "DEBUG")
                emit(AIEvent.EnrichmentsExecuted(emptyList()))
                return
            }

            // 3. Regenerate DataCommands from EnrichmentBlocks
            val enrichmentProcessor = com.assistant.core.ai.enrichments.EnrichmentProcessor(context, coordinator)
            val isRelative = state.sessionType == SessionType.AUTOMATION
            val allDataCommands = mutableListOf<com.assistant.core.ai.data.DataCommand>()

            for (segment in richContent.segments) {
                if (segment is com.assistant.core.ai.data.MessageSegment.EnrichmentBlock) {
                    val commands = enrichmentProcessor.generateCommands(
                        type = segment.type,
                        config = segment.config,
                        isRelative = isRelative
                    )
                    allDataCommands.addAll(commands)
                }
            }

            if (allDataCommands.isEmpty()) {
                LogManager.aiSession("executeEnrichments: No enrichment commands generated", "DEBUG")
                emit(AIEvent.EnrichmentsExecuted(emptyList()))
                return
            }

            LogManager.aiSession("executeEnrichments: Generated ${allDataCommands.size} commands from enrichments", "DEBUG")

            // 4. Transform commands via UserCommandProcessor
            val processor = UserCommandProcessor(context)
            val executableCommands = processor.processCommands(allDataCommands, periodReference(state, sessionId))

            // 5. Execute via CommandExecutor with sessionId for schema deduplication
            val executor = commandExecutor
            val result = executor.executeCommands(
                commands = executableCommands,
                messageType = SystemMessageType.DATA_ADDED,
                level = "enrichments",
                sessionId = sessionId  // Enable schema deduplication
            )

            // 6. Store SystemMessage ONLY if commands were executed (even if all CACHED)
            // Note: If all toggles unchecked, no commands generated → no SystemMessage
            if (result.systemMessage.commandResults.isNotEmpty()) {
                val formattedData = result.promptResults.toPromptSection()
                val systemMessageWithData = result.systemMessage.copy(
                    formattedData = formattedData
                )

                // DEBUG: Log schema.get results being stored
                val schemaResults = systemMessageWithData.commandResults.filter { it.command == "schemas.get" }
                if (schemaResults.isNotEmpty()) {
                    LogManager.aiSession("Storing ${schemaResults.size} schema.get results:", "DEBUG")
                    schemaResults.forEach { cmdResult ->
                        val schemaId = cmdResult.data?.get("schema_id")
                        LogManager.aiSession("  - schema_id=$schemaId, status=${cmdResult.status}, hasData=${cmdResult.data != null}", "DEBUG")
                    }
                }

                val systemSessionMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.SYSTEM,
                    richContent = null,
                    textContent = null,
                    aiMessage = null,
                    aiMessageJson = null,
                    systemMessage = systemMessageWithData,
                    executionMetadata = null,
                    excludeFromPrompt = false
                )

                // Above the size threshold the data waits for the user (CHAT) or is refused (AUTOMATION)
                if (storeDataWithinThreshold(state, sessionId, systemSessionMessage)) return
                LogManager.aiSession("Stored enrichments SystemMessage with ${result.systemMessage.commandResults.size} results", "DEBUG")
            } else {
                LogManager.aiSession("No commands executed from enrichments - skipping SystemMessage creation", "DEBUG")
            }

            // 7. Emit event with CommandResults
            emit(AIEvent.EnrichmentsExecuted(result.systemMessage.commandResults))

        } catch (e: Exception) {
            LogManager.aiSession("executeEnrichments failed: ${e.message}", "ERROR", e)
            emit(AIEvent.SystemErrorOccurred(e.message ?: "Unknown error"))
        }
    }

    /**
     * Call AI provider with current prompt.
     *
     * Flow:
     * 1. Verify provider exists and is configured
     * 2. Build prompt data from session
     * 3. Check network availability
     * 4. Call AI provider
     * 5. Store AI message (raw JSON)
     * 6. Parse AI response
     * 7. Emit AIResponseReceived → triggers PARSING_AI_RESPONSE phase
     */
    private suspend fun callAI(state: AIState) {
        val sessionId = state.sessionId ?: run {
            LogManager.aiSession("callAI: No session ID in state", "ERROR")
            emit(AIEvent.SystemErrorOccurred("No session ID"))
            return
        }

        val s = com.assistant.core.strings.Strings.`for`(context = context)

        try {

            // 1. Verify provider before building prompt
            val providerCheckResult = com.assistant.core.ai.providers.ProviderVerifier.verifyProvider(state, context)
            if (!providerCheckResult.isValid) {
                LogManager.aiSession("callAI: Provider check failed: ${providerCheckResult.errorMessage}", "ERROR")

                // Create system message for both CHAT and AUTOMATION (visible in UI, excluded from prompt)
                val errorMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.SYSTEM,
                    richContent = null,
                    textContent = null,
                    aiMessage = null,
                    aiMessageJson = null,
                    systemMessage = com.assistant.core.ai.data.SystemMessage(
                        type = SystemMessageType.PROVIDER_ERROR,
                        commandResults = emptyList(),
                        summary = providerCheckResult.errorMessage ?: s.shared("ai_error_provider_not_found"),
                        formattedData = null
                    ),
                    executionMetadata = null,
                    excludeFromPrompt = true // Excluded from prompt (audit only)
                )
                messageRepository.storeMessage(sessionId, errorMessage)

                // Emit ProviderErrorOccurred - state machine handles CHAT vs AUTOMATION differently
                // CHAT: returns to IDLE (session stays active)
                // AUTOMATION: closes session with ERROR reason
                emit(AIEvent.ProviderErrorOccurred(providerCheckResult.errorMessage ?: "Provider error"))
                return
            }

            // 2. Determine which provider to use
            // For AUTOMATION: use session's configured providerId
            // For CHAT: use active provider (providerId = null)
            val providerId: String? = if (state.sessionType == SessionType.AUTOMATION) {
                // Get session to retrieve providerId
                val coordinator = com.assistant.core.coordinator.Coordinator(context)
                val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf(
                    "session_id" to sessionId
                ))

                if (sessionResult.status == com.assistant.core.commands.CommandStatus.SUCCESS) {
                    val sessionData = sessionResult.data?.get("session") as? Map<*, *>
                    val sessionProviderId = sessionData?.get("provider_id") as? String
                    LogManager.aiSession("callAI: AUTOMATION session using provider '$sessionProviderId'", "DEBUG")
                    sessionProviderId
                } else {
                    LogManager.aiSession("callAI: Failed to get session providerId, will use active provider", "WARN")
                    null
                }
            } else {
                // CHAT: use active provider
                LogManager.aiSession("callAI: CHAT session using active provider", "DEBUG")
                null
            }

            // 3. Build prompt data
            val promptData = promptManager.buildPromptData(sessionId, context)

            // Check network availability
            if (!com.assistant.core.utils.NetworkUtils.isNetworkAvailable(context)) {
                LogManager.aiSession("callAI: Network unavailable", "WARN")

                // Create system message (visible in UI, excluded from prompt)
                val networkErrorMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.SYSTEM,
                    richContent = null,
                    textContent = null,
                    aiMessage = null,
                    aiMessageJson = null,
                    systemMessage = com.assistant.core.ai.data.SystemMessage(
                        type = SystemMessageType.NETWORK_ERROR,
                        commandResults = emptyList(),
                        summary = s.shared("ai_error_network_unavailable"),
                        formattedData = null
                    ),
                    executionMetadata = null,
                    excludeFromPrompt = true // Excluded from prompt (audit only)
                )
                messageRepository.storeMessage(sessionId, networkErrorMessage)

                if (state.sessionType == SessionType.AUTOMATION) {
                    // AUTOMATION: infinite retry with 30s delay
                    emit(AIEvent.NetworkErrorOccurred(0))
                } else {
                    // CHAT: immediate failure
                    emit(AIEvent.SystemErrorOccurred("Network unavailable"))
                }
                return
            }

            // Call AI provider with determined providerId
            LogManager.aiSession("callAI: Calling AI provider (providerId: $providerId)", "DEBUG")
            val response = aiClient.query(promptData, providerId)

            // Check if session was interrupted or closed while we were waiting for response
            val currentState = stateRepository.currentState
            if (currentState.phase == Phase.INTERRUPTED) {
                LogManager.aiSession("callAI: Session interrupted during AI call, ignoring response", "INFO")
                // Transition back to IDLE after ignoring response
                emit(AIEvent.AIResponseIgnored)
                return
            }

            // Check if session was stopped/closed (STOP button clicked)
            if (currentState.phase == Phase.CLOSED || currentState.sessionId == null || currentState.sessionId != sessionId) {
                LogManager.aiSession("callAI: Session closed during AI call (phase=${currentState.phase}, sessionId=${currentState.sessionId}), ignoring response", "INFO")
                return
            }

            if (response.success) {
                LogManager.aiSession("callAI: AI response received (${response.tokensUsed} tokens)", "INFO")

                // Store AI message with raw JSON and token metrics
                val aiMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.AI,
                    richContent = null,
                    textContent = null,
                    aiMessage = null, // Will be parsed in PARSING phase
                    aiMessageJson = response.content,
                    systemMessage = null,
                    executionMetadata = null,
                    excludeFromPrompt = false,
                    // Token usage from API response
                    inputTokens = response.inputTokens,
                    cacheWriteTokens = response.cacheWriteTokens,
                    cacheReadTokens = response.cacheReadTokens,
                    outputTokens = response.tokensUsed
                )

                messageRepository.storeMessage(sessionId, aiMessage)

                // Update session tokens and cost incrementally
                updateSessionTokensAndCost(
                    sessionId = sessionId,
                    inputTokens = response.inputTokens,
                    cacheWriteTokens = response.cacheWriteTokens,
                    cacheReadTokens = response.cacheReadTokens,
                    outputTokens = response.tokensUsed,
                    providerId = providerId
                )

                // Emit success → triggers parsing
                emit(AIEvent.AIResponseReceived(response.content))

            } else {
                val errorMessage = response.errorMessage ?: "Unknown error"
                val failure = response.failure ?: AIFailure.REFUSED
                LogManager.aiSession("callAI: AI provider error ($failure): $errorMessage", "ERROR")

                if (failure != AIFailure.NETWORK) {
                    // The provider was reached: retrying on a timer would bill the same call again
                    // Create system message (visible in UI, excluded from prompt)
                    val systemErrorMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = null,
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = com.assistant.core.ai.data.SystemMessage(
                            type = SystemMessageType.PROVIDER_ERROR,
                            commandResults = emptyList(),
                            summary = errorMessage,
                            formattedData = null
                        ),
                        executionMetadata = null,
                        excludeFromPrompt = true // Excluded from prompt (audit only)
                    )
                    messageRepository.storeMessage(sessionId, systemErrorMessage)

                    emit(AIEvent.ProviderErrorOccurred(errorMessage))
                } else {
                    // Nothing reached the provider: waiting for the network costs nothing
                    // Create system message (visible in UI, excluded from prompt)
                    val networkErrorMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = null,
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = com.assistant.core.ai.data.SystemMessage(
                            type = SystemMessageType.NETWORK_ERROR,
                            commandResults = emptyList(),
                            summary = errorMessage,
                            formattedData = null
                        ),
                        executionMetadata = null,
                        excludeFromPrompt = true // Excluded from prompt (audit only)
                    )
                    messageRepository.storeMessage(sessionId, networkErrorMessage)

                    emit(AIEvent.NetworkErrorOccurred(0))
                }
            }

        } catch (e: Exception) {
            LogManager.aiSession("callAI failed: ${e.message}", "ERROR", e)

            // The HTTP call has its own catch inside the client, so what lands here comes from
            // our own handling around it. Only an I/O error is worth waiting on.
            val failure = aiFailureOf(e)

            // Create system message (visible in UI, excluded from prompt)
            val exceptionErrorMessage = SessionMessage(
                id = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sender = MessageSender.SYSTEM,
                richContent = null,
                textContent = null,
                aiMessage = null,
                aiMessageJson = null,
                systemMessage = com.assistant.core.ai.data.SystemMessage(
                    type = if (failure == AIFailure.NETWORK) SystemMessageType.NETWORK_ERROR else SystemMessageType.PROVIDER_ERROR,
                    commandResults = emptyList(),
                    summary = "${s.shared("ai_error_network_call_failed")}: ${e.message}",
                    formattedData = null
                ),
                executionMetadata = null,
                excludeFromPrompt = true // Excluded from prompt (audit only)
            )
            messageRepository.storeMessage(sessionId, exceptionErrorMessage)

            if (failure == AIFailure.NETWORK) {
                emit(AIEvent.NetworkErrorOccurred(0))
            } else {
                emit(AIEvent.ProviderErrorOccurred(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Parse AI response JSON into AIMessage structure.
     *
     * Flow:
     * 1. Load last AI message
     * 2. Parse aiMessageJson → AIMessage with full validation
     * 3. Validate constraints (mutual exclusivity, field dependencies)
     * 4. Validate communication module schemas if present
     * 5. Log parsed structure and format errors
     * 6. Emit AIResponseParsed or ParseErrorOccurred
     *
     * Fallback: If parsing fails, creates AIMessage with translated error prefix.
     *
     * Uses AIResponseParser logic for validation rules.
     */
    private suspend fun parseAIResponse(state: AIState) {
        try {
            val sessionId = state.sessionId ?: run {
                LogManager.aiSession("parseAIResponse: No session ID in state", "ERROR")
                emit(AIEvent.SystemErrorOccurred("No session ID"))
                return
            }

            val s = com.assistant.core.strings.Strings.`for`(context = context)

            // Load last AI message
            val messages = messageRepository.loadMessages(sessionId)
            val lastAIMessage = messages.lastOrNull { it.sender == MessageSender.AI }

            if (lastAIMessage == null || lastAIMessage.aiMessageJson == null) {
                LogManager.aiSession("parseAIResponse: No AI message to parse", "ERROR")
                emit(AIEvent.ParseErrorOccurred("No AI message found"))
                return
            }

            val aiMessageJson = lastAIMessage.aiMessageJson
            val formatErrors = mutableListOf<String>()

            // Log raw response (VERBOSE)
            LogManager.aiSession("AI RAW RESPONSE: $aiMessageJson", "VERBOSE")

            // Clean markdown markers from response (LLMs often wrap JSON in ```json...```)
            // This ensures consistent format for parsing
            val cleanedJson = aiMessageJson.trim().let { content ->
                val withoutOpening = content.removePrefix("```json").removePrefix("```").trimStart()
                withoutOpening.removeSuffix("```").trimEnd()
            }

            // Parse JSON → AIMessage
            val parsedAIMessage = AIMessage.fromJson(cleanedJson)

            if (parsedAIMessage != null) {
                // Clean postText if no actionCommands (silently fix, not an error)
                // This ensures AI sees clean response in next round without confusion
                var cleanedAIMessage = parsedAIMessage
                val hasActionCommands = parsedAIMessage.actionCommands != null && parsedAIMessage.actionCommands.isNotEmpty()

                if (parsedAIMessage.postText != null && !hasActionCommands) {
                    LogManager.aiSession("parseAIResponse: Cleaning postText without actionCommands", "DEBUG")
                    cleanedAIMessage = parsedAIMessage.copy(postText = null)
                }

                // Validate field constraints (from AIResponseParser logic)
                val dataCommandsList = cleanedAIMessage.dataCommands
                val hasDataCommands = dataCommandsList != null && dataCommandsList.isNotEmpty()
                val hasCommunicationModule = cleanedAIMessage.communicationModule != null

                // Count action types present
                val actionTypesCount = listOf(hasDataCommands, hasActionCommands, hasCommunicationModule).count { it }

                // Rule 1: At most one action type
                if (actionTypesCount > 1) {
                    val presentTypes = mutableListOf<String>()
                    if (hasDataCommands) presentTypes.add("data_commands")
                    if (hasActionCommands) presentTypes.add("action_commands")
                    if (hasCommunicationModule) presentTypes.add("communication_module")
                    formatErrors.add(s.shared("ai_error_validation_multiple_action_types").format(presentTypes.joinToString(", ")))
                }

                // Rule 2: validationRequest only with actionCommands
                if (cleanedAIMessage.validationRequest != null && !hasActionCommands) {
                    formatErrors.add(s.shared("ai_error_validation_request_without_actions"))
                }

                // Validate communication module schemas if present
                cleanedAIMessage.communicationModule?.let { module ->
                    val schema = com.assistant.core.ai.data.CommunicationModuleSchemas.getSchema(module.type, context)
                    if (schema != null) {
                        val validation = com.assistant.core.validation.SchemaValidator.validate(
                            schema = schema,
                            data = module.data,
                            context = context
                        )
                        if (!validation.isValid) {
                            formatErrors.add("Invalid communication module: ${validation.errorMessage}")
                        }
                    }
                }

                // If format errors detected, store FORMAT_ERROR message and emit error event
                if (formatErrors.isNotEmpty()) {
                    LogManager.aiSession("parseAIResponse: Format errors detected: ${formatErrors.joinToString("; ")}", "WARN")

                    // Create FORMAT_ERROR system message for AI to see and fix
                    val formatErrorMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = null,
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = com.assistant.core.ai.data.SystemMessage(
                            type = SystemMessageType.FORMAT_ERROR,
                            commandResults = emptyList(),
                            summary = "Erreurs de format JSON : ${formatErrors.joinToString("; ")}",
                            formattedData = null
                        ),
                        executionMetadata = null,
                        excludeFromPrompt = false // Sent to AI prompt for correction
                    )
                    messageRepository.storeMessage(sessionId, formatErrorMessage)

                    emit(AIEvent.ParseErrorOccurred(formatErrors.joinToString("; ")))
                    return
                }

                // Log parsed structure (DEBUG) - using cleanedAIMessage
                LogManager.aiSession(
                    "AI PARSED MESSAGE:\n" +
                    "  preText: ${cleanedAIMessage.preText.take(100)}${if (cleanedAIMessage.preText.length > 100) "..." else ""}\n" +
                    "  validationRequest: ${cleanedAIMessage.validationRequest ?: "null"}\n" +
                    "  dataCommands: ${cleanedAIMessage.dataCommands?.size ?: 0} commands\n" +
                    "  actionCommands: ${cleanedAIMessage.actionCommands?.size ?: 0} commands\n" +
                    "  postText: ${cleanedAIMessage.postText?.take(50) ?: "null"}\n" +
                    "  keepControl: ${cleanedAIMessage.keepControl ?: "null"}\n" +
                    "  communicationModule: ${cleanedAIMessage.communicationModule?.type ?: "null"}\n" +
                    "  completed: ${cleanedAIMessage.completed ?: "null"}",
                    "DEBUG"
                )

                // Update message in DB with parsed AIMessage (follows pattern of enrichments/actions storage)
                val updatedMessage = lastAIMessage.copy(aiMessage = cleanedAIMessage)
                messageRepository.updateMessage(sessionId, updatedMessage)

                // Emit success with cleanedAIMessage (postText removed if no actionCommands)
                emit(AIEvent.AIResponseParsed(cleanedAIMessage))

            } else {
                // Parsing failed - create fallback message
                LogManager.aiSession("parseAIResponse: JSON parsing failed, creating fallback message", "WARN")

                val errorPrefix = s.shared("ai_response_invalid_format")
                val fallbackMessage = AIMessage(
                    preText = "$errorPrefix ${aiMessageJson.take(500)}",
                    validationRequest = null,
                    dataCommands = null,
                    actionCommands = null,
                    postText = null,
                    keepControl = null,
                    communicationModule = null,
                    completed = null
                )

                // Log fallback (DEBUG)
                LogManager.aiSession(
                    "AI FALLBACK MESSAGE (invalid JSON):\n" +
                    "  preText: ${fallbackMessage.preText.take(100)}${if (fallbackMessage.preText.length > 100) "..." else ""}",
                    "DEBUG"
                )

                // Create FORMAT_ERROR system message for AI to see and fix
                val formatErrorMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.SYSTEM,
                    richContent = null,
                    textContent = null,
                    aiMessage = null,
                    aiMessageJson = null,
                    systemMessage = com.assistant.core.ai.data.SystemMessage(
                        type = SystemMessageType.FORMAT_ERROR,
                        commandResults = emptyList(),
                        summary = "Échec parsing JSON : format invalide. Tu dois répondre avec un JSON valide selon le schéma AIMessage.",
                        formattedData = null
                    ),
                    executionMetadata = null,
                    excludeFromPrompt = false // Sent to AI prompt for correction
                )
                messageRepository.storeMessage(sessionId, formatErrorMessage)

                emit(AIEvent.ParseErrorOccurred("Failed to parse AI response JSON"))
            }

        } catch (e: Exception) {
            LogManager.aiSession("parseAIResponse failed: ${e.message}", "ERROR", e)

            // Create FORMAT_ERROR system message for AI to see
            val sessionId = state.sessionId
            if (sessionId != null) {
                val formatErrorMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.SYSTEM,
                    richContent = null,
                    textContent = null,
                    aiMessage = null,
                    aiMessageJson = null,
                    systemMessage = com.assistant.core.ai.data.SystemMessage(
                        type = SystemMessageType.FORMAT_ERROR,
                        commandResults = emptyList(),
                        summary = "Erreur technique lors du parsing : ${e.message}",
                        formattedData = null
                    ),
                    executionMetadata = null,
                    excludeFromPrompt = false
                )
                messageRepository.storeMessage(sessionId, formatErrorMessage)
            }

            emit(AIEvent.ParseErrorOccurred(e.message ?: "Unknown parsing error"))
        }
    }

    /**
     * Prepare continuation guidance message based on continuation reason.
     *
     * Flow:
     * 1. Determine guidance message based on continuationReason
     * 2. Store guidance as SYSTEM message
     * 3. Emit ContinuationReady to continue to CALLING_AI
     */
    private suspend fun prepareContinuation(state: AIState) {
        try {
            val sessionId = state.sessionId ?: run {
                LogManager.aiSession("prepareContinuation: No session ID in state", "ERROR")
                emit(AIEvent.SystemErrorOccurred("No session ID"))
                return
            }

            val reason = state.continuationReason ?: run {
                LogManager.aiSession("prepareContinuation: No continuation reason", "ERROR")
                emit(AIEvent.SystemErrorOccurred("No continuation reason"))
                return
            }

            val s = com.assistant.core.strings.Strings.`for`(context = context)

            // Determine guidance message based on reason
            val guidanceText = when (reason) {
                ContinuationReason.AUTOMATION_NO_COMMANDS -> {
                    s.shared("ai_automation_no_commands_guidance")
                }
                ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED -> {
                    s.shared("ai_completion_confirmation_required")
                }
            }

            LogManager.aiSession("prepareContinuation: Creating guidance message for reason: $reason", "DEBUG")

            // Store guidance as SYSTEM message (sent to AI in prompt)
            val guidanceMessage = SessionMessage(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sender = MessageSender.SYSTEM,
                richContent = null,
                textContent = guidanceText,
                aiMessage = null,
                aiMessageJson = null,
                systemMessage = null,
                executionMetadata = null,
                excludeFromPrompt = false // Included in AI prompt
            )

            messageRepository.storeMessage(sessionId, guidanceMessage)

            // Emit ContinuationReady to transition to CALLING_AI
            emit(AIEvent.ContinuationReady)

        } catch (e: Exception) {
            LogManager.aiSession("prepareContinuation failed: ${e.message}", "ERROR", e)
            emit(AIEvent.SystemErrorOccurred(e.message ?: "Unknown error"))
        }
    }

    /**
     * The instant this session's relative periods resolve against.
     *
     * An AUTOMATION reads the data of the run it was scheduled for, not of the moment it finally
     * got to run: reopened after a long absence, a daily automation catching up on the 3rd of
     * August must read the 3rd of August. Everything else resolves against the clock.
     *
     * The scheduled time is read from the session rather than carried in the state, so a resumed
     * session gets the same answer as the run that started it.
     */
    private suspend fun periodReference(state: AIState, sessionId: String): Long {
        if (state.sessionType != SessionType.AUTOMATION) {
            return System.currentTimeMillis()
        }

        val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf(
            "session_id" to sessionId
        ))
        val sessionData = (sessionResult.data?.get("session") as? Map<*, *>)
        val scheduled = sessionData?.get("scheduled_execution_time") as? Long

        if (scheduled == null) {
            // An AUTOMATION session always carries one; without it the periods would silently
            // move to today, which is the bug this exists to prevent.
            LogManager.aiSession("periodReference: AUTOMATION session $sessionId has no scheduledExecutionTime, falling back to now (data error)", "ERROR")
            return System.currentTimeMillis()
        }

        LogManager.aiSession("periodReference: AUTOMATION session $sessionId resolves periods on its scheduled time", "DEBUG")
        return scheduled
    }

    /**
     * Schedule network retry with 30s delay.
     */
    private fun scheduleNetworkRetry(state: AIState) {
        // Cancel previous retry if any
        networkRetryJob?.cancel()

        networkRetryJob = processingScope.launch {
            delay(30_000L) // 30 seconds

            // Check if still in network retry phase (could have been interrupted)
            val currentState = stateRepository.currentState
            if (currentState.phase != Phase.WAITING_NETWORK_RETRY) {
                LogManager.aiSession("scheduleNetworkRetry: Phase changed during delay, cancelling retry", "DEBUG")
                return@launch
            }

            // Check if network is now available
            if (com.assistant.core.utils.NetworkUtils.isNetworkAvailable(context)) {
                emit(AIEvent.NetworkAvailable)
            } else {
                // Schedule another retry
                emit(AIEvent.NetworkRetryScheduled)
            }
        }
    }

    /**
     * Execute data query commands.
     *
     * Flow:
     * 1. Load last AI message and extract dataCommands
     * 2. Process via AICommandProcessor
     * 3. Execute via CommandExecutor
     * 4. Store SystemMessage with formatted data
     * 5. Emit DataQueriesExecuted event
     */
    private suspend fun executeDataQueries(state: AIState) {
        try {
            val sessionId = state.sessionId ?: run {
                LogManager.aiSession("executeDataQueries: No session ID in state", "ERROR")
                emit(AIEvent.SystemErrorOccurred("No session ID"))
                return
            }

            // Load last AI message
            val messages = messageRepository.loadMessages(sessionId)
            val lastAIMessage = messages.lastOrNull { it.sender == MessageSender.AI }

            if (lastAIMessage == null || lastAIMessage.aiMessage == null) {
                LogManager.aiSession("executeDataQueries: No AI message with parsed content", "ERROR")
                emit(AIEvent.DataQueriesExecuted(emptyList()))
                return
            }

            val dataCommands = lastAIMessage.aiMessage.dataCommands
            if (dataCommands.isNullOrEmpty()) {
                LogManager.aiSession("executeDataQueries: No dataCommands to execute", "DEBUG")
                emit(AIEvent.DataQueriesExecuted(emptyList()))
                return
            }

            LogManager.aiSession("executeDataQueries: Executing ${dataCommands.size} data commands", "DEBUG")

            // Process via AICommandProcessor
            val processor = AICommandProcessor(context)
            val transformationResult = processor.processDataCommands(dataCommands, periodReference(state, sessionId))

            // Check if all commands were successfully transformed
            if (transformationResult.errors.isNotEmpty()) {
                LogManager.aiSession("executeDataQueries: ${transformationResult.errors.size} command(s) failed transformation", "WARN")

                // Create FORMAT_ERROR message with detailed errors for AI to see and fix
                val errorDetails = transformationResult.errors.joinToString("; ")
                val formatErrorMessage = SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.SYSTEM,
                    richContent = null,
                    textContent = null,
                    aiMessage = null,
                    aiMessageJson = null,
                    systemMessage = com.assistant.core.ai.data.SystemMessage(
                        type = SystemMessageType.FORMAT_ERROR,
                        commandResults = emptyList(),
                        summary = "Erreurs transformation dataCommands : $errorDetails",
                        formattedData = null
                    ),
                    executionMetadata = null,
                    excludeFromPrompt = false // Sent to AI prompt for correction
                )
                messageRepository.storeMessage(sessionId, formatErrorMessage)

                emit(AIEvent.ParseErrorOccurred("${transformationResult.errors.size} dataCommands failed transformation"))
                return
            }

            // Execute via CommandExecutor with sessionId for schema deduplication
            val executor = commandExecutor
            val result = executor.executeCommands(
                commands = transformationResult.executableCommands,
                messageType = SystemMessageType.DATA_ADDED,
                level = "ai_data",
                sessionId = sessionId  // Enable schema deduplication
            )

            // Store SystemMessage with formattedData
            // For DATA_ADDED: rebuild formattedData from promptResults
            // For SCHEMA_REQUIRED: keep original formattedData (promptResults is empty)
            val systemMessageWithData = if (result.promptResults.isNotEmpty()) {
                val formattedData = result.promptResults.toPromptSection()
                result.systemMessage.copy(formattedData = formattedData)
            } else {
                // Keep original formattedData (already set in CommandExecutor for SCHEMA_REQUIRED)
                result.systemMessage
            }

            val systemSessionMessage = SessionMessage(
                id = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sender = MessageSender.SYSTEM,
                richContent = null,
                textContent = null,
                aiMessage = null,
                aiMessageJson = null,
                systemMessage = systemMessageWithData,
                executionMetadata = null,
                excludeFromPrompt = false
            )

            // Above the size threshold the data waits for the user (CHAT) or is refused (AUTOMATION)
            if (storeDataWithinThreshold(state, sessionId, systemSessionMessage)) return

            // Emit event
            emit(AIEvent.DataQueriesExecuted(result.systemMessage.commandResults))

        } catch (e: Exception) {
            LogManager.aiSession("executeDataQueries failed: ${e.message}", "ERROR", e)
            emit(AIEvent.SystemErrorOccurred(e.message ?: "Unknown error"))
        }
    }

    /**
     * Execute action commands with validation logic.
     *
     * Flow:
     * 1. Load last AI message and extract actionCommands
     * 2. Check if validation is required (ValidationResolver)
     * 3. If validation required:
     *    - Create fallback VALIDATION_CANCELLED message
     *    - Update waiting context in state repository
     *    - State machine will transition to WAITING_VALIDATION
     * 4. If no validation:
     *    - Process via AICommandProcessor
     *    - Execute via CommandExecutor
     *    - Store postText if present and all success
     *    - Store SystemMessage with results
     *    - Emit ActionsExecuted with results, success status, and keepControl
     */
    private suspend fun executeActions(state: AIState) {
        try {
            val sessionId = state.sessionId ?: run {
                LogManager.aiSession("executeActions: No session ID in state", "ERROR")
                emit(AIEvent.SystemErrorOccurred("No session ID"))
                return
            }

            // Load last AI message
            val messages = messageRepository.loadMessages(sessionId)
            val lastAIMessage = messages.lastOrNull { it.sender == MessageSender.AI }

            if (lastAIMessage == null || lastAIMessage.aiMessage == null) {
                LogManager.aiSession("executeActions: No AI message with parsed content", "ERROR")
                emit(AIEvent.ActionsExecuted(emptyList(), true, false))
                return
            }

            val aiMessage = lastAIMessage.aiMessage
            val actionCommands = aiMessage.actionCommands
            if (actionCommands.isNullOrEmpty()) {
                LogManager.aiSession("executeActions: No actionCommands to execute", "DEBUG")
                emit(AIEvent.ActionsExecuted(emptyList(), true, false))
                return
            }

            LogManager.aiSession("executeActions: Processing ${actionCommands.size} action commands", "DEBUG")

            // Validation has already been checked in WAITING_VALIDATION phase (CHAT)
            // or skipped entirely (AUTOMATION)
            // Now execute actions directly

            // Process via AICommandProcessor
            val processor = AICommandProcessor(context)
            val transformationResult = processor.processActionCommands(actionCommands)

            // Check if all commands were successfully transformed
            if (transformationResult.errors.isNotEmpty()) {
                        LogManager.aiSession("executeActions: ${transformationResult.errors.size} command(s) failed transformation", "WARN")

                        // Create FORMAT_ERROR message with detailed errors for AI to see and fix
                        val errorDetails = transformationResult.errors.joinToString("; ")
                        val formatErrorMessage = SessionMessage(
                            id = java.util.UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            sender = MessageSender.SYSTEM,
                            richContent = null,
                            textContent = null,
                            aiMessage = null,
                            aiMessageJson = null,
                            systemMessage = com.assistant.core.ai.data.SystemMessage(
                                type = SystemMessageType.FORMAT_ERROR,
                                commandResults = emptyList(),
                                summary = "Erreurs transformation actionCommands : $errorDetails",
                                formattedData = null
                            ),
                            executionMetadata = null,
                            excludeFromPrompt = false // Sent to AI prompt for correction
                        )
                        messageRepository.storeMessage(sessionId, formatErrorMessage)

                        emit(AIEvent.ParseErrorOccurred("${transformationResult.errors.size} actionCommands failed transformation"))
                        return
                    }

                    // Execute via CommandExecutor with sessionId (for consistency, though actions typically don't need deduplication)
                    val executor = commandExecutor
                    val result = executor.executeCommands(
                        commands = transformationResult.executableCommands,
                        messageType = SystemMessageType.ACTIONS_EXECUTED,
                        level = "ai_actions",
                        sessionId = sessionId
                    )

                    // Check if all succeeded
                    val allSuccess = result.systemMessage.commandResults.all {
                        it.status == CommandStatus.SUCCESS
                    }

                    // Store SystemMessage with results (always, even on failure)
                    val systemSessionMessage = SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        sender = MessageSender.SYSTEM,
                        richContent = null,
                        textContent = null,
                        aiMessage = null,
                        aiMessageJson = null,
                        systemMessage = result.systemMessage,
                        executionMetadata = null,
                        excludeFromPrompt = false
                    )

                    messageRepository.storeMessage(sessionId, systemSessionMessage)

                    if (allSuccess) {
                        // All actions succeeded

                        // Store postText as separate message if present
                        if (aiMessage.postText != null) {
                            val postTextMessage = SessionMessage(
                                id = java.util.UUID.randomUUID().toString(),
                                timestamp = System.currentTimeMillis(),
                                sender = MessageSender.AI,
                                richContent = null,
                                textContent = aiMessage.postText,
                                aiMessage = null,
                                aiMessageJson = null,
                                systemMessage = null,
                                executionMetadata = null,
                                excludeFromPrompt = true // PostText excluded from prompt
                            )
                            messageRepository.storeMessage(sessionId, postTextMessage)
                        }

                        // Emit success event with keepControl flag
                        val keepControl = aiMessage.keepControl == true
                        emit(AIEvent.ActionsExecuted(
                            results = result.systemMessage.commandResults,
                            allSuccess = true,
                            keepControl = keepControl
                        ))
                    } else {
                        // Some actions failed - emit ActionFailureOccurred for retry logic
                        LogManager.aiSession("executeActions: ${result.systemMessage.commandResults.count { it.status != CommandStatus.SUCCESS }} actions failed", "WARN")
                        emit(AIEvent.ActionFailureOccurred(
                            errors = result.systemMessage.commandResults
                        ))
                    }

        } catch (e: Exception) {
            LogManager.aiSession("executeActions failed: ${e.message}", "ERROR", e)
            emit(AIEvent.SystemErrorOccurred(e.message ?: "Unknown error"))
        }
    }

    /**
     * Schedule session closure with 5s delay (AUTOMATION only)
     */
    private fun scheduleSessionClosure(state: AIState) {
        // Cancel previous closure job if any
        sessionClosureJob?.cancel()

        sessionClosureJob = processingScope.launch {
            delay(5_000L) // 5 seconds

            // Check if session is still in AWAITING_SESSION_CLOSURE phase
            val currentState = stateRepository.currentState
            if (currentState.phase == Phase.AWAITING_SESSION_CLOSURE) {
                // Close session with COMPLETED reason
                emit(AIEvent.SessionCompleted(SessionEndReason.COMPLETED))
            }
        }

        LogManager.aiSession(
            "Session closure scheduled in 5s for session ${state.sessionId}",
            "DEBUG"
        )
    }

    /**
     * Handle session completion cleanup.
     *
     * Persists endReason to DB (already done via syncStateToDb),
     * shows toast for ERROR endReason, clears cache, and forces IDLE.
     */
    private suspend fun handleSessionCompletion(state: AIState) {
        val sessionId = state.sessionId ?: return
        val endReason = state.endReason

        LogManager.aiSession(
            "Session completed: $sessionId, reason: $endReason",
            "INFO"
        )

        // Show toast for provider errors (ERROR reason)
        if (endReason == SessionEndReason.ERROR) {
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val errorMessage = s.shared("ai_provider_error") // "Erreur du fournisseur IA"

            // Show toast on main thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                com.assistant.core.ui.UI.Toast(
                    context = context,
                    message = errorMessage,
                    duration = com.assistant.core.ui.Duration.LONG
                )
            }
        }

        // Clear message cache
        messageRepository.clearCache(sessionId)

        // Cancel any pending jobs
        networkRetryJob?.cancel()
        sessionClosureJob?.cancel()

        // Force state to idle
        stateRepository.forceIdle()

        // Note: processNextSessionActivation() is now called in handleStateChange(Phase.CLOSED)
        // after this function completes, ensuring endReason is persisted before scheduler checks DB
    }

    /**
     * Create interruption system message (audit only).
     *
     * This message is excluded from prompt to avoid polluting AI context.
     * It's only for user history/audit trail.
     */
    private suspend fun createInterruptionMessage(state: AIState) {
        val sessionId = state.sessionId ?: return

        val s = com.assistant.core.strings.Strings.`for`(context = context)

        val message = SessionMessage(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sender = MessageSender.SYSTEM,
            richContent = null,
            textContent = s.shared("ai_round_interrupted"), // "Round IA interrompu"
            aiMessage = null,
            aiMessageJson = null,
            systemMessage = null,
            executionMetadata = null,
            excludeFromPrompt = true // Excluded from AI prompt
        )

        messageRepository.storeMessage(sessionId, message)

        LogManager.aiSession("Interruption message created for session $sessionId", "DEBUG")
    }

    /**
     * Check if validation is required and create waiting context if needed.
     *
     * Called when entering WAITING_VALIDATION phase (CHAT only, after actionCommands detected).
     * Uses ValidationResolver to check if validation actually required.
     * If yes: creates WaitingContext and waits for user
     * If no: emits ValidationNotRequired to proceed directly to EXECUTING_ACTIONS
     */
    private suspend fun checkValidationRequired(state: AIState) {
        val sessionId = state.sessionId ?: return

        // Load last AI message to get action commands
        val messages = messageRepository.loadMessages(sessionId)
        val lastAIMessage = messages.lastOrNull { it.sender == MessageSender.AI && it.aiMessage != null } ?: run {
            LogManager.aiSession("checkValidationRequired: No AI message found", "ERROR")
            emit(AIEvent.ValidationNotRequired)
            return
        }

        val aiMessage = lastAIMessage.aiMessage ?: run {
            LogManager.aiSession("checkValidationRequired: No AIMessage found", "ERROR")
            emit(AIEvent.ValidationNotRequired)
            return
        }

        val actionCommands = aiMessage.actionCommands ?: emptyList()

        if (actionCommands.isEmpty()) {
            LogManager.aiSession("checkValidationRequired: No action commands", "DEBUG")
            emit(AIEvent.ValidationNotRequired)
            return
        }

        // Check if validation required via ValidationResolver
        val validationResult = validationResolver.shouldValidate(
            actions = actionCommands,
            sessionId = sessionId,
            aiMessageId = lastAIMessage.id,
            aiRequestedValidation = aiMessage.validationRequest == true
        )

        when (validationResult) {
            is com.assistant.core.ai.validation.ValidationResult.RequiresValidation -> {
                LogManager.aiSession("checkValidationRequired: Validation required", "INFO")

                // Create and set waiting context (no fallback message needed)
                val waitingContext = WaitingContext.Validation(
                    validationContext = validationResult.context
                )

                stateRepository.updateWaitingContext(waitingContext)
                // Stay in WAITING_VALIDATION - wait for user response
            }

            is com.assistant.core.ai.validation.ValidationResult.NoValidation -> {
                LogManager.aiSession("checkValidationRequired: No validation required, proceeding to execution", "DEBUG")
                // Proceed directly to EXECUTING_ACTIONS
                emit(AIEvent.ValidationNotRequired)
            }
        }
    }

    /**
     * Store a data SystemMessage, holding it to the session's data size threshold.
     *
     * The size is that of the data text as the AI would receive it. Within the threshold the
     * message is stored as it is and the caller carries on. Above it:
     * - CHAT: stored out of the prompt as DATA_AWAITING_CONFIRMATION, and the user is asked
     * - AUTOMATION: nobody can be asked, so the data is not stored; the AI gets a DATA_REFUSED
     *   message naming the size, the threshold and the requests, telling it to narrow them
     *
     * @return true when the flow now waits for the user, so the caller must not carry on
     */
    private suspend fun storeDataWithinThreshold(state: AIState, sessionId: String, message: SessionMessage): Boolean {
        val dataChars = message.systemMessage?.formattedData?.length ?: 0
        val sessionType = state.sessionType ?: SessionType.CHAT
        val maxDataChars = AppConfigManager.getAILimits().getLimitsForSessionType(sessionType).maxDataChars

        if (dataChars <= maxDataChars) {
            messageRepository.storeMessage(sessionId, message)
            return false
        }

        LogManager.aiSession("Data of $dataChars characters above the $sessionType threshold of $maxDataChars", "INFO")
        val systemMessage = message.systemMessage ?: return false

        if (sessionType == SessionType.CHAT) {
            messageRepository.storeMessage(sessionId, message.copy(
                systemMessage = systemMessage.copy(type = SystemMessageType.DATA_AWAITING_CONFIRMATION),
                excludeFromPrompt = true
            ))
            emit(AIEvent.DataConfirmationRequested)
            return true
        }

        val s = com.assistant.core.strings.Strings.`for`(context = context)
        messageRepository.storeMessage(sessionId, message.copy(
            systemMessage = systemMessage.copy(
                type = SystemMessageType.DATA_REFUSED,
                summary = s.shared("ai_system_data_too_large_automation").format(dataChars, maxDataChars),
                formattedData = null
            )
        ))
        return false
    }

    /**
     * Create the data confirmation waiting context when entering WAITING_DATA_CONFIRMATION.
     *
     * Read from the last message awaiting confirmation, so the same context comes back after
     * the app was closed during the wait.
     */
    private suspend fun createDataConfirmationWaitingContext(state: AIState) {
        val sessionId = state.sessionId ?: return
        val pending = messageRepository.loadMessages(sessionId).lastOrNull {
            it.systemMessage?.type == SystemMessageType.DATA_AWAITING_CONFIRMATION
        } ?: run {
            LogManager.aiSession("createDataConfirmationWaitingContext: No data awaiting confirmation", "ERROR")
            return
        }
        val sessionType = state.sessionType ?: SessionType.CHAT
        stateRepository.updateWaitingContext(WaitingContext.DataConfirmation(
            messageId = pending.id,
            dataChars = pending.systemMessage?.formattedData?.length ?: 0,
            maxDataChars = AppConfigManager.getAILimits().getLimitsForSessionType(sessionType).maxDataChars
        ))
    }

    /**
     * Create communication waiting context when entering WAITING_COMMUNICATION_RESPONSE phase.
     *
     * Extracts communication module from last AI message and creates WaitingContext.
     */
    private suspend fun createCommunicationWaitingContext(state: AIState) {
        val sessionId = state.sessionId ?: return

        // Load last AI message to get communication module
        val messages = messageRepository.loadMessages(sessionId)
        val lastAIMessage = messages.lastOrNull { it.sender == MessageSender.AI && it.aiMessage != null } ?: run {
            LogManager.aiSession("createCommunicationWaitingContext: No AI message found", "ERROR")
            return
        }

        val aiMessage = lastAIMessage.aiMessage ?: return
        val communicationModule = aiMessage.communicationModule ?: run {
            LogManager.aiSession("createCommunicationWaitingContext: No communication module found", "ERROR")
            return
        }

        // Update waiting context (no fallback message needed)
        val waitingContext = WaitingContext.Communication(
            communicationModule = communicationModule,
            aiMessageId = lastAIMessage.id
        )

        stateRepository.updateWaitingContext(waitingContext)
        LogManager.aiSession("Communication waiting context created for session $sessionId", "DEBUG")
    }

    /**
     * Handle scheduler heartbeat event.
     *
     * Watchdog logic:
     * - If CHAT active: check timeout only if automation waiting
     * - If AUTOMATION active: check global timeout + inactivity timeout
     * - If IDLE: try to activate next session
     */
    private suspend fun handleSchedulerHeartbeat() {
        val currentState = stateRepository.currentState

        when (currentState.sessionType) {
            SessionType.CHAT -> {
                // Check if automation waiting (queue or scheduled)
                val queuedSessions = com.assistant.core.ai.orchestration.AIOrchestrator.queuedSessions.value
                val nextSession = com.assistant.core.ai.scheduling.AISessionScheduler(
                    aiDao = com.assistant.core.database.AppDatabase.getDatabase(context).aiDao(),
                    automationScheduler = com.assistant.core.ai.scheduling.AutomationScheduler(context)
                ).getNextSession(queuedSessions)

                val hasWaitingAutomations = nextSession != null

                if (hasWaitingAutomations) {
                    if (com.assistant.core.ai.scheduling.SessionSlotPolicy.shouldTimeout(
                            currentState,
                            hasWaitingAutomations = true,
                            currentTime = System.currentTimeMillis()
                        )
                    ) {
                        LogManager.aiSession("Heartbeat: CHAT timeout (automation waiting)", "INFO")
                        emit(AIEvent.SessionCompleted(SessionEndReason.TIMEOUT))
                    }
                }
            }

            SessionType.AUTOMATION -> {
                if (com.assistant.core.ai.scheduling.SessionSlotPolicy.shouldTimeout(
                        currentState,
                        hasWaitingAutomations = false,
                        currentTime = System.currentTimeMillis()
                    )
                ) {
                    LogManager.aiSession("Heartbeat: AUTOMATION timeout", "INFO")
                    emit(AIEvent.SessionCompleted(SessionEndReason.TIMEOUT))
                }
            }

            SessionType.SEED -> {
                // SEED sessions are never active, this should not happen
                LogManager.aiSession("Heartbeat: SEED session active (unexpected)", "WARN")
            }

            null -> {
                // IDLE - try to activate next session
                processNextSessionActivation()
            }
        }
    }

    /**
     * Process next session activation when slot is free.
     *
     * Priority: CHAT (queue) > MANUAL (queue) > SCHEDULED (calculated)
     */
    private suspend fun processNextSessionActivation() {
        val queuedSessions = com.assistant.core.ai.orchestration.AIOrchestrator.queuedSessions.value
        val sessionScheduler = com.assistant.core.ai.scheduling.AISessionScheduler(
            aiDao = com.assistant.core.database.AppDatabase.getDatabase(context).aiDao(),
            automationScheduler = com.assistant.core.ai.scheduling.AutomationScheduler(context)
        )

        val nextSession = sessionScheduler.getNextSession(queuedSessions)

        if (nextSession != null) {
            val sessionInfo = if (nextSession.isResume()) {
                "Resume session ${nextSession.sessionId}"
            } else {
                "Create session from automation ${nextSession.automationId}"
            }

            LogManager.aiSession(
                "Heartbeat: Activating next session - $sessionInfo (type=${nextSession.sessionType}, trigger=${nextSession.trigger})",
                "INFO"
            )

            // Remove from queue if needed
            if (nextSession.removeFromQueue && nextSession.sessionId != null) {
                com.assistant.core.ai.orchestration.AIOrchestrator.dequeueSession(nextSession.sessionId)
            }

            // Activate session based on type (Resume or Create)
            if (nextSession.isResume()) {
                // Resume existing session
                emit(AIEvent.SessionActivationRequested(nextSession.sessionId!!, nextSession.sessionType))
            } else {
                // Create new automation session from automation
                // For SCHEDULED: scheduledFor = planned execution time from scheduler
                com.assistant.core.ai.orchestration.AIOrchestrator.executeAutomation(
                    nextSession.automationId!!,
                    scheduledFor = nextSession.scheduledFor!!
                )
            }
        }
    }

    /**
     * Shutdown processor and cancel all jobs.
     *
     * After shutdown, initialize() can be called again to restart the processor.
     */
    /**
     * Update session tokens and cost incrementally after receiving AI response
     *
     * Loads current tokens from DB, adds new tokens from this response,
     * calculates costs if model prices available, and saves back to DB.
     *
     * @param sessionId Session to update
     * @param inputTokens Uncached input tokens from API response
     * @param cacheWriteTokens Cache write tokens from API response
     * @param cacheReadTokens Cache read tokens from API response
     * @param outputTokens Output tokens from API response
     * @param providerId Provider ID (nullable for active provider)
     */
    private suspend fun updateSessionTokensAndCost(
        sessionId: String,
        inputTokens: Int,
        cacheWriteTokens: Int,
        cacheReadTokens: Int,
        outputTokens: Int,
        providerId: String?
    ) {
        try {
            val database = com.assistant.core.database.AppDatabase.getDatabase(context)
            val aiDao = database.aiDao()

            // Load current session
            val session = aiDao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("updateSessionTokensAndCost: Session $sessionId not found", "ERROR")
                return
            }

            // Load current tokens and add new ones
            val currentTokens = com.assistant.core.ai.data.SessionTokens.fromJson(session.tokensJson)
            val updatedTokens = currentTokens.addMessage(
                inputTokens = inputTokens,
                cacheWriteTokens = cacheWriteTokens,
                cacheReadTokens = cacheReadTokens,
                outputTokens = outputTokens
            )

            // Try to calculate costs (requires modelId from provider config)
            val costJson = try {
                // Determine which provider to use (same logic as callAI)
                val effectiveProviderId = providerId ?: run {
                    // Get active provider
                    val activeProvider = aiDao.getActiveProviderConfig()
                    activeProvider?.providerId
                }

                if (effectiveProviderId != null) {
                    // Load provider config to get modelId
                    val providerConfig = aiDao.getProviderConfig(effectiveProviderId)
                    if (providerConfig != null) {
                        val configJson = JSONObject(providerConfig.configJson)
                        val modelId = configJson.optString("model", "")

                        if (modelId.isNotEmpty()) {
                            // Get model pricing
                            val modelPrice = com.assistant.core.ai.utils.ModelPriceManager.getModelPrice(effectiveProviderId, modelId)

                            if (modelPrice != null) {
                                // Calculate costs (use 0.0 if cache prices not available)
                                val inputCost = updatedTokens.totalUncachedInputTokens * modelPrice.inputCostPerToken
                                val cacheWriteCost = updatedTokens.totalCacheWriteTokens * (modelPrice.cacheWriteCostPerToken ?: 0.0)
                                val cacheReadCost = updatedTokens.totalCacheReadTokens * (modelPrice.cacheReadCostPerToken ?: 0.0)
                                val outputCost = updatedTokens.totalOutputTokens * modelPrice.outputCostPerToken
                                val totalCost = inputCost + cacheWriteCost + cacheReadCost + outputCost

                                // Create cost breakdown
                                val costBreakdown = com.assistant.core.ai.data.SessionCostBreakdown(
                                    modelId = modelId,
                                    inputCost = inputCost,
                                    cacheWriteCost = cacheWriteCost,
                                    cacheReadCost = cacheReadCost,
                                    outputCost = outputCost,
                                    totalCost = totalCost
                                )

                                costBreakdown.toJson()
                            } else {
                                LogManager.aiSession("updateSessionTokensAndCost: Model price not available for $effectiveProviderId/$modelId", "DEBUG")
                                null
                            }
                        } else {
                            LogManager.aiSession("updateSessionTokensAndCost: No model in provider config", "DEBUG")
                            null
                        }
                    } else {
                        LogManager.aiSession("updateSessionTokensAndCost: Provider config not found for $effectiveProviderId", "DEBUG")
                        null
                    }
                } else {
                    LogManager.aiSession("updateSessionTokensAndCost: No provider ID available", "DEBUG")
                    null
                }
            } catch (e: Exception) {
                LogManager.aiSession("updateSessionTokensAndCost: Failed to calculate costs: ${e.message}", "WARN", e)
                null
            }

            // Update session with new tokens and costs
            aiDao.updateSessionTokensAndCost(
                sessionId = sessionId,
                tokensJson = updatedTokens.toJson(),
                costJson = costJson
            )

            LogManager.aiSession(
                "updateSessionTokensAndCost: Updated session $sessionId - " +
                "tokens (input: ${updatedTokens.totalUncachedInputTokens}, " +
                "cacheWrite: ${updatedTokens.totalCacheWriteTokens}, " +
                "cacheRead: ${updatedTokens.totalCacheReadTokens}, " +
                "output: ${updatedTokens.totalOutputTokens}), " +
                "cost: ${if (costJson != null) "available" else "unavailable"}",
                "DEBUG"
            )

        } catch (e: Exception) {
            LogManager.aiSession("updateSessionTokensAndCost: Failed to update session: ${e.message}", "ERROR", e)
        }
    }

    fun shutdown() {
        LogManager.aiSession(
            "AIEventProcessor.shutdown() called, canceling all jobs",
            "INFO"
        )

        networkRetryJob?.cancel()
        sessionClosureJob?.cancel()
        stateCollectorJob?.cancel()
        processingScope.cancel()

        // Allow reinitialization after shutdown
        initialized = false
    }
}
