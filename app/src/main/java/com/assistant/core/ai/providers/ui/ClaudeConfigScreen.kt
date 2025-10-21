package com.assistant.core.ai.providers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.ui.components.ToolConfigActions
import com.assistant.core.strings.Strings
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.ai.providers.ClaudeProviderCore
import com.assistant.core.ai.providers.ClaudeModelInfo
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Configuration screen for Claude AI provider variants
 *
 * Shared configuration UI for all Claude provider variants (standard, economic, etc.).
 * Each variant passes its core instance and display name to customize the screen.
 *
 * Displays form with:
 * - API key field (required, password type)
 * - Model selection (dynamic from API)
 *
 * Validates configuration against provider schema before saving.
 *
 * @param core The ClaudeProviderCore instance for this variant
 * @param displayName Human-readable name for this provider variant (e.g., "Claude", "Claude (économique)")
 * @param config Current configuration JSON
 * @param onSave Callback to save configuration
 * @param onCancel Callback to cancel without saving
 * @param onReset Callback to reset/delete configuration (nullable)
 */
@Composable
internal fun ClaudeConfigScreen(
    core: ClaudeProviderCore,
    displayName: String,
    config: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()

    // Form states
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }

    // Track if initial config had a model (to decide auto-selection behavior)
    var hadInitialModel by remember { mutableStateOf(false) }

    // Parse and update form states when config changes
    LaunchedEffect(config) {
        try {
            val configJson = JSONObject(config)
            apiKey = configJson.optString("api_key", "")
            val initialModel = configJson.optString("model", "")
            selectedModel = initialModel
            hadInitialModel = initialModel.isNotEmpty()
        } catch (e: Exception) {
            // Invalid JSON, keep defaults
            hadInitialModel = false
        }
    }

    // Models state
    var availableModels by remember { mutableStateOf<List<ClaudeModelInfo>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchModelsError by remember { mutableStateOf<String?>(null) }

    // Validation state
    var validationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Function to fetch models from API
    val fetchModels = {
        if (apiKey.trim().isEmpty()) {
            UI.Toast(context, s.shared("ai_provider_claude_enter_api_key_first"), Duration.SHORT)
        } else {
            coroutineScope.launch {
                isFetchingModels = true
                fetchModelsError = null

                val result = core.fetchAvailableModels(apiKey.trim())

                if (result.success) {
                    availableModels = result.models
                    // Auto-select first model only if config was initially empty
                    if (!hadInitialModel && result.models.isNotEmpty()) {
                        selectedModel = result.models.first().id
                    }
                } else {
                    fetchModelsError = result.errorMessage
                    UI.Toast(
                        context,
                        s.shared("ai_provider_claude_fetch_error").format(result.errorMessage ?: "Unknown"),
                        Duration.LONG
                    )
                }

                isFetchingModels = false
            }
        }
    }

    // Auto-fetch models on load if API key already exists
    LaunchedEffect(Unit) {
        if (apiKey.trim().isNotEmpty() && availableModels.isEmpty() && !isFetchingModels) {
            fetchModels()
        }
    }

    // Validation and save function
    val validateAndSave = {
        // Check if models have been fetched
        if (availableModels.isEmpty()) {
            UI.Toast(context, s.shared("ai_provider_claude_fetch_models"), Duration.SHORT)
        } else if (selectedModel.isEmpty()) {
            UI.Toast(context, s.shared("ai_provider_claude_no_models"), Duration.SHORT)
        } else {
            // Build config object
            val configData = mapOf(
                "api_key" to apiKey.trim(),
                "model" to selectedModel
                // TODO: Add max_tokens configuration when implementing advanced settings
                // For now, using schema default (2000)
            )

            // Get schema for validation
            // Schema ID is variant-specific, getAllSchemaIds() returns the correct one
            val schemaIds = core.getAllSchemaIds()
            val schema = if (schemaIds.isNotEmpty()) core.getSchema(schemaIds.first(), context) else null

            if (schema == null) {
                validationResult = ValidationResult.error(s.shared("ai_error_schema_not_found"))
            } else {
                // Validate config
                val validation = SchemaValidator.validate(schema, configData, context)

                if (validation.isValid) {
                    // Save config as JSON
                    val configJson = JSONObject(configData).toString()
                    onSave(configJson)
                } else {
                    validationResult = validation
                }
            }
        }
    }

    // Reset configuration - delete from DB
    val resetConfig = {
        // Call parent reset callback to delete config from DB
        onReset?.invoke()
    }

    // Show validation errors via toast
    LaunchedEffect(validationResult.errorMessage) {
        validationResult.errorMessage?.let { error ->
            UI.Toast(context, error, Duration.LONG)
        }
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = displayName,
            subtitle = s.shared("settings_ai_providers_config"),
            icon = null,
            leftButton = null,
            rightButton = null,
            onLeftClick = { },
            onRightClick = { }
        )

        // Configuration form
        UI.Card(
            type = CardType.DEFAULT,
            size = Size.M
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section title
                UI.Text(
                    text = s.shared("label_configuration"),
                    type = TextType.SUBTITLE
                )

                // API Key field
                UI.FormField(
                    label = s.shared("ai_provider_claude_api_key"),
                    value = apiKey,
                    onChange = { apiKey = it },
                    fieldType = FieldType.PASSWORD,
                    required = true,
                    state = if (validationResult.isValid) ComponentState.NORMAL else ComponentState.ERROR
                )

                // Fetch models button (only show if no models loaded and not fetching)
                if (availableModels.isEmpty() && !isFetchingModels) {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        size = Size.M,
                        state = if (apiKey.trim().isNotEmpty()) ComponentState.NORMAL else ComponentState.DISABLED,
                        onClick = fetchModels
                    ) {
                        UI.Text(
                            text = s.shared("ai_provider_claude_fetch_models"),
                            type = TextType.BODY
                        )
                    }
                }

                // Loading indicator
                if (isFetchingModels) {
                    UI.Text(
                        text = s.shared("ai_provider_claude_fetching_models"),
                        type = TextType.CAPTION
                    )
                }

                // Models loaded status
                if (availableModels.isNotEmpty() && !isFetchingModels) {
                    UI.Text(
                        text = s.shared("ai_provider_claude_models_loaded").format(availableModels.size),
                        type = TextType.CAPTION
                    )
                }

                // Model selection (disabled if no models available)
                if (availableModels.isNotEmpty()) {
                    val selectedDisplayName = availableModels.find { it.id == selectedModel }?.displayName ?: ""

                    UI.FormSelection(
                        label = s.shared("ai_provider_claude_model"),
                        options = availableModels.map { it.displayName },
                        selected = selectedDisplayName,
                        onSelect = { displayName ->
                            // Find model by displayName and get its id
                            selectedModel = availableModels.find { it.displayName == displayName }?.id ?: ""
                        },
                        required = true
                    )
                } else if (!isFetchingModels) {
                    // Show disabled state when no models
                    UI.Text(
                        text = s.shared("ai_provider_claude_model"),
                        type = TextType.SUBTITLE
                    )
                    UI.Text(
                        text = s.shared("ai_provider_claude_no_models"),
                        type = TextType.CAPTION
                    )
                }

                // TODO: Add max_tokens slider when implementing advanced provider settings
                // This will require:
                // - Add UI.Slider component to UI system
                // - Add configuration toggle for "Advanced settings"
                // - Add token limit field with validation (1-4096)
                // - Consider per-provider token limits and cost implications
                // - Add documentation about token usage and pricing

                // Help text
                UI.Text(
                    text = s.shared("ai_provider_claude_help"),
                    type = TextType.CAPTION
                )
            }
        }

        // Form actions
        // AI provider config always uses SAVE (not CREATE vs SAVE like tools)
        UI.FormActions {
            UI.ActionButton(
                action = ButtonAction.SAVE,
                onClick = validateAndSave,
                enabled = apiKey.trim().isNotEmpty() && selectedModel.isNotEmpty()
            )

            UI.ActionButton(
                action = ButtonAction.CANCEL,
                onClick = onCancel
            )

            // Reset button - only if callback provided
            if (onReset != null) {
                UI.ActionButton(
                    action = ButtonAction.RESET,
                    requireConfirmation = true,
                    onClick = resetConfig
                )
            }
        }
    }
}
