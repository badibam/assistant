package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * OpenAI-compatible provider - Standard variant
 *
 * Any server speaking Chat Completions at an https address of the user's: Ollama, llama.cpp,
 * vLLM, LM Studio, OpenRouter. All implementation is OpenAICompatibleProviderCore.
 */
class OpenAICompatibleStandardProvider(private val context: Context) : AIProvider {

    private val core = OpenAICompatibleProviderCore(context, "compatible_standard")

    override fun getProviderId(): String = "compatible_standard"

    override fun getDisplayName(): String = "Compatible OpenAI"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    // The address is the user's, so listing needs it; the key only if the server asks for one
    override fun modelListingSettings(): List<String> = listOf("base_url")

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun query(promptData: PromptData, config: String): AIResponse = core.query(promptData, config)
}

/**
 * OpenAI-compatible provider - Economic variant
 *
 * Same as OpenAICompatibleStandardProvider with its own configuration: a second server, or a
 * smaller model on the same one.
 */
class OpenAICompatibleEconomicProvider(private val context: Context) : AIProvider {

    private val core = OpenAICompatibleProviderCore(context, "compatible_economic")

    override fun getProviderId(): String = "compatible_economic"

    override fun getDisplayName(): String = "Compatible OpenAI (économique)"

    override fun getConfigSettings(context: Context): List<SettingNode> = core.configSettings(context)

    override fun getConfigHelp(context: Context): String = core.configHelp(context)

    override fun modelListingSettings(): List<String> = listOf("base_url")

    override suspend fun listModels(config: JSONObject): ProviderModels = core.fetchAvailableModels(config)

    override fun configError(config: JSONObject, context: Context): String? = core.configError(config, context)

    override suspend fun query(promptData: PromptData, config: String): AIResponse = core.query(promptData, config)
}
