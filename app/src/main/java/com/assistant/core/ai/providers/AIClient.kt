package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.providers.AIProvider
import com.assistant.core.ai.providers.AIProviderRegistry
import com.assistant.core.ai.prompts.PromptResult
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
     * Send prompt to AI provider and return parsed response
     */
    suspend fun query(promptResult: PromptResult, providerId: String): OperationResult {
        LogManager.aiService("AIClient.query() called with provider: $providerId, tokens: ${promptResult.totalTokens}")

        return withContext(Dispatchers.IO) {
            try {
                // Get active provider
                val provider = providerRegistry.getProvider(providerId)
                if (provider == null) {
                    LogManager.aiService("Provider not found: $providerId", "ERROR")
                    return@withContext OperationResult.error("Provider not found: $providerId")
                }

                LogManager.aiService("Using provider: ${provider.getDisplayName()}")

                // Get provider configuration via coordinator
                val configResult = coordinator.processUserAction("ai_provider_config.get", mapOf(
                    "providerId" to providerId
                ))

                if (!configResult.isSuccess) {
                    LogManager.aiService("Provider configuration not found: $providerId", "ERROR")
                    return@withContext OperationResult.error("Provider configuration not found: $providerId")
                }

                val providerConfig = configResult.data?.get("config") as? String ?: "{}"
                val isConfigured = configResult.data?.get("isConfigured") as? Boolean ?: false

                if (!isConfigured) {
                    LogManager.aiService("Provider not configured: $providerId", "ERROR")
                    return@withContext OperationResult.error("Provider not configured: $providerId")
                }

                // Send query to provider
                LogManager.aiService("Sending prompt to ${provider.getDisplayName()}: ${promptResult.prompt.length} characters")
                val aiResponse = provider.query(promptResult.prompt, providerConfig)

                LogManager.aiService("Received response from ${provider.getDisplayName()}: success=${aiResponse.success}")

                if (aiResponse.success) {
                    // Parse response to AIMessage
                    val aiMessage = parseAIResponse(aiResponse.content)
                    if (aiMessage != null) {
                        LogManager.aiService("Successfully parsed AI response to AIMessage")
                        OperationResult.success(mapOf(
                            "aiMessage" to aiMessage,
                            "aiMessageJson" to aiResponse.content,
                            "provider" to providerId,
                            "tokensUsed" to aiResponse.tokensUsed
                        ))
                    } else {
                        LogManager.aiService("Failed to parse AI response", "ERROR")
                        OperationResult.error("Failed to parse AI response")
                    }
                } else {
                    LogManager.aiService("AI provider returned error: ${aiResponse.errorMessage}", "ERROR")
                    OperationResult.error("AI provider error: ${aiResponse.errorMessage}")
                }

            } catch (e: Exception) {
                LogManager.aiService("AIClient query failed: ${e.message}", "ERROR", e)
                OperationResult.error("AI query failed: ${e.message}")
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
                ValidationRequest(
                    message = it.getString("message"),
                    status = it.optString("status", null)?.let { status ->
                        ValidationStatus.valueOf(status)
                    } ?: ValidationStatus.PENDING
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

            val postText = json.optString("postText", null).takeIf { it.isNotEmpty() }

            val communicationModule = json.optJSONObject("communicationModule")?.let {
                // TODO: Parse communication module
                // For now, return null
                null
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
    val tokensUsed: Int = 0
)