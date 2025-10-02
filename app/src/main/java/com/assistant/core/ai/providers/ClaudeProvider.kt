package com.assistant.core.ai.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.ai.providers.ui.ClaudeConfigScreen
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
import com.assistant.core.strings.Strings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Claude AI Provider - Stub implementation for testing complete flow
 */
class ClaudeProvider : AIProvider {

    companion object {
        private const val CLAUDE_API_BASE_URL = "https://api.anthropic.com"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val TIMEOUT_SECONDS = 30L
    }

    // Shared OkHttp client instance
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
     * Send query to Claude API
     * TODO: Implement real Claude API call with authentication and error handling
     */
    override suspend fun query(prompt: String, config: String): AIResponse {
        LogManager.aiService("ClaudeProvider.query() called with prompt length: ${prompt.length}")
        LogManager.aiService("Claude config: $config")

        try {
            // TODO: Parse config JSON to extract api_key, model, max_tokens
            // TODO: Make actual HTTP request to Claude API
            // TODO: Parse response and convert to AIMessage structure

            // Stub implementation for flow testing
            LogManager.aiService("TODO: Implement real Claude API call")

            val stubResponse = """
            {
                "preText": "Je suis Claude, assistant IA créé par Anthropic. Voici ma réponse de test.",
                "actions": null,
                "postText": "Ceci est une réponse stub pour tester le flow complet.",
                "communicationModule": null
            }
            """.trimIndent()

            return AIResponse(
                success = true,
                content = stubResponse,
                errorMessage = null,
                tokensUsed = 42 // Stub token count
            )

        } catch (e: Exception) {
            LogManager.aiService("Claude query failed: ${e.message}", "ERROR", e)
            return AIResponse(
                success = false,
                content = "",
                errorMessage = "Claude API error: ${e.message}",
                tokensUsed = 0
            )
        }
    }
}