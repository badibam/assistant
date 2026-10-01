package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * Interface for AI providers (Claude, OpenAI, DeepSeek, etc.)
 *
 * A provider declares its settings with the fields (docs/DATA.md): the schema its
 * config is held to (AIProviderSettings) and its config screen (AIProviderConfigScreen) are
 * generated from the declaration. The API key is a secret setting.
 *
 * Providers receive PromptData (raw L1-L3 + messages) and:
 * 1. Transform messages to provider-specific format
 * 2. Construct final prompt with cache breakpoints if supported
 * 3. Call provider API
 * 4. Return raw JSON response (orchestrator handles parsing)
 */
interface AIProvider {

    /**
     * Unique provider identifier
     */
    fun getProviderId(): String

    /**
     * Human-readable provider name
     */
    fun getDisplayName(): String

    /** The settings of this provider's config: api_key and model at least. */
    fun getConfigSettings(context: Context): List<SettingNode>

    /** What the config screen says under the form: where to get a key, what the models are. */
    fun getConfigHelp(context: Context): String

    /**
     * The settings listing the models needs, by name: the config screen offers to list them once
     * these are filled in, and lists them again when one changes. The API key, for a provider
     * whose address is its own.
     */
    fun modelListingSettings(): List<String> = listOf("api_key")

    /** The models [config] gives access to, for the config screen to offer them. */
    suspend fun listModels(config: JSONObject): ProviderModels

    /**
     * Why [config] cannot be stored, beyond what its schema checks, or null when it can: a rule
     * between settings or on a value's form, which the field types do not say. No such rule by default.
     */
    fun configError(config: JSONObject, context: Context): String? = null

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

/** A model a provider offers: its identifier, and the name it is shown under. */
data class ProviderModel(val id: String, val label: String)

/** The models a provider listed, or why it could not. */
data class ProviderModels(val models: List<ProviderModel>, val error: String? = null)

/**
 * Longest answer asked of a provider when its config sets none. Without streaming nothing arrives
 * before the whole answer is generated: a long answer is a long silent wait, which the read
 * timeout has to cover and a mobile network may cut. Anthropic advises about 16k without streaming.
 */
const val DEFAULT_MAX_OUTPUT_TOKENS = 16_000

/** Longest answer a provider's config may ask for. */
const val MAX_OUTPUT_TOKENS = 32_000
