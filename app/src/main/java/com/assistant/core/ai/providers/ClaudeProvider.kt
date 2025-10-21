package com.assistant.core.ai.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.providers.ui.ClaudeConfigScreen
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
import com.assistant.core.strings.Strings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Model information from Claude API
 */
data class ClaudeModelInfo(
    val id: String,
    val displayName: String,
    val createdAt: String
)

/**
 * Result of fetching available models
 */
data class FetchModelsResult(
    val success: Boolean,
    val models: List<ClaudeModelInfo>,
    val errorMessage: String?
)

/**
 * Claude AI Provider
 */
class ClaudeProvider(private val context: Context) : AIProvider {

    companion object {
        private const val CLAUDE_API_BASE_URL = "https://api.anthropic.com"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val TIMEOUT_MINUTES = 2L  // 2 minutes timeout per HTTP request
    }

    // Shared OkHttp client instance
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .readTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    override fun getProviderId(): String = "claude"

    override fun getDisplayName(): String = "Claude (Anthropic)"

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when (schemaId) {
            "ai_provider_claude_config" -> createClaudeConfigSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("ai_provider_claude_config")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (fieldName) {
            "api_key" -> s.shared("ai_provider_claude_api_key")
            "model" -> s.shared("ai_provider_claude_model")
            "max_tokens" -> s.shared("ai_provider_claude_max_tokens")
            else -> fieldName
        }
    }

    private fun createClaudeConfigSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        val content = """
        {
            "type": "object",
            "properties": {
                "api_key": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.MEDIUM_LENGTH},
                    "description": "${s.shared("ai_provider_claude_schema_api_key")}"
                },
                "model": {
                    "type": "string",
                    "minLength": 1,
                    "description": "${s.shared("ai_provider_claude_schema_model")}"
                },
                "max_tokens": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 4096,
                    "default": 2000,
                    "description": "${s.shared("ai_provider_claude_schema_max_tokens")}"
                }
            },
            "required": ["api_key", "model"],
            "additionalProperties": false
        }
        """.trimIndent()

        return Schema(
            id = "ai_provider_claude_config",
            displayName = s.shared("ai_provider_claude_config_display_name"),
            description = s.shared("ai_provider_claude_config_description"),
            category = SchemaCategory.AI_PROVIDER,
            content = content
        )
    }

    /**
     * Fetch available models from Claude API
     *
     * @param apiKey The Claude API key for authentication
     * @return FetchModelsResult with success status, models list, and optional error
     */
    suspend fun fetchAvailableModels(apiKey: String): FetchModelsResult = withContext(Dispatchers.IO) {
        try {
            LogManager.aiService("ClaudeProvider.fetchAvailableModels() - Fetching models from API")

            // Refresh model prices in parallel (non-blocking)
            launch {
                try {
                    com.assistant.core.ai.utils.ModelPriceManager.refresh()
                } catch (e: Exception) {
                    LogManager.aiService("Failed to refresh model prices: ${e.message}", "WARN")
                }
            }

            // Build request
            val request = Request.Builder()
                .url("$CLAUDE_API_BASE_URL/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .get()
                .build()

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

                return@withContext FetchModelsResult(
                    success = false,
                    models = emptyList(),
                    errorMessage = errorMessage
                )
            }

            // Parse successful response
            val responseBody = response.body?.string() ?: "{}"
            val jsonResponse = JSONObject(responseBody)
            val dataArray = jsonResponse.optJSONArray("data")

            if (dataArray == null) {
                LogManager.aiService("Claude API response missing 'data' field", "ERROR")
                return@withContext FetchModelsResult(
                    success = false,
                    models = emptyList(),
                    errorMessage = "Invalid API response"
                )
            }

            // Parse models
            val models = mutableListOf<ClaudeModelInfo>()
            for (i in 0 until dataArray.length()) {
                val modelObj = dataArray.optJSONObject(i)
                if (modelObj != null) {
                    models.add(
                        ClaudeModelInfo(
                            id = modelObj.optString("id", ""),
                            displayName = modelObj.optString("display_name", ""),
                            createdAt = modelObj.optString("created_at", "")
                        )
                    )
                }
            }

            LogManager.aiService("Successfully fetched ${models.size} models from Claude API")

            FetchModelsResult(
                success = true,
                models = models,
                errorMessage = null
            )

        } catch (e: Exception) {
            LogManager.aiService("Failed to fetch Claude models: ${e.message}", "ERROR", e)
            FetchModelsResult(
                success = false,
                models = emptyList(),
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Configuration UI for Claude provider
     */
    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        ClaudeConfigScreen(
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    /**
     * Send query to Claude API with PromptData
     *
     * Implementation:
     * - Transform PromptData to Claude Messages API format via toClaudeJson()
     * - Apply cache_control breakpoints (L1, L2, L3, last message)
     * - Make HTTP POST to /v1/messages
     * - Parse response with cache metrics via toClaudeAIResponse()
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse = withContext(Dispatchers.IO) {
        LogManager.aiService("ClaudeProvider.query() called with ${promptData.sessionMessages.size} messages")

        try {
            // Parse config
            val configJson = JSONObject(config)
            val apiKey = configJson.getString("api_key")

            // Transform PromptData to Claude JSON via extension
            val requestJson = promptData.toClaudeJson(configJson)
            val requestBody = requestJson.toString()

            LogManager.aiService("Built Claude request: ${requestBody.length} characters")
            LogManager.aiService("Request includes ${promptData.sessionMessages.size} messages")

            // Log raw prompt for debugging (VERBOSE level) - formatted for maximum readability
            val prettyJson = Json { prettyPrint = true }
            val formattedPrompt = prettyJson.encodeToString(JsonObject.serializer(), requestJson)
            LogManager.aiService("=== RAW PROMPT TO CLAUDE API ===\n$formattedPrompt\n=== END RAW PROMPT ===", "VERBOSE")

            // Save raw prompt to file for debugging (overwrites previous)
            // Accessible via: adb pull /data/data/com.assistant/files/last_prompt_claude.txt
            try {
                val debugFile = File(context.filesDir, "last_prompt_claude.txt")
                debugFile.writeText(formattedPrompt)
                LogManager.aiService("Raw prompt saved to: ${debugFile.absolutePath}", "DEBUG")
            } catch (e: Exception) {
                LogManager.aiService("Failed to save prompt to file: ${e.message}", "WARN")
            }

            // Build HTTP request
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("$CLAUDE_API_BASE_URL/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .post(requestBody.toRequestBody(mediaType))
                .build()

            // Execute request
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

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

            LogManager.aiService(
                "Claude tokens - Input: ${aiResponse.inputTokens}, " +
                "Cache write: ${aiResponse.cacheWriteTokens}, " +
                "Cache read: ${aiResponse.cacheReadTokens}, " +
                "Output: ${aiResponse.tokensUsed}"
            )

            return@withContext aiResponse

        } catch (e: Exception) {
            LogManager.aiService("Claude query failed: ${e.message}", "ERROR", e)
            return@withContext AIResponse(
                success = false,
                content = "",
                errorMessage = "Claude API error: ${e.message}",
                tokensUsed = 0,
                cacheWriteTokens = 0,
                cacheReadTokens = 0,
                inputTokens = 0
            )
        }
    }
}