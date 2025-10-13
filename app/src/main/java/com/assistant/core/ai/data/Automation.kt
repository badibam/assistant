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
    val icon: String,                       // User-chosen icon
    val zoneId: String,                     // Automation attached to a zone
    val seedSessionId: String,              // Points to SEED session with initial message
    val schedule: ScheduleConfig?,          // null = no time-based triggering
    val triggerIds: List<String>,           // Empty = no event-based triggering
    val dismissOlderInstances: Boolean = false,  // Skip older instances if newer exists in queue
    val providerId: String,                 // AI provider to use for execution
    val isEnabled: Boolean,
    val createdAt: Long,
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
