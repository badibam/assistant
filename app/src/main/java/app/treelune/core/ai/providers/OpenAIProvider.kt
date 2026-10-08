package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * OpenAI AI Provider - Standard variant
 *
 * Default OpenAI provider using standard models (e.g., gpt-5.5).
 * Delegates all implementation to OpenAIProviderCore with variant ID "openai_standard".
 *
 * This variant is recommended for:
 * - Standard use cases requiring high-quality responses
 * - Production workloads with complex reasoning requirements
 * - General-purpose AI interactions
 *
 * Configuration:
 * - API key: OpenAI API key
 * - Model: Model ID (e.g., "gpt-5.5", "gpt-5.5-2026-04-23")
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

    override fun factsProvider(): String = OpenAIProviderCore.FACTS_PROVIDER

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

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
 * Economic OpenAI provider using cost-optimized models (e.g., gpt-5.4-mini).
 * Delegates all implementation to OpenAIProviderCore with variant ID "openai_economic".
 *
 * This variant is recommended for:
 * - High-volume use cases where cost is a primary concern
 * - Development and testing environments
 * - Tasks that don't require the most advanced reasoning
 *
 * Configuration:
 * - API key: OpenAI API key (can be same as standard or different)
 * - Model: Model ID (e.g., "gpt-5.4-mini")
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

    override fun factsProvider(): String = OpenAIProviderCore.FACTS_PROVIDER

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    /**
     * Send query to OpenAI API with PromptData
     * Delegates to core implementation
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
