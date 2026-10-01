package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.AIMessageSchemas
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.prompts.SchemaModelView
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingValues
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Core of the OpenAI-compatible provider variants: any server speaking Chat Completions at an
 * address of the user's (Ollama, llama.cpp, vLLM, LM Studio, OpenRouter, a rented GPU), where the
 * OpenAI provider speaks OpenAI's own Responses API.
 *
 * Its config: the server's base address (https only, isHttpsAddress), an API key that a local
 * server may not ask for, the model the server lists, how far the answer is forced to a form
 * (OutputForcing), the temperature and the longest answer.
 *
 * @param variantId Unique identifier of the variant, which names its config and its debug files
 */
internal class OpenAICompatibleProviderCore(
    private val context: Context,
    private val variantId: String
) {

    companion object {
        // Failing to connect means nothing was sent: the sooner it is known, the sooner an
        // automation waits for the network, at no cost
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        // No streaming: nothing arrives until the whole answer is generated, so this covers
        // reading the prompt and generating a long answer, slower on a server of one's own
        private const val READ_TIMEOUT_MINUTES = 10L
        private const val WRITE_TIMEOUT_MINUTES = 2L
    }

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

    /** The settings of this variant's config. */
    fun configSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        fun text(name: String, required: Boolean, secret: Boolean = false) = SettingNode.Field(
            FieldDefinition(name, s.shared("ai_provider_compatible_$name"), s.shared("ai_provider_compatible_schema_$name"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name)),
            required = required, secret = secret)
        val forcings = OutputForcing.entries.map { it.stored }
        return listOf(
            text("base_url", required = true),
            text("api_key", required = false, secret = true),
            // Chosen among the models the server lists, by the config screen
            text("model", required = true),
            // An explicit choice: what a server accepts varies, and the level is what the bench compares
            SettingNode.Field(FieldDefinition("output_forcing", s.shared("ai_provider_compatible_output_forcing"), s.shared("ai_provider_compatible_schema_output_forcing"),
                FieldType.CHOICE, false, mapOf("options" to ChoiceSettings.storedOptions(forcings,
                    forcings.associateWith { s.shared("ai_provider_compatible_output_forcing_$it") }))), required = true),
            SettingNode.Field(FieldDefinition("temperature", s.shared("ai_provider_openai_temperature"), s.shared("ai_provider_openai_schema_temperature"),
                FieldType.NUMERIC, false, mapOf("min" to 0, "max" to 2, "decimals" to 1)), default = 1.0),
            SettingNode.Field(FieldDefinition("max_output_tokens", s.shared("ai_provider_openai_max_output_tokens"), s.shared("ai_provider_openai_schema_max_output_tokens"),
                FieldType.NUMERIC, false, mapOf("min" to 1, "max" to MAX_OUTPUT_TOKENS, "decimals" to 0)), default = DEFAULT_MAX_OUTPUT_TOKENS)
        )
    }

    /** What an address looks like, what the forcing levels do, and what temperature does. */
    fun configHelp(context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("ai_provider_compatible_help") + "\n\n" + s.shared("ai_provider_compatible_output_forcing_help") +
            "\n\n" + s.shared("ai_provider_openai_temperature_help")
    }

    /** An address that is not https is refused before it is stored, not when a call fails on it. */
    fun configError(config: JSONObject, context: Context): String? =
        if (isHttpsAddress(config.optString("base_url").trim())) null
        else Strings.`for`(context = context).shared("ai_provider_compatible_https_required")

    // ========================================================================================
    // Models
    // ========================================================================================

    /** The models the server at [config]'s address lists (GET /models), its key sent when there is one. */
    suspend fun fetchAvailableModels(config: JSONObject): ProviderModels = withContext(Dispatchers.IO) {
        try {
            val address = config.getString("base_url").trim()
            if (!isHttpsAddress(address)) {
                return@withContext ProviderModels(emptyList(), Strings.`for`(context = context).shared("ai_provider_compatible_https_required"))
            }
            LogManager.aiService("OpenAICompatibleProviderCore.fetchAvailableModels() - Variant: $variantId")

            val request = Request.Builder()
                .url(endpointOf(address, "models"))
                .withKey(config.optString("api_key").trim())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    LogManager.aiService("Compatible server error listing models: ${response.code} - $body", "ERROR")
                    return@withContext ProviderModels(emptyList(), errorMessageOf(body) ?: "HTTP ${response.code}")
                }
                val data = Json.parseToJsonElement(body).jsonObject["data"] as? JsonArray
                    ?: return@withContext ProviderModels(emptyList(), "Invalid API response: no 'data' list")
                // The list names models by their identifier alone
                val models = data.mapNotNull { element ->
                    (element as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.let { ProviderModel(id = it, label = it) }
                }
                LogManager.aiService("Listed ${models.size} models from the compatible server")
                ProviderModels(models)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiService("Failed to list the compatible server's models: ${e.message}", "ERROR", e)
            ProviderModels(emptyList(), e.message ?: "Unknown error")
        }
    }

    // ========================================================================================
    // Query
    // ========================================================================================

    /** Send [promptData] to the server's /chat/completions, and read its answer. */
    suspend fun query(promptData: PromptData, config: String): AIResponse = withContext(Dispatchers.IO) {
        LogManager.aiService("OpenAICompatibleProviderCore.query() - Variant: $variantId, Messages: ${promptData.sessionMessages.size}")

        try {
            // Read the config through its declaration: an absent setting is its default
            val settings = SettingValues(configSettings(context), JSONObject(config))
            val address = settings.string("base_url")?.trim() ?: error("Provider config has no base_url")
            // A config stored before the rule, or written around the service, still never goes out in clear
            if (!isHttpsAddress(address)) error(Strings.`for`(context = context).shared("ai_provider_compatible_https_required"))
            val forcing = OutputForcing.of(settings.string("output_forcing") ?: error("Provider config has no output_forcing"))

            val requestJson = promptData.toChatCompletionsJson(
                model = settings.string("model") ?: error("Provider config has no model"),
                temperature = settings.number("temperature")!!.toDouble(),
                maxTokens = settings.number("max_output_tokens")!!.toInt(),
                forcing = forcing,
                responseSchema = if (forcing == OutputForcing.SCHEMA) responseSchemaForModel() else null,
                datetimeText = promptData.buildDatetimeMessage(context)
            )
            val requestBody = requestJson.toString()
            LogManager.aiService("Built Chat Completions request: ${requestBody.length} characters")

            // The prompt goes whole to a file: a log line would keep only its start
            // Accessible via: adb pull /data/data/com.assistant/files/last_prompt_compatible_<variant>.txt
            writeDebugFile("last_prompt_compatible_${variantId}.txt", Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), requestJson))

            val request = Request.Builder()
                .url(endpointOf(address, "chat/completions"))
                .withKey(settings.string("api_key")?.trim() ?: "")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            // Cancelling the session's call cancels this one
            val response = httpClient.awaitReply(request)
            val responseBody = response.body

            if (!response.isSuccessful) {
                LogManager.aiService("Compatible server error: ${response.code} - $responseBody", "ERROR")
                return@withContext AIResponse(
                    success = false,
                    content = "",
                    errorMessage = errorMessageOf(responseBody) ?: "HTTP ${response.code}",
                    failure = aiFailureOf(response.code),
                    tokensUsed = 0,
                    cacheWriteTokens = 0,
                    cacheReadTokens = 0,
                    inputTokens = 0
                )
            }

            LogManager.aiService("Compatible server success: ${responseBody.length} characters")
            writeDebugFile("last_response_compatible_${variantId}.txt", responseBody)

            val aiResponse = Json.parseToJsonElement(responseBody).toChatCompletionsResponse()
            LogManager.aiService(
                "Compatible server tokens - Input: ${aiResponse.inputTokens}, " +
                "Cached: ${aiResponse.cacheReadTokens}, " +
                "Output: ${aiResponse.tokensUsed}"
            )
            return@withContext aiResponse

        } catch (e: CancellationException) {
            // Stop or Interrupt cut the call: nothing to report, the caller is gone
            throw e
        } catch (e: Exception) {
            LogManager.aiService("Compatible server query failed: ${e.message}", "ERROR", e)
            return@withContext AIResponse(
                success = false,
                content = "",
                errorMessage = "Compatible server error: ${e.message}",
                failure = aiFailureOf(e),
                tokensUsed = 0,
                cacheWriteTokens = 0,
                cacheReadTokens = 0,
                inputTokens = 0
            )
        }
    }

    /** The schema of the AI's answer as the model reads it, the one its prompt shows (SchemaModelView). */
    private fun responseSchemaForModel(): JsonObject {
        val stored = JSONObject(AIMessageSchemas.getAIMessageResponseSchema(context).content)
        val forModel = SchemaModelView.forModel(stored, AppConfigManager.getDateTimeConfig().getZoneId())
        return Json.parseToJsonElement(forModel.toString()).jsonObject
    }

    /** The key as a bearer token, when there is one: a local server may ask for none. */
    private fun Request.Builder.withKey(apiKey: String): Request.Builder =
        if (apiKey.isEmpty()) this else header("Authorization", "Bearer $apiKey")

    /** The message of an OpenAI-shaped error body ({"error": {"message": …}}), or null. */
    private fun errorMessageOf(body: String): String? = try {
        (Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        null
    }

    private fun writeDebugFile(name: String, content: String) {
        try {
            File(context.filesDir, name).writeText(content)
            LogManager.aiService("Debug file written: $name", "DEBUG")
        } catch (e: Exception) {
            LogManager.aiService("Failed to write debug file $name: ${e.message}", "WARN")
        }
    }
}
