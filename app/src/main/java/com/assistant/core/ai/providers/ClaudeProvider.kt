package com.assistant.core.ai.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.providers.ui.ClaudeConfigScreen
import com.assistant.core.validation.Schema

/**
 * Model information from Claude API
 * Shared data class for all Claude provider variants
 */
data class ClaudeModelInfo(
    val id: String,
    val displayName: String,
    val createdAt: String
)

/**
 * Result of fetching available models from Claude API
 * Shared data class for all Claude provider variants
 */
data class FetchModelsResult(
    val success: Boolean,
    val models: List<ClaudeModelInfo>,
    val errorMessage: String?
)

/**
 * Claude AI Provider - Standard variant
 *
 * Default Claude provider using standard models (e.g., claude-sonnet-4-5).
 * Delegates all implementation to ClaudeProviderCore with variant ID "claude_standard".
 *
 * This variant is recommended for:
 * - Standard use cases requiring balanced performance and cost
 * - Production workloads with consistent quality requirements
 * - General-purpose AI interactions
 *
 * Configuration:
 * - API key: Claude API key from Anthropic
 * - Model: Selected from available models via API
 * - Max tokens: Response length limit (optional, default 2000)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * with different API keys or models if desired.
 */
class ClaudeStandardProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other Claude variants
    // Exposed as internal to allow ClaudeConfigScreen to access it
    internal val core = ClaudeProviderCore(context, "claude_standard")

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "claude_standard"

    override fun getDisplayName(): String = "Claude"

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return core.getSchema(schemaId, context)
    }

    override fun getAllSchemaIds(): List<String> {
        return core.getAllSchemaIds()
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        return core.getFormFieldName(fieldName, context)
    }

    /**
     * Configuration UI for Claude standard provider
     * Passes core and displayName to shared config screen
     */
    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        ClaudeConfigScreen(
            core = core,
            displayName = getDisplayName(),
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    /**
     * Send query to Claude API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}

/**
 * Claude AI Provider - Economic variant
 *
 * Economic Claude provider using cost-optimized models (e.g., claude-haiku-4).
 * Delegates all implementation to ClaudeProviderCore with variant ID "claude_economic".
 *
 * This variant is recommended for:
 * - High-volume use cases where cost is a primary concern
 * - Development and testing environments
 * - Tasks that don't require the most advanced reasoning
 *
 * Configuration:
 * - API key: Claude API key from Anthropic (can be same as standard or different)
 * - Model: Selected from available models via API (typically haiku models)
 * - Max tokens: Response length limit (optional, default 2000)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * independently.
 */
class ClaudeEconomicProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other Claude variants
    // Exposed as internal to allow ClaudeConfigScreen to access it
    internal val core = ClaudeProviderCore(context, "claude_economic")

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "claude_economic"

    override fun getDisplayName(): String = "Claude (économique)"

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return core.getSchema(schemaId, context)
    }

    override fun getAllSchemaIds(): List<String> {
        return core.getAllSchemaIds()
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        return core.getFormFieldName(fieldName, context)
    }

    /**
     * Configuration UI for Claude economic provider
     * Passes core and displayName to shared config screen
     */
    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        ClaudeConfigScreen(
            core = core,
            displayName = getDisplayName(),
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    /**
     * Send query to Claude API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
