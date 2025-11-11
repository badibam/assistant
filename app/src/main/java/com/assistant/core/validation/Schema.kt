package com.assistant.core.validation

/**
 * Schema data class for direct schema ID mapping
 * Each schema is complete and standalone, with full internationalization support
 */
data class Schema(
    /**
     * Unique identifier for this schema (e.g., "tracking_config_numeric", "zone_config")
     * Used for direct schema lookup and AI system integration
     */
    val id: String,

    /**
     * Internationalized display name for the schema
     * Used in AI documentation and UI contexts
     */
    val displayName: String,

    /**
     * Internationalized description of what this schema validates
     * Provides context for AI and human understanding
     */
    val description: String,

    /**
     * Schema category for organization and filtering
     */
    val category: SchemaCategory,

    /**
     * Pure JSON Schema content for validation
     * Complete standalone schema, must be valid JSON Schema Draft 7
     */
    val content: String
)

/**
 * Schema categories for organization and filtering
 */
enum class SchemaCategory {
    /** Tool configuration schemas */
    TOOL_CONFIG,

    /** Tool data schemas */
    TOOL_DATA,

    /** Tool execution schemas */
    TOOL_EXECUTION,

    /** AI provider configuration schemas */
    AI_PROVIDER,

    /** Transcription provider configuration schemas */
    TRANSCRIPTION_PROVIDER,

    /** Application configuration schemas */
    APP_CONFIG,

    /** Zone configuration schemas */
    ZONE_CONFIG,

    /** Custom field type definition schemas */
    FIELD_TYPE,

    /** Utility schemas (reusable components like ScheduleConfig) */
    UTILITY
}