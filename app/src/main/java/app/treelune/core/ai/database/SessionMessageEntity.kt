package app.treelune.core.ai.database

import androidx.room.*
import app.treelune.core.ai.data.MessageSender

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
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["sender"])
    ]
)
data class SessionMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val timestamp: Long,
    val sender: MessageSender,

    // Complex structures as JSON
    @ColumnInfo(name = "rich_content_json") val richContentJson: String?,      // RichMessage serialized
    @ColumnInfo(name = "text_content") val textContent: String?,          // Simple text content
    @ColumnInfo(name = "ai_message_json") val aiMessageJson: String?,        // Original AI JSON for prompt consistency
    @ColumnInfo(name = "ai_message_parsed_json") val aiMessageParsedJson: String?,  // Parsed AIMessage for UI
    @ColumnInfo(name = "system_message_json") val systemMessageJson: String?,    // SystemMessage serialized
    @ColumnInfo(name = "execution_metadata_json") val executionMetadataJson: String?, // ExecutionMetadata for automations
    @ColumnInfo(name = "exclude_from_prompt") val excludeFromPrompt: Boolean = false, // Exclude from prompt generation (UI-only messages)

    // Token usage metrics (for AI messages only, 0 for USER/SYSTEM)
    // Note: API providers return inputTokens as UNCACHED only. Total input = inputTokens + cacheWriteTokens + cacheReadTokens
    @ColumnInfo(name = "input_tokens") val inputTokens: Int = 0,           // Uncached input tokens (from API, already excludes cache tokens)
    @ColumnInfo(name = "cache_write_tokens") val cacheWriteTokens: Int = 0,      // Cache write tokens (generic, all providers)
    @ColumnInfo(name = "cache_read_tokens") val cacheReadTokens: Int = 0,       // Cache read tokens (generic, all providers)
    @ColumnInfo(name = "output_tokens") val outputTokens: Int = 0,          // Output tokens generated

    // Model and prices per token of the AI call, as at the time of the call (null: unknown)
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    @ColumnInfo(name = "input_price") val inputPrice: Double? = null,
    @ColumnInfo(name = "cache_write_price") val cacheWritePrice: Double? = null,
    @ColumnInfo(name = "cache_read_price") val cacheReadPrice: Double? = null,
    @ColumnInfo(name = "output_price") val outputPrice: Double? = null,
    // A call cut or lost after its request went out: billed perhaps, usage unknown
    @ColumnInfo(name = "usage_unknown") val usageUnknown: Boolean = false
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