package com.assistant.core.ui.selectors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.fields.ToolFields
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.ui.components.PeriodPicker
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.LogManager

/** The selection across a rotation, as PointerSelection writes it. */
private val PointerSelectionSaver: Saver<PointerSelection, String> = Saver(
    save = { it.toJson() },
    restore = { PointerSelection.fromJson(it) }
)

/**
 * The POINTER selector, on one screen (docs/design/pointer.md).
 *
 * At the top, where the user is and the places one level down (ThingBrowser). At the bottom,
 * once a zone or a tool is reached: the config and the entries to attach, and for a tool the
 * period, the value filters and the fields that narrow its entries, with the sentence that says
 * what will go.
 *
 * @param reference The name of what its relative dates resolve against when the pointer is
 *   replayed later (an automation's starting message: its scheduled time); null in a chat, where
 *   a date is fixed when it is chosen
 * @param onConfirm The pointer's stored form, its text written each time the message is read
 */
@Composable
fun PointerSelector(
    reference: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var selection by rememberSaveable(stateSaver = PointerSelectionSaver) { mutableStateOf(PointerSelection()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // Reloaded, never saved: the fields of the tool reached
    var fields by remember { mutableStateOf<Map<String, FieldDefinition>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selection.tool?.id) {
        val toolId = selection.tool?.id
        fields = if (toolId == null) emptyMap() else try {
            ToolFields.filterable(toolId, context, s)
        } catch (e: Exception) {
            LogManager.ui("PointerSelector: fields of $toolId not loaded: ${e.message}", "ERROR", e)
            errorMessage = s.shared("error_loading_options")
            emptyMap()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { UI.Toast(context, it); errorMessage = null }
    }

    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = selection.complete,
        onCancel = onDismiss,
        onConfirm = {
            onConfirm(selection.pointer().toJson().toString())
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(text = s.shared("pointer_enrichment_selector_title"), type = TextType.TITLE, fillMaxWidth = true)

            ThingBrowser(selection.path, { selection = selection.at(it) }, POINTED)

            if (selection.complete) {
                AttachPanel(
                    selection = selection,
                    fields = fields,
                    reference = reference,
                    onChange = { selection = it },
                    onOpenFilters = { showFilters = true }
                )
            }
        }
    }

    if (showFilters && selection.tool != null) {
        PointerFiltersDialog(
            toolInstanceId = selection.tool!!.id,
            fields = fields,
            filters = selection.filters,
            chosenFields = selection.fields,
            reference = reference,
            onDismiss = { showFilters = false },
            onConfirm = { filters, chosen ->
                selection = selection.copy(filters = filters, fields = chosen)
                showFilters = false
            }
        )
    }
}

/** What a pointer designates: a zone, or a tool; an entry is only pointed at by the app. */
private val POINTED = ReferenceTarget(setOf(ReferenceKind.ZONE, ReferenceKind.TOOL_INSTANCE), emptyList())

/**
 * What goes with the pointer: the two boxes and the period, for a tool its filters and fields,
 * then the sentence that says what will go.
 */
@Composable
private fun AttachPanel(
    selection: PointerSelection,
    fields: Map<String, FieldDefinition>,
    reference: String?,
    onChange: (PointerSelection) -> Unit,
    onOpenFilters: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val isTool = selection.level == ReferenceKind.TOOL_INSTANCE

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UI.Checkbox(checked = selection.config, onCheckedChange = { onChange(selection.copy(config = it)) }, label = s.shared("pointer_attach_config"))
            UI.Checkbox(checked = selection.entries, onCheckedChange = { onChange(selection.copy(entries = it)) }, label = s.shared("pointer_attach_entries"))
        }

        UI.Text(text = s.shared("pointer_period"), type = TextType.SUBTITLE)
        PeriodPicker(selection.period, { onChange(selection.copy(period = it)) }, FieldType.DATETIME, reference)

        if (isTool) {
            UI.Button(type = ButtonType.DEFAULT, onClick = onOpenFilters) {
                UI.Text(text = s.shared("pointer_filters_and_fields"), type = TextType.BODY)
            }
            for (i in 0 until selection.filters.length()) {
                UI.Text(text = PointerDescription.filter(selection.filters.getJSONObject(i), fields, s), type = TextType.CAPTION)
            }
            selection.fields?.let { kept ->
                UI.Text(text = s.shared("pointer_part_fields").format(kept.joinToString(", ") { fields[it]?.displayName ?: it }), type = TextType.CAPTION)
            }
        }

        UI.Text(text = PointerDescription.summary(selection, fields, s), type = TextType.BODY)
    }
}
