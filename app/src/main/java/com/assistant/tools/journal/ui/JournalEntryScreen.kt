package com.assistant.tools.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
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
import com.assistant.tools.journal.utils.DateFormatUtils
import kotlinx.coroutines.launch
import org.json.JSONObject
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

    // UI states
    var isLoading by remember { mutableStateOf(!isCreating) } // Don't load if creating
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(isCreating) } // Start in edit mode if creating

    // Entry data states
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // Transcription states
    var audioFilePath by remember { mutableStateOf("") }
    var transcriptionStatus by remember { mutableStateOf<TranscriptionStatus?>(null) }
    var activeTranscriptionModel by remember { mutableStateOf("") }

    // Date/time picker states
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Validation state
    var validationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Load entry if not creating
    LaunchedEffect(entryId) {
        if (!isCreating) {
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
            val entryData = mapOf(
                "tool_instance_id" to toolInstanceId,
                "tooltype" to "journal",
                "name" to title,
                "timestamp" to timestamp,
                "data" to mapOf(
                    "content" to content
                )
            )

            val schema = toolType.getSchema("journal_data", context)
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
                    val params = mapOf(
                        "id" to entryId,
                        "toolInstanceId" to toolInstanceId,
                        "schema_id" to "journal_data",
                        "name" to title,
                        "timestamp" to timestamp,
                        "data" to JSONObject().apply {
                            put("content", content)
                        }
                    )

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
        // Header
        UI.PageHeader(
            title = if (isEditing) s.shared("action_edit") else title.ifBlank { s.tool("placeholder_untitled") },
            leftButton = ButtonAction.BACK,
            rightButton = if (!isEditing) ButtonAction.EDIT else null,
            onLeftClick = {
                if (isEditing && !isCreating) {
                    // In edit mode (not creating), Back = Cancel
                    handleCancel()
                } else {
                    // In consultation mode or creating, Back = navigate back
                    onNavigateBack()
                }
            },
            onRightClick = if (!isEditing) {
                { isEditing = true }
            } else null
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
                }
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
