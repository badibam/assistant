package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.utils.AIFormatUtils
import com.assistant.core.ai.utils.PhaseUtils
import com.assistant.core.ai.utils.toDisplayString
import com.assistant.core.ai.utils.toEndReasonDisplayString
import com.assistant.core.ai.utils.toEndReasonColor
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils
import com.assistant.core.utils.FormatUtils

/**
 * ExecutionCard - Display automation execution summary
 *
 * Shows:
 * - Scheduled execution time
 * - Actual start time (createdAt)
 * - Phase (current/final) with colored status indicator
 * - End reason (if ended) with colored status indicator
 * - Duration (lastActivity - createdAt)
 * - Total roundtrips
 * - Token usage (formatted)
 * - Cost (formatted)
 * - VIEW button for detail navigation
 *
 * Layout: 2 columns with data rows, VIEW button at bottom
 *
 * Usage: In AutomationScreen execution list
 */
@Composable
fun ExecutionCard(
    sessionId: String,
    scheduledExecutionTime: Long?,
    createdAt: Long,
    phase: Phase,
    endReason: String?,
    duration: Long,
    totalRoundtrips: Int,
    totalTokens: Int,
    cost: Double?,
    livePhase: Phase? = null, // Real-time phase from AIState if this session is active
    onViewClick: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val colorScheme = MaterialTheme.colorScheme

    // Use live phase if provided, otherwise use stored phase
    val displayPhase = livePhase ?: phase

    // Get status color for end reason (or phase if still running)
    val statusColor = endReason.toEndReasonColor(colorScheme)

    UI.Card(
        type = CardType.DEFAULT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Scheduled time | Status (EndReason if exists, else Phase)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Scheduled execution time
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = if (scheduledExecutionTime != null) {
                            "${s.shared("automation_scheduled_label")}: ${DateUtils.formatFullDateTime(scheduledExecutionTime)}"
                        } else {
                            s.shared("automation_manual_execution")
                        },
                        type = TextType.CAPTION
                    )
                }

                // Right column: EndReason if exists, else Phase (both with status indicator)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.StatusIndicator(color = statusColor, size = 8.dp)
                    UI.Text(
                        text = if (endReason != null) {
                            endReason.toEndReasonDisplayString(context)
                        } else {
                            displayPhase.toDisplayString(context)
                        },
                        type = TextType.CAPTION
                    )
                }
            }

            // Row 2: Started time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Started time
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = "${s.shared("automation_started_label")}: ${DateUtils.formatFullDateTime(createdAt)}",
                        type = TextType.CAPTION
                    )
                }
            }

            Divider()

            // Row 3: Duration | Roundtrips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Duration
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = "${s.shared("automation_duration_label")}: ${FormatUtils.formatDuration(duration)}",
                        type = TextType.CAPTION
                    )
                }

                // Right column: Roundtrips
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    UI.Text(
                        text = s.shared("automation_roundtrips_label").format(totalRoundtrips),
                        type = TextType.CAPTION
                    )
                }
            }

            // Row 4: Tokens | Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Tokens
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = "${AIFormatUtils.formatTokenCount(totalTokens)} ${s.shared("automation_tokens_label")}",
                        type = TextType.CAPTION
                    )
                }

                // Right column: Cost
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    UI.Text(
                        text = AIFormatUtils.formatCost(cost),
                        type = TextType.CAPTION
                    )
                }
            }

            Divider()

            // Row 5: VIEW button (right-aligned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                UI.ActionButton(
                    action = ButtonAction.VIEW,
                    display = ButtonDisplay.LABEL,
                    size = Size.S,
                    onClick = onViewClick
                )
            }
        }
    }
}
