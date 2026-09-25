package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.fields.settings.SettingNode

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
 * - Max output tokens: Response length limit (optional, default DEFAULT_MAX_OUTPUT_TOKENS)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * with different API keys or models if desired.
 */
class OpenAIStandardProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other OpenAI variants
    private val core = OpenAIProviderCore(context, "openai_standard")

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "openai_standard"

    override fun getDisplayName(): String = "OpenAI"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override suspend fun listModels(apiKey: String): ProviderModels = core.fetchAvailableModels(apiKey)

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
 * - Max output tokens: Response length limit (optional, default DEFAULT_MAX_OUTPUT_TOKENS)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * independently.
 */
class OpenAIEconomicProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other OpenAI variants
    private val core = OpenAIProviderCore(context, "openai_economic")

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "openai_economic"

    override fun getDisplayName(): String = "OpenAI (économique)"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override suspend fun listModels(apiKey: String): ProviderModels = core.fetchAvailableModels(apiKey)

    /**
     * Send query to OpenAI API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
