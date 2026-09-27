package com.assistant.tools.list.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.FieldValuesSaver
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.tools.list.ListItem

/**
 * An item opened: its name, required, and the list's own fields; it can be deleted from here.
 *
 * What is typed survives a rotation, kept by the item's id; the dialog is only shown with the
 * item loaded, so it never saves over it from empty values.
 *
 * @param onSave The name and the values of the list's fields, emptied ones absent
 */
@Composable
internal fun ListItemDialog(
    item: ListItem,
    fields: List<FieldDefinition>,
    onSave: (name: String, extra: Map<String, Any?>) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "list", context = context) }

    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var extra by rememberSaveable(item.id, stateSaver = FieldValuesSaver) { mutableStateOf(item.extra) }

    UI.Dialog(
        type = DialogType.CONFIRM,
        onConfirm = { onSave(name, extra) },
        onCancel = onCancel,
        confirmEnabled = name.isNotBlank()
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UI.Text(s.tool("edit_item_title"), TextType.SUBTITLE)
            UI.FormField(
                label = s.tool("field_content"),
                value = name,
                onChange = { name = it },
                fieldType = FieldType.TEXT,
                required = true
            )
            // A line between the content and the list's fields, as between two of those fields
            if (fields.isNotEmpty()) UI.Divider()
            CustomFieldsInput(
                customFieldsMetadata = fields,
                values = extra,
                onValuesChange = { extra = it },
                context = context
            )
            UI.ActionButton(
                action = ButtonAction.DELETE,
                display = ButtonDisplay.LABEL,
                requireConfirmation = true,
                confirmMessage = s.tool("delete_item_confirm").format(item.name),
                onClick = onDelete
            )
        }
    }
}
