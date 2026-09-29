package com.assistant.core.fields

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.LogManager

/** The name of [reference] as it is now, loaded once shown: null while loading. */
@Composable
private fun rememberReferenceName(reference: Reference?, context: Context): String? {
    val s = remember { Strings.`for`(context = context) }
    var shown by remember(reference) { mutableStateOf<String?>(null) }
    LaunchedEffect(reference) {
        if (reference == null) return@LaunchedEffect
        shown = try {
            when (val name = loadReferenceNames(listOf(reference), context)[reference]) {
                is ReferenceName.Named -> name.name
                ReferenceName.Deleted, null -> s.shared("pointer_target_deleted")
            }
        } catch (e: Exception) {
            LogManager.ui("Reference $reference: name not read: ${e.message}", "ERROR", e)
            s.shared("field_reference_unreadable")
        }
    }
    return shown
}

/** A REFERENCE value: the name of what it designates, as it is now. */
@Composable
fun ReferenceValue(value: Any?, context: Context) {
    val s = remember { Strings.`for`(context = context) }
    val reference = ReferenceTarget.referenceOf(value)
    if (reference == null) {
        UI.Text(text = value.toString(), type = TextType.BODY)
        return
    }
    UI.Text(text = rememberReferenceName(reference, context) ?: s.shared("tools_loading"), type = TextType.BODY)
}

/**
 * The input of a REFERENCE field: what it designates by name, a picker opened on touch, and a
 * button emptying it when it is optional.
 */
@Composable
fun ReferenceInput(fieldDef: FieldDefinition, value: Any?, onChange: (Any?) -> Unit, context: Context, required: Boolean) {
    val reference = ReferenceTarget.referenceOf(value)
    val name = rememberReferenceName(reference, context)
    var picking by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            UI.FormField(
                label = fieldDef.displayName,
                value = if (reference == null) "" else name ?: "",
                onChange = {},
                required = required,
                readonly = true,
                onClick = { picking = true }
            )
        }
        if (!required && reference != null) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                display = com.assistant.core.ui.ButtonDisplay.ICON,
                size = com.assistant.core.ui.Size.S,
                onClick = { onChange(null) }
            )
        }
    }

    if (picking) {
        ReferencePicker(
            target = ReferenceTarget.fromConfig(fieldDef.config),
            onDismiss = { picking = false },
            onPick = { picked ->
                picking = false
                onChange(mapOf("kind" to picked.kind.name) + (picked.id?.let { mapOf("id" to it) } ?: emptyMap()))
            }
        )
    }
}

/** One thing offered by the picker. */
private data class Choice(val reference: Reference, val name: String, val detail: String? = null)

/** What the picker offers, as references.choices reads it. */
private data class Choices(
    val zones: List<Choice> = emptyList(),
    val tools: List<Choice> = emptyList(),
    val entries: List<Pair<String, List<Choice>>> = emptyList()
)

/**
 * The things a reference of [target] may designate: the app, the zones, the tool instances, the
 * entries -- those of the tools the field is restricted to, grouped by tool with a search, or,
 * when any entry is taken, those of a tool browsed into first.
 */
@Composable
private fun ReferencePicker(target: ReferenceTarget, onDismiss: () -> Unit, onPick: (Reference) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var query by rememberSaveable { mutableStateOf("") }
    var browsed by rememberSaveable { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf<Choices?>(null) }
    var failed by remember { mutableStateOf(false) }
    val entries = ReferenceKind.ENTRY in target.kinds

    LaunchedEffect(query, browsed) {
        val result = Coordinator(context).processUserAction("references.choices", buildMap {
            put("kinds", target.kinds.map { it.name })
            if (target.toolInstances.isNotEmpty()) put("tool_instances", target.toolInstances)
            browsed?.let { put("tool_instance_id", it) }
            if (query.isNotBlank()) put("query", query)
        })
        if (!result.isSuccess) {
            LogManager.ui("ReferencePicker: choices not read: ${result.error}", "ERROR")
            failed = true
            return@LaunchedEffect
        }
        fun rows(key: String) = (result.data?.get(key) as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
        choices = Choices(
            zones = rows("zones").map { Choice(Reference(ReferenceKind.ZONE, it["id"] as String), it["name"] as? String ?: "") },
            tools = rows("tool_instances").map {
                Choice(Reference(ReferenceKind.TOOL_INSTANCE, it["id"] as String), it["name"] as? String ?: "", it["zone_name"] as? String)
            },
            entries = rows("entries").map { group ->
                (group["tool_name"] as? String ?: "") to (group["entries"] as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
                    .map { Choice(Reference(ReferenceKind.ENTRY, it["id"] as String), it["name"] as? String ?: "") }
            }
        )
    }

    UI.Dialog(type = DialogType.SELECTION, onConfirm = {}, onCancel = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UI.Text(text = s.shared("field_reference_pick"), type = TextType.TITLE, fillMaxWidth = true)
            val current = choices
            when {
                failed -> UI.Text(text = s.shared("error_loading_options"), type = TextType.ERROR)
                current == null -> UI.LoadingIndicator()
                else -> {
                    if (ReferenceKind.APP in target.kinds) {
                        ChoiceRow(Choice(Reference(ReferenceKind.APP, null), s.shared("pointer_level_app")), onPick)
                    }
                    Section(s.shared("reference_kind_zone"), current.zones, onPick)
                    // A tool is picked when tools are taken; otherwise it is browsed into, for its entries
                    if (ReferenceKind.TOOL_INSTANCE in target.kinds) {
                        Section(s.shared("reference_kind_tool_instance"), current.tools, onPick)
                    } else if (entries && target.toolInstances.isEmpty() && browsed == null) {
                        Section(s.shared("scope_select_tool"), current.tools) { browsed = it.id }
                    }
                    if (entries && (target.toolInstances.isNotEmpty() || browsed != null)) {
                        UI.FormField(label = s.shared("field_reference_search"), value = query, onChange = { query = it }, required = false)
                        current.entries.forEach { (tool, rows) -> Section(tool, rows, onPick) }
                        if (current.entries.all { it.second.isEmpty() }) {
                            UI.Text(text = s.shared("scope_no_options"), type = TextType.BODY)
                        }
                    }
                }
            }
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onDismiss)
        }
    }
}

@Composable
private fun Section(title: String, rows: List<Choice>, onPick: (Reference) -> Unit) {
    if (rows.isEmpty()) return
    UI.Text(text = title, type = TextType.SUBTITLE)
    rows.forEach { ChoiceRow(it, onPick) }
}

@Composable
private fun ChoiceRow(choice: Choice, onPick: (Reference) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(text = choice.name, type = TextType.BODY)
            choice.detail?.let { UI.Text(text = it, type = TextType.CAPTION) }
        }
        UI.ActionButton(action = ButtonAction.SELECT, onClick = { onPick(choice.reference) })
    }
}
