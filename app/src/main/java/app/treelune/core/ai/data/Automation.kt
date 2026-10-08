package app.treelune.core.ai.data

import app.treelune.core.utils.ScheduleConfig

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
    /** A scheduled automation's choice about missed runs (AutomationSettings.LIMITED or UNLIMITED); null without schedule */
    val catchUp: String? = null,

    /**
     * How late a scheduled occurrence may be and still run, in milliseconds, for a limited
     * catch-up; null otherwise.
     *
     * Beyond it the occurrence is skipped: it leaves a log line and no session, since the
     * history is made of sessions and an empty "skipped" one would be a shape to handle
     * everywhere. Same notion as the Messages tooltype's validity_window.
     */
    val catchUpWindow: Long? = null,

    /** Among the occurrences that are due, run only the most recent one instead of each */
    val dismissOlderInstances: Boolean = false,
    val providerId: String,                 // AI provider to use for execution
    val isEnabled: Boolean,
    val group: String? = null,              // Group within zone (null = ungrouped)
    val createdAt: Long,
    val updatedAt: Long,                    // Last modification timestamp (used to skip missed executions after disable)
    val lastExecutionId: String?,           // ID of most recent execution session
    val executionHistory: List<String>      // IDs of execution sessions (newest first)
) {
    companion object {
        /**
         * An automation as the automations service gives it in a result: the single reader of
         * that form, which fails loudly on a result that does not have it.
         */
        fun fromResult(map: Map<*, *>): Automation {
            @Suppress("UNCHECKED_CAST")
            val schedule = (map["schedule"] as Map<String, Any?>?)?.let {
                kotlinx.serialization.json.Json.decodeFromString(ScheduleConfig.serializer(),
                    app.treelune.core.utils.JsonUtils.toJSONObject(it).toString())
            }
            val catchUp = map["catch_up"] as Map<*, *>?
            return Automation(
                id = map["id"] as String,
                name = map["name"] as String,
                zoneId = map["zone_id"] as String,
                seedSessionId = map["seed_session_id"] as String,
                schedule = schedule,
                triggerIds = (map["trigger_ids"] as List<*>).map { it as String },
                catchUp = catchUp?.get("limit") as String?,
                catchUpWindow = (catchUp?.get("window") as Number?)?.toLong(),
                // Without a schedule nothing is missed, and the setting is absent
                dismissOlderInstances = catchUp?.get("dismiss_older_instances") as Boolean? ?: false,
                providerId = map["provider_id"] as String,
                isEnabled = map["is_enabled"] as Boolean,
                group = map["group"] as String?,
                createdAt = (map["created_at"] as Number).toLong(),
                updatedAt = (map["updated_at"] as Number).toLong(),
                lastExecutionId = map["last_execution_id"] as String?,
                executionHistory = (map["execution_history"] as List<*>).map { it as String }
            )
        }
    }
}

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
