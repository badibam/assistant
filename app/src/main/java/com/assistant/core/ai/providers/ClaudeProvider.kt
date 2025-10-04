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
     * Send query to Claude API with PromptData
     *
     * Provider responsibilities:
     * - Transform PromptData to Claude API format (messages array)
     * - Fuse SystemMessages with formattedData into conversation history
     * - Add cache breakpoints for L1-L3 (Claude prompt caching)
     * - Make API call
     * - Return raw JSON response
     *
     * TODO: Implement real Claude API call with authentication and caching
     */
    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        LogManager.aiService("ClaudeProvider.query() called with ${promptData.sessionMessages.size} messages")
        LogManager.aiService("Claude config: $config")

        try {
            // TODO: Parse config JSON to extract api_key, model, max_tokens
            // val configJson = JSONObject(config)
            // val apiKey = configJson.getString("api_key")
            // val model = configJson.getString("model")
            // val maxTokens = configJson.getInt("max_tokens")

            // Build prompt from PromptData
            // TODO: Transform to Claude Messages API format with cache breakpoints
            val prompt = buildPromptFromData(promptData)

            LogManager.aiService("Built prompt: ${prompt.length} characters")
            LogManager.aiService("TODO: Implement real Claude API call with prompt caching")

            // Stub implementation for flow testing
            val stubResponse = """
            {
                "preText": "Je suis Claude, assistant IA créé par Anthropic. Voici ma réponse de test.",
                "dataCommands": null,
                "actionCommands": null,
                "postText": null,
                "communicationModule": null
            }
            """.trimIndent()

            return AIResponse(
                success = true,
                content = stubResponse,
                errorMessage = null,
                tokensUsed = 42, // Stub token count
                cacheCreationTokens = 100,
                cacheReadTokens = 500,
                inputTokens = 50
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

    /**
     * Build final prompt string from PromptData
     * TODO: Transform to Claude Messages API format instead of string
     *
     * This is a TEMPORARY stub - real implementation should:
     * 1. Build messages array with system/user/assistant roles
     * 2. Fuse SystemMessages.formattedData into conversation
     * 3. Add cache breakpoints after L1, L2, L3
     */
    private fun buildPromptFromData(promptData: PromptData): String {
        val sb = StringBuilder()

        // Add levels as system context
        sb.appendLine(promptData.level1Content)
        sb.appendLine()
        sb.appendLine(promptData.level2Content)
        sb.appendLine()
        sb.appendLine(promptData.level3Content)
        sb.appendLine()

        // Add conversation history
        sb.appendLine("## Conversation History")
        sb.appendLine()

        for (message in promptData.sessionMessages) {
            when (message.sender) {
                MessageSender.USER -> {
                    sb.appendLine("[USER]")
                    val text = message.richContent?.linearText ?: message.textContent ?: ""
                    sb.appendLine(text)
                    sb.appendLine()
                }
                MessageSender.AI -> {
                    sb.appendLine("[ASSISTANT]")
                    val text = message.aiMessageJson ?: message.textContent ?: ""
                    sb.appendLine(text)
                    sb.appendLine()
                }
                MessageSender.SYSTEM -> {
                    sb.appendLine("[SYSTEM]")
                    sb.appendLine(message.systemMessage?.summary ?: "")
                    if (message.systemMessage?.formattedData != null) {
                        sb.appendLine()
                        sb.appendLine(message.systemMessage.formattedData)
                    }
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }
}