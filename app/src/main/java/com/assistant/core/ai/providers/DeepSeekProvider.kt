package com.assistant.core.ai.providers

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.providers.ui.ClaudeConfigScreen
import com.assistant.core.validation.Schema

/**
 * DeepSeek AI Provider - Standard variant
 *
 * Calls DeepSeek through its Anthropic-compatible endpoint, so all implementation
 * is ClaudeProviderCore with MessagesApi.DEEPSEEK (endpoint, model listing, effort levels).
 *
 * Configuration:
 * - API key: DeepSeek API key
 * - Model: Selected from available models via API (e.g., "deepseek-v4-pro")
 * - Effort: Reasoning effort, required (low, high, max)
 * - Max tokens: Response length limit, reasoning included (optional)
 */
class DeepSeekStandardProvider(private val context: Context) : AIProvider {

    // Exposed as internal to allow ClaudeConfigScreen to access it
    internal val core = ClaudeProviderCore(context, "deepseek_standard", MessagesApi.DEEPSEEK)

    override fun getProviderId(): String = "deepseek_standard"

    override fun getDisplayName(): String = "DeepSeek"

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return core.getSchema(schemaId, context)
    }

    override fun getAllSchemaIds(): List<String> {
        return core.getAllSchemaIds()
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        return core.getFormFieldName(fieldName, context)
    }

    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        ClaudeConfigScreen(
            core = core,
            displayName = getDisplayName(),
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}

/**
 * DeepSeek AI Provider - Economic variant
 *
 * Same as DeepSeekStandardProvider with its own configuration,
 * typically a cheaper model (e.g., "deepseek-v4-flash") or a lower effort.
 */
class DeepSeekEconomicProvider(private val context: Context) : AIProvider {

    // Exposed as internal to allow ClaudeConfigScreen to access it
    internal val core = ClaudeProviderCore(context, "deepseek_economic", MessagesApi.DEEPSEEK)

    override fun getProviderId(): String = "deepseek_economic"

    override fun getDisplayName(): String = "DeepSeek (économique)"

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return core.getSchema(schemaId, context)
    }

    override fun getAllSchemaIds(): List<String> {
        return core.getAllSchemaIds()
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        return core.getFormFieldName(fieldName, context)
    }

    @Composable
    override fun getConfigScreen(
        config: String,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
        onReset: (() -> Unit)?
    ) {
        ClaudeConfigScreen(
            core = core,
            displayName = getDisplayName(),
            config = config,
            onSave = onSave,
            onCancel = onCancel,
            onReset = onReset
        )
    }

    override suspend fun query(promptData: PromptData, config: String): AIResponse {
        return core.query(promptData, config)
    }
}
