package com.assistant.core.ai.data

import com.assistant.core.utils.ScheduleConfig

/**
 * Automation configuration defining when and how AI should act automatically
 *
 * An automation is a template that creates AUTOMATION sessions when triggered.
 * It references a SEED session containing the initial user message/prompt.
 */
data class Automation(
    val id: String,
    val name: String,
    val zoneId: String,                     // Automation attached to a zone
    val seedSessionId: String,              // Points to SEED session with initial message
    val schedule: ScheduleConfig?,          // null = no time-based triggering
    val triggerIds: List<String>,           // Empty = no event-based triggering
    /**
     * How late a scheduled occurrence may be and still run, in minutes. Null = no limit.
     *
     * Beyond it the occurrence is skipped: it leaves a log line and no session, since the
     * history is made of sessions and an empty "skipped" one would be a shape to handle
     * everywhere. Same notion as the Messages tooltype's validity_window.
     */
    val catchUpWindowMinutes: Long? = null,

    /** Among the occurrences that are due, run only the most recent one instead of each */
    val dismissOlderInstances: Boolean = false,
    val providerId: String,                 // AI provider to use for execution
    val isEnabled: Boolean,
    val group: String? = null,              // Group within zone (null = ungrouped)
    val createdAt: Long,
    val updatedAt: Long,                    // Last modification timestamp (used to skip missed executions after disable)
    val lastExecutionId: String?,           // ID of most recent execution session
    val executionHistory: List<String>      // IDs of execution sessions (newest first)
)

/**
 * Trigger logic deduced from configuration:
 *
 * - schedule == null && triggerIds.isEmpty() → MANUAL only (execute via UI button)
 * - schedule != null && triggerIds.isEmpty() → SCHEDULE only (time-based)
 * - schedule == null && triggerIds.isNotEmpty() → TRIGGER only (event-based, OR between triggers)
 * - schedule != null && triggerIds.isNotEmpty() → HYBRID (schedule OR any trigger)
 */

/**
 * What a scheduled automation saved before catch-up settings existed is read as.
 *
 * No window and the most recent occurrence only: back from a long absence it starts once,
 * and the user adjusts from there. This is a reading rule for old data, applied by the
 * database migration and by the import of a backup that predates the field. It is not a
 * "needs configuring" state, which the scheduler and the screen would then have to carry.
 */
object LegacyCatchUp {
    val WINDOW_MINUTES: Long? = null
    const val DISMISS_OLDER = true
}
