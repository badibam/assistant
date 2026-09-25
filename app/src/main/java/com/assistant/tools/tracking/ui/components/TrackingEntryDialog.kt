package com.assistant.tools.tracking.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.CoreFields
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.strings.Strings
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.FieldType as UIFieldType
import com.assistant.core.ui.FieldValuesSaver
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.tools.tracking.TrackingConfig
import com.assistant.tools.tracking.TrackingKind
import com.assistant.tools.tracking.TrackingToolType
import org.json.JSONObject

/**
 * An entry as the dialog hands it back: what the user entered, and whether to keep it as a
 * shortcut.
 */
data class TrackingEntryDraft(
    val name: String,
    val timestamp: Long,
    val value: Any?,
    val unit: String?,
    val extra: Map<String, Any?>,
    val addToShortcuts: Boolean = false
)

/**
 * Creates or edits a tracking entry. Every field is entered by the component of its field type:
 * the name and the moment, declared by the core; the value and its unit, declared by the tool
 * type for this tool's config; the user's own fields.
 *
 * @param initial What the dialog opens with
 * @param nameEditable False when the entry comes from a shortcut, whose name it keeps
 * @param offerShortcut Whether to offer keeping the entry as a new shortcut (a free entry)
 */
@Composable
fun TrackingEntryDialog(
    config: JSONObject,
    title: String,
    dialogType: DialogType,
    initial: TrackingEntryDraft,
    nameEditable: Boolean,
    offerShortcut: Boolean,
    onConfirm: (TrackingEntryDraft) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    val kind = remember(config) { TrackingKind.of(config) }
    val declared = remember(config) { TrackingToolType.getEntryFields(config, context).data.map { it.definition } }
    val valueField = declared.firstOrNull { it.name == "value" }
    val unitField = declared.firstOrNull { it.name == "unit" }
    val extraFields = remember(config) { config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList() }
    val units = remember(config) { TrackingConfig.units(config) }

    var name by rememberSaveable { mutableStateOf(initial.name) }
    var timestamp by rememberSaveable { mutableStateOf(initial.timestamp) }
    // The value and its unit, in one saved map so any field type's value survives a rotation.
    // A new entry takes the first unit: the one the config lists first.
    var values by rememberSaveable(stateSaver = FieldValuesSaver) {
        mutableStateOf(mapOf("value" to initial.value, "unit" to (initial.unit ?: units.firstOrNull())))
    }
    var extra by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(initial.extra) }
    var addToShortcuts by rememberSaveable { mutableStateOf(false) }

    // A timer's value comes from its stopwatch and an occurrence has none: both can be saved
    // without one
    val valueNeeded = kind != TrackingKind.TIMER && kind != TrackingKind.OCCURRENCE
    val complete = name.isNotBlank() && (!valueNeeded || values["value"] != null)

    UI.Dialog(
        type = dialogType,
        confirmEnabled = complete,
        onConfirm = {
            onConfirm(
                TrackingEntryDraft(
                    name = name.trim(),
                    timestamp = timestamp,
                    value = values["value"],
                    unit = if (unitField != null) values["unit"] as? String else null,
                    extra = extra,
                    addToShortcuts = addToShortcuts
                )
            )
        },
        onCancel = onCancel
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UI.Text(title, TextType.SUBTITLE)

            UI.FormField(
                label = s.shared("tools_config_label_name"),
                value = name,
                onChange = { name = it },
                required = true,
                readonly = !nameEditable,
                fieldType = UIFieldType.TEXT
            )

            FieldInput(
                fieldDef = CoreFields.timestamp(s::shared),
                value = timestamp,
                onChange = { new -> (new as? Number)?.toLong()?.let { timestamp = it } },
                context = context
            )

            if (valueField != null) {
                FieldInput(
                    fieldDef = valueField,
                    value = values["value"],
                    onChange = { values = values + ("value" to it) },
                    context = context
                )
            }

            if (unitField != null) {
                UI.FormSelection(
                    label = unitField.displayName,
                    options = units,
                    selected = values["unit"] as? String ?: "",
                    onSelect = { values = values + ("unit" to it) },
                    required = true
                )
            }

            if (extraFields.isNotEmpty()) {
                CustomFieldsInput(
                    customFieldsMetadata = extraFields,
                    values = extra,
                    onValuesChange = { extra = it },
                    context = context
                )
            }

            if (offerShortcut) {
                UI.Checkbox(
                    checked = addToShortcuts,
                    onCheckedChange = { addToShortcuts = it },
                    label = s.tool("usage_add_to_shortcuts")
                )
            }
        }
    }
}
