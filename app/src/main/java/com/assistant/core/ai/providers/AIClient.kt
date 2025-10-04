package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.providers.AIProvider
import com.assistant.core.ai.providers.AIProviderRegistry
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI Client - pure logic for interfacing with AI providers
 *
 * Responsibilities:
 * - Convert PromptResult to provider-specific format
 * - Parse provider responses to AIMessage
 * - Handle provider errors and fallbacks
 * - Uses AIProviderConfigService for configurations (no direct DB access)
 */
class AIClient(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val providerRegistry = AIProviderRegistry(context)

    /**
     * Send prompt data to AI provider and return raw response
     * Provider transforms PromptData to its specific format and handles prompt construction
     *
     * @param promptData The raw prompt data (L1-L3 + messages)
     * @return AIResponse with raw JSON string (not parsed)
     */
    suspend fun query(promptData: PromptData): AIResponse {
        LogManager.aiService("AIClient.query() called with PromptData: ${promptData.sessionMessages.size} messages", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // Get provider ID from session (embedded in messages)
                // For now, use default "claude" - TODO: extract from session
                val providerId = "claude"

                // Get provider
                val provider = providerRegistry.getProvider(providerId)
                if (provider == null) {
                    LogManager.aiService("Provider not found: $providerId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = "Provider not found: $providerId"
                    )
                }

                LogManager.aiService("Using provider: ${provider.getDisplayName()}", "DEBUG")

                // Get provider configuration via coordinator
                val configResult = coordinator.processUserAction("ai_provider_config.get", mapOf(
                    "providerId" to providerId
                ))

                if (!configResult.isSuccess) {
                    LogManager.aiService("Provider configuration not found: $providerId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = "Provider configuration not found: $providerId"
                    )
                }

                val providerConfig = configResult.data?.get("config") as? String ?: "{}"
                val isConfigured = configResult.data?.get("isConfigured") as? Boolean ?: false

                if (!isConfigured) {
                    LogManager.aiService("Provider not configured: $providerId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = "Provider not configured: $providerId"
                    )
                }

                // Send query to provider with PromptData
                LogManager.aiService("Sending PromptData to ${provider.getDisplayName()}", "DEBUG")
                val aiResponse = provider.query(promptData, providerConfig)

                LogManager.aiService("Received response from ${provider.getDisplayName()}: success=${aiResponse.success}", "DEBUG")

                aiResponse

            } catch (e: Exception) {
                LogManager.aiService("AIClient query failed: ${e.message}", "ERROR", e)
                AIResponse(
                    success = false,
                    content = "",
                    errorMessage = "AI query failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Get list of available providers via coordinator
     */
    suspend fun getAvailableProviders(): List<AIProviderInfo> {
        val result = coordinator.processUserAction("ai_provider_config.list")

        return if (result.isSuccess) {
            val providers = result.data?.get("providers") as? List<*> ?: emptyList<Any>()
            providers.mapNotNull { item ->
                val providerMap = item as? Map<*, *> ?: return@mapNotNull null
                AIProviderInfo(
                    id = providerMap["id"] as? String ?: return@mapNotNull null,
                    displayName = providerMap["displayName"] as? String ?: "",
                    isConfigured = providerMap["isConfigured"] as? Boolean ?: false,
                    isActive = providerMap["isActive"] as? Boolean ?: false
                )
            }
        } else {
            LogManager.aiService("Failed to get providers: ${result.error}", "ERROR")
            emptyList()
        }
    }

    /**
     * Get active provider ID via coordinator
     */
    suspend fun getActiveProviderId(): String? {
        val result = coordinator.processUserAction("ai_provider_config.get_active")
        return if (result.isSuccess) {
            result.data?.get("activeProviderId") as? String
        } else {
            LogManager.aiService("Failed to get active provider: ${result.error}", "ERROR")
            null
        }
    }

    // ========================================================================================
    // Private Implementation
    // ========================================================================================

    /**
     * Parse AI provider response JSON to AIMessage object
     */
    private fun parseAIResponse(responseJson: String): AIMessage? {
        return try {
            LogManager.aiService("Parsing AI response JSON: ${responseJson.length} characters")

            val json = JSONObject(responseJson)

            // Parse required preText
            val preText = json.getString("preText")

            // Parse optional fields
            val validationRequest = json.optJSONObject("validationRequest")?.let {
                val statusString = it.optString("status", "")
                val status = if (statusString.isNotEmpty()) {
                    try {
                        ValidationStatus.valueOf(statusString)
                    } catch (e: Exception) {
                        ValidationStatus.PENDING
                    }
                } else {
                    ValidationStatus.PENDING
                }

                ValidationRequest(
                    message = it.getString("message"),
                    status = status
                )
            }

            val dataCommands = json.optJSONArray("dataCommands")?.let { array ->
                (0 until array.length()).map { index ->
                    val commandJson = array.getJSONObject(index)
                    DataCommand(
                        id = commandJson.getString("id"),
                        type = commandJson.getString("type"),
                        params = parseParams(commandJson.getJSONObject("params")),
                        isRelative = commandJson.optBoolean("isRelative", false)
                    )
                }
            }

            val actionCommands = json.optJSONArray("actionCommands")?.let { array ->
                (0 until array.length()).map { index ->
                    val commandJson = array.getJSONObject(index)
                    DataCommand(
                        id = commandJson.getString("id"),
                        type = commandJson.getString("type"),
                        params = parseParams(commandJson.getJSONObject("params")),
                        isRelative = commandJson.optBoolean("isRelative", false)
                    )
                }
            }

            val postText = json.optString("postText", "").takeIf { it.isNotEmpty() }

            val communicationModule = json.optJSONObject("communicationModule")?.let { moduleJson ->
                try {
                    val type = moduleJson.getString("type")
                    val dataJson = moduleJson.getJSONObject("data")
                    val data = parseParams(dataJson)

                    // Validate via CommunicationModuleSchemas
                    val schema = CommunicationModuleSchemas.getSchema(type, context)
                    if (schema == null) {
                        LogManager.aiService("Unknown communication module type: $type", "WARN")
                        return@let null
                    }

                    val validation = com.assistant.core.validation.SchemaValidator.validate(schema, data, context)
                    if (!validation.isValid) {
                        LogManager.aiService("Invalid communication module data for type $type: ${validation.errorMessage}", "WARN")
                        return@let null
                    }

                    // Create appropriate module instance
                    when (type) {
                        "MultipleChoice" -> CommunicationModule.MultipleChoice(type, data)
                        "Validation" -> CommunicationModule.Validation(type, data)
                        else -> {
                            LogManager.aiService("Unsupported communication module type: $type", "WARN")
                            null
                        }
                    }
                } catch (e: Exception) {
                    LogManager.aiService("Failed to parse communication module: ${e.message}", "WARN", e)
                    null
                }
            }

            val aiMessage = AIMessage(
                preText = preText,
                validationRequest = validationRequest,
                dataCommands = dataCommands,
                actionCommands = actionCommands,
                postText = postText,
                communicationModule = communicationModule
            )

            LogManager.aiService("Successfully parsed AIMessage: preText present, ${actionCommands?.size ?: 0} actionCommands, ${dataCommands?.size ?: 0} dataCommands")

            aiMessage

        } catch (e: Exception) {
            LogManager.aiService("Failed to parse AI response: ${e.message}", "ERROR", e)
            null
        }
    }

    private fun parseParams(paramsJson: JSONObject): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        paramsJson.keys().forEach { key ->
            params[key] = paramsJson.get(key)
        }
        return params
    }
}

/**
 * Information about an AI provider
 */
data class AIProviderInfo(
    val id: String,
    val displayName: String,
    val isConfigured: Boolean,
    val isActive: Boolean
)

/**
 * AI provider response structure
 */
data class AIResponse(
    val success: Boolean,
    val content: String,
    val errorMessage: String? = null,
    val tokensUsed: Int = 0,
    // Cache metrics (provider-specific, 0 if not supported)
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val inputTokens: Int = 0
)