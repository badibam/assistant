package com.assistant.tools.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.DateUtils
import com.assistant.core.transcription.ui.TranscribableTextField
import com.assistant.core.transcription.ui.TranscriptionStatus
import com.assistant.core.transcription.models.TranscriptionContext
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.CustomFieldsDisplay
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.tools.journal.utils.DateFormatUtils
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/**
 * Screen for viewing and editing a single journal entry
 *
 * Features:
 * - Consultation mode (readonly) with Edit button
 * - Edition mode with editable fields
 * - Date/time picker
 * - Title field
 * - Content field with transcription support
 * - Cancel in creation mode = auto-delete entry
 */
@Composable
fun JournalEntryScreen(
    entryId: String,
    toolInstanceId: String,
    isCreating: Boolean,  // true = entry just created with default values
    onNavigateBack: () -> Unit
) {
    LogManager.ui("JournalEntryScreen called - entryId=$entryId, isCreating=$isCreating")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "journal", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // UI states (temporary, don't survive rotation)
    var isLoading by remember { mutableStateOf(!isCreating) } // Don't load if creating
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Entry data states (survive rotation)
    var isEditing by rememberSaveable { mutableStateOf(isCreating) } // Start in edit mode if creating
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var timestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    // Custom fields states
    var customFieldsDefinitions by remember { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    var customFieldsValues by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    // Transcription states (survive rotation where possible)
    var audioFilePath by remember { mutableStateOf("") } // Recalculated on recomposition
    var transcriptionStatus by remember { mutableStateOf<TranscriptionStatus?>(null) } // Complex type, reload on recomposition
    var activeTranscriptionModel by rememberSaveable { mutableStateOf("") }

    // Date/time picker states
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Validation state
    var validationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Load tool instance config to get custom fields definitions
    LaunchedEffect(toolInstanceId) {
        val configResult = coordinator.processUserAction(
            "tools.get",
            mapOf("tool_instance_id" to toolInstanceId)
        )

        if (configResult?.isSuccess == true) {
            val toolData = configResult.data?.get("tool_instance") as? Map<*, *>
            toolData?.let { data ->
                val configJson = data["config_json"] as? String ?: "{}"
                try {
                    val config = JSONObject(configJson)
                    val customFieldsArray = config.optJSONArray("custom_fields")
                    if (customFieldsArray != null) {
                        customFieldsDefinitions = customFieldsArray.toFieldDefinitions()
                        LogManager.ui("Loaded ${customFieldsDefinitions.size} custom field definitions")
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error parsing custom fields definitions: ${e.message}", "ERROR")
                }
            }
        }
    }

    // Load entry if not creating
    // Force reload on every composition by resetting custom fields before load
    LaunchedEffect(entryId, isCreating) {
        LogManager.ui("LaunchedEffect triggered: entryId=$entryId, isCreating=$isCreating")
        if (!isCreating) {
            // Reset custom fields to ensure clean state
            customFieldsValues = emptyMap()
            LogManager.ui("Loading journal entry: $entryId")
            val params = mapOf(
                "entry_id" to entryId
            )

            val result = coordinator.processUserAction("tool_data.get_single", params)

            if (result?.isSuccess == true) {
                val entryData = result.data?.get("entry") as? Map<*, *>
                entryData?.let { data ->
                    title = data["name"] as? String ?: ""
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

                    // Parse data field
                    val dataValue = data["data"]
                    val parsedData = try {
                        when (dataValue) {
                            is Map<*, *> -> dataValue as Map<String, Any>
                            is String -> {
                                val dataJson = JSONObject(dataValue)
                                mutableMapOf<String, Any>().apply {
                                    dataJson.keys().forEach { key -> put(key, dataJson.get(key)) }
                                }
                            }
                            else -> emptyMap()
                        }
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing journal entry data: ${e.message}", "ERROR")
                        emptyMap<String, Any>()
                    }

                    content = parsedData["content"] as? String ?: ""

                    // Load custom fields values
                    // Note: service returns "customFields" (camelCase), not "custom_fields"
                    val customFieldsData = data["customFields"]
                    val parsedCustomFields = try {
                        when (customFieldsData) {
                            is Map<*, *> -> customFieldsData as Map<String, Any?>
                            is String -> {
                                val customFieldsJson = JSONObject(customFieldsData)
                                mutableMapOf<String, Any?>().apply {
                                    customFieldsJson.keys().forEach { key -> put(key, customFieldsJson.get(key)) }
                                }
                            }
                            else -> emptyMap()
                        }
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing custom fields: ${e.message}", "ERROR")
                        emptyMap<String, Any?>()
                    }
                    customFieldsValues = parsedCustomFields
                    LogManager.ui("Loaded ${customFieldsValues.size} custom field values")

                    // TODO: Load transcription data if available
                    // audioFilePath = parsedData["audio_file_path"] as? String ?: ""
                    // transcriptionStatus = ...

                    LogManager.ui("Successfully loaded entry: title=$title")
                }
            } else {
                errorMessage = s.tool("error_entry_load")
            }
            isLoading = false
        } else {
            // In creation mode, initialize with current timestamp
            timestamp = System.currentTimeMillis()
        }
    }

    // Initialize audio file path
    LaunchedEffect(entryId) {
        // Audio files are stored in: /data/data/.../files/journal_audio/{entryId}.wav
        // Format: WAV 16kHz mono 16-bit PCM (Vosk requirement)
        val journalAudioDir = File(context.filesDir, "journal_audio")
        journalAudioDir.mkdirs()
        audioFilePath = File(journalAudioDir, "$entryId.wav").absolutePath
    }

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Validation function
    fun validateForm() {
        val toolType = ToolTypeManager.getToolType("journal")
        if (toolType != null) {
            // Build data structure like service expects
            // Note: schema_id must be at root level for validation (per ActionValidator pattern)
            // CRITICAL: data field is required by schema, and content must be present (even if empty)
            // to prevent SchemaValidator from filtering out the entire data map when it's empty
            val entryData = mutableMapOf<String, Any>(
                "tool_instance_id" to toolInstanceId,
                "tooltype" to "journal",
                "schema_id" to "journal_data",  // Required for validation
                "name" to title,
                "timestamp" to timestamp,
                "data" to mapOf(
                    "content" to content  // Always include content, even if empty string
                )
            )

            // Add custom fields if any (for validation)
            if (customFieldsValues.isNotEmpty()) {
                entryData["custom_fields"] = customFieldsValues
            }

            LogManager.ui("Journal validation - entryData: $entryData")

            // Get schema WITH toolInstanceId to include custom fields definitions
            val schema = toolType.getSchema("journal_data", context, toolInstanceId)
            validationResult = if (schema != null) {
                SchemaValidator.validate(schema, entryData, context)
            } else {
                ValidationResult.error("Journal data schema not found")
            }

            LogManager.ui("Journal validation result: isValid=${validationResult.isValid}")
            if (!validationResult.isValid) {
                LogManager.ui("Journal validation error: ${validationResult.errorMessage}")
            }
        } else {
            validationResult = ValidationResult.error("Tool type 'journal' not found")
        }
    }

    // Save function
    val handleSave = {
        // Validate before saving
        validateForm()

        if (validationResult.isValid) {
            coroutineScope.launch {
                isSaving = true
                try {
                    val params = mutableMapOf<String, Any>(
                        "id" to entryId,
                        "toolInstanceId" to toolInstanceId,
                        "schema_id" to "journal_data",
                        "name" to title,
                        "timestamp" to timestamp,
                        "data" to JSONObject().apply {
                            put("content", content)
                        }
                    )

                    // Add custom fields if any
                    if (customFieldsValues.isNotEmpty()) {
                        params["custom_fields"] = JSONObject(customFieldsValues)
                    }

                    val result = coordinator.processUserAction("tool_data.update", params)
                    if (result?.isSuccess == true) {
                        LogManager.ui("Successfully saved journal entry")
                        if (isCreating) {
                            // After first save, no longer in creating mode
                            isEditing = false
                        } else {
                            // Switch back to consultation mode
                            isEditing = false
                        }
                    } else {
                        errorMessage = s.tool("error_entry_save")
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error during save: ${e.message}", "ERROR")
                    errorMessage = s.tool("error_entry_save")
                } finally {
                    isSaving = false
                }
            }
        } else {
            // Validation failed, show error
            errorMessage = validationResult.errorMessage ?: s.shared("message_validation_error_simple")
        }
    }

    // Cancel function
    val handleCancel = {
        if (isCreating) {
            // Delete entry automatically if cancelling creation
            coroutineScope.launch {
                val params = mapOf("id" to entryId)
                coordinator.processUserAction("tool_data.delete", params)
                LogManager.ui("Deleted journal entry on cancel: $entryId")
                onNavigateBack()
            }
        } else {
            // Just switch back to consultation mode
            isEditing = false
        }
    }

    // Early return for loading state
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            UI.Text(s.tool("loading_entry"), TextType.BODY)
        }
        return
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header (no right button - EDIT moved to bottom actions)
        UI.PageHeader(
            title = if (isEditing) s.shared("action_edit") else title.ifBlank { s.tool("placeholder_untitled") },
            leftButton = ButtonAction.BACK,
            onLeftClick = {
                if (isEditing && !isCreating) {
                    // In edit mode (not creating), Back = Cancel
                    handleCancel()
                } else {
                    // In consultation mode or creating, Back = navigate back
                    onNavigateBack()
                }
            }
        )

        if (isEditing) {
            // Edit mode: separate cards for each field

            // Date/time field
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.Text(
                        text = s.tool("label_date_time"),
                        type = TextType.SUBTITLE
                    )

                    val formattedDate = remember(timestamp) {
                        DateFormatUtils.formatJournalDate(timestamp, context)
                    }

                    // Editable date/time (clickable to open pickers)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    ) {
                        UI.Text(
                            text = formattedDate,
                            type = TextType.BODY
                        )
                    }
                }
            }

            // Title field
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.FormField(
                        label = s.tool("label_title"),
                        value = title,
                        onChange = { title = it },
                        fieldType = FieldType.TEXT,
                        required = true
                    )
                }
            }

            // Content field with transcription support
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TranscribableTextField(
                        label = s.tool("label_content"),
                        value = content,
                        onChange = { content = it },
                        audioFilePath = audioFilePath,
                        transcriptionStatus = transcriptionStatus,
                        modelName = activeTranscriptionModel,
                        enabled = true,
                        required = false,  // Content is optional - can be filled via transcription or typing
                        // Auto-transcription configuration
                        autoTranscribe = true,
                        transcriptionContext = TranscriptionContext(
                            entryId = entryId,
                            toolInstanceId = toolInstanceId,
                            tooltype = "journal",
                            fieldName = "content"
                        ),
                        onTranscriptionStatusChange = { transcriptionStatus = it }
                    )
                }
            }

            // Custom fields input (if any custom fields defined)
            if (customFieldsDefinitions.isNotEmpty()) {
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomFieldsInput(
                            fields = customFieldsDefinitions,
                            values = customFieldsValues,
                            onValuesChange = { newValues ->
                                customFieldsValues = newValues
                                LogManager.ui("Custom fields values updated")
                            },
                            context = context
                        )
                    }
                }
            }

            // Form actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.ActionButton(
                    action = ButtonAction.CANCEL,
                    onClick = handleCancel
                )

                UI.ActionButton(
                    action = ButtonAction.SAVE,
                    onClick = handleSave
                )
            }
        } else {
            // Consultation mode: single card with all content (like JournalCard but full content)
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Date/time
                    val formattedDate = remember(timestamp) {
                        DateFormatUtils.formatJournalDate(timestamp, context)
                    }
                    UI.Text(
                        text = formattedDate,
                        type = TextType.CAPTION
                    )

                    // Title
                    UI.Text(
                        text = title.ifBlank { s.tool("placeholder_untitled") },
                        type = TextType.SUBTITLE
                    )

                    // Full content (no truncation)
                    UI.Text(
                        text = content,
                        type = TextType.BODY
                    )

                    // Custom fields display (if any)
                    if (customFieldsDefinitions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CustomFieldsDisplay(
                            fields = customFieldsDefinitions,
                            values = customFieldsValues,
                            context = context
                        )
                    }
                }
            }

            // Action buttons in consultation mode (aligned right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.LABEL,
                    onClick = { isEditing = true }
                )

                Spacer(modifier = Modifier.width(8.dp))

                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.LABEL,
                    requireConfirmation = true,
                    confirmMessage = s.tool("confirm_delete_entry"),
                    onClick = {
                        coroutineScope.launch {
                            val params = mapOf("id" to entryId)
                            val result = coordinator.processUserAction("tool_data.delete", params)
                            if (result?.isSuccess == true) {
                                LogManager.ui("Successfully deleted journal entry: $entryId")
                                onNavigateBack()
                            } else {
                                errorMessage = s.tool("error_entry_delete")
                            }
                        }
                    }
                )
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val currentDate = DateUtils.formatDateForDisplay(timestamp)
        UI.DatePicker(
            selectedDate = currentDate,
            onDateSelected = { newDate ->
                // Update date part of timestamp, keep time part
                val currentTime = DateUtils.formatTimeForDisplay(timestamp)
                timestamp = DateUtils.combineDateTime(newDate, currentTime)
                showDatePicker = false
                showTimePicker = true // Chain to time picker
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time picker dialog
    if (showTimePicker) {
        val currentTime = DateUtils.formatTimeForDisplay(timestamp)
        UI.TimePicker(
            selectedTime = currentTime,
            onTimeSelected = { newTime ->
                // Update time part of timestamp, keep date part
                val currentDate = DateUtils.formatDateForDisplay(timestamp)
                timestamp = DateUtils.combineDateTime(currentDate, newTime)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
