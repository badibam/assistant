package com.assistant.core.ai.providers

import androidx.compose.runtime.Composable
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.validation.SchemaProvider

/**
 * Interface for AI providers (Claude, OpenAI, DeepSeek, etc.)
 * Extends SchemaProvider for unified validation with existing architecture
 *
 * Each provider implements this interface to provide AI query capabilities
 * Configuration validation handled via SchemaValidator.validate(provider, config, context, "config")
 */
interface AIProvider : SchemaProvider {

    /**
     * Unique provider identifier
     */
    fun getProviderId(): String

    /**
     * Human-readable provider name
     */
    fun getDisplayName(): String

    /**
     * Configuration UI for this provider
     * Uses SchemaProvider.getConfigSchema() for validation
     */
    @Composable
    fun getConfigScreen(config: String, onSave: (String) -> Unit)

    /**
     * Send query to AI provider
     */
    suspend fun query(prompt: String, config: String): AIResponse

    // SchemaProvider provides:
    // - getConfigSchema(): String (JSON Schema for provider config)
    // - getDataSchema(): String? (null for providers - no data schema needed)
    // - getFormFieldName(String): String (field name translations)

    // Validation handled via: SchemaValidator.validate(provider, configData, context, "config")
}