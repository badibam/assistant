package com.assistant.core.ai.scheduling

import android.content.Context
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.database.AutomationEntity
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.strings.Strings
import com.assistant.core.utils.FormatUtils
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
 * Called by AISessionScheduler when slot is free and queue is empty
 */
class AutomationScheduler(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val database = AppDatabase.getDatabase(context)
    private val aiDao = database.aiDao()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Format timestamp to human-readable date for logs (HH:mm:ss dd/MM/yyyy)
     */
    private fun formatTimestamp(timestamp: Long): String {
        return com.assistant.core.utils.DateUtils.format(timestamp, "HH:mm:ss dd/MM/yyyy")
    }

    /**
     * CatchUpPolicy.searchStart, plus the log line the skipping deserves.
     *
     * Skipped occurrences leave this line and nothing else: the history is made of sessions, and
     * an empty "skipped" one would be a shape to handle in every screen that reads it.
     */
    private fun catchUpSearchStart(
        automation: AutomationEntity,
        schedule: ScheduleConfig,
        lastCompletedSession: AISessionEntity?,
        now: Long
    ): Long {
        val lastExecutionTime = lastCompletedSession?.scheduledExecutionTime ?: 0L
        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = lastExecutionTime,
            automationUpdatedAt = automation.updatedAt,
            scheduleStartDate = schedule.startDate,
            catchUpWindowMinutes = automation.catchUpWindowMinutes,
            now = now
        )

        if (CatchUpPolicy.windowSkippedOccurrences(
                lastExecutionTime = lastExecutionTime,
                automationUpdatedAt = automation.updatedAt,
                scheduleStartDate = schedule.startDate,
                catchUpWindowMinutes = automation.catchUpWindowMinutes,
                now = now
            )
        ) {
            LogManager.aiSession(
                "AutomationScheduler: Automation ${automation.id} skips what is older than its catch-up window " +
                "of ${automation.catchUpWindowMinutes} min (search starts at ${formatTimestamp(start)})",
                "INFO"
            )
        }

        return start
    }

    /**
     * Get next automation session to execute
     * Returns Resume/Create/None based on enabled automations and their states
     *
     * Logic:
     * 1. Check ALL incomplete sessions (any automation, even disabled) → RESUME
     * 2. For each enabled automation WITH schedule (without incomplete session):
     *    - Calculate next execution time from last completed → CREATE if time passed
     * 3. Sort all candidates by scheduledTime ASC (oldest first)
     * 4. Return first candidate or None
     */
    suspend fun getNextSession(): NextSession {
        try {
            LogManager.aiSession("AutomationScheduler: Calculating next session to execute", "DEBUG")

            // Build list of candidates (resume or create)
            val candidates = mutableListOf<ScheduleCandidate>()

            // Step 1: Check ALL incomplete sessions (any automation, even if disabled)
            // This ensures SUSPENDED sessions (from CHAT eviction) are always resumed
            val incompleteSessions = aiDao.getAllIncompleteAutomationSessions()

            LogManager.aiSession(
                "AutomationScheduler: Found ${incompleteSessions.size} incomplete sessions to check",
                "DEBUG"
            )

            for (incompleteSession in incompleteSessions) {
                // scheduledExecutionTime must not be null for AUTOMATION sessions
                if (incompleteSession.scheduledExecutionTime == null) {
                    LogManager.aiSession(
                        "AutomationScheduler: Skipping incomplete session ${incompleteSession.id} - scheduledExecutionTime is null (data error)",
                        "ERROR"
                    )
                    continue
                }

                val automationId = incompleteSession.automationId
                if (automationId == null) {
                    LogManager.aiSession(
                        "AutomationScheduler: Skipping incomplete session ${incompleteSession.id} - automationId is null (data error)",
                        "ERROR"
                    )
                    continue
                }

                LogManager.aiSession(
                    "AutomationScheduler: Found incomplete session ${incompleteSession.id} for automation $automationId " +
                    "(endReason=${incompleteSession.endReason}, scheduled=${formatTimestamp(incompleteSession.scheduledExecutionTime)})",
                    "INFO"
                )
                candidates.add(
                    ScheduleCandidate(
                        automationId = automationId,
                        scheduledTime = incompleteSession.scheduledExecutionTime,
                        action = CandidateAction.RESUME,
                        sessionId = incompleteSession.id
                    )
                )
            }

            // Step 2: For enabled automations WITH schedule, calculate next scheduled execution
            // Only if no incomplete session was found above
            val allEnabledAutomations = aiDao.getAllEnabledAutomations()

            if (allEnabledAutomations.isEmpty()) {
                LogManager.aiSession("AutomationScheduler: No enabled automations", "DEBUG")
            }

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

                val now = System.currentTimeMillis()
                val fromTimestamp = catchUpSearchStart(automation, schedule, lastCompletedSession, now)

                LogManager.aiSession(
                    "AutomationScheduler: Calculating next execution for automation ${automation.id} " +
                    "(lastCompleted=${lastCompletedSession?.scheduledExecutionTime?.let { formatTimestamp(it) }}, " +
                    "updatedAt=${formatTimestamp(automation.updatedAt)}, " +
                    "startDate=${schedule.startDate?.let { formatTimestamp(it) }}, " +
                    "catchUpWindowMinutes=${automation.catchUpWindowMinutes ?: "unlimited"}, " +
                    "fromTimestamp=${formatTimestamp(fromTimestamp)})",
                    "DEBUG"
                )

                val nextExecutionTime = ScheduleCalculator.calculateNextExecution(
                    pattern = schedule.pattern,
                    startDate = schedule.startDate,
                    endDate = schedule.endDate,
                    fromTimestamp = fromTimestamp
                )

                if (nextExecutionTime == null) {
                    LogManager.aiSession("AutomationScheduler: No more executions for automation ${automation.id} (schedule ended or invalid)", "DEBUG")
                    continue
                }

                // Check if execution time has passed
                if (nextExecutionTime <= now) {
                    // Among the occurrences that are due, run the most recent one or the oldest
                    val dueTime = if (automation.dismissOlderInstances) {
                        CatchUpPolicy.lastDueOccurrence(schedule, fromTimestamp, now, nextExecutionTime)
                    } else {
                        nextExecutionTime
                    }

                    if (dueTime != nextExecutionTime) {
                        LogManager.aiSession(
                            "AutomationScheduler: Automation ${automation.id} set to the most recent due occurrence " +
                            "(${formatTimestamp(dueTime)} instead of ${formatTimestamp(nextExecutionTime)}); the ones in between are dropped",
                            "INFO"
                        )
                    }

                    LogManager.aiSession(
                        "AutomationScheduler: Automation ${automation.id} is due (scheduled=${formatTimestamp(dueTime)}, now=${formatTimestamp(now)})",
                        "INFO"
                    )
                    candidates.add(
                        ScheduleCandidate(
                            automationId = automation.id,
                            scheduledTime = dueTime,
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
     * Where the search for the next occurrence starts.
     *
     * The base is the last completed run, or the automation's last modification, whichever is
     * later: a config change must not drag it back through occurrences that predate the change.
     * With neither, the schedule's start date, or now.
     *
     * The catch-up window then pulls that start forward. An occurrence older than the window is
     * skipped, so starting before it would mean walking to each one only to drop it -- which is
     * how 47 days of missed runs became 47 sessions. Skipped occurrences leave this log line and
     * nothing else: the history is made of sessions, and an empty "skipped" one would be a shape
     * to handle in every screen that reads it.
     */

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

    /**
     * Get next execution info for a specific automation
     * Used by AutomationScreen to display "Prochaine exécution: dans 2h"
     *
     * @param automationId ID of the automation
     * @return NextExecution with scheduled time and message, or null if no next execution
     */
    suspend fun getNextExecutionForAutomation(automationId: String): NextExecution? {
        try {
            val s = Strings.`for`(context = context)

            LogManager.aiSession("AutomationScheduler: Calculating next execution for automation $automationId", "DEBUG")

            // Load automation
            val automation = aiDao.getAutomationById(automationId)
            if (automation == null) {
                LogManager.aiSession("AutomationScheduler: Automation not found: $automationId", "WARN")
                return null
            }

            // Check if active
            if (!automation.isEnabled) {
                LogManager.aiSession("AutomationScheduler: Automation $automationId is disabled", "DEBUG")
                return null
            }

            // Check if has schedule configuration
            if (automation.scheduleJson.isNullOrEmpty()) {
                LogManager.aiSession("AutomationScheduler: Automation $automationId has no schedule (manual execution only)", "DEBUG")
                return null
            }

            // Parse schedule
            val schedule = try {
                json.decodeFromString<ScheduleConfig>(automation.scheduleJson)
            } catch (e: Exception) {
                LogManager.aiSession("AutomationScheduler: Failed to parse schedule for automation $automationId: ${e.message}", "ERROR", e)
                return null
            }

            // Get last completed session to calculate next execution time
            val lastCompletedSession = aiDao.getLastCompletedAutomationSession(automationId)

            // Same start of search as getNextSession, so the screen announces the run that
            // will actually happen and not one the window has already ruled out
            val fromTimestamp = catchUpSearchStart(automation, schedule, lastCompletedSession, System.currentTimeMillis())

            LogManager.aiSession(
                "AutomationScheduler: Calculating next execution for automation $automationId " +
                "(lastCompleted=${lastCompletedSession?.scheduledExecutionTime?.let { formatTimestamp(it) }}, " +
                "updatedAt=${formatTimestamp(automation.updatedAt)}, " +
                "fromTimestamp=${formatTimestamp(fromTimestamp)})",
                "DEBUG"
            )

            val nextExecutionTime = ScheduleCalculator.calculateNextExecution(
                pattern = schedule.pattern,
                startDate = schedule.startDate,
                endDate = schedule.endDate,
                fromTimestamp = fromTimestamp
            )

            if (nextExecutionTime == null) {
                LogManager.aiSession("AutomationScheduler: No more executions for automation $automationId (schedule ended or invalid)", "DEBUG")
                return null
            }

            // Format message with relative time
            val relativeTime = FormatUtils.formatRelativeTime(nextExecutionTime, context)
            val message = s.shared("automation_next_execution").format(relativeTime)

            LogManager.aiSession(
                "AutomationScheduler: Next execution for automation $automationId: ${formatTimestamp(nextExecutionTime)} ($relativeTime)",
                "INFO"
            )

            return NextExecution(
                scheduledTime = nextExecutionTime,
                message = message
            )

        } catch (e: Exception) {
            LogManager.aiSession("AutomationScheduler: Error calculating next execution for automation $automationId: ${e.message}", "ERROR", e)
            return null
        }
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

/**
 * Next execution info for a specific automation
 * Used by AutomationScreen to display next scheduled execution
 */
data class NextExecution(
    val scheduledTime: Long,  // Timestamp when next execution is scheduled
    val message: String       // Formatted message like "Prochaine exécution: dans 2h"
)
