package com.assistant.core.transcription.database

import androidx.room.*

/**
 * Transcription Provider configuration database entity
 *
 * Stores configuration for each transcription provider with:
 * - Searchable fields as columns (providerId, isActive)
 * - Configuration JSON as text
 *
 * Only one provider can be active at a time (enforced by service logic).
 */
@Entity(
    tableName = "transcription_provider_configs",
    indices = [
        Index(value = ["providerId"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class TranscriptionProviderConfigEntity(
    @PrimaryKey val providerId: String,      // Provider ID (e.g., "vosk", "whisper_local")
    val displayName: String,                  // Human-readable name
    val configJson: String,                   // JSON configuration (models, settings, etc.)
    val isConfigured: Boolean,                // Whether config has all required fields
    val isActive: Boolean,                    // Whether this is the active provider
    val createdAt: Long,                      // Creation timestamp
    val updatedAt: Long                       // Last update timestamp
)
