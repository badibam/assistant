package app.treelune.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.data.Automation
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.ai.scheduling.AutomationScheduler
import app.treelune.core.fields.settings.scheduleSummary
import app.treelune.core.ai.scheduling.NextExecution
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AutomationCard - Display automation in zone list
 *
 * Shows:
 * - Name + Enabled status
 * - Trigger type (manual/schedule/triggers/hybrid)
 * - Next execution time (if scheduled)
 * - Queued status badge (if in queue)
 * - Actions: Test + View + Edit + Toggle enabled + Cancel (if queued)
 *
 * Usage: In ZoneScreen automation section
 */
@Composable
fun AutomationCard(
    automation: Automation,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onView: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onStartChat: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Next execution, computed by the scheduler itself so the card shows what will actually run
    var nextExecution by remember { mutableStateOf<NextExecution?>(null) }
    LaunchedEffect(automation.id, automation.isEnabled, automation.schedule, automation.updatedAt) {
        nextExecution = if (automation.isEnabled && automation.schedule != null) {
            withContext(Dispatchers.IO) {
                AutomationScheduler(context).getNextExecutionForAutomation(automation.id)
            }
        } else {
            null
        }
    }

    // Observe queued sessions to detect if this automation is queued
    val queuedSessions by AIOrchestrator.queuedSessions.collectAsState()

    // Need to load session from DB to check automationId (QueuedSession only has sessionId)
    var queuedAutomationSession by remember { mutableStateOf<app.treelune.core.ai.scheduling.QueuedSession?>(null) }

    LaunchedEffect(queuedSessions, automation.id) {
        // Find queued AUTOMATION sessions and check if one belongs to this automation
        val coordinator = app.treelune.core.coordinator.Coordinator(context)

        for (queued in queuedSessions) {
            if (queued.sessionType == SessionType.AUTOMATION) {
                // Load session from DB to get automationId
                val result = coordinator.processUserAction("ai_sessions.get", mapOf("id" to queued.sessionId))
                if (result.status == app.treelune.core.commands.CommandStatus.SUCCESS) {
                    val sessionAutomationId = result.data?.get("automation_id") as? String
                    if (sessionAutomationId == automation.id) {
                        queuedAutomationSession = queued
                        break
                    }
                }
            }
        }

        // Reset if not found
        if (queuedSessions.none { it.sessionId == queuedAutomationSession?.sessionId }) {
            queuedAutomationSession = null
        }
    }

    UI.Card(
        type = CardType.DEFAULT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            // Header row: Name + Enabled toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.M),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = automation.name,
                        type = TextType.SUBTITLE
                    )
                }

                // Enabled toggle
                UI.BooleanField(
                    label = "",
                    value = automation.isEnabled,
                    onValueChange = onToggleEnabled
                )
            }

            // Trigger type and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator text
                val statusText = when {
                    !automation.isEnabled -> s.shared("label_disabled")
                    automation.schedule == null && automation.triggerIds.isEmpty() -> s.shared("automation_status_manual")
                    automation.schedule != null && automation.triggerIds.isNotEmpty() -> {
                        // Hybrid: schedule + triggers
                        val triggerCount = automation.triggerIds.size
                        "${scheduleSummary(automation.schedule!!, s)} + ${s.shared("automation_triggers_count").format(triggerCount)}"
                    }
                    automation.schedule != null -> scheduleSummary(automation.schedule!!, s)
                    else -> s.shared("automation_triggers_count").format(automation.triggerIds.size)
                }

                UI.Text(
                    text = statusText,
                    type = TextType.CAPTION
                )
            }

            // Next execution time (if scheduled and enabled)
            nextExecution?.let {
                UI.Text(
                    text = it.message,
                    type = TextType.CAPTION
                )
            }

            // Queued badge (if automation is in queue)
            queuedAutomationSession?.let { queued ->
                // Calculate position in queue (1-indexed for display)
                val position = queuedSessions.indexOfFirst { it.sessionId == queued.sessionId } + 1
                UI.Text(
                    text = s.shared("ai_automation_queued_badge").format(position),
                    type = TextType.CAPTION
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel execution button (if queued)
                if (queuedAutomationSession != null) {
                    val scope = rememberCoroutineScope()
                    UI.Button(
                        type = ButtonType.DANGER,
                        size = Size.S,
                        onClick = {
                            scope.launch {
                                AIOrchestrator.cancelQueuedSession(queuedAutomationSession!!.sessionId)
                            }
                        }
                    ) {
                        UI.Text(
                            text = s.shared("ai_automation_cancel_execution"),
                            type = TextType.BODY
                        )
                    }
                } else {
                    // Test button (manual execution) - only shown if not queued
                    UI.ActionButton(
                        action = ButtonAction.START,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onTest
                    )

                    // Chat button (start chat with SEED pre-fill)
                    UI.ActionButton(
                        action = ButtonAction.AI_CHAT,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onStartChat
                    )
                }

                // View button (executions history)
                UI.ActionButton(
                    action = ButtonAction.VIEW,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onView
                )

                // Edit button
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onEdit
                )
            }
        }
    }
}
