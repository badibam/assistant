package com.assistant.tools.journal.ui

import com.assistant.core.tools.BaseSchemas
import com.assistant.core.ui.FieldValuesSaver
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
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.CustomFieldsDisplay
import com.assistant.tools.journal.utils.DateFormatUtils
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

/**
 * Screen for viewing and editing a single journal entry
 *
 * Features:
 * - Consultation mode (readonly) with Edit button
 * - Edition mode with editable fields
 * - Date/time picker
 * - Title field
 * - Content field
 * - Cancel before the first save = auto-delete entry; cancel after it = reload the stored entry
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Entry data states (survive rotation)
    var isEditing by rememberSaveable { mutableStateOf(isCreating) } // Start in edit mode if creating
    // True until the first save. isCreating is fixed for the screen's life, so it cannot say
    // whether a cancel discards a whole new entry or only the edits of a saved one.
    var isUnsaved by rememberSaveable { mutableStateOf(isCreating) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var timestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    // Custom fields values state (definitions loaded automatically by CustomFieldsInput/Display)
    var customFieldsValues by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    // Date/time picker states
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    // Validation state
    var validationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Reads the stored entry into the form: on opening, and on cancel to drop the edits
    suspend fun loadEntry(): Boolean {
        // Nothing is touched unless the read succeeds: a failed reload on cancel must leave
        // the open form as it was, since it can still be saved
        var loaded = false
        LogManager.ui("Loading journal entry: $entryId")
        val params = mapOf(
            "entry_id" to entryId
        )

        val result = coordinator.processUserAction("tool_data.get_single", params)

        if (result?.isSuccess == true) {
            val entryData = result.data?.get("entry") as? Map<*, *>
            entryData?.let { data ->
                title = data["name"] as? String ?: ""
                // Milliseconds from the service, as stored. An entry with none keeps the
                // present moment, which is what a new entry starts from anyway.
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

                // The service hands out an object; the string form stays at the database edge.
                @Suppress("UNCHECKED_CAST")
                val parsedData = (data["data"] as? Map<String, Any>) ?: emptyMap()

                content = parsedData["content"] as? String ?: ""

                // Load custom fields values
                @Suppress("UNCHECKED_CAST")
                customFieldsValues = (data["extra"] as? Map<String, Any?>) ?: emptyMap()
                LogManager.ui("Loaded ${customFieldsValues.size} custom field values")

                LogManager.ui("Successfully loaded entry: title=$title")
                loaded = true
            }
        } else {
            errorMessage = s.tool("error_entry_load")
        }
        return loaded
    }

    // Load entry if not creating; a new entry starts from the current time
    val entryLoad = rememberLoadOnce(entryId, isCreating) {
        LogManager.ui("Loading entry: entryId=$entryId, isCreating=$isCreating")
        if (isCreating) {
            timestamp = System.currentTimeMillis()
            true
        } else {
            loadEntry()
        }
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
            // CRITICAL: data field is required by schema, and content must be present (even if empty)
            // to prevent SchemaValidator from filtering out the entire data map when it's empty
            val entryData = mutableMapOf<String, Any>(
                "tool_instance_id" to toolInstanceId,
                "tooltype" to "journal",
                "name" to title,
                "timestamp" to timestamp,
                "data" to mapOf(
                    "content" to content  // Always include content, even if empty string
                )
            )

            // Add custom fields if any (for validation)
            if (customFieldsValues.isNotEmpty()) {
                entryData["extra"] = customFieldsValues
            }

            LogManager.ui("Journal validation - entryData: $entryData")

            // Get schema WITH toolInstanceId to include custom fields definitions. A tool whose
            // custom fields cannot be read fails here, and says why rather than crash the screen.
            validationResult = try {
                SchemaValidator.validate(BaseSchemas.entrySchema(toolType, toolInstanceId, context), entryData, context)
            } catch (e: IllegalStateException) {
                ValidationResult.error(e.message ?: "Journal data schema unavailable")
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
                        "tool_instance_id" to toolInstanceId,
                        "name" to title,
                        "timestamp" to timestamp,
                        "data" to JSONObject().apply {
                            put("content", content)
                        }
                    )

                    // Add custom fields if any
                    if (customFieldsValues.isNotEmpty()) {
                        params["extra"] = JSONObject(customFieldsValues)
                    }

                    val result = coordinator.processUserAction("tool_data.update", params)
                    if (result?.isSuccess == true) {
                        LogManager.ui("Successfully saved journal entry")
                        // Back to consultation mode; the entry now exists as saved
                        isUnsaved = false
                        isEditing = false
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
        if (isUnsaved) {
            // Delete entry automatically if cancelling creation
            coroutineScope.launch {
                val params = mapOf("id" to entryId)
                coordinator.processUserAction("tool_data.delete", params)
                LogManager.ui("Deleted journal entry on cancel: $entryId")
                onNavigateBack()
            }
        } else {
            // Back to consultation with what is stored, not what was typed. If the entry
            // cannot be read, the form stays open rather than showing the abandoned input.
            coroutineScope.launch {
                if (loadEntry()) isEditing = false
            }
        }
    }

    // Early return for loading state
    if (entryLoad == LoadState.LOADING) {
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
                if (isEditing && !isUnsaved) {
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

            // Content field
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.FormField(
                        label = s.tool("label_content"),
                        value = content,
                        onChange = { content = it },
                        fieldType = FieldType.TEXT_UNLIMITED,
                        required = false
                    )
                }
            }

            // Custom fields input (if any custom fields defined)
            // Custom fields (definitions loaded automatically from toolInstanceId)
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomFieldsInput(
                        toolInstanceId = toolInstanceId,
                        values = customFieldsValues,
                        onValuesChange = { newValues ->
                            customFieldsValues = newValues
                            LogManager.ui("Custom fields values updated")
                        },
                        context = context
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
                    onClick = { handleCancel() }
                )

                UI.ActionButton(
                    action = ButtonAction.SAVE,
                    enabled = entryLoad == LoadState.LOADED,
                    onClick = { handleSave() }
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
                    // Custom fields display (definitions loaded automatically from toolInstanceId)
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomFieldsDisplay(
                        toolInstanceId = toolInstanceId,
                        values = customFieldsValues,
                        context = context
                    )
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
                // Both halves were produced by DateUtils a line ago. If they cannot be read
                // back, the entry keeps the moment it had rather than jumping to now.
                DateUtils.combineDateTime(newDate, currentTime)?.let { timestamp = it }
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
                DateUtils.combineDateTime(currentDate, newTime)?.let { timestamp = it }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
