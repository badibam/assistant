package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * Claude AI Provider - Standard variant
 *
 * Default Claude provider using standard models (e.g., claude-sonnet-5-5).
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
 * - Max tokens: Response length limit (optional, default DEFAULT_MAX_OUTPUT_TOKENS)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * with different API keys or models if desired.
 */
class ClaudeStandardProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other Claude variants
    private val core = ClaudeProviderCore(context, "claude_standard", MessagesApi.ANTHROPIC)

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "claude_standard"

    override fun getDisplayName(): String = "Claude"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

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
 * Economic Claude provider using cost-optimized models (e.g., claude-haiku-4-5-20251001).
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
 * - Max tokens: Response length limit (optional, default DEFAULT_MAX_OUTPUT_TOKENS)
 *
 * Each variant maintains its own configuration in the database,
 * allowing users to configure both standard and economic variants
 * independently.
 */
class ClaudeEconomicProvider(private val context: Context) : AIProvider {

    // Core implementation shared with other Claude variants
    private val core = ClaudeProviderCore(context, "claude_economic", MessagesApi.ANTHROPIC)

    // ========================================================================================
    // AIProvider Implementation
    // ========================================================================================

    override fun getProviderId(): String = "claude_economic"

    override fun getDisplayName(): String = "Claude (économique)"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    /**
     * Send query to Claude API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
