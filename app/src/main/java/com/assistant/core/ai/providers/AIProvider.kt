package com.assistant.core.ai.providers

import androidx.compose.runtime.Composable
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.providers.AIResponse
import com.assistant.core.validation.SchemaProvider

/**
 * Interface for AI providers (Claude, OpenAI, DeepSeek, etc.)
 * Extends SchemaProvider for unified validation with existing architecture
 *
 * Each provider implements this interface to provide AI query capabilities
 * Configuration validation handled via SchemaValidator.validate(schema, configData, context)
 *
 * Providers receive PromptData (raw L1-L3 + messages) and:
 * 1. Transform messages to provider-specific format
 * 2. Construct final prompt with cache breakpoints if supported
 * 3. Call provider API
 * 4. Return raw JSON response (orchestrator handles parsing)
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
     * Send query to AI provider with PromptData
     *
     * Provider responsibilities:
     * - Transform PromptData (L1-L3 + messages) to provider-specific API format
     * - Fuse SystemMessages with formattedData into conversation history
     * - Add cache breakpoints if supported (e.g., Claude's prompt caching)
     * - Make API call
     * - Return raw JSON response (orchestrator parses to AIMessage)
     *
     * @param promptData Raw prompt data from PromptManager
     * @param config Provider configuration JSON
     * @return AIResponse with raw JSON content
     */
    suspend fun query(promptData: PromptData, config: String): AIResponse
}