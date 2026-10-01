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
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.defaultValues
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.FieldValuesSaver
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.tools.list.DueNotice
import com.assistant.tools.list.ListItem
import com.assistant.tools.list.ListToolType

/**
 * An item opened: its name, required, its due date in a list that has them, and the list's own
 * fields; it can be deleted from here.
 *
 * What is typed survives a rotation, kept by the item's id; the dialog is only shown with the
 * item loaded, so it never saves over it from empty values.
 *
 * @param onSave The name, the values of the list's fields, emptied ones absent, and the due date, null for none
 */
@Composable
internal fun ListItemDialog(
    item: ListItem,
    fields: List<FieldDefinition>,
    dueDates: Boolean,
    onSave: (name: String, extra: Map<String, Any?>, dueAt: Long?) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "list", context = context) }

    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var extra by rememberSaveable(item.id, stateSaver = FieldValuesSaver) { mutableStateOf(item.extra) }
    var dueAt by rememberSaveable(item.id) { mutableStateOf(item.dueAt) }

    UI.Dialog(
        type = DialogType.CONFIRM,
        onConfirm = { onSave(name, extra, dueAt) },
        onCancel = onCancel,
        confirmEnabled = name.isNotBlank()
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.Text(s.tool("edit_item_title"), TextType.SUBTITLE)
            UI.FormField(
                label = s.tool("field_content"),
                value = name,
                onChange = { name = it },
                fieldType = FieldType.TEXT,
                required = true
            )
            if (dueDates) DueAtInput(dueAt) { dueAt = it }
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

/**
 * A new item: its name, required, its due date in a list that has them, and the list's own
 * fields at their default values. What is typed survives a rotation.
 *
 * @param onAdd The name, the values of the list's fields, emptied ones absent, and the due date, null for none
 */
@Composable
internal fun ListAddDialog(
    fields: List<FieldDefinition>,
    dueDates: Boolean,
    onAdd: (name: String, extra: Map<String, Any?>, dueAt: Long?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "list", context = context) }

    var name by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(fields.defaultValues()) }
    var dueAt by rememberSaveable { mutableStateOf<Long?>(null) }

    UI.Dialog(
        type = DialogType.CONFIRM,
        onConfirm = { onAdd(name, extra.filterValues { it != null }, dueAt) },
        onCancel = onCancel,
        confirmEnabled = name.isNotBlank()
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.Text(s.tool("add_item_title"), TextType.SUBTITLE)
            UI.FormField(
                label = s.tool("field_content"),
                value = name,
                onChange = { name = it },
                fieldType = FieldType.TEXT,
                required = true
            )
            if (dueDates) DueAtInput(dueAt) { dueAt = it }
            // A line between the content and the list's fields, as between two of those fields
            if (fields.isNotEmpty()) UI.Divider()
            CustomFieldsInput(
                customFieldsMetadata = fields,
                values = extra,
                onValuesChange = { extra = it },
                context = context,
                newEntry = true
            )
        }
    }
}

/** An item's due date, entered as its field: emptied, the item has none. */
@Composable
internal fun DueAtInput(dueAt: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val field = remember { ListToolType.dueAtField(context) }
    com.assistant.core.fields.FieldInput(field, dueAt, { onChange((it as? Number)?.toLong()) }, context)
}

/**
 * An item's due date as a line below its name: the date as its field shows it, and "late" once
 * it has passed while the item is not checked. Nothing without a due date.
 */
@Composable
internal fun DueAtLine(item: ListItem, now: Long) {
    val dueAt = item.dueAt ?: return
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "list", context = context) }
    val field = remember { ListToolType.dueAtField(context) }
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S)
    ) {
        com.assistant.core.fields.FieldValue(field, dueAt, context)
        if (DueNotice.isLate(item, now)) UI.Text(s.tool("due_late"), TextType.ERROR)
    }
}
