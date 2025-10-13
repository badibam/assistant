package com.assistant.core.ai.database

import androidx.room.*
import com.assistant.core.ai.data.SessionType

/**
 * AI Session database entity with hybrid approach:
 * - Searchable fields as columns
 * - Complex structures as JSON
 */
@Entity(
    tableName = "ai_sessions",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["type"]),
        Index(value = ["lastActivity"]),
        Index(value = ["automationId"]),
        Index(value = ["state"])
    ]
)
data class AISessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: SessionType,
    val requireValidation: Boolean = false,  // Session-level validation toggle
    val waitingStateJson: String? = null,    // Persisted waiting state (null = no waiting)
    val automationId: String?,              // null for CHAT/SEED, automation ID for AUTOMATION
    val scheduledExecutionTime: Long?,      // For AUTOMATION: scheduled time (not actual execution time)
    val providerId: String,                 // Fixed for the session
    val providerSessionId: String,          // Provider API session ID
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean,
    val state: String = "IDLE",             // SessionState as string
    val lastNetworkErrorTime: Long? = null, // Last network error timestamp
    val endReason: String? = null           // SessionEndReason as string
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