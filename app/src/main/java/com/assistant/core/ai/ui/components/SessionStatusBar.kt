package com.assistant.core.ai.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.domain.Phase
import com.assistant.core.strings.Strings
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * Status bar displaying current AI session phase.
 *
 * Shows user-friendly phase label based on current phase and session type.
 * Displayed at bottom of AIScreen for constant visibility.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Observes phase from AIState
 * - Translates technical phase to user-friendly label
 * - Different labels for CHAT vs AUTOMATION when relevant
 */
@Composable
fun SessionStatusBar(
    phase: Phase,
    sessionType: SessionType?,
    context: Context
) {
    val s = remember { Strings.`for`(context = context) }

    // Get status text based on phase and session type
    val statusText = getStatusText(phase, sessionType, s)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        UI.Text(
            text = statusText,
            type = TextType.CAPTION
        )
    }
}

/**
 * Get user-friendly status text for current phase.
 *
 * Translates technical phase names to localized, understandable labels.
 * Some phases have different labels depending on session type.
 */
private fun getStatusText(
    phase: Phase,
    sessionType: SessionType?,
    s: com.assistant.core.strings.StringsContext
): String {
    return when (phase) {
        Phase.IDLE -> {
            // Different label for CHAT vs AUTOMATION
            if (sessionType == SessionType.CHAT) {
                s.shared("ai_phase_idle_chat") // "En attente"
            } else {
                s.shared("ai_phase_idle_automation") // "Prêt"
            }
        }

        Phase.CALLING_AI -> {
            s.shared("ai_phase_calling_ai") // "Appel IA..."
        }

        // Group all processing phases under single label
        Phase.EXECUTING_ENRICHMENTS,
        Phase.PARSING_AI_RESPONSE,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.RETRYING_AFTER_FORMAT_ERROR,
        Phase.RETRYING_AFTER_ACTION_FAILURE,
        Phase.WAITING_NETWORK_RETRY -> {
            s.shared("ai_phase_executing") // "Traitement..."
        }

        Phase.PREPARING_CONTINUATION -> {
            s.shared("ai_phase_preparing_continuation") // "Préparation continuation..."
        }

        Phase.WAITING_VALIDATION -> {
            s.shared("ai_phase_waiting_validation") // "En attente de validation"
        }

        Phase.WAITING_COMMUNICATION_RESPONSE,
        Phase.WAITING_COMPLETION_CONFIRMATION -> {
            s.shared("ai_phase_waiting_communication") // "En attente de réponse"
        }

        Phase.INTERRUPTED -> {
            // Interrupted is brief transitional state, show idle label
            if (sessionType == SessionType.CHAT) {
                s.shared("ai_phase_idle_chat")
            } else {
                s.shared("ai_phase_idle_automation")
            }
        }

        Phase.AWAITING_SESSION_CLOSURE -> {
            s.shared("ai_phase_awaiting_closure") // "⏱ Fermeture dans 5s..."
        }

        Phase.CLOSED -> {
            s.shared("ai_phase_closed") // "Session terminée"
        }
    }
}
