package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FilterOperator
import com.assistant.core.strings.Strings
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Size
import com.assistant.core.utils.LogManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.ui.components.PeriodPicker
import org.json.JSONArray
import org.json.JSONObject

/**
 * The value filters and the fields of a tool's entries, edited apart from the selector.
 *
 * Every filter must hold. One is added by picking a field, a condition among those its type takes
 * (EntryFilters.operatorsFor) and a value entered with the field's own input; it is checked as
 * tool_data.get will check it, so a filter that would be refused cannot be added. A date field
 * is filtered as the period is, with the period picker: its start and its end, relative to the
 * context's reference when it has one. The period itself is not here: it has its place on the selector.
 *
 * A text field also offers the values its entries hold, the most frequent first, to pick one
 * rather than type it.
 *
 * @param toolInstanceId The tool whose entries are filtered
 * @param reference The name of what relative dates resolve against, null where a date is fixed
 *   when it is chosen
 * @param fields The tool's fields a filter may name, by path
 * @param chosenFields The fields to attach, all of them when null
 */
@Composable
fun PointerFiltersDialog(
    toolInstanceId: String,
    fields: Map<String, FieldDefinition>,
    filters: JSONArray,
    chosenFields: List<String>?,
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
    // The filter being written, {"field", "op", "value"}
    var draft by rememberSaveable { mutableStateOf("{}") }

    val list = JSONArray(current)
    val draftJson = JSONObject(draft)
    val draftField = filterable[draftJson.optString("field")]
    val draftOp = FilterOperator.of(draftJson.optString("op"))
    // A date field's period, the condition its editor stands for
    val isDate = draftField?.type == FieldType.DATE || draftField?.type == FieldType.DATETIME
    val draftPeriod = draftJson.optJSONObject(PERIOD)?.let { EntryPeriod.fromJson(it) { key -> s.shared(key) } }
    val periodBounds = draftPeriod?.let { periodFilters(draftJson.getString("field"), it) }
    val draftValid = when {
        periodBounds != null -> periodBounds.length() > 0
        else -> draftField != null && draftOp != null &&
            EntryFilters.parse(JSONArray().put(draftJson), filterable) { s.shared(it) } is EntryFilters.Parsed.Ready
    }

    // The filters set, with the one being written when it is complete: confirming the dialog
    // keeps what is on screen, without the add button being pressed first
    fun withDraft(): JSONArray {
        val all = JSONArray(current)
        if (!draftValid) return all
        if (periodBounds != null) for (i in 0 until periodBounds.length()) all.put(periodBounds.get(i))
        else all.put(JSONObject(draft))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(text = s.shared("pointer_filters_title"), type = TextType.TITLE, fillMaxWidth = true)

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
            val paths = filterable.keys.toList()
            UI.FormSelection(
                label = s.shared("pointer_filter_field"),
                options = paths.map { filterable.getValue(it).displayName },
                selected = draftField?.displayName ?: "",
                onSelect = { label ->
                    val path = paths.first { filterable.getValue(it).displayName == label }
                    draft = JSONObject().put("field", path).toString()
                },
                required = true
            )
            if (draftField != null) {
                // A date takes a period, or no answer, or an answer
                val ops = if (isDate) listOf(FilterOperator.ABSENT, FilterOperator.PRESENT) else EntryFilters.operatorsFor(draftField.type).toList()
                val periodLabel = s.shared("filter_op_period")
                val labels = (if (isDate) listOf(periodLabel) else emptyList()) + ops.map { PointerDescription.operator(it, s) }
                UI.FormSelection(
                    label = s.shared("pointer_filter_condition"),
                    options = labels,
                    selected = when {
                        draftPeriod != null -> periodLabel
                        else -> draftOp?.let { PointerDescription.operator(it, s) } ?: ""
                    },
                    onSelect = { label ->
                        val next = JSONObject().put("field", draftJson.getString("field"))
                        if (label == periodLabel) next.put(PERIOD, EntryPeriod().toJson())
                        else next.put("op", ops.first { PointerDescription.operator(it, s) == label }.key)
                        draft = next.toString()
                    },
                    required = true
                )
            }
            if (draftPeriod != null) {
                // Another field starts a new period: the pickers keep no mode of the one before
                key(draftJson.getString("field")) {
                    PeriodPicker(draftPeriod, { period ->
                        draft = JSONObject(draft).put(PERIOD, period.toJson()).toString()
                    }, draftField!!.type, reference)
                }
                UI.ActionButton(action = ButtonAction.ADD, enabled = draftValid, onClick = {
                    current = withDraft().toString()
                    draft = "{}"
                })
            } else if (draftField != null && draftOp != null) {
                fun setValue(value: Any?) {
                    draft = JSONObject(draft).apply { if (value == null) remove("value") else put("value", value) }.toString()
                }
                FilterValueInput(draftField, draftOp, if (draftJson.isNull("value")) null else draftJson.opt("value"), ::setValue)
                if (draftField.type == FieldType.TEXT && (draftOp == FilterOperator.CONTAINS || draftOp == FilterOperator.EQUAL)) {
                    PresentValues(toolInstanceId, draftJson.getString("field"), ::setValue)
                }
                UI.ActionButton(action = ButtonAction.ADD, enabled = draftValid, onClick = {
                    current = withDraft().toString()
                    draft = "{}"
                })
            }

            // The fields to attach
            UI.Checkbox(checked = choosing, onCheckedChange = { choosing = it }, label = s.shared("pointer_choose_fields"))
            if (choosing) {
                fields.forEach { (path, field) ->
                    UI.Checkbox(
                        checked = path in chosen,
                        onCheckedChange = { on -> chosen = if (on) chosen + path else chosen - path },
                        label = field.displayName
                    )
                }
            }
        }
    }
}

