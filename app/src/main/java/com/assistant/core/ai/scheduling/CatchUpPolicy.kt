package com.assistant.core.ai.scheduling

import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import java.time.ZoneId

/**
 * Which missed occurrence an automation catches up on, when the phone was off or the app closed.
 *
 * Separated from AutomationScheduler so it can be checked: the scheduler needs a Context to build
 * a Coordinator and open the database, and what is worth checking here is the arithmetic, not the
 * loading. Nothing below reads a clock, a setting or a row -- every value it uses is an argument.
 */
object CatchUpPolicy {

    /**
     * Where to start looking for occurrences that are due.
     *
     * The base is the last completed run, or the automation's last modification, whichever is
     * later: a config change must not drag it back through occurrences that predate the change.
     * With neither, now.
     *
     * The catch-up window then pulls that start forward. An occurrence older than the window is
     * skipped, so starting before it would mean walking to each one only to drop it -- which is
     * how 47 days of missed runs became 47 sessions.
     *
     * @param lastExecutionTime when the last completed run was scheduled for, or 0 if there is none
     * @param automationUpdatedAt when the automation was last modified
     * @param catchUpWindow how late an occurrence may still run, in milliseconds, or null for no limit
     * @return the instant to search from
     */
    fun searchStart(
        lastExecutionTime: Long,
        automationUpdatedAt: Long,
        catchUpWindow: Long?,
        now: Long
    ): Long {
        val referenceTime = maxOf(lastExecutionTime, automationUpdatedAt)
        val base = if (referenceTime > 0) referenceTime else now

        if (catchUpWindow == null) return base

        val windowStart = now - catchUpWindow
        return if (windowStart <= base) base else windowStart
    }

    /**
     * Whether the window moved the search forward, which is what the scheduler reports.
     *
     * Kept beside searchStart rather than recomputed at the call site, so the two cannot come to
     * disagree about what was skipped.
     */
    fun windowSkippedOccurrences(
        lastExecutionTime: Long,
        automationUpdatedAt: Long,
        catchUpWindow: Long?,
        now: Long
    ): Boolean {
        if (catchUpWindow == null) return false
        val referenceTime = maxOf(lastExecutionTime, automationUpdatedAt)
        val base = if (referenceTime > 0) referenceTime else now
        return now - catchUpWindow > base
    }

    /**
     * The most recent occurrence that is already due, for an automation set to run only the
     * latest one it missed.
     *
     * Found by bisecting on the start of the search rather than by walking occurrence by
     * occurrence: the schedule calculator only ever answers "the first one after this instant",
     * and a schedule firing several times a day has hundreds of them after a month away. What
     * makes the bisection valid is that this answer never decreases as its argument grows, so
     * "the next one after t is still due" is true up to some t and false after it. Forty-odd
     * probes cover any delay, whatever the pattern.
     *
     * @param firstDue the occurrence the caller already found due, returned when the bisection
     *   lands back on it
     * @param zoneId the timezone the schedule's wall-clock times are read in; defaults to the
     *   configured one, as a parameter so this can be checked without a database open
     */
    fun lastDueOccurrence(
        schedule: ScheduleConfig,
        fromTimestamp: Long,
        now: Long,
        firstDue: Long,
        zoneId: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()
    ): Long {
        fun nextAfter(t: Long): Long? = ScheduleCalculator.calculateNextExecution(
            pattern = schedule.pattern,
            fromTimestamp = t,
            zoneId = zoneId
        )

        // Invariant: nextAfter(low) is due; nextAfter(high) is not, or does not exist
        var low = fromTimestamp
        var high = now
        while (high - low > 1) {
            val mid = low + (high - low) / 2
            val candidate = nextAfter(mid)
            if (candidate != null && candidate <= now) low = mid else high = mid
        }

        return nextAfter(low) ?: firstDue
    }
}
