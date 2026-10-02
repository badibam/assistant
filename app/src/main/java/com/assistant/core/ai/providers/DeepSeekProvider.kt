package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * DeepSeek AI Provider - Standard variant
 *
 * Calls DeepSeek through its Anthropic-compatible endpoint, so all implementation
 * is ClaudeProviderCore with MessagesApi.DEEPSEEK (endpoint, model listing, effort levels).
 *
 * Configuration:
 * - API key: DeepSeek API key
 * - Model: Selected from available models via API (e.g., "deepseek-v4-pro")
 * - Effort: Reasoning effort, required (low, high, max)
 * - Max tokens: Response length limit, reasoning included (optional)
 */
class DeepSeekStandardProvider(private val context: Context) : AIProvider {

    private val core = ClaudeProviderCore(context, "deepseek_standard", MessagesApi.DEEPSEEK)

    override fun getProviderId(): String = "deepseek_standard"

    override fun getDisplayName(): String = "DeepSeek"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}

/**
 * DeepSeek AI Provider - Economic variant
 *
 * Same as DeepSeekStandardProvider with its own configuration,
 * typically a cheaper model (e.g., "deepseek-flash") or a lower effort.
 */
class DeepSeekEconomicProvider(private val context: Context) : AIProvider {

    private val core = ClaudeProviderCore(context, "deepseek_economic", MessagesApi.DEEPSEEK)

    override fun getProviderId(): String = "deepseek_economic"

    override fun getDisplayName(): String = "DeepSeek (économique)"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config.getString("api_key"))

    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
