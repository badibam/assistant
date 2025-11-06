package com.assistant.core.ai.database

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
        Index(value = ["zoneId"]),
        Index(value = ["isEnabled"]),
        Index(value = ["seedSessionId"])
    ]
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val zoneId: String,
    val seedSessionId: String,
    val scheduleJson: String?,              // JSON of ScheduleConfig
    val triggerIdsJson: String,             // JSON array of trigger IDs
    val dismissOlderInstances: Boolean,
    val providerId: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,                    // Last modification timestamp (config change, enable/disable)
    val lastExecutionId: String?,
    val executionHistoryJson: String,       // JSON array of execution session IDs

    /**
     * Group assignment for this automation (nullable string)
     * Links to zone's tool_groups array
     * null = ungrouped automation
     */
    val group: String? = null
)
