package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.PromptData
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.FieldLimits
import com.assistant.core.strings.Strings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
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
) : SchemaProvider {

    companion object {
        private const val OPENAI_API_BASE_URL = "https://api.openai.com"
        private const val TIMEOUT_MINUTES = 2L  // 2 minutes timeout per HTTP request
    }

    // Shared OkHttp client instance with configured timeouts
    // Lazy initialization ensures client is only created when needed
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .readTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    // ========================================================================================
    // SchemaProvider Implementation
    // ========================================================================================

    /**
     * Get schema by ID
     * Uses variantId to generate unique schema IDs per variant
     */
    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when (schemaId) {
            "ai_provider_${variantId}_config" -> createOpenAIConfigSchema(context)
            else -> null
        }
    }

    /**
     * Get all schema IDs supported by this provider variant
     */
    override fun getAllSchemaIds(): List<String> {
        return listOf("ai_provider_${variantId}_config")
    }

    /**
     * Get human-readable field name for schema validation errors
     */
    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (fieldName) {
            "api_key" -> s.shared("ai_provider_openai_api_key")
            "model" -> s.shared("ai_provider_openai_model")
            "temperature" -> s.shared("ai_provider_openai_temperature")
            "max_output_tokens" -> s.shared("ai_provider_openai_max_output_tokens")
            else -> fieldName
        }
    }

    /**
     * Create configuration schema for this OpenAI variant
     *
     * Schema defines required fields:
     * - api_key: API key for OpenAI API authentication
     * - model: Model ID (e.g., "gpt-4.1", "gpt-4.1-mini")
     * - temperature: Sampling temperature (optional, default 1.0)
     * - max_output_tokens: Maximum response length (optional, default 2000)
     */
    private fun createOpenAIConfigSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        val content = """
        {
            "type": "object",
            "properties": {
                "api_key": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.MEDIUM_LENGTH},
                    "description": "${s.shared("ai_provider_openai_schema_api_key")}"
                },
                "model": {
                    "type": "string",
                    "minLength": 1,
                    "description": "${s.shared("ai_provider_openai_schema_model")}"
                },
                "temperature": {
                    "type": "number",
                    "minimum": 0.0,
                    "maximum": 2.0,
                    "default": 1.0,
                    "description": "${s.shared("ai_provider_openai_schema_temperature")}"
                },
                "max_output_tokens": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 4096,
                    "default": 2000,
                    "description": "${s.shared("ai_provider_openai_schema_max_output_tokens")}"
                }
            },
            "required": ["api_key", "model"],
            "additionalProperties": false
        }
        """.trimIndent()

        return Schema(
            id = "ai_provider_${variantId}_config",
            displayName = s.shared("ai_provider_openai_config_display_name"),
            description = s.shared("ai_provider_openai_config_description"),
            category = SchemaCategory.AI_PROVIDER,
            content = content
        )
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
     * @return OpenAIFetchModelsResult with success status, models list, and optional error
     */
    suspend fun fetchAvailableModels(apiKey: String): OpenAIFetchModelsResult = withContext(Dispatchers.IO) {
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

                return@withContext OpenAIFetchModelsResult(
                    success = false,
                    models = emptyList(),
                    errorMessage = errorMessage
                )
            }

            // Parse successful response
            val responseBody = response.body?.string() ?: "{}"
            val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
            val dataArray = jsonResponse["data"]?.jsonArray

            if (dataArray == null) {
                LogManager.aiService("OpenAI API response missing 'data' field", "ERROR")
                return@withContext OpenAIFetchModelsResult(
                    success = false,
                    models = emptyList(),
                    errorMessage = "Invalid API response"
                )
            }

            // Parse models
            val models = mutableListOf<OpenAIModelInfo>()
            dataArray.forEach { modelElement ->
                val modelObj = modelElement.jsonObject
                models.add(
                    OpenAIModelInfo(
                        id = modelObj["id"]?.jsonPrimitive?.content ?: "",
                        created = modelObj["created"]?.jsonPrimitive?.long ?: 0L,
                        ownedBy = modelObj["owned_by"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }

            LogManager.aiService("Successfully fetched ${models.size} models from OpenAI API")

            OpenAIFetchModelsResult(
                success = true,
                models = models,
                errorMessage = null
            )

        } catch (e: Exception) {
            LogManager.aiService("Failed to fetch OpenAI models: ${e.message}", "ERROR", e)
            OpenAIFetchModelsResult(
                success = false,
                models = emptyList(),
                errorMessage = e.message ?: "Unknown error"
            )
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
            // Parse config
            val configJson = org.json.JSONObject(config)
            val apiKey = configJson.getString("api_key")

            // Transform PromptData to OpenAI JSON via extension
            val requestJson = promptData.toOpenAIJson(configJson)
            val requestBody = requestJson.toString()

            LogManager.aiService("Built OpenAI request: ${requestBody.length} characters")

            // Log raw prompt for debugging (VERBOSE level) - formatted for maximum readability
            val prettyJson = Json { prettyPrint = true }
            val formattedPrompt = prettyJson.encodeToString(JsonObject.serializer(), requestJson)
            LogManager.aiService("=== RAW PROMPT TO OPENAI API ===\n$formattedPrompt\n=== END RAW PROMPT ===", "VERBOSE")

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

            // Execute request
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

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

        } catch (e: Exception) {
            LogManager.aiService("OpenAI query failed: ${e.message}", "ERROR", e)
            return@withContext AIResponse(
                success = false,
                content = "",
                errorMessage = "OpenAI API error: ${e.message}",
                tokensUsed = 0,
                cacheWriteTokens = 0,
                cacheReadTokens = 0,
                inputTokens = 0
            )
        }
    }
}
