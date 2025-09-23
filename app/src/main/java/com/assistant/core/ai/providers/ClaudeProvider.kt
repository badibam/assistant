package com.assistant.core.ai.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
import com.assistant.core.strings.Strings

/**
 * Claude AI Provider - Stub implementation for testing complete flow
 */
class ClaudeProvider : AIProvider {

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
                    "enum": ["claude-3-sonnet", "claude-3-haiku"],
                    "default": "claude-3-sonnet",
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
            "required": ["api_key"],
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
     * Configuration UI for Claude provider
     * TODO: Implement real form with API key field
     */
    @Composable
    override fun getConfigScreen(config: String, onSave: (String) -> Unit) {
        // TODO: Implement UI configuration form
        // For now, empty stub to allow compilation
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