/** The key of a date field's period in the filter being written. */
private const val PERIOD = "period"

/** A period on the date field [path] as filters: ">=" its start and "<=" its end, each only when set. */
internal fun periodFilters(path: String, period: EntryPeriod): JSONArray = JSONArray().apply {
    period.start?.let { put(JSONObject().put("field", path).put("op", ">=").put("value", it.toJson())) }
    period.end?.let { put(JSONObject().put("field", path).put("op", "<=").put("value", it.toJson())) }
}

/** The values [path] holds among the tool's entries, each a button that picks it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresentValues(toolInstanceId: String, path: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // null while they are read; a read that fails says so
    var values by remember(path) { mutableStateOf<List<String>?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(toolInstanceId, path) {
        val result = Coordinator(context).processUserAction("tool_data.values", mapOf("tool_instance_id" to toolInstanceId, "field" to path))
        if (result.isSuccess) {
            values = (result.data?.get("values") as? List<*>)?.map { it.toString() } ?: emptyList()
        } else {
            LogManager.ui("PointerFiltersDialog: values of $path not read: ${result.error}", "ERROR")
            failed = true
        }
    }
    when {
        failed -> UI.Text(text = s.shared("error_loading_options"), type = TextType.CAPTION)
        !values.isNullOrEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UI.Text(text = s.shared("pointer_filter_present_values"), type = TextType.CAPTION)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                values!!.forEach { value ->
                    UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = { onPick(value) }) {
                        UI.Text(text = value, type = TextType.CAPTION)
                    }
                }
            }
        }
    }
}

/**
 * The value of a filter, entered as its field is: none for "no answer" and "answered", two for
 * a range, the options to tick for a choice.
 */
@Composable
private fun FilterValueInput(field: FieldDefinition, op: FilterOperator, value: Any?, onChange: (Any?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    when {
        op == FilterOperator.ABSENT || op == FilterOperator.PRESENT -> Unit
        op == FilterOperator.IN -> {
            val choice = ChoiceSettings.fromConfig(field.config)
            val ticked = (value as? JSONArray)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            choice.options.forEach { option ->
                UI.Checkbox(
                    checked = option in ticked,
                    onCheckedChange = { on -> onChange(JSONArray(if (on) ticked + option else ticked - option)) },
                    label = choice.labelOf(option)
                )
            }
        }
        op == FilterOperator.BETWEEN -> {
            val bounds = value as? JSONArray ?: JSONArray().put(JSONObject.NULL).put(JSONObject.NULL)
            fun bound(i: Int) = bounds.opt(i).takeIf { it != JSONObject.NULL }
            FieldInput(field.copy(displayName = s.shared("filter_low")), bound(0), { onChange(JSONArray().put(it ?: JSONObject.NULL).put(bounds.opt(1))) }, context, required = true)
            FieldInput(field.copy(displayName = s.shared("filter_high")), bound(1), { onChange(JSONArray().put(bounds.opt(0)).put(it ?: JSONObject.NULL)) }, context, required = true)
        }
        op == FilterOperator.CONTAINS -> UI.FormField(
            label = s.shared("filter_value"),
            value = value as? String ?: "",
            onChange = { onChange(it.takeIf { t -> t.isNotEmpty() }) },
            required = true
        )
        field.type == FieldType.BOOLEAN -> UI.BooleanField(
            label = field.displayName,
            value = value as? Boolean,
            onValueChange = onChange,
            required = true
        )
        else -> FieldInput(field, value, onChange, context, required = true)
    }
}
