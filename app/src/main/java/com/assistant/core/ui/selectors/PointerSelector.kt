package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/** The selection across a rotation, as PointerSelection writes it. */
private val PointerSelectionSaver: Saver<PointerSelection, String> = Saver(
    save = { it.toJson() },
    restore = { PointerSelection.fromJson(it) }
)

/**
 * The POINTER selector, on one screen (docs/design/pointer.md): the selection of entries
 * (SelectionPicker), then once a zone or a tool is reached the config and the entries to attach,
 * with the sentence that says what will go.
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
    val fields = rememberToolFields(selection.draft.tool?.id)

    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = selection.complete,
        onCancel = onDismiss,
        onConfirm = { onConfirm(selection.pointer().toJson().toString()) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(text = s.shared("pointer_enrichment_selector_title"), type = TextType.TITLE, fillMaxWidth = true)

            SelectionPicker(selection.draft, { selection = selection.copy(draft = it) }, POINTED, fields, reference, offerFields = true)

            if (selection.complete) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    UI.Checkbox(checked = selection.config, onCheckedChange = { selection = selection.copy(config = it) }, label = s.shared("pointer_attach_config"))
                    UI.Checkbox(checked = selection.entries, onCheckedChange = { selection = selection.copy(entries = it) }, label = s.shared("pointer_attach_entries"))
                }
                UI.Text(text = PointerDescription.summary(selection, fields, s), type = TextType.BODY)
            }
        }
    }
}

/** What a pointer designates: a zone, or a tool; an entry is only pointed at by the app. */
private val POINTED = ReferenceTarget(setOf(ReferenceKind.ZONE, ReferenceKind.TOOL_INSTANCE), emptyList())
