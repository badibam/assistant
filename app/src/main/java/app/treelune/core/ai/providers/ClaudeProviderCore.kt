package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.ai.data.PromptData
import app.treelune.core.utils.LogManager
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.Strings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Endpoint serving the Anthropic Messages API format
 *
 * DeepSeek accepts this format on its /anthropic endpoint (x-api-key supported,
 * anthropic-version ignored), so both vendors share ClaudeProviderCore.
 * Checked on https://api-docs.deepseek.com/guides/anthropic_api, 2026-09-19.
 *
 * @param messagesUrl POST endpoint for queries
 * @param modelsUrl GET endpoint listing available models
 * @param stringPrefix Prefix of the vendor-specific strings (API key label, help, schema texts)
 * @param factsProvider The vendor's name in the providers' facts (ProviderFacts)
 * @param effortsFromFacts A model's effort levels come from the facts; otherwise from the model
 *   list, which declares them (capabilities.effort)
 * @param effortRequired The vendor's default effort is unknown: an effort is always chosen
 * @param verifiesAnsweringModel Reject a response whose "model" differs from the requested one
 */
internal enum class MessagesApi(
    val messagesUrl: String,
    val modelsUrl: String,
    val stringPrefix: String,
    val factsProvider: String,
    val effortsFromFacts: Boolean,
    val effortRequired: Boolean,
    val verifiesAnsweringModel: Boolean
) {
    ANTHROPIC(
        messagesUrl = "https://api.anthropic.com/v1/messages",
        modelsUrl = "https://api.anthropic.com/v1/models",
        stringPrefix = "ai_provider_claude",
        factsProvider = "anthropic",
        effortsFromFacts = false,
        effortRequired = false,
        // Aliases resolve to dated IDs, so the answering model legitimately differs
        verifiesAnsweringModel = false
    ),

    // Its effort levels are read from the facts (deepseek-effort-levels), and so is the default
    // being unknown (deepseek-default-effort-unknown), hence an effort always chosen. Its model
    // list now declares effort too (effort.supported_levels, effort.default_level), unread: the
    // fate of those facts waits in provider-facts. It declares the images a model reads at the
    // top level (input_modalities), where Anthropic's has capabilities.
    // Model substitution, measured 2026-09-19: an unknown ID gets an explicit error, but claude-*
    // names are answered by deepseek-flash, which the response "model" field reveals. Valid IDs
    // come back unchanged, so any mismatch is rejected rather than silently billed as another model.
    DEEPSEEK(
        messagesUrl = "https://api.deepseek.com/anthropic/v1/messages",
        modelsUrl = "https://api.deepseek.com/models",
        stringPrefix = "ai_provider_deepseek",
        factsProvider = "deepseek",
        effortsFromFacts = true,
        effortRequired = true,
        verifiesAnsweringModel = true
    )
}

/**
 * Whether the model list says the model reads images: Anthropic's by capabilities.image_input,
 * DeepSeek's by input_modalities; null when it says nothing.
 */
internal fun MessagesApi.listedImageInput(model: JSONObject): Boolean? = when (this) {
    MessagesApi.ANTHROPIC -> model.optJSONObject("capabilities")?.optJSONObject("image_input")
        ?.takeIf { it.has("supported") }?.getBoolean("supported")
    // An "image" among its input_modalities: DeepSeek's list, OpenAI's format
    MessagesApi.DEEPSEEK -> model.optJSONArray("input_modalities")
        ?.let { modalities -> (0 until modalities.length()).any { modalities.optString(it) == "image" } }
}

/**
 * Core implementation for Claude AI Provider variants
 *
 * Contains all shared logic for Claude API integration:
 * - HTTP client configuration with 2-minute timeout
 * - Declaration of the config's settings
 * - Model fetching from Claude API
 * - Query execution with prompt caching
 * - Response parsing with token metrics
 *
 * This internal class is used by public provider variants:
 * - ClaudeStandardProvider, ClaudeEconomicProvider (MessagesApi.ANTHROPIC)
 * - DeepSeekStandardProvider, DeepSeekEconomicProvider (MessagesApi.DEEPSEEK)
 *
 * Each variant creates its own core instance with a unique variantId,
 * allowing separate configurations while sharing all implementation code.
 *
 * @param context Android context for database and file access
 * @param variantId Unique identifier for this variant (e.g., "claude_standard", "deepseek_economic")
 * @param api Endpoint and vendor specifics for this variant
 */
