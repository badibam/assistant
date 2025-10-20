package com.assistant.core.ai.database

import androidx.room.*
import com.assistant.core.ai.data.SessionType

/**
 * AI Session database entity for Event-Driven Architecture V2.
 *
 * Stores complete session state for atomic memory + DB synchronization.
 * - Searchable fields as columns
 * - Waiting context as JSON
 *
 * Architecture: Event-Driven State Machine (V2)
 * - phase: Current execution phase (replaces old 'state')
 * - Loop counter: totalRoundtrips (only limit enforced)
 * - Timestamps: lastEventTime, lastUserInteractionTime (for inactivity calculation)
 * - waitingContextJson: Serialized WaitingContext (validation, communication, completion)
 */
@Entity(
    tableName = "ai_sessions",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["type"]),
        Index(value = ["lastActivity"]),
        Index(value = ["automationId"]),
        Index(value = ["phase"]),
        Index(value = ["endReason"])
    ]
)
data class AISessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: SessionType,
    val requireValidation: Boolean = false,  // Session-level validation toggle

    // ==================== Event-Driven State (V2) ====================

    /** Current execution phase (IDLE, CALLING_AI, EXECUTING_ACTIONS, etc.) */
    val phase: String = "IDLE",

    /** Serialized WaitingContext (validation, communication, completion) - null if not waiting */
    val waitingContextJson: String? = null,

    // ==================== Loop Counters ====================

    /** Total autonomous roundtrips count (never reset during session) */
    val totalRoundtrips: Int = 0,

    // ==================== Timestamps ====================

    /** Timestamp of last event processed (any event) */
    val lastEventTime: Long = 0L,

    /** Timestamp of last user interaction (message, validation, response) */
    val lastUserInteractionTime: Long = 0L,

    // ==================== Session Metadata ====================

    val automationId: String?,              // null for CHAT/SEED, automation ID for AUTOMATION
    val scheduledExecutionTime: Long?,      // For AUTOMATION: scheduled time (not actual execution time)
    val providerId: String,                 // Fixed for the session
    val providerSessionId: String,          // Provider API session ID
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean,
    val endReason: String? = null,          // SessionEndReason as string (null = crash/incomplete)
    val tokensUsed: Int? = null             // Total tokens consumed by this session (for monitoring)
)

/**
 * Level 4 queries (enrichments) are extracted from message history
 * Level 2 queries are generated dynamically from current user context
 */

/**
 * Type converters for Room
 */
class AITypeConverters {
    @TypeConverter
    fun fromSessionType(value: SessionType): String = value.name

    @TypeConverter
    fun toSessionType(value: String): SessionType = SessionType.valueOf(value)
}