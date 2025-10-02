package com.assistant.core.ai.database

import androidx.room.*

/**
 * AI Provider configuration database entity
 *
 * Stores configuration for each AI provider with:
 * - Searchable fields as columns (providerId, isActive)
 * - Configuration JSON as text
 *
 * Only one provider can be active at a time (enforced by service logic).
 */
@Entity(
    tableName = "ai_provider_configs",
    indices = [
        Index(value = ["providerId"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class AIProviderConfigEntity(
    @PrimaryKey val providerId: String,      // Provider ID (e.g., "claude", "openai")
    val displayName: String,                  // Human-readable name
    val configJson: String,                   // JSON configuration (API keys, models, etc.)
    val isConfigured: Boolean,                // Whether config has all required fields
    val isActive: Boolean,                    // Whether this is the active provider
    val createdAt: Long,                      // Creation timestamp
    val updatedAt: Long                       // Last update timestamp
)
