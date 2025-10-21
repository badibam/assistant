package com.assistant.core.ai.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.providers.ui.OpenAIConfigScreen
import com.assistant.core.validation.Schema

/**
 * Model information from OpenAI API
 * Shared data class for all OpenAI provider variants
 */
data class OpenAIModelInfo(
    val id: String,
    val created: Long,
    val ownedBy: String
)

/**
 * Result of fetching available models from OpenAI API
 * Shared data class for all OpenAI provider variants
 */
data class OpenAIFetchModelsResult(
    val success: Boolean,
    val models: List<OpenAIModelInfo>,
    val errorMessage: String?
)

/**
 * OpenAI AI Provider - Standard variant
 *
 * Default OpenAI provider using standard models (e.g., gpt-4.1).
 * Delegates all implementation to OpenAIProviderCore with variant ID "openai_standard".
 *
 * This variant is recommended for:
 * - Standard use cases requiring high-quality responses
 * - Production workloads with complex reasoning requirements
 * - General-purpose AI interactions
 *
 * Configuration:
 * - API key: OpenAI API key
 * - Model: Model ID (e.g., "gpt-4.1", "gpt-4.1-2025-04-14")
 * - Temperature: Sampling temperature 0.0-2.0 (optional, default 1.0)
 * - Max output tokens: Response length limit (optional, default 2000)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * with different API keys or models if desired.
 */
class OpenAIStandardProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other OpenAI variants
    // Exposed as internal to allow OpenAIConfigScreen to access it
    internal val core = OpenAIProviderCore(context, "openai_standard")

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "openai_standard"

    override fun getDisplayName(): String = "OpenAI"

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
     * Configuration UI for OpenAI standard provider
     * Passes core and displayName to shared config screen
     */
    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        OpenAIConfigScreen(
            core = core,
            displayName = getDisplayName(),
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    /**
     * Send query to OpenAI API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}

/**
 * OpenAI AI Provider - Economic variant
 *
 * Economic OpenAI provider using cost-optimized models (e.g., gpt-4.1-mini).
 * Delegates all implementation to OpenAIProviderCore with variant ID "openai_economic".
 *
 * This variant is recommended for:
 * - High-volume use cases where cost is a primary concern
 * - Development and testing environments
 * - Tasks that don't require the most advanced reasoning
 *
 * Configuration:
 * - API key: OpenAI API key (can be same as standard or different)
 * - Model: Model ID (e.g., "gpt-4.1-mini")
 * - Temperature: Sampling temperature 0.0-2.0 (optional, default 1.0)
 * - Max output tokens: Response length limit (optional, default 2000)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * independently.
 */
class OpenAIEconomicProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other OpenAI variants
    // Exposed as internal to allow OpenAIConfigScreen to access it
    internal val core = OpenAIProviderCore(context, "openai_economic")

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "openai_economic"

    override fun getDisplayName(): String = "OpenAI (économique)"

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
     * Configuration UI for OpenAI economic provider
     * Passes core and displayName to shared config screen
     */
    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        OpenAIConfigScreen(
            core = core,
            displayName = getDisplayName(),
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    /**
     * Send query to OpenAI API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
