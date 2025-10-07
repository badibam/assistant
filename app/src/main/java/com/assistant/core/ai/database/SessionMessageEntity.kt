package com.assistant.core.ai.database

import androidx.room.*
import com.assistant.core.ai.data.MessageSender

/**
 * Session message database entity with hybrid approach:
 * - Searchable fields as columns
 * - Complex structures as JSON
 */
@Entity(
    tableName = "session_messages",
    foreignKeys = [
        ForeignKey(
            entity = AISessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"]),
        Index(value = ["sender"])
    ]
)
data class SessionMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val sender: MessageSender,

    // Complex structures as JSON
    val richContentJson: String?,      // RichMessage serialized
    val textContent: String?,          // Simple text content
    val aiMessageJson: String?,        // Original AI JSON for prompt consistency
    val aiMessageParsedJson: String?,  // Parsed AIMessage for UI
    val systemMessageJson: String?,    // SystemMessage serialized
    val executionMetadataJson: String?, // ExecutionMetadata for automations
    val excludeFromPrompt: Boolean = false, // Exclude from prompt generation (UI-only messages)

    // Token usage metrics (for AI messages only, 0 for USER/SYSTEM)
    val inputTokens: Int = 0,           // Regular input tokens (non-cached)
    val cacheWriteTokens: Int = 0,      // Cache write tokens (generic, all providers)
    val cacheReadTokens: Int = 0,       // Cache read tokens (generic, all providers)
    val outputTokens: Int = 0           // Output tokens generated
)

/**
 * Message type converters for Room
 */
class MessageTypeConverters {
    @TypeConverter
    fun fromMessageSender(value: MessageSender): String = value.name

    @TypeConverter
    fun toMessageSender(value: String): MessageSender = MessageSender.valueOf(value)
}