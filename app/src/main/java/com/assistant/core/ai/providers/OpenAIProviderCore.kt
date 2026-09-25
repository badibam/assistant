package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.utils.LogManager
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.strings.Strings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Core implementation for OpenAI AI Provider variants
 *
 * Contains all shared logic for OpenAI API integration:
 * - HTTP client configuration with 2-minute timeout
 * - Schema management for configuration validation
 * - Query execution with token tracking
 * - Response parsing with usage metrics
 *
 * This internal class is used by public provider variants:
 * - OpenAIStandardProvider (default model, e.g., gpt-4.1)
 * - OpenAIEconomicProvider (economic model, e.g., gpt-4.1-mini)
 *
 * Each variant creates its own core instance with a unique variantId,
 * allowing separate configurations while sharing all implementation code.
 *
 * @param context Android context for database and file access
 * @param variantId Unique identifier for this variant (e.g., "openai_standard", "openai_economic")
 */
internal class OpenAIProviderCore(
    private val context: Context,
    private val variantId: String
) {

    companion object {
        private const val OPENAI_API_BASE_URL = "https://api.openai.com"
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

    /** The settings of this variant's config: its API key (secret), the model, temperature and longest answer. */
    fun configSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        fun text(name: String, secret: Boolean = false) = SettingNode.Field(
            FieldDefinition(name, s.shared("ai_provider_openai_$name"), s.shared("ai_provider_openai_schema_$name"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name)),
            required = true, secret = secret)
        return listOf(
            text("api_key", secret = true),
            // Chosen among the models the key gives access to, by the config screen
            text("model"),
            SettingNode.Field(FieldDefinition("temperature", s.shared("ai_provider_openai_temperature"), s.shared("ai_provider_openai_schema_temperature"),
                FieldType.NUMERIC, false, mapOf("min" to 0, "max" to 2, "decimals" to 1)), default = 1.0),
            SettingNode.Field(FieldDefinition("max_output_tokens", s.shared("ai_provider_openai_max_output_tokens"), s.shared("ai_provider_openai_schema_max_output_tokens"),
                FieldType.NUMERIC, false, mapOf("min" to 1, "max" to MAX_OUTPUT_TOKENS, "decimals" to 0)), default = DEFAULT_MAX_OUTPUT_TOKENS)
        )
    }

    /** Where to get a key, and what temperature does. */
    fun configHelp(context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("ai_provider_openai_help") + "\n\n" + s.shared("ai_provider_openai_temperature_help")
    }

    // ========================================================================================
    // Public API for Provider Variants
    // ========================================================================================

    /**
     * Fetch available models from OpenAI API
     *
     * Makes HTTP GET request to /v1/models endpoint to retrieve list of available models.
     *
     * @param apiKey The OpenAI API key for authentication
     * @return The models, or the error that prevented listing them
     */
    suspend fun fetchAvailableModels(apiKey: String): ProviderModels = withContext(Dispatchers.IO) {
        try {
            LogManager.aiService("OpenAIProviderCore.fetchAvailableModels() - Variant: $variantId")

            // Build request
            val request = Request.Builder()
                .url("$OPENAI_API_BASE_URL/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            // Execute request
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                LogManager.aiService("OpenAI API error: ${response.code} - $errorBody", "ERROR")

                // Try to parse error message from response
                val errorMessage = try {
                    val errorJson = Json.parseToJsonElement(errorBody).jsonObject
                    val errorObj = errorJson["error"]?.jsonObject
                    errorObj?.get("message")?.jsonPrimitive?.content ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }

                return@withContext ProviderModels(emptyList(), errorMessage)
            }

            // Parse successful response
            val responseBody = response.body?.string() ?: "{}"
            val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
            val dataArray = jsonResponse["data"]?.jsonArray

            if (dataArray == null) {
                LogManager.aiService("OpenAI API response missing 'data' field", "ERROR")
                return@withContext ProviderModels(emptyList(), "Invalid API response")
            }

            // Parse models
            // The list names models by their identifier alone
            val models = dataArray.mapNotNull { element ->
                element.jsonObject["id"]?.jsonPrimitive?.content?.let { ProviderModel(id = it, label = it) }
            }

            LogManager.aiService("Successfully fetched ${models.size} models from OpenAI API")

            ProviderModels(models)

        } catch (e: Exception) {
            LogManager.aiService("Failed to fetch OpenAI models: ${e.message}", "ERROR", e)
            ProviderModels(emptyList(), e.message ?: "Unknown error")
        }
    }

    /**
     * Send query to OpenAI API with PromptData
     *
     * Implementation:
     * - Transform PromptData to OpenAI Responses API format via toOpenAIJson()
     * - Make HTTP POST to /v1/responses
     * - Parse response with token metrics via toOpenAIResponse()
     * - Save raw prompt to file for debugging
     *
     * OpenAI API format:
     * - Structured messages array with roles (system, user, assistant)
     * - Similar structure to Claude but no explicit prompt caching
     * - Token metrics: input (total), cached, output
     *
     * @param promptData Raw prompt data from PromptManager
     * @param config Provider configuration JSON with api_key, model, temperature, max_output_tokens
     * @return AIResponse with content, token counts, and usage metrics
     */
    suspend fun query(promptData: PromptData, config: String): AIResponse = withContext(Dispatchers.IO) {
        LogManager.aiService("OpenAIProviderCore.query() - Variant: $variantId, Messages: ${promptData.sessionMessages.size}")

        try {
            // Read the config through its declaration: an absent setting is its default
            val settings = com.assistant.core.fields.settings.SettingValues(configSettings(context), org.json.JSONObject(config))
            val apiKey = settings.string("api_key") ?: error("Provider config has no api_key")

            // Transform PromptData to OpenAI JSON via extension
            val requestJson = promptData.toOpenAIJson(
                model = settings.string("model") ?: error("Provider config has no model"),
                temperature = settings.number("temperature")!!.toDouble(),
                maxOutputTokens = settings.number("max_output_tokens")!!.toInt(),
                datetimeText = promptData.buildDatetimeMessage(context)
            )
            val requestBody = requestJson.toString()

            LogManager.aiService("Built OpenAI request: ${requestBody.length} characters")

            // The prompt goes whole to a file below: a log line would keep only its start
            val prettyJson = Json { prettyPrint = true }
            val formattedPrompt = prettyJson.encodeToString(JsonObject.serializer(), requestJson)

            // Save raw prompt to file for debugging (overwrites previous)
            // Accessible via: adb pull /data/data/com.assistant/files/last_prompt_openai_<variant>.txt
            try {
                val debugFile = File(context.filesDir, "last_prompt_openai_${variantId}.txt")
                debugFile.writeText(formattedPrompt)
                LogManager.aiService("Raw prompt saved to: ${debugFile.absolutePath}", "DEBUG")
            } catch (e: Exception) {
                LogManager.aiService("Failed to save prompt to file: ${e.message}", "WARN")
            }

            // Build HTTP request
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("$OPENAI_API_BASE_URL/v1/responses")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody(mediaType))
                .build()

            // Execute request: cancelling the session's call cancels this one
            val response = httpClient.awaitReply(request)
            val responseBody = response.body

            if (!response.isSuccessful) {
                LogManager.aiService("OpenAI API error: ${response.code} - $responseBody", "ERROR")

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
            LogManager.aiService("OpenAI API success: ${responseBody.length} characters")

            // Save raw response to file for debugging (overwrites previous)
            try {
                val responseFile = File(context.filesDir, "last_response_openai_${variantId}.txt")
                responseFile.writeText(responseBody)
                LogManager.aiService("Raw response saved to: ${responseFile.absolutePath}", "DEBUG")
            } catch (e: Exception) {
                LogManager.aiService("Failed to save response to file: ${e.message}", "WARN")
            }

            val jsonResponse = Json.parseToJsonElement(responseBody)
            val aiResponse = jsonResponse.toOpenAIResponse()

            LogManager.aiService(
                "OpenAI tokens - Input: ${aiResponse.inputTokens}, " +
                "Cached: ${aiResponse.cacheReadTokens}, " +
                "Output: ${aiResponse.tokensUsed}"
            )

            return@withContext aiResponse

        } catch (e: CancellationException) {
            // Stop or Interrupt cut the call: nothing to report, the caller is gone
            throw e
        } catch (e: Exception) {
            LogManager.aiService("OpenAI query failed: ${e.message}", "ERROR", e)
            return@withContext AIResponse(
                success = false,
                content = "",
                errorMessage = "OpenAI API error: ${e.message}",
                failure = aiFailureOf(e),
                tokensUsed = 0,
                cacheWriteTokens = 0,
                cacheReadTokens = 0,
                inputTokens = 0
            )
        }
    }
}
