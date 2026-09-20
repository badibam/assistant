package com.assistant.core.ai.database

import androidx.room.*

/**
 * AI Provider configuration database entity
 *
 * Stores configuration for each AI provider with:
 * - Searchable fields as columns (provider_id, is_active)
 * - Configuration JSON as text
 *
 * Only one provider can be active at a time (enforced by service logic).
 */
@Entity(
    tableName = "ai_provider_configs",
    indices = [
        Index(value = ["provider_id"], unique = true),
        Index(value = ["is_active"])
    ]
)
data class AIProviderConfigEntity(
    @PrimaryKey @ColumnInfo(name = "provider_id") val providerId: String,  // Provider ID (e.g., "claude", "openai")
    @ColumnInfo(name = "display_name") val displayName: String,             // Human-readable name
    @ColumnInfo(name = "config_json") val configJson: String,               // JSON configuration (API keys, models, etc.)
    @ColumnInfo(name = "is_configured") val isConfigured: Boolean,          // Whether config has all required fields
    @ColumnInfo(name = "is_active") val isActive: Boolean,                  // Whether this is the active provider
    @ColumnInfo(name = "created_at") val createdAt: Long,                   // Creation timestamp
    @ColumnInfo(name = "updated_at") val updatedAt: Long                    // Last update timestamp
)
