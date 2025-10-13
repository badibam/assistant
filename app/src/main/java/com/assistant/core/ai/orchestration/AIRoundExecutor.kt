package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.providers.AIClient
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.ai.validation.ValidationResolver
import com.assistant.core.ai.validation.ValidationResult
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI Round Executor - executes AI rounds with autonomous loops
 *
 * Responsibilities:
 * - Execute complete AI rounds (prompt building, AI call, autonomous loops)
 * - Manage autonomous loop counters and limits
 * - Handle communication modules and validation during loops
 * - Coordinate with other components for storage, parsing, user interaction
 * - Manage watchdog for AUTOMATION sessions
 *
 * This is the most complex component, orchestrating the entire AI round flow.
 */
class AIRoundExecutor(
    private val context: Context,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope,
    private val aiClient: AIClient,
    private val sessionController: AISessionController,
    private val messageStorage: AIMessageStorage,
    private val userInteractionManager: AIUserInteractionManager,
    private val responseParser: AIResponseParser
) {

    private val s = Strings.`for`(context = context)

    // Round execution state
    private val _isRoundInProgress = MutableStateFlow(false)
    val isRoundInProgress: StateFlow<Boolean> = _isRoundInProgress.asStateFlow()

    // ========================================================================================
    // Main Round Execution API
    // ========================================================================================

    /**
     * Execute AI round with autonomous loops
     * Uses active session, requires processUserMessage() or similar to have been called first
     *
     * Flow:
     * 1. Build prompt data (L1-L2 + messages)
     * 2. Call AI with PromptData (with network checks, retry for AUTOMATION)
     * 3. Store AI response
     * 4. AUTONOMOUS LOOPS (data queries, actions with retries, communication modules)
     */
    suspend fun executeAIRound(reason: RoundReason): OperationResult {
        val sessionId = sessionController.getActiveSessionId() ?: return OperationResult.error("No active session")

        // Prevent concurrent rounds
        if (_isRoundInProgress.value) {
            LogManager.aiSession("AI round already in progress, rejecting concurrent call", "WARN")
            return OperationResult.error("AI round already in progress")
        }

        LogManager.aiSession("AIRoundExecutor.executeAIRound($reason) for session $sessionId", "DEBUG")

        _isRoundInProgress.value = true

        return withContext(Dispatchers.IO) {
            try {
                // Update activity timestamp
                sessionController.updateActivityTimestamp()

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
                    scope.launch {
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
                // Process next in queue will be handled by session controller closure
            }
        }
    }

    // ========================================================================================
    // Internal Round Execution
    // ========================================================================================

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
        userInteractionManager.resetInterruptionFlag()

        // 1. Build prompt data
        val promptData = PromptManager.buildPromptData(sessionId, context)

        // Check session still active before first AI call
        if (!isSessionStillActive(sessionId)) {
            LogManager.aiSession("Session stopped before first AI call", "INFO")
            return OperationResult.error(s.shared("ai_error_session_stopped"))
        }

        // 2. Call AI with PromptData (with network check and retry)
        var aiResponse = callAIWithRetry(promptData, sessionType)

        // Clean markdown markers from response immediately (LLMs often wrap JSON in ```json...```)
        // This ensures consistent format throughout the round
        if (aiResponse.success && aiResponse.content.isNotEmpty()) {
            val cleanedContent = aiResponse.content.trim().let { content ->
                val withoutOpening = content.removePrefix("```json").removePrefix("```").trimStart()
                withoutOpening.removeSuffix("```").trimEnd()
            }
            aiResponse = aiResponse.copy(content = cleanedContent)
        }

        // Log token usage stats
        responseParser.logTokenStats(aiResponse)

        if (!aiResponse.success) {
            // Network error or timeout - store NETWORK_ERROR and potentially requeue
            messageStorage.storeNetworkErrorMessage(aiResponse.errorMessage ?: s.shared("ai_error_network_call_failed"), sessionId)

            if (sessionType == SessionType.AUTOMATION) {
                // Requeue AUTOMATION at priority position (would need session controller support)
                // For now, just close session
                sessionController.closeActiveSession()
            }

            return OperationResult.error(aiResponse.errorMessage ?: s.shared("ai_error_network_call_failed"))
        }

        // Check 1: Interruption before storing initial AI response
        if (userInteractionManager.shouldInterruptRound()) {
            LogManager.aiSession("Round interrupted before storing initial AI response", "INFO")
            messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
            return OperationResult.success()
        }

        // 3. Store AI response and get message ID for validation
        val initialAIMessageId = messageStorage.storeAIMessage(aiResponse, sessionId)

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
                messageStorage.storeSessionTimeoutMessage(s.shared("ai_error_session_timeout_automation").format(timeoutMinutes), sessionId)

                // Update session endReason to SESSION_TIMEOUT
                coordinator.processUserAction("ai_sessions.update_end_reason", mapOf(
                    "sessionId" to sessionId,
                    "endReason" to SessionEndReason.INACTIVITY_TIMEOUT.name
                ))

                break
            }

            // Parse AI message for commands
            val parseResult = responseParser.parseAIMessageFromResponse(aiResponse)

            // Check for format errors
            if (parseResult.formatErrors.isNotEmpty()) {
                // Check limit
                if (consecutiveFormatErrors >= limits.maxFormatErrorRetries) {
                    messageStorage.storeLimitReachedMessage(s.shared("ai_limit_format_errors_reached"), sessionId)
                    break
                }

                val errorList = parseResult.formatErrors.joinToString("\n") { "- $it" }
                val errorSummary = s.shared("ai_error_format_errors").format(errorList)
                LogManager.aiSession("Format errors detected: $errorSummary", "WARN")
                messageStorage.storeFormatErrorMessage(errorSummary, sessionId)

                // Check session still active before continuing
                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before format error retry", "INFO")
                    break
                }

                // Continue loop to send error back to AI for correction
                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                responseParser.logTokenStats(aiResponse)
                if (!aiResponse.success) break

                // Check interruption BEFORE storing (ignore response completely)
                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted before storing format error correction response", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                // Store AI response (don't need ID here, validation only on actions)
                messageStorage.storeAIMessage(aiResponse, sessionId)

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

            // PRIORITY 0: Check if AI indicated completion (AUTOMATION only)
            if (aiMessage.completed == true) {
                LogManager.aiSession("AI indicated completion with completed=true flag", "INFO")

                // Update session endReason to COMPLETED
                coordinator.processUserAction("ai_sessions.update_end_reason", mapOf(
                    "sessionId" to sessionId,
                    "endReason" to SessionEndReason.COMPLETED.name
                ))

                // No need for explicit system message, AI's preText already explains completion
                // Session will be deactivated naturally when loop exits
                break
            }

            // 4a. COMMUNICATION MODULE (prioritaire)
            if (aiMessage.communicationModule != null) {
                if (!handleCommunicationModule(aiMessage, sessionId, sessionType)) {
                    break // User cancelled or session stopped
                }

                aiResponse = callAIWithRetry(PromptManager.buildPromptData(sessionId, context), sessionType)
                responseParser.logTokenStats(aiResponse)
                if (!aiResponse.success) break

                // Check interruption BEFORE storing
                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted before storing communication module response", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                messageStorage.storeAIMessage(aiResponse, sessionId)
                totalRoundtrips++
                continue
            }

            // 4b. DATA COMMANDS (queries)
            if (aiMessage.dataCommands != null && aiMessage.dataCommands.isNotEmpty()) {
                if (consecutiveDataQueries >= limits.maxDataQueryIterations) {
                    messageStorage.storeLimitReachedMessage(s.shared("ai_limit_data_queries_reached"), sessionId)
                    break
                }

                val dataSystemMessage = messageStorage.executeDataCommands(aiMessage.dataCommands)
                messageStorage.storeSystemMessage(dataSystemMessage, sessionId)

                // Check session still active before continuing
                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before data query retry", "INFO")
                    break
                }

                // Check interruption after executing data commands
                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted after data commands execution", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                val newPromptData = PromptManager.buildPromptData(sessionId, context)
                aiResponse = callAIWithRetry(newPromptData, sessionType)
                responseParser.logTokenStats(aiResponse)
                if (!aiResponse.success) break

                // Check interruption BEFORE storing
                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted before storing data query response", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                messageStorage.storeAIMessage(aiResponse, sessionId)

                consecutiveDataQueries++
                consecutiveActionRetries = 0  // Reset
                totalRoundtrips++
                continue
            }

            // 4c. ACTION COMMANDS (mutations)
            if (aiMessage.actionCommands != null && aiMessage.actionCommands.isNotEmpty()) {
                val continueLoop = handleActionCommands(
                    aiMessage,
                    sessionId,
                    sessionType,
                    limits,
                    initialAIMessageId,
                    consecutiveActionRetries
                )

                if (continueLoop.first) {
                    // Keep control - reload aiResponse and continue
                    aiResponse = callAIWithRetry(PromptManager.buildPromptData(sessionId, context), sessionType)
                    responseParser.logTokenStats(aiResponse)
                    if (!aiResponse.success) break

                    if (userInteractionManager.shouldInterruptRound()) {
                        LogManager.aiSession("Round interrupted before storing keepControl continuation response", "INFO")
                        messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                        break
                    }

                    messageStorage.storeAIMessage(aiResponse, sessionId)

                    // Reset consecutive counters (new cycle)
                    consecutiveActionRetries = 0
                    consecutiveDataQueries = 0
                    totalRoundtrips++
                    continue
                } else if (continueLoop.second > 0) {
                    // Retry - reload aiResponse and continue
                    consecutiveActionRetries = continueLoop.second
                    aiResponse = callAIWithRetry(PromptManager.buildPromptData(sessionId, context), sessionType)
                    responseParser.logTokenStats(aiResponse)
                    if (!aiResponse.success) break

                    if (userInteractionManager.shouldInterruptRound()) {
                        LogManager.aiSession("Round interrupted before storing action retry response", "INFO")
                        messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                        break
                    }

                    messageStorage.storeAIMessage(aiResponse, sessionId)

                    consecutiveDataQueries = 0  // Reset
                    totalRoundtrips++
                    continue
                } else {
                    // Normal end or limit reached
                    break
                }
            }

            // No commands: For AUTOMATION, send reminder to continue or complete
            if (sessionType == SessionType.AUTOMATION) {
                LogManager.aiSession("AUTOMATION: No commands in AI response, sending continuation reminder", "DEBUG")

                messageStorage.storeSystemMessage(
                    SystemMessage(
                        type = SystemMessageType.DATA_ADDED,
                        commandResults = emptyList(),
                        summary = s.shared("ai_automation_continue_reminder"),
                        formattedData = null
                    ),
                    sessionId
                )

                // Check session still active
                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before continuation reminder", "INFO")
                    break
                }

                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted before sending continuation reminder", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                // Continue loop to send reminder to AI
                aiResponse = callAIWithRetry(PromptManager.buildPromptData(sessionId, context), sessionType)
                responseParser.logTokenStats(aiResponse)
                if (!aiResponse.success) break

                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted before storing continuation response", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    break
                }

                messageStorage.storeAIMessage(aiResponse, sessionId)
                totalRoundtrips++
                continue
            }

            // CHAT: No commands, end loop normally
            break
        }

        if (totalRoundtrips >= limits.maxAutonomousRoundtrips) {
            messageStorage.storeLimitReachedMessage(s.shared("ai_limit_total_roundtrips_reached"), sessionId)
        }

        LogManager.aiSession("AI round completed successfully", "INFO")
        return OperationResult.success()
    }

    // ========================================================================================
    // Loop Handlers
    // ========================================================================================

    /**
     * Handle communication module
     * Returns false if user cancelled or session stopped, true to continue
     */
    private suspend fun handleCommunicationModule(
        aiMessage: AIMessage,
        sessionId: String,
        sessionType: SessionType
    ): Boolean {
        val module = aiMessage.communicationModule ?: return true

        // 1. Store module as text message for UI display
        val moduleText = module.toText(context)
        messageStorage.storeCommunicationModuleTextMessage(moduleText, sessionId, triggerUIUpdate = false)

        // 2. Create fallback COMMUNICATION_CANCELLED message
        val cancelMessageId = messageStorage.createAndStoreCommunicationCancelledMessage(sessionId, triggerUIUpdate = false)

        // 3. Wait for user response
        val userResponse = if (cancelMessageId != null) {
            userInteractionManager.waitForUserResponse(module, cancelMessageId)
        } else {
            userInteractionManager.waitForUserResponse(module, "")
        }

        // Check if user cancelled
        if (userResponse == null) {
            messageStorage.updateMessagesFlow(sessionId)
            LogManager.aiSession("User cancelled communication module", "INFO")
            return false
        }

        // User responded - format and store
        val formattedResponse = "${s.shared("ai_module_response_prefix")} $userResponse"
        coordinator.processUserAction("ai_sessions.create_message", mapOf(
            "sessionId" to sessionId,
            "sender" to MessageSender.USER.name,
            "textContent" to formattedResponse,
            "timestamp" to System.currentTimeMillis()
        ))

        messageStorage.updateMessagesFlow(sessionId)

        // Check session still active
        return isSessionStillActive(sessionId)
    }

    /**
     * Handle action commands
     * Returns pair: (keepControl: Boolean, retryCounter: Int)
     * - keepControl true = continue with new AI response
     * - retryCounter > 0 = retry with counter value
     * - both false/0 = end loop
     */
    private suspend fun handleActionCommands(
        aiMessage: AIMessage,
        sessionId: String,
        sessionType: SessionType,
        limits: SessionLimits,
        initialAIMessageId: String?,
        consecutiveActionRetries: Int
    ): Pair<Boolean, Int> {
        val actions = aiMessage.actionCommands ?: return Pair(false, 0)

        // VALIDATION RESOLUTION
        val validationResolver = ValidationResolver(context)
        val currentAIMessageId = initialAIMessageId ?: ""

        val validationResult = validationResolver.shouldValidate(
            actions = actions,
            sessionId = sessionId,
            aiMessageId = currentAIMessageId,
            aiRequestedValidation = aiMessage.validationRequest == true
        )

        when (validationResult) {
            is ValidationResult.RequiresValidation -> {
                LogManager.aiSession("ValidationResult.RequiresValidation - ${validationResult.context.verbalizedActions.size} actions", "DEBUG")

                // Create fallback message
                val cancelMessageId = messageStorage.createAndStoreValidationCancelledMessage(sessionId, triggerUIUpdate = false)

                // Wait for validation
                val validated = if (cancelMessageId != null) {
                    userInteractionManager.waitForUserValidation(validationResult.context, cancelMessageId)
                } else {
                    userInteractionManager.waitForUserValidation(validationResult.context, "")
                }

                if (!validated) {
                    messageStorage.updateMessagesFlow(sessionId)
                    LogManager.aiSession("User refused validation", "INFO")
                    return Pair(false, 0)
                }
            }
            ValidationResult.NoValidation -> {
                LogManager.aiSession("ValidationResult.NoValidation - continuing directly", "DEBUG")
            }
        }

        // Execute actions
        val actionSystemMessage = messageStorage.executeActionCommands(actions)
        messageStorage.storeSystemMessage(actionSystemMessage, sessionId)

        // Check if all actions succeeded
        val allSuccess = actionSystemMessage.commandResults.all { it.status == CommandStatus.SUCCESS }

        if (allSuccess) {
            // Store postText if present
            if (aiMessage.postText != null && aiMessage.postText.isNotEmpty()) {
                messageStorage.storePostTextMessage(aiMessage.postText, sessionId)
            }

            // Check if AI wants to keep control OR if AUTOMATION (always continue for AUTOMATION)
            val shouldKeepControl = aiMessage.keepControl == true || sessionType == SessionType.AUTOMATION

            if (shouldKeepControl) {
                if (sessionType == SessionType.AUTOMATION) {
                    LogManager.aiSession("AUTOMATION mode: continuing autonomous loop after successful actions", "DEBUG")
                } else {
                    LogManager.aiSession("AI requested keepControl, continuing autonomous loop", "DEBUG")
                }

                if (!isSessionStillActive(sessionId)) {
                    LogManager.aiSession("Session stopped before continuation", "INFO")
                    return Pair(false, 0)
                }

                if (userInteractionManager.shouldInterruptRound()) {
                    LogManager.aiSession("Round interrupted before continuation", "INFO")
                    messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                    return Pair(false, 0)
                }

                return Pair(true, 0) // Keep control
            } else {
                return Pair(false, 0) // Normal success end (CHAT only)
            }
        } else {
            // Failures - retry
            if (consecutiveActionRetries >= limits.maxActionRetries) {
                messageStorage.storeLimitReachedMessage(s.shared("ai_limit_action_retries_reached"), sessionId)
                return Pair(false, 0)
            }

            if (!isSessionStillActive(sessionId)) {
                LogManager.aiSession("Session stopped before action retry", "INFO")
                return Pair(false, 0)
            }

            if (userInteractionManager.shouldInterruptRound()) {
                LogManager.aiSession("Round interrupted after action commands execution", "INFO")
                messageStorage.storeInterruptedMessage(s.shared("ai_status_interrupted"), sessionId)
                return Pair(false, 0)
            }

            return Pair(false, consecutiveActionRetries + 1) // Retry
        }
    }

    // ========================================================================================
    // Helpers
    // ========================================================================================

    /**
     * Call AI with retry logic for AUTOMATION (infinite retries with 30s delay), network checks for both
     */
    private suspend fun callAIWithRetry(promptData: PromptData, sessionType: SessionType): AIResponse {
        val sessionId = sessionController.getActiveSessionId()

        if (sessionType == SessionType.AUTOMATION && sessionId != null) {
            // AUTOMATION: Infinite retries with network check loop
            while (true) {
                // Check network availability
                if (!com.assistant.core.utils.NetworkUtils.isNetworkAvailable(context)) {
                    LogManager.aiSession("Network unavailable for AUTOMATION, entering wait loop", "WARN")

                    // Update session state to WAITING_NETWORK
                    coordinator.processUserAction("ai_sessions.update_state", mapOf(
                        "sessionId" to sessionId,
                        "state" to SessionState.WAITING_NETWORK.name
                    ))

                    // Update lastNetworkErrorTime
                    coordinator.processUserAction("ai_sessions.update_last_network_error_time", mapOf(
                        "sessionId" to sessionId,
                        "timestamp" to System.currentTimeMillis()
                    ))

                    // Wait 30 seconds before retry
                    delay(30_000)
                    continue // Loop back to network check
                }

                // Network available - reset state to PROCESSING
                coordinator.processUserAction("ai_sessions.update_state", mapOf(
                    "sessionId" to sessionId,
                    "state" to SessionState.PROCESSING.name
                ))

                // Try AI call
                val response = aiClient.query(promptData)
                if (response.success) {
                    return response
                }

                // AI call failed (not network issue) - log and retry after delay
                LogManager.aiSession("AI call failed for AUTOMATION: ${response.errorMessage}", "WARN")

                // Update lastNetworkErrorTime if error seems network-related
                coordinator.processUserAction("ai_sessions.update_last_network_error_time", mapOf(
                    "sessionId" to sessionId,
                    "timestamp" to System.currentTimeMillis()
                ))

                // Wait 30 seconds before retry
                delay(30_000)
                // Continue infinite loop
            }
        } else {
            // CHAT: Single attempt with network check
            if (!com.assistant.core.utils.NetworkUtils.isNetworkAvailable(context)) {
                val errorMsg = s.shared("ai_error_network_unavailable")
                LogManager.aiSession(errorMsg, "WARN")
                return AIResponse(success = false, content = "", errorMessage = errorMsg)
            }

            return aiClient.query(promptData)
        }
    }

    /**
     * Check if session is still active (not closed by user or system)
     */
    private fun isSessionStillActive(sessionId: String): Boolean {
        return sessionController.getActiveSessionId() == sessionId
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
            SessionType.SEED -> {
                // SEED sessions are never executed, they serve as templates only
                throw IllegalStateException("Cannot execute AI round for SEED session type")
            }
        }
    }

    /**
     * Check if session is inactive for real reason (not network-related)
     * Used to distinguish real inactivity from legitimate network wait
     *
     * @param session The session to check
     * @param thresholdMillis Inactivity threshold in milliseconds
     * @return true if inactive for real reason, false if network-related or not yet inactive
     */
    private fun isInactiveForRealReason(session: AISession, thresholdMillis: Long): Boolean {
        val now = System.currentTimeMillis()

        // Find time of last command execution (DATA_ADDED or ACTIONS_EXECUTED)
        val lastCommandTime = session.messages
            .filter { msg ->
                val type = msg.systemMessage?.type
                type == SystemMessageType.DATA_ADDED || type == SystemMessageType.ACTIONS_EXECUTED
            }
            .maxOfOrNull { it.timestamp } ?: session.createdAt

        val inactivityDuration = now - lastCommandTime

        // Not yet inactive
        if (inactivityDuration < thresholdMillis) {
            return false
        }

        // Inactive for > threshold, but is it due to network issues?
        return when {
            // Currently waiting for network → legitimate wait, not real inactivity
            session.state == SessionState.WAITING_NETWORK -> false

            // Recent network error (within threshold) → legitimate wait, not real inactivity
            session.lastNetworkErrorTime != null &&
                (now - session.lastNetworkErrorTime!!) < thresholdMillis -> false

            // No network issues → real inactivity
            else -> true
        }
    }
}

/**
 * Session limits configuration
 */
data class SessionLimits(
    val maxDataQueryIterations: Int,
    val maxActionRetries: Int,
    val maxFormatErrorRetries: Int,
    val maxAutonomousRoundtrips: Int
)
