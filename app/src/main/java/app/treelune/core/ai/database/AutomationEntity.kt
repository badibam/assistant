package app.treelune.core.ai.database

import androidx.room.*

/**
 * Automation database entity
 * Complex structures (schedule, triggerIds, executionHistory) stored as JSON
 *
 * Stored in common AppDatabase alongside AI sessions and provider configs
 */
@Entity(
    tableName = "automations",
    indices = [
        Index(value = ["zone_id"]),
        Index(value = ["is_enabled"]),
        Index(value = ["seed_session_id"])
    ]
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "seed_session_id") val seedSessionId: String,
    @ColumnInfo(name = "schedule_json") val scheduleJson: String?,              // JSON of ScheduleConfig
    @ColumnInfo(name = "trigger_ids_json") val triggerIdsJson: String,             // JSON array of trigger IDs
    // A scheduled automation's choice about missed runs, "limited" or "unlimited"; null without schedule
    @ColumnInfo(name = "catch_up") val catchUp: String?,
    // How late an occurrence may still run, in milliseconds, for a "limited" catch-up
    @ColumnInfo(name = "catch_up_window") val catchUpWindow: Long?,
    @ColumnInfo(name = "dismiss_older_instances") val dismissOlderInstances: Boolean,
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,                    // Last modification timestamp (config change, enable/disable)
    @ColumnInfo(name = "last_execution_id") val lastExecutionId: String?,
    @ColumnInfo(name = "execution_history_json") val executionHistoryJson: String,       // JSON array of execution session IDs

    /**
     * Group assignment for this automation (nullable string)
     * Links to zone's tool_groups array
     * null = ungrouped automation
     */
    val group: String? = null,

    /** What its AI may reach, AccessMask's stored form; an empty list reaches everything */
    @ColumnInfo(name = "access_json") val accessJson: String = "[]"
)
