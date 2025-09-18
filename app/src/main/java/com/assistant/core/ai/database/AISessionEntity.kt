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
        Index(value = ["lastActivity"])
    ]
)
data class AISessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: SessionType,
    val providerId: String,        // Fixed for the session
    val providerSessionId: String, // Provider API session ID
    val scheduleConfigJson: String?, // JSON for ScheduleConfig (automation only)
    val queryListsJson: String?,   // JSON for SessionQueryLists (Level 2 + Level 4)
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean
)

/**
 * Query lists for prompt generation levels
 */
data class SessionQueryLists(
    val level2Queries: List<String>,  // Stable user context (JSON serialized DataQuery ids)
    val level4Queries: List<String>   // Session data accumulated (JSON serialized DataQuery ids)
)

/**
 * Type converters for Room
 */
class AITypeConverters {
    @TypeConverter
    fun fromSessionType(value: SessionType): String = value.name

    @TypeConverter
    fun toSessionType(value: String): SessionType = SessionType.valueOf(value)
}