internal class ClaudeProviderCore(
    private val context: Context,
    private val variantId: String,
    val api: MessagesApi
) {

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
        // Failing to connect means nothing was sent: the sooner it is known, the sooner an
        // automation waits for the network, at no cost
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        // No streaming: nothing arrives until the whole answer is generated, so this covers
        // the full generation of a long answer
        private const val READ_TIMEOUT_MINUTES = 10L
        private const val WRITE_TIMEOUT_MINUTES = 2L
    }

    // Shared OkHttp client instance with configured timeouts
    // Lazy initialization ensures client is only created when needed
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(WRITE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    // ========================================================================================
    // Settings
    // ========================================================================================

    /**
     * The settings of this variant's config: its API key (secret), the model, the longest answer,
     * and the reasoning (ReasoningSettings), offered by the config screen as the model allows.
     */
    fun configSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        // MEDIUM: an API key runs past SHORT's 60 characters (an OpenRouter key over 70, a Claude one over 100)
        val text = { name: String -> FieldDefinition(name, "", null, FieldType.TEXT, false, mapOf("length" to TextLength.MEDIUM.name)) }
        return listOf(
            SettingNode.Field(text("api_key").copy(displayName = s.shared("${api.stringPrefix}_api_key"),
                description = s.shared("${api.stringPrefix}_schema_api_key")), required = true, secret = true),
            // Chosen among the models the key gives access to, by the config screen
            SettingNode.Field(text("model").copy(displayName = s.shared("ai_provider_model"),
                description = s.shared("${api.stringPrefix}_schema_model")), required = true),
            SettingNode.Field(FieldDefinition("max_tokens", s.shared("ai_provider_claude_max_tokens"), s.shared("ai_provider_claude_schema_max_tokens"),
                FieldType.NUMERIC, false, mapOf("min" to 1, "max" to MAX_OUTPUT_TOKENS, "decimals" to 0)), default = DEFAULT_MAX_OUTPUT_TOKENS)
        ) + ReasoningSettings.nodes(s, thinkingOff = true) + ImageInput.node(s)
    }

    /** Why [config] cannot be stored: its reasoning settings out of what the model allows. */
    fun configError(config: JSONObject, context: Context): String? =
        ReasoningSettings.error(config, ProviderFacts.of(context), api.factsProvider, api.effortsFromFacts, api.effortRequired,
            Strings.`for`(context = context))

    /** Where to get a key, and what the models are. */
    fun configHelp(context: Context): String = Strings.`for`(context = context).shared("${api.stringPrefix}_help")

    // ========================================================================================
    // Public API for Provider Variants
    // ========================================================================================

    /**
     * Fetch available models from Claude API
     *
     * Makes HTTP GET request to /v1/models endpoint to retrieve list of available models.
     * Also triggers background refresh of model pricing data (non-blocking).
     *
     * @param apiKey The Claude API key for authentication
     * @return The models, or the error that prevented listing them
     */
    suspend fun fetchAvailableModels(apiKey: String): ProviderModels = withContext(Dispatchers.IO) {
        try {
            LogManager.aiService("ClaudeProviderCore.fetchAvailableModels() - Variant: $variantId")

            // Build request: DeepSeek lists models on its OpenAI-format endpoint (Bearer auth)
            val requestBuilder = Request.Builder().url(api.modelsUrl).get()
            when (api) {
                MessagesApi.ANTHROPIC -> requestBuilder
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                MessagesApi.DEEPSEEK -> requestBuilder
                    .header("Authorization", "Bearer $apiKey")
            }
            val request = requestBuilder.build()

            // Execute request
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                LogManager.aiService("Claude API error: ${response.code} - $errorBody", "ERROR")

                // Try to parse error message from response
                val errorMessage = try {
                    val errorJson = JSONObject(errorBody)
                    val errorObj = errorJson.optJSONObject("error")
                    errorObj?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }

                return@withContext ProviderModels(emptyList(), errorMessage)
            }

            // Parse successful response
            val responseBody = response.body?.string() ?: "{}"
            val jsonResponse = JSONObject(responseBody)
            val dataArray = jsonResponse.optJSONArray("data")

            if (dataArray == null) {
                LogManager.aiService("Claude API response missing 'data' field", "ERROR")
                return@withContext ProviderModels(emptyList(), "Invalid API response")
            }

            // Parse models, each with what its config may set of its reasoning
            val facts = ProviderFacts.of(context)
            val models = mutableListOf<ProviderModel>()
            for (i in 0 until dataArray.length()) {
                val modelObj = dataArray.optJSONObject(i)
                if (modelObj != null) {
                    // The OpenAI-format model list (DeepSeek) has no display_name: the ID is the name
                    val id = modelObj.optString("id", "")
                    val listedEfforts = if (api.effortsFromFacts) null else declaredEfforts(modelObj)
                    models.add(ProviderModel(id = id, label = modelObj.optString("display_name", "").ifEmpty { id },
                        reasoning = ReasoningSettings.of(facts, api.factsProvider, id, listedEfforts, api.effortRequired),
                        readsImages = api.listedImageInput(modelObj)))
                }
            }

            LogManager.aiService("Successfully fetched ${models.size} models from Claude API")

            ProviderModels(models)

        } catch (e: Exception) {
            LogManager.aiService("Failed to fetch Claude models: ${e.message}", "ERROR", e)
            ProviderModels(emptyList(), e.message ?: "Unknown error")
        }
    }


    /**
     * The effort levels Anthropic's model list declares for a model, in its order: the keys of
     * capabilities.effort whose "supported" is true. Empty when it declares none.
     */
    private fun declaredEfforts(model: JSONObject): List<String> {
        val effort = model.optJSONObject("capabilities")?.optJSONObject("effort") ?: return emptyList()
        if (!effort.optBoolean("supported")) return emptyList()
        return effort.keys().asSequence()
            .filter { effort.optJSONObject(it)?.optBoolean("supported") == true }
            .toList()
    }

    /**
     * Send query to Claude API with PromptData
     *
     * Implementation:
     * - Transform PromptData to Claude Messages API format via toClaudeJson()
     * - Apply cache_control breakpoints (L1, L2, last message)
     * - Make HTTP POST to /v1/messages
     * - Parse response with cache metrics via toClaudeAIResponse()
     * - Save raw prompt to file for debugging
     *
     * @param promptData Raw prompt data from PromptManager
     * @param config Provider configuration JSON with api_key, model, max_tokens
     * @return AIResponse with content, token counts, and cache metrics
     */
    suspend fun query(promptData: PromptData, config: String): AIResponse = withContext(Dispatchers.IO) {
        LogManager.aiService("ClaudeProviderCore.query() - Variant: $variantId, Messages: ${promptData.sessionMessages.size}")

        try {
            // Read the config through its declaration: an absent max_tokens is its default
            val settings = app.treelune.core.fields.settings.SettingValues(configSettings(context), JSONObject(config))
            val apiKey = settings.string("api_key") ?: error("Provider config has no api_key")
            val requestedModel = settings.string("model") ?: error("Provider config has no model")

            // Transform PromptData to Claude JSON via extension
            val requestJson = promptData.toClaudeJson(
                model = requestedModel,
                maxTokens = settings.number("max_tokens")!!.toInt(),
                effort = settings.string(ReasoningSettings.EFFORT),
                thinking = ReasoningSettings.thinkingType(settings.boolean(ReasoningSettings.THINKING_OFF), ProviderFacts.of(context),
                    api.factsProvider, requestedModel),
                datetimeText = promptData.buildDatetimeMessage(context),
                imageData = { app.treelune.core.ai.enrichments.AttachedImages.base64(context, it) }
            )
            val requestBody = requestJson.toString()

            LogManager.aiService("Built Claude request: ${requestBody.length} characters")

            // The prompt goes whole to files below: a log line would keep only its start
            val prettyJson = Json { prettyPrint = true }
            val formattedPrompt = prettyJson.encodeToString(JsonObject.serializer(), requestJson)

            // Save prompts to files for debugging (overwrites previous)
            // Accessible via: adb pull /data/data/app.treelune/files/last_prompt_*
            try {
                // Save Level 1 content with REAL line breaks (human-readable)
                val level1File = File(context.filesDir, "last_prompt_level1_${variantId}.txt")
                level1File.writeText(promptData.level1Content)
                LogManager.aiService("Level 1 content (with real line breaks) saved to: ${level1File.absolutePath}", "DEBUG")

                // Save full JSON (with escaped \n)
                val jsonFile = File(context.filesDir, "last_prompt_json_${variantId}.txt")
                jsonFile.writeText(formattedPrompt)
                LogManager.aiService("Full JSON prompt saved to: ${jsonFile.absolutePath}", "DEBUG")
            } catch (e: Exception) {
                LogManager.aiService("Failed to save prompt to file: ${e.message}", "WARN")
            }

            // Build HTTP request
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(api.messagesUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .post(requestBody.toRequestBody(mediaType))
                .build()

            // Execute request: cancelling the session's call cancels this one
            val response = httpClient.awaitReply(request)
            val responseBody = response.body

            if (!response.isSuccessful) {
                LogManager.aiService("Claude API error: ${response.code} - $responseBody", "ERROR")

                // Parse error message
                val errorMessage = try {
                    val errorJson = Json.parseToJsonElement(responseBody).jsonObject
                    val errorObj = errorJson["error"]?.jsonObject
                    errorObj?.get("message")?.jsonPrimitive?.content ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }

                return@withContext AIResponse(
                    success = false,
                    content = "",
                    errorMessage = errorMessage,
                    failure = aiFailureOf(response.code),
                    tokensUsed = 0,
                    cacheWriteTokens = 0,
                    cacheReadTokens = 0,
                    inputTokens = 0
                )
            }

            // Parse successful response via extension
            LogManager.aiService("Claude API success: ${responseBody.length} characters")
            val jsonResponse = Json.parseToJsonElement(responseBody)
            val aiResponse = jsonResponse.toClaudeAIResponse()

            // Refuse a substituted model: what comes back is not what the config asked for.
            if (api.verifiesAnsweringModel && aiResponse.success) {
                val answeringModel = jsonResponse.jsonObject["model"]?.jsonPrimitive?.contentOrNull
                if (answeringModel != requestedModel) {
                    LogManager.aiService("Model substituted: requested $requestedModel, answered by $answeringModel", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = "Provider answered with model '$answeringModel' instead of '$requestedModel'.",
                        failure = AIFailure.CONFIG,
                        tokensUsed = 0,
                        cacheWriteTokens = 0,
                        cacheReadTokens = 0,
                        inputTokens = 0
                    )
                }
            }

            LogManager.aiService(
                "Claude tokens - Input: ${aiResponse.inputTokens}, " +
                "Cache write: ${aiResponse.cacheWriteTokens}, " +
                "Cache read: ${aiResponse.cacheReadTokens}, " +
                "Output: ${aiResponse.tokensUsed}"
            )

            return@withContext aiResponse

        } catch (e: CancellationException) {
            // Stop or Interrupt cut the call: nothing to report, the caller is gone
            throw e
        } catch (e: Exception) {
            LogManager.aiService("Claude query failed: ${e.message}", "ERROR", e)
            return@withContext AIResponse(
                success = false,
                content = "",
                errorMessage = "Claude API error: ${e.message}",
                failure = aiFailureOf(e),
                tokensUsed = 0,
                cacheWriteTokens = 0,
                cacheReadTokens = 0,
                inputTokens = 0
            )
        }
    }
}
