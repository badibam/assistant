package com.assistant.core.transcription.providers.vosk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.models.TranscriptionModel
import com.assistant.core.transcription.providers.vosk.VoskProvider
import com.assistant.core.ui.*
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuration screen for Vosk transcription provider
 *
 * Displays:
 * - List of available models (from VoskModels.ALL_MODELS)
 * - Downloaded models with delete option
 * - Default model selection
 * - Model download interface
 *
 * Workflow:
 * 1. User browses available models by language
 * 2. User downloads desired model(s)
 * 3. User selects a default model from downloaded models
 * 4. Configuration saved with default_model and downloaded_models list
 *
 * @param provider VoskProvider instance
 * @param config Current configuration JSON
 * @param onSave Callback to save configuration
 * @param onCancel Callback to cancel without saving
 * @param onReset Callback to reset/delete configuration (nullable)
 */
@Composable
internal fun VoskConfigScreen(
    provider: VoskProvider,
    config: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Form states
    var selectedDefaultModel by remember { mutableStateOf("") }
    var downloadedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableModels by remember { mutableStateOf<List<TranscriptionModel>>(emptyList()) }

    // UI states
    var isDownloading by remember { mutableStateOf(false) }
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var validationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Observe download progress from provider
    val downloadProgress by provider.downloadProgress.collectAsState()

    // Filter state for available models
    var selectedLanguageFilter by remember { mutableStateOf("all") }

    // Parse config on load
    LaunchedEffect(config) {
        try {
            val configJson = JSONObject(config)
            selectedDefaultModel = configJson.optString("default_model", "")

            val downloadedArray = configJson.optJSONArray("downloaded_models")
            downloadedModels = if (downloadedArray != null) {
                List(downloadedArray.length()) { i -> downloadedArray.getString(i) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            // Invalid JSON, keep defaults
            selectedDefaultModel = ""
            downloadedModels = emptyList()
        }
    }

    // Load available models and downloaded models on init
    LaunchedEffect(Unit) {
        availableModels = provider.listAvailableModels(config)
        downloadedModels = provider.getDownloadedModels()
    }

    // Get unique languages for filter
    val availableLanguages = remember(availableModels) {
        availableModels.map { it.language }.distinct().sorted()
    }

    // Filter available models by language
    val filteredModels = remember(availableModels, selectedLanguageFilter) {
        if (selectedLanguageFilter == "all") {
            availableModels
        } else {
            availableModels.filter { it.language == selectedLanguageFilter }
        }
    }

    // Helper function to format size
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%d MB".format(bytes / 1_000_000)
            else -> "%d KB".format(bytes / 1_000)
        }
    }

    // Helper function to get quality string
    fun getQualityString(quality: String): String {
        return when (quality) {
            "small" -> s.shared("vosk_quality_small")
            "medium" -> s.shared("vosk_quality_medium")
            "large" -> s.shared("vosk_quality_large")
            else -> quality
        }
    }

    // Download model function
    val downloadModel: (String) -> Unit = { modelId ->
        coroutineScope.launch {
            isDownloading = true
            downloadingModelId = modelId

            val result = provider.downloadModel(modelId, config)

            if (result.success) {
                // Refresh downloaded models list
                downloadedModels = provider.getDownloadedModels()

                // Auto-select as default if no default set
                if (selectedDefaultModel.isEmpty()) {
                    selectedDefaultModel = modelId
                }

                UI.Toast(context, s.shared("vosk_download_success"), Duration.SHORT)
            } else {
                UI.Toast(
                    context,
                    s.shared("vosk_download_failed").format(result.errorMessage ?: "Unknown"),
                    Duration.LONG
                )
            }

            isDownloading = false
            downloadingModelId = null
        }
    }

    // Delete model function
    val deleteModel: (String) -> Unit = { modelId ->
        coroutineScope.launch {
            val success = provider.deleteModel(modelId)

            if (success) {
                // Refresh downloaded models list
                downloadedModels = provider.getDownloadedModels()

                // Clear default if deleted model was default
                if (selectedDefaultModel == modelId) {
                    selectedDefaultModel = ""
                }

                UI.Toast(context, s.shared("vosk_delete_success"), Duration.SHORT)
            } else {
                UI.Toast(context, s.shared("vosk_delete_failed"), Duration.SHORT)
            }
        }
    }

    // Validate and save
    val validateAndSave = {
        if (selectedDefaultModel.isEmpty()) {
            UI.Toast(context, s.shared("vosk_select_model_first"), Duration.SHORT)
        } else {
            // Build config object
            val configData = mapOf(
                "default_model" to selectedDefaultModel,
                "downloaded_models" to downloadedModels
            )

            // Get schema for validation
            val schemaIds = provider.getAllSchemaIds()
            val schema = if (schemaIds.isNotEmpty()) provider.getSchema(schemaIds.first(), context) else null

            if (schema == null) {
                validationResult = ValidationResult.error(s.shared("error_schema_not_found"))
            } else {
                // Validate config
                val validation = SchemaValidator.validate(schema, configData, context)

                if (validation.isValid) {
                    // Save config as JSON
                    val configJson = JSONObject().apply {
                        put("default_model", selectedDefaultModel)
                        put("downloaded_models", JSONArray(downloadedModels))
                    }.toString()

                    onSave(configJson)
                } else {
                    validationResult = validation
                }
            }
        }
    }

    // Reset configuration
    val resetConfig = {
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
            title = s.shared("vosk_config_title"),
            subtitle = s.shared("settings_ai_providers_config"),
            icon = null,
            leftButton = null,
            rightButton = null,
            onLeftClick = { },
            onRightClick = { }
        )

        // Downloaded models section
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
                    text = s.shared("vosk_downloaded_models"),
                    type = TextType.SUBTITLE
                )

                if (downloadedModels.isEmpty()) {
                    UI.Text(
                        text = s.shared("vosk_no_models_downloaded"),
                        type = TextType.CAPTION
                    )
                } else {
                    // Default model selection
                    val selectedModelName = availableModels
                        .find { it.id == selectedDefaultModel }?.name ?: s.shared("vosk_no_default_model")

                    UI.FormSelection(
                        label = s.shared("vosk_default_model"),
                        options = downloadedModels.mapNotNull { modelId ->
                            availableModels.find { it.id == modelId }?.name
                        },
                        selected = selectedModelName,
                        onSelect = { displayName ->
                            // Find model by name and get its id
                            selectedDefaultModel = availableModels.find { it.name == displayName }?.id ?: ""
                        },
                        required = true
                    )

                    // List downloaded models
                    downloadedModels.forEach { modelId ->
                        val model = availableModels.find { it.id == modelId }
                        if (model != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Model info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    UI.Text(
                                        text = model.name,
                                        type = TextType.BODY
                                    )
                                    UI.Text(
                                        text = "${s.shared("vosk_model_size").format(formatSize(model.size))} • ${getQualityString(model.quality)}",
                                        type = TextType.CAPTION
                                    )
                                }

                                // Delete button
                                UI.ActionButton(
                                    action = ButtonAction.DELETE,
                                    display = ButtonDisplay.ICON,
                                    size = Size.S,
                                    requireConfirmation = true,
                                    confirmMessage = s.shared("vosk_delete_confirm_message")
                                        .format(model.name, formatSize(model.size)),
                                    onClick = { deleteModel(modelId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Available models section
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
                    text = s.shared("vosk_available_models"),
                    type = TextType.SUBTITLE
                )

                // Language filter
                if (availableLanguages.isNotEmpty()) {
                    val languageOptions = listOf("all") + availableLanguages
                    val languageDisplayNames = languageOptions.map { lang ->
                        if (lang == "all") "Toutes les langues" else lang.uppercase()
                    }
                    val selectedDisplayName = if (selectedLanguageFilter == "all") {
                        "Toutes les langues"
                    } else {
                        selectedLanguageFilter.uppercase()
                    }

                    UI.FormSelection(
                        label = s.shared("vosk_model_language"),
                        options = languageDisplayNames,
                        selected = selectedDisplayName,
                        onSelect = { displayName ->
                            selectedLanguageFilter = if (displayName == "Toutes les langues") {
                                "all"
                            } else {
                                availableLanguages.find { it.uppercase() == displayName } ?: "all"
                            }
                        },
                        required = false
                    )
                }

                // Download progress indicator
                if (downloadProgress != null) {
                    val progress = downloadProgress!!
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        UI.Text(
                            text = "${s.shared("vosk_downloading")} ${progress.progress}%",
                            type = TextType.CAPTION
                        )
                        if (progress.maxAttempts > 1) {
                            UI.Text(
                                text = "(${progress.attempt}/${progress.maxAttempts})",
                                type = TextType.CAPTION
                            )
                        }
                    }
                } else if (isDownloading) {
                    UI.Text(
                        text = s.shared("vosk_downloading"),
                        type = TextType.CAPTION
                    )
                }

                // List available models
                filteredModels.forEach { model ->
                    val isDownloaded = downloadedModels.contains(model.id)
                    val isCurrentlyDownloading = downloadingModelId == model.id

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Model info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            UI.Text(
                                text = model.name,
                                type = TextType.BODY
                            )
                            UI.Text(
                                text = "${s.shared("vosk_model_language").format(model.language.uppercase())} • " +
                                       "${s.shared("vosk_model_size").format(formatSize(model.size))} • " +
                                       getQualityString(model.quality),
                                type = TextType.CAPTION
                            )
                        }

                        // Download button (only if not downloaded)
                        if (!isDownloaded) {
                            UI.Button(
                                type = ButtonType.PRIMARY,
                                size = Size.S,
                                state = if (isCurrentlyDownloading || isDownloading) {
                                    ComponentState.DISABLED
                                } else {
                                    ComponentState.NORMAL
                                },
                                onClick = { downloadModel(model.id) }
                            ) {
                                UI.Text(
                                    text = s.shared("vosk_download_model"),
                                    type = TextType.BODY
                                )
                            }
                        } else {
                            // Show "Downloaded" indicator
                            UI.Text(
                                text = "✓",
                                type = TextType.BODY
                            )
                        }
                    }
                }
            }
        }

        // Help text
        UI.Card(
            type = CardType.DEFAULT,
            size = Size.M
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                UI.Text(
                    text = s.shared("vosk_help"),
                    type = TextType.CAPTION
                )
            }
        }

        // Form actions
        UI.FormActions {
            UI.ActionButton(
                action = ButtonAction.SAVE,
                onClick = validateAndSave,
                enabled = selectedDefaultModel.isNotEmpty()
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
