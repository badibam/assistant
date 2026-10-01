package com.assistant.core.ai.utils

import android.content.Context
import com.assistant.core.ai.domain.Phase
import com.assistant.core.strings.Strings
import com.assistant.core.ui.StatusColor

/**
 * Utility functions and extensions for AI Phase and SessionEndReason
 * Used by AutomationScreen and other AI UI components for consistent display
 */
object PhaseUtils {

    /**
     * Convert Phase enum to display string using i18n
     *
     * @param phase Phase to convert
     * @param context Android context for strings
     * @return Localized phase display string
     */
    fun phaseToDisplayString(phase: Phase, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (phase) {
            Phase.IDLE -> s.shared("ai_phase_idle")
            Phase.EXECUTING_ENRICHMENTS -> s.shared("ai_phase_executing_enrichments")
            Phase.CALLING_AI -> s.shared("ai_phase_calling_ai")
            Phase.PARSING_AI_RESPONSE -> s.shared("ai_phase_parsing")
            Phase.PREPARING_CONTINUATION -> s.shared("ai_phase_preparing_continuation")
            Phase.WAITING_VALIDATION -> s.shared("ai_phase_waiting_validation")
            Phase.WAITING_COMMUNICATION_RESPONSE -> s.shared("ai_phase_waiting_communication")
            Phase.WAITING_DATA_CONFIRMATION -> s.shared("ai_phase_waiting_data_confirmation")
            Phase.EXECUTING_DATA_QUERIES -> s.shared("ai_phase_executing_queries")
            Phase.EXECUTING_ACTIONS -> s.shared("ai_phase_executing_actions")
            Phase.WAITING_NETWORK_RETRY -> s.shared("ai_phase_waiting_network")
            Phase.RETRYING_AFTER_FORMAT_ERROR -> s.shared("ai_phase_retrying")
            Phase.RETRYING_AFTER_ACTION_FAILURE -> s.shared("ai_phase_retrying")
            Phase.INTERRUPTED -> s.shared("ai_phase_interrupted")
            Phase.AWAITING_SESSION_CLOSURE -> s.shared("ai_phase_awaiting_closure")
            Phase.CLOSED -> s.shared("ai_phase_completed")
        }
    }

    /**
     * Convert SessionEndReason string to display string using i18n
     * SessionEndReason values: COMPLETED, LIMIT_REACHED, TIMEOUT, ERROR, CANCELLED, INTERRUPTED, NETWORK_ERROR, SUSPENDED
     *
     * @param endReason SessionEndReason string (nullable)
     * @param context Android context for strings
     * @return Localized end reason display string, or "Interrompu" for null
     */
    fun endReasonToDisplayString(endReason: String?, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (endReason?.uppercase()) {
            "COMPLETED" -> s.shared("ai_end_reason_completed")
            "LIMIT_REACHED" -> s.shared("ai_end_reason_limit_reached")
            "TIMEOUT" -> s.shared("ai_end_reason_timeout")
            "ERROR" -> s.shared("ai_end_reason_error")
            "CANCELLED" -> s.shared("ai_end_reason_cancelled")
            "INTERRUPTED" -> s.shared("ai_end_reason_interrupted")
            "NETWORK_ERROR" -> s.shared("ai_end_reason_network_error")
            "SUSPENDED" -> s.shared("ai_end_reason_suspended")
            null -> s.shared("ai_end_reason_interrupted")  // null = crash/incomplete
            else -> endReason // Fallback to raw value if unknown
        }
    }

    /**
     * The state a SessionEndReason shows as, for the status mark of an execution (ExecutionCard):
     * success when completed, an error when it failed or was stopped, a warning when it was cut
     * short (a limit, the network, a suspension, or no reason: still running or interrupted).
     */
    fun endReasonToStatus(endReason: String?): StatusColor {
        return when (endReason?.uppercase()) {
            "COMPLETED" -> StatusColor.SUCCESS
            "ERROR", "CANCELLED", "TIMEOUT" -> StatusColor.ERROR
            else -> StatusColor.WARNING
        }
    }
}

/**
 * Extension function: Convert Phase to display string
 */
fun Phase.toDisplayString(context: Context): String {
    return PhaseUtils.phaseToDisplayString(this, context)
}

/**
 * Extension function: Convert SessionEndReason string to display string
 */
fun String?.toEndReasonDisplayString(context: Context): String {
    return PhaseUtils.endReasonToDisplayString(this, context)
}

/**
 * Extension function: the state a SessionEndReason string shows as
 */
fun String?.toEndReasonStatus(): StatusColor {
    return PhaseUtils.endReasonToStatus(this)
}
