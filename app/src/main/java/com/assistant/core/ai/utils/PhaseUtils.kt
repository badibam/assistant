package com.assistant.core.ai.utils

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.assistant.core.ai.domain.Phase
import com.assistant.core.strings.Strings

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
            Phase.EXECUTING_DATA_QUERIES -> s.shared("ai_phase_executing_queries")
            Phase.EXECUTING_ACTIONS -> s.shared("ai_phase_executing_actions")
            Phase.WAITING_COMPLETION_CONFIRMATION -> s.shared("ai_phase_waiting_completion")
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
     * Get semantic color for SessionEndReason
     * Used for status display in AutomationScreen ExecutionCard
     *
     * Color mapping:
     * - primary (blue): COMPLETED
     * - tertiary (orange/warning): LIMIT_REACHED, NETWORK_ERROR, SUSPENDED, null (interrupted)
     * - error (red): ERROR, CANCELLED, TIMEOUT
     *
     * @param endReason SessionEndReason string (nullable)
     * @param colorScheme Material theme color scheme
     * @return Color for the end reason
     */
    fun endReasonToColor(endReason: String?, colorScheme: ColorScheme): Color {
        return when (endReason?.uppercase()) {
            "COMPLETED" -> colorScheme.primary
            "LIMIT_REACHED", "NETWORK_ERROR", "SUSPENDED", null -> colorScheme.tertiary
            "ERROR", "CANCELLED", "TIMEOUT" -> colorScheme.error
            else -> colorScheme.tertiary  // Default to warning
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
 * Extension function: Get color for SessionEndReason string
 */
fun String?.toEndReasonColor(colorScheme: ColorScheme): Color {
    return PhaseUtils.endReasonToColor(this, colorScheme)
}
