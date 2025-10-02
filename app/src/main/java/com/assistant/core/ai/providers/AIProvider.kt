package com.assistant.core.ai.providers

import androidx.compose.runtime.Composable
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.validation.SchemaProvider

/**
 * Interface for AI providers (Claude, OpenAI, DeepSeek, etc.)
 * Extends SchemaProvider for unified validation with existing architecture
 *
 * Each provider implements this interface to provide AI query capabilities
 * Configuration validation handled via SchemaValidator.validate(schema, configData, context)
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
     *
     * @param config Current configuration JSON
     * @param onSave Callback to save configuration
     * @param onCancel Callback to cancel without saving
     * @param onReset Callback to reset/delete configuration (nullable)
     */
    @Composable
    fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    )

    /**
     * Send query to AI provider
     */
    suspend fun query(prompt: String, config: String): AIResponse
}