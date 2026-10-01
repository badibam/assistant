package com.assistant.tools.messages.ui.components

import com.assistant.core.ui.FieldValuesSaver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import com.assistant.tools.messages.ui.Occurrence
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Writes the part of a pending occurrence that belongs to this send alone.
 *
 * Only the day's title, body and custom field values are editable here. The common part is not
 * shown as an editable field and not copied in: it is read from the template when the
 * occurrence goes out, so editing the template still reaches everything not yet sent. Priority
 * and recurrence belong to the template too and are edited in the config screen.
 *
 * Nothing here is mandatory. A reminder whose text never varies is sent by its common part
 * alone, and this dialog is simply never opened for it.
 */
@Composable
fun EditOccurrenceDialog(
    toolInstanceId: String,
    occurrence: Occurrence,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val scope = rememberCoroutineScope()

    var title by rememberSaveable(occurrence.id) { mutableStateOf(occurrence.ownTitle ?: "") }
    var content by rememberSaveable(occurrence.id) { mutableStateOf(occurrence.ownContent ?: "") }
    var customFields by rememberSaveable(occurrence.id, stateSaver = FieldValuesSaver) { mutableStateOf(occurrence.extra) }
    var isSaving by remember { mutableStateOf(false) }

    UI.Dialog(
        type = DialogType.CONFIRM,
        onCancel = onDismiss,
        onConfirm = {
            if (isSaving) return@Dialog
            isSaving = true

            scope.launch {
                try {
                    // An emptied field is stored absent rather than as an empty string, so that
                    // "contributes nothing" means the same in the data as it does in the code
                    val data = JSONObject().apply {
                        title.takeIf { it.isNotBlank() }?.let { put("title", it) }
                        content.takeIf { it.isNotBlank() }?.let { put("content", it) }
                    }

                    val params = mutableMapOf<String, Any>(
                        "id" to occurrence.id,
                        "data" to data
                    )
                    // The user's fields, those emptied sent as null to be cleared
                    params["extra"] = com.assistant.core.fields.extraForUpdate(occurrence.extra, customFields)

                    val result = coordinator.processUserAction("tool_data.update", params)
                    if (result.isSuccess) {
                        LogManager.ui("Occurrence ${occurrence.id} updated")
                        onSaved()
                    } else {
                        LogManager.ui("Failed to update occurrence ${occurrence.id}: ${result.error}", "ERROR")
                        onError(result.error ?: s.tool("error_save"))
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error saving occurrence: ${e.message}", "ERROR", e)
                    onError(s.tool("error_save"))
                } finally {
                    isSaving = false
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            UI.Text(s.tool("edit_occurrence_title"), TextType.SUBTITLE)
            UI.Text(s.tool("edit_occurrence_hint"), TextType.CAPTION)

            UI.FormField(
                required = false,
                label = s.tool("label_title"),
                value = title,
                onChange = { title = it },
                fieldType = FieldType.TEXT
            )

            UI.FormField(
                required = false,
                label = s.tool("label_content"),
                value = content,
                onChange = { content = it },
                fieldType = FieldType.TEXT_LONG
            )

            // Definitions come from the instance config, so a field renamed or dropped in the
            // template is reflected here without the occurrence archiving its own copy
            CustomFieldsInput(
                toolInstanceId = toolInstanceId,
                values = customFields,
                onValuesChange = { customFields = it },
                context = context
            )
        }
    }
}
