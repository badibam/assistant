package com.assistant.core.ai.scheduling

import android.content.Context
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automation Scheduler - Pure calculation helper
 *
 * Responsibilities:
 * - Calculate which automation should execute next (if any)
 * - Detect incomplete sessions to resume (crash, network error, suspended)
 * - Calculate next scheduled execution time from schedule configuration
 *
 * NO notion of slot/queue - just pure calculation
 * Called by AISessionScheduler when slot is free and queue is empty
 */
class AutomationScheduler(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val database = AppDatabase.getDatabase(context)
    private val aiDao = database.aiDao()
    private val json = Json { ignoreUnknownKeys = true }

    // Date formatter for logs (HH:mm:ss dd/MM/yyyy)
    private val dateFormatter = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())

    /**
     * Format timestamp to human-readable date for logs
     */
    private fun formatTimestamp(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    /**
     * Get next automation session to execute
     * Returns Resume/Create/None based on enabled automations and their states
     *
     * Logic:
     * 1. For each enabled automation (with or without schedule):
     *    - Check for incomplete session (crash/network/suspended/manual) → RESUME
     * 2. For each enabled automation WITH schedule:
     *    - Calculate next execution time from last completed → CREATE if time passed
     * 3. Sort all candidates by scheduledTime ASC (oldest first)
     * 4. Return first candidate or None
     */
    suspend fun getNextSession(): NextSession {
        try {
            LogManager.aiSession("AutomationScheduler: Calculating next session to execute", "DEBUG")

            // Get ALL enabled automations (with or without schedule)
            val allEnabledAutomations = aiDao.getAllEnabledAutomations()

            if (allEnabledAutomations.isEmpty()) {
                LogManager.aiSession("AutomationScheduler: No enabled automations", "DEBUG")
                return NextSession.None
            }

            // Build list of candidates (resume or create)
            val candidates = mutableListOf<ScheduleCandidate>()

            // Step 1: Check ALL enabled automations for incomplete sessions (MANUAL or SCHEDULED)
            for (automation in allEnabledAutomations) {
                // Check for incomplete session (to resume)
                val incompleteSession = aiDao.getIncompleteAutomationSession(automation.id)

                if (incompleteSession != null) {
                    // Session to resume (crash, network error, or suspended)
                    // scheduledExecutionTime must not be null for AUTOMATION sessions
                    if (incompleteSession.scheduledExecutionTime == null) {
                        LogManager.aiSession(
                            "AutomationScheduler: Skipping incomplete session ${incompleteSession.id} - scheduledExecutionTime is null (data error)",
                            "ERROR"
                        )
                        continue
                    }

                    LogManager.aiSession(
                        "AutomationScheduler: Found incomplete session for automation ${automation.id} " +
                        "(endReason=${incompleteSession.endReason}, scheduled=${formatTimestamp(incompleteSession.scheduledExecutionTime)})",
                        "INFO"
                    )
                    candidates.add(
                        ScheduleCandidate(
                            automationId = automation.id,
                            scheduledTime = incompleteSession.scheduledExecutionTime,
                            action = CandidateAction.RESUME,
                            sessionId = incompleteSession.id
                        )
                    )
                    // Continue to next automation (only one candidate per automation)
                }
            }

            // Step 2: For automations WITH schedule, calculate next scheduled execution
            // Only if no incomplete session was found above
            val automationsWithIncomplete = candidates.map { it.automationId }.toSet()
            val automationsWithSchedule = allEnabledAutomations
                .filter { !it.scheduleJson.isNullOrEmpty() && !automationsWithIncomplete.contains(it.id) }

            LogManager.aiSession(
                "AutomationScheduler: Found ${candidates.size} sessions to resume, checking ${automationsWithSchedule.size} scheduled automations",
                "DEBUG"
            )

            for (automation in automationsWithSchedule) {
                // Parse schedule
                val schedule = try {
                    json.decodeFromString<ScheduleConfig>(automation.scheduleJson!!)
                } catch (e: Exception) {
                    LogManager.aiSession("AutomationScheduler: Failed to parse schedule for automation ${automation.id}: ${e.message}", "ERROR", e)
                    continue
                }

                // Get last completed session to calculate next execution time
                val lastCompletedSession = aiDao.getLastCompletedAutomationSession(automation.id)

                // Calculate next execution time
                // Priority: last completed execution > schedule startDate > now
                val fromTimestamp = lastCompletedSession?.scheduledExecutionTime
                    ?: schedule.startDate
                    ?: System.currentTimeMillis()

                LogManager.aiSession(
                    "AutomationScheduler: Calculating next execution for automation ${automation.id} " +
                    "(lastCompleted=${lastCompletedSession?.scheduledExecutionTime?.let { formatTimestamp(it) }}, " +
                    "startDate=${schedule.startDate?.let { formatTimestamp(it) }}, " +
                    "fromTimestamp=${formatTimestamp(fromTimestamp)})",
                    "DEBUG"
                )

                val nextExecutionTime = ScheduleCalculator.calculateNextExecution(
                    pattern = schedule.pattern,
                    timezone = schedule.timezone,
                    startDate = schedule.startDate,
                    endDate = schedule.endDate,
                    fromTimestamp = fromTimestamp
                )

                if (nextExecutionTime == null) {
                    LogManager.aiSession("AutomationScheduler: No more executions for automation ${automation.id} (schedule ended or invalid)", "DEBUG")
                    continue
                }

                // Check if execution time has passed
                val now = System.currentTimeMillis()
                if (nextExecutionTime <= now) {
                    LogManager.aiSession(
                        "AutomationScheduler: Automation ${automation.id} is due (next=${formatTimestamp(nextExecutionTime)}, now=${formatTimestamp(now)})",
                        "INFO"
                    )
                    candidates.add(
                        ScheduleCandidate(
                            automationId = automation.id,
                            scheduledTime = nextExecutionTime,
                            action = CandidateAction.CREATE,
                            sessionId = null
                        )
                    )
                } else {
                    LogManager.aiSession(
                        "AutomationScheduler: Automation ${automation.id} not yet due (next=${formatTimestamp(nextExecutionTime)}, now=${formatTimestamp(now)})",
                        "DEBUG"
                    )
                }
            }

            // Sort candidates by scheduled time (oldest first)
            val sortedCandidates = candidates.sortedBy { it.scheduledTime }

            // Return first candidate or None
            return when {
                sortedCandidates.isEmpty() -> {
                    LogManager.aiSession("AutomationScheduler: No candidates to execute", "DEBUG")
                    NextSession.None
                }
                else -> {
                    val first = sortedCandidates.first()
                    LogManager.aiSession(
                        "AutomationScheduler: Selected ${first.action} for automation ${first.automationId} " +
                        "(scheduled=${formatTimestamp(first.scheduledTime)}${if (first.sessionId != null) ", sessionId=${first.sessionId}" else ""})",
                        "INFO"
                    )
                    when (first.action) {
                        CandidateAction.RESUME -> NextSession.Resume(first.sessionId!!)
                        CandidateAction.CREATE -> NextSession.Create(first.automationId, first.scheduledTime)
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.aiSession("AutomationScheduler: Error calculating next session: ${e.message}", "ERROR", e)
            return NextSession.None
        }
    }

    /**
     * Internal candidate for scheduling decision
     */
    private data class ScheduleCandidate(
        val automationId: String,
        val scheduledTime: Long,
        val action: CandidateAction,
        val sessionId: String?  // For RESUME, null for CREATE
    )

    /**
     * Internal action type
     */
    private enum class CandidateAction {
        RESUME,  // Resume existing incomplete session
        CREATE   // Create new scheduled session
    }
}

/**
 * Result of getNextSession() calculation
 * Tells AISessionScheduler what to do (if anything)
 */
sealed class NextSession {
    /**
     * Resume existing incomplete session (crash, network error)
     * NO system message should be added - transparent resume
     */
    data class Resume(val sessionId: String) : NextSession()

    /**
     * Create new scheduled session for this automation
     */
    data class Create(
        val automationId: String,
        val scheduledFor: Long  // Timestamp for scheduledExecutionTime
    ) : NextSession()

    /**
     * Nothing to execute right now
     */
    object None : NextSession()
}
