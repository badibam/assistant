package com.assistant.core.ai.scheduling

import android.content.Context
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import kotlinx.serialization.json.Json

/**
 * Automation Scheduler - Pure calculation helper
 *
 * Responsibilities:
 * - Calculate which automation should execute next (if any)
 * - Detect incomplete sessions to resume (crash, network error, suspended)
 * - Calculate next scheduled execution time from schedule configuration
 *
 * NO notion of slot/queue - just pure calculation
 * Called by AISessionController.tick() when slot is free and queue is empty
 */
class AutomationScheduler(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val database = AppDatabase.getDatabase(context)
    private val aiDao = database.aiDao()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get next automation session to execute
     * Returns Resume/Create/None based on enabled automations and their states
     *
     * Logic:
     * 1. For each enabled automation with schedule:
     *    - Check for incomplete session (crash/network/suspended) → RESUME
     *    - Else calculate next execution time from last completed → CREATE if time passed
     * 2. Sort all candidates by scheduledTime ASC (oldest first)
     * 3. Return first candidate or None
     */
    suspend fun getNextSession(): NextSession {
        try {
            LogManager.aiSession("AutomationScheduler: Calculating next session to execute", "DEBUG")

            // Get all enabled automations with schedule
            val enabledAutomations = aiDao.getAllEnabledAutomations()
                .filter { !it.scheduleJson.isNullOrEmpty() }

            if (enabledAutomations.isEmpty()) {
                LogManager.aiSession("AutomationScheduler: No enabled automations with schedule", "DEBUG")
                return NextSession.None
            }

            LogManager.aiSession("AutomationScheduler: Found ${enabledAutomations.size} enabled automations with schedule", "DEBUG")

            // Build list of candidates (resume or create)
            val candidates = mutableListOf<ScheduleCandidate>()

            for (automation in enabledAutomations) {
                // Check for incomplete session (to resume)
                val incompleteSession = aiDao.getIncompleteAutomationSession(automation.id)

                if (incompleteSession != null) {
                    // Session to resume (crash, network error, or suspended)
                    LogManager.aiSession(
                        "AutomationScheduler: Found incomplete session for automation ${automation.id} " +
                        "(endReason=${incompleteSession.endReason}, scheduled=${incompleteSession.scheduledExecutionTime})",
                        "INFO"
                    )
                    candidates.add(
                        ScheduleCandidate(
                            automationId = automation.id,
                            scheduledTime = incompleteSession.scheduledExecutionTime ?: System.currentTimeMillis(),
                            action = CandidateAction.RESUME,
                            sessionId = incompleteSession.id
                        )
                    )
                    continue  // Skip to next automation (only one candidate per automation)
                }

                // No incomplete session → calculate next scheduled execution
                val schedule = try {
                    json.decodeFromString<ScheduleConfig>(automation.scheduleJson!!)
                } catch (e: Exception) {
                    LogManager.aiSession("AutomationScheduler: Failed to parse schedule for automation ${automation.id}: ${e.message}", "ERROR", e)
                    continue
                }

                // Get last completed session to calculate next execution time
                val lastCompletedSession = aiDao.getLastCompletedAutomationSession(automation.id)

                // Calculate next execution time from last completed (or from now if no history)
                val fromTimestamp = lastCompletedSession?.scheduledExecutionTime ?: System.currentTimeMillis()
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
                        "AutomationScheduler: Automation ${automation.id} is due (next=$nextExecutionTime, now=$now)",
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
                        "AutomationScheduler: Automation ${automation.id} not yet due (next=$nextExecutionTime, now=$now)",
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
                        "(scheduled=${first.scheduledTime}${if (first.sessionId != null) ", sessionId=${first.sessionId}" else ""})",
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
 * Tells AISessionController what to do (if anything)
 */
sealed class NextSession {
    /**
     * Resume existing incomplete session (crash, network error, or user pause)
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
