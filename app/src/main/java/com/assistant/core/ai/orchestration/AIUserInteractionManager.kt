package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.ai.validation.ValidationContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * AI User Interaction Manager - manages user interactions during AI rounds
 *
 * Responsibilities:
 * - Manage waiting state (validation, communication modules)
 * - Handle user validation resume
 * - Handle user response to communication modules resume
 * - Handle round interruption
 * - Manage continuations for suspended coroutines
 *
 * This class ensures that user interactions can survive UI lifecycle changes
 * by using a singleton-scoped CoroutineScope.
 */
class AIUserInteractionManager(
    private val context: Context,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope,
    private val messageStorage: AIMessageStorage
) {

    // State for user interaction (observable by UI)
    private val _waitingState = MutableStateFlow<WaitingState>(WaitingState.None)
    val waitingState: StateFlow<WaitingState> = _waitingState.asStateFlow()

    // Continuations for user interaction suspension
    // These remain valid across ChatScreen navigation since they're in singleton scope
    private var validationContinuation: Continuation<Boolean>? = null
    private var responseContinuation: Continuation<String?>? = null

    // Round interruption flag (set by user via UI)
    @Volatile
    private var shouldInterruptRound = false

    // ========================================================================================
    // Waiting State Management
    // ========================================================================================

    /**
     * Wait for user validation (suspend until UI calls resumeWithValidation)
     *
     * @param context ValidationContext avec les actions et métadonnées
     * @param cancelMessageId ID of VALIDATION_CANCELLED message to delete if user validates
     * @return true si user valide, false si user refuse
     */
    suspend fun waitForUserValidation(context: ValidationContext, cancelMessageId: String): Boolean =
        suspendCancellableCoroutine { cont ->
            LogManager.aiSession("waitForUserValidation() called - updating WaitingState", "DEBUG")
            LogManager.aiSession("ValidationContext has ${context.verbalizedActions.size} actions", "DEBUG")
            _waitingState.value = WaitingState.WaitingValidation(context, cancelMessageId)
            LogManager.aiSession("WaitingState updated to WaitingValidation", "DEBUG")
            validationContinuation = cont
            LogManager.aiSession("Validation continuation set, suspending coroutine...", "DEBUG")
        }

    /**
     * Wait for user response to communication module (suspend until UI calls resumeWithResponse)
     *
     * @param module Communication module to display
     * @param cancelMessageId ID of COMMUNICATION_CANCELLED message to delete if user responds
     * @return User response text, or null if cancelled
     */
    suspend fun waitForUserResponse(module: CommunicationModule, cancelMessageId: String): String? =
        suspendCancellableCoroutine { cont ->
            _waitingState.value = WaitingState.WaitingResponse(module, cancelMessageId)
            responseContinuation = cont
        }

    // ========================================================================================
    // Resume Functions (called from UI)
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
                    scope.launch {
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
     * @param currentSessionId Current active session ID
     */
    fun resumeWithResponse(response: String?, currentSessionId: String?) {
        // Delete COMMUNICATION_CANCELLED message if user provided a response
        if (response != null) {
            val currentState = _waitingState.value
            if (currentState is WaitingState.WaitingResponse) {
                val cancelMessageId = currentState.cancelMessageId
                if (cancelMessageId.isNotEmpty() && currentSessionId != null) {
                    scope.launch {
                        try {
                            val deleteResult = coordinator.processUserAction("ai_sessions.delete_message", mapOf(
                                "messageId" to cancelMessageId
                            ))
                            if (deleteResult.isSuccess) {
                                LogManager.aiSession("Deleted COMMUNICATION_CANCELLED fallback message: $cancelMessageId", "DEBUG")
                                // Trigger UI update to show text message (CANCELLED is now deleted)
                                messageStorage.updateMessagesFlow(currentSessionId)
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

    // ========================================================================================
    // Interruption Management
    // ========================================================================================

    /**
     * Interrupt active AI round
     * Called from UI when user wants to stop autonomous loops
     * The round will stop after current operation completes
     */
    fun interruptActiveRound() {
        shouldInterruptRound = true
        LogManager.aiSession("User requested round interruption", "INFO")
    }

    /**
     * Check if round should be interrupted
     */
    fun shouldInterruptRound(): Boolean = shouldInterruptRound

    /**
     * Reset interruption flag (called at start of new round)
     */
    fun resetInterruptionFlag() {
        shouldInterruptRound = false
    }

    // ========================================================================================
    // Session Closure Support
    // ========================================================================================

    /**
     * Resume any pending user interactions to unblock suspended coroutines
     * Called when session is closed to prevent hanging coroutines
     */
    fun resumePendingInteractions() {
        if (_waitingState.value is WaitingState.WaitingResponse) {
            responseContinuation?.resume(null)  // Resume with null (cancelled)
            responseContinuation = null
            LogManager.aiSession("Resumed pending communication module with cancellation", "DEBUG")
        }
        if (_waitingState.value is WaitingState.WaitingValidation) {
            validationContinuation?.resume(false)  // Resume with false (refused)
            validationContinuation = null
            LogManager.aiSession("Resumed pending validation with refusal", "DEBUG")
        }
        _waitingState.value = WaitingState.None
    }
}
