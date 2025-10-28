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
    private var sessionClosureJob: Job? = null

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
     * Handle state change and execute side effects based on phase.
     */
    private suspend fun handleStateChange(state: AIState) {
        when (state.phase) {
            Phase.IDLE -> {
                // Check if just transitioned from WAITING_COMMUNICATION_RESPONSE
                // This happens when CommunicationCancelled event is emitted
                // We need to handle the side effect here since the event itself doesn't have a dedicated phase
                // (No explicit handling needed - message creation is done in emit() before transition)
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
     * 2. Extract RichMessage and regenerate DataCommands from EnrichmentBlocks
     * 3. Transform commands via UserCommandProcessor
     * 4. Execute via CommandExecutor
     * 5. Store SystemMessage with results
     * 6. Emit EnrichmentsExecuted event with CommandResults
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

            val richContent = lastUserMessage.richContent
            if (richContent == null) {
                LogManager.aiSession("executeEnrichments: USER message has no richContent", "DEBUG")
                emit(AIEvent.EnrichmentsExecuted(emptyList()))
                return
            }

            // 2. Regenerate DataCommands from EnrichmentBlocks
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

            // 3. Transform commands via UserCommandProcessor
            val processor = UserCommandProcessor(context)
            val executableCommands = processor.processCommands(allDataCommands)

            // 4. Execute via CommandExecutor with sessionId for schema deduplication
            val executor = commandExecutor
            val result = executor.executeCommands(
                commands = executableCommands,
                messageType = SystemMessageType.DATA_ADDED,
                level = "enrichments",
                sessionId = sessionId  // Enable schema deduplication
            )

            // 5. Store SystemMessage ONLY if commands were executed (even if all CACHED)
            // Note: If all toggles unchecked, no commands generated → no SystemMessage
            if (result.systemMessage.commandResults.isNotEmpty()) {
                val formattedData = result.promptResults.joinToString("\n\n") {
                    "# ${it.dataTitle}\n${it.formattedData}"
                }
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

                messageRepository.storeMessage(sessionId, systemSessionMessage)
                LogManager.aiSession("Stored enrichments SystemMessage with ${result.systemMessage.commandResults.size} results", "DEBUG")
            } else {
                LogManager.aiSession("No commands executed from enrichments - skipping SystemMessage creation", "DEBUG")
            }

            // 6. Emit event with CommandResults
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
                    "sessionId" to sessionId
                ))

                if (sessionResult.status == com.assistant.core.commands.CommandStatus.SUCCESS) {
                    val sessionData = sessionResult.data?.get("session") as? Map<*, *>
                    val sessionProviderId = sessionData?.get("providerId") as? String
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

                // Emit success → triggers parsing
                emit(AIEvent.AIResponseReceived(response.content))

            } else {
                val errorMessage = response.errorMessage ?: "Unknown error"
                LogManager.aiSession("callAI: AI provider error: $errorMessage", "ERROR")

                // Detect provider configuration errors (permanent failures)
                val isProviderError = errorMessage.contains("provider", ignoreCase = true) ||
                                     errorMessage.contains("configuré", ignoreCase = true) ||
                                     errorMessage.contains("configured", ignoreCase = true)

                if (isProviderError) {
                    // Provider not configured or invalid config - permanent error
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
                    // Network/temporary error
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
                    type = SystemMessageType.NETWORK_ERROR,
                    commandResults = emptyList(),
                    summary = "${s.shared("ai_error_network_call_failed")}: ${e.message}",
                    formattedData = null
                ),
                executionMetadata = null,
                excludeFromPrompt = true // Excluded from prompt (audit only)
            )
            messageRepository.storeMessage(sessionId, exceptionErrorMessage)

            emit(AIEvent.NetworkErrorOccurred(0))
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
                    if (hasDataCommands) presentTypes.add("dataCommands")
                    if (hasActionCommands) presentTypes.add("actionCommands")
                    if (hasCommunicationModule) presentTypes.add("communicationModule")
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
            val transformationResult = processor.processDataCommands(dataCommands)

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
            val formattedData = result.promptResults.joinToString("\n\n") {
                "# ${it.dataTitle}\n${it.formattedData}"
            }
            val systemMessageWithData = result.systemMessage.copy(
                formattedData = formattedData
            )

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

            messageRepository.storeMessage(sessionId, systemSessionMessage)

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

                LogManager.aiSession(
                    "Heartbeat: CHAT active (${currentState.sessionId}), automation waiting: $hasWaitingAutomations",
                    "DEBUG"
                )

                if (hasWaitingAutomations) {
                    val sessionScheduler = com.assistant.core.ai.scheduling.AISessionScheduler(
                        aiDao = com.assistant.core.database.AppDatabase.getDatabase(context).aiDao(),
                        automationScheduler = com.assistant.core.ai.scheduling.AutomationScheduler(context)
                    )

                    if (sessionScheduler.shouldTimeout(currentState, hasWaitingAutomations = true)) {
                        LogManager.aiSession("Heartbeat: CHAT timeout (automation waiting)", "INFO")
                        emit(AIEvent.SessionCompleted(SessionEndReason.TIMEOUT))
                    }
                }
            }

            SessionType.AUTOMATION -> {
                LogManager.aiSession(
                    "Heartbeat: AUTOMATION active (${currentState.sessionId}), checking timeouts",
                    "DEBUG"
                )

                val sessionScheduler = com.assistant.core.ai.scheduling.AISessionScheduler(
                    aiDao = com.assistant.core.database.AppDatabase.getDatabase(context).aiDao(),
                    automationScheduler = com.assistant.core.ai.scheduling.AutomationScheduler(context)
                )

                if (sessionScheduler.shouldTimeout(currentState, hasWaitingAutomations = false)) {
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
        LogManager.aiSession("Heartbeat: Slot free, checking for next session to activate", "DEBUG")

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
        } else {
            LogManager.aiSession("Heartbeat: No sessions to activate", "DEBUG")
        }
    }

    /**
     * Shutdown processor and cancel all jobs.
     */
    fun shutdown() {
        networkRetryJob?.cancel()
        sessionClosureJob?.cancel()
        processingScope.cancel()
    }
}
