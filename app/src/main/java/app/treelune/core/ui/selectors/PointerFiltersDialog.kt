package app.treelune.core.ui.selectors

import app.treelune.core.conditions.Conditions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.EntryFilters
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONArray
import org.json.JSONObject

/**
 * The value filters and the fields of a tool's entries, edited apart from the selector.
 *
 * Every filter must hold. One is written with ConditionPicker and checked as tool_data.get will
 * check it, so a filter that would be refused cannot be added. The period itself is not here: it
 * has its place on the selector.
 *
 * @param toolInstanceId The tool whose entries are filtered
 * @param reference The name of what relative dates resolve against, null where a date is fixed
 *   when it is chosen
 * @param fields The tool's fields a filter may name, by path
 * @param chosenFields The fields to attach, all of them when null
 * @param offerFields Whether the fields kept are chosen here, or the conditions alone
 */
@Composable
fun PointerFiltersDialog(
    toolInstanceId: String,
    fields: Map<String, FieldDefinition>,
    filters: JSONArray,
    chosenFields: List<String>?,
    offerFields: Boolean,
    reference: String?,
    onDismiss: () -> Unit,
    onConfirm: (filters: JSONArray, fields: List<String>?) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // The period is a filter on timestamp, set on the selector
    val filterable = remember(fields) { fields.filterKeys { it != "timestamp" } }

    var current by rememberSaveable { mutableStateOf(filters.toString()) }
    var choosing by rememberSaveable { mutableStateOf(chosenFields != null) }
    var chosen by rememberSaveable { mutableStateOf(chosenFields ?: fields.keys.toList()) }
    // The condition being written (ConditionPicker)
    var draft by rememberSaveable { mutableStateOf("{}") }

    val list = JSONArray(current)
    val written = conditions(JSONObject(draft), filterable) { s.shared(it) }

    // The filters set, with the one being written when it is complete: confirming the dialog
    // keeps what is on screen, without the add button being pressed first
    fun withDraft(): JSONArray {
        val all = JSONArray(current)
        if (written != null) for (i in 0 until written.length()) all.put(written.get(i))
        return all
    }

    UI.Dialog(
        type = DialogType.CONFIGURE,
        confirmEnabled = !choosing || chosen.isNotEmpty(),
        onCancel = onDismiss,
        onConfirm = { onConfirm(withDraft(), chosen.takeIf { choosing }) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            UI.Text(text = s.shared(if (offerFields) "pointer_filters_title" else "selection_filters_title"), type = TextType.TITLE, fillMaxWidth = true)

            // The filters set, each removable
            for (i in 0 until list.length()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.Text(text = PointerDescription.filter(list.getJSONObject(i), fields, s), type = TextType.BODY)
                    }
                    UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, onClick = {
                        current = JSONArray((0 until list.length()).filter { it != i }.map { list.get(it) }).toString()
                    })
                }
            }

            // A new filter: the field, then the condition its type takes, then the value
            UI.Text(text = s.shared("pointer_filter_new"), type = TextType.SUBTITLE)
            ConditionPicker(toolInstanceId, filterable, JSONObject(draft), { draft = it.toString() }, reference, s)
            if (Conditions.fieldOf(JSONObject(draft)) != null) {
                UI.ActionButton(action = ButtonAction.ADD, enabled = written != null, onClick = {
                    current = withDraft().toString()
                    draft = "{}"
                })
            }

            // The fields to attach
            if (offerFields) UI.Checkbox(checked = choosing, onCheckedChange = { choosing = it }, label = s.shared("pointer_choose_fields"))
            if (offerFields && choosing) {
                fieldLabels(fields).forEach { (path, label) ->
                    UI.Checkbox(
                        checked = path in chosen,
                        onCheckedChange = { on -> chosen = if (on) chosen + path else chosen - path },
                        label = label
                    )
                }
            }
        }
    }
}
