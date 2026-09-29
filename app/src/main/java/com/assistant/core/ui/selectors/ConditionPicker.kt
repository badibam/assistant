package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.conditions.Conditions
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FilterOperator
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.PeriodPicker
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Condition brick's selector (docs/BRICKS.md), for a condition put on each entry of a tool: a
 * field on the left (FieldPicker), the operators its type takes (EntryFilters.operatorsFor), and
 * the value entered with the field's own input. A date field is given a period instead, with the
 * period picker: its start and its end, relative to [reference] when the context has one. A text
 * field also offers the values its entries hold, the most frequent first.
 *
 * [draft] is the condition being written, in its stored form (Conditions), or for a period
 * `{"left": {"field"}, "period"}`; [conditions] turns it into the conditions it stands for.
 *
 * @param fields The tool's fields a condition may name, by path
 */
@Composable
fun ConditionPicker(
    toolInstanceId: String,
    fields: Map<String, FieldDefinition>,
    draft: JSONObject,
    onDraft: (JSONObject) -> Unit,
    reference: String?,
    s: StringsContext
) {
    val path = Conditions.fieldOf(draft)
    val field = path?.let { fields[it] }
    val op = FilterOperator.of(draft.optString(Conditions.OP))
    val isDate = field?.type == FieldType.DATE || field?.type == FieldType.DATETIME
    val period = draft.optJSONObject(PERIOD)?.let { EntryPeriod.fromJson(it) { key -> s.shared(key) } }
    // The value as written so far: a pair of bounds keeps the one set while the other is not
    val value: Any? = when (val right = draft.opt(Conditions.RIGHT)) {
        is JSONArray -> JSONArray((0 until right.length()).map { (right.opt(it) as? JSONObject)?.opt(Conditions.CONSTANT) ?: JSONObject.NULL })
        is JSONObject -> right.opt(Conditions.CONSTANT)?.takeIf { it != JSONObject.NULL }
        else -> null
    }

    FieldPicker(
        label = s.shared("pointer_filter_field"),
        fields = fields,
        selected = path?.let { FieldPick.Path(it) },
        onSelect = { pick -> onDraft(JSONObject().put(Conditions.LEFT, JSONObject().put(Conditions.FIELD, (pick as FieldPick.Path).path))) }
    )
    if (field == null) return

    // A date takes a period, or no answer, or an answer
    val ops = if (isDate) listOf(FilterOperator.ABSENT, FilterOperator.PRESENT) else EntryFilters.operatorsFor(field.type).toList()
    val periodLabel = s.shared("filter_op_period")
    val labels = (if (isDate) listOf(periodLabel) else emptyList()) + ops.map { PointerDescription.operator(it, s) }
    UI.FormSelection(
        label = s.shared("pointer_filter_condition"),
        options = labels,
        selected = when {
            period != null -> periodLabel
            else -> op?.let { PointerDescription.operator(it, s) } ?: ""
        },
        onSelect = { label ->
            onDraft(
                if (label == periodLabel) JSONObject().put(Conditions.LEFT, JSONObject().put(Conditions.FIELD, path)).put(PERIOD, EntryPeriod().toJson())
                else Conditions.onField(path, ops.first { PointerDescription.operator(it, s) == label }.key, null)
            )
        },
        required = true
    )
    if (period != null) {
        // Another field starts a new period: the pickers keep no mode of the one before
        key(path) {
            PeriodPicker(period, { next -> onDraft(JSONObject(draft.toString()).put(PERIOD, next.toJson())) }, field.type, reference)
        }
    } else if (op != null) {
        fun setValue(next: Any?) = onDraft(Conditions.onField(path, op.key, next))
        ValueInput(field, op, value, ::setValue)
        if (field.type == FieldType.TEXT && (op == FilterOperator.CONTAINS || op == FilterOperator.EQUAL)) {
            PresentValues(toolInstanceId, path, ::setValue)
        }
    }
}

/**
 * The conditions [draft] stands for once complete and valid against [fields]: one, or for a
 * period one per bound set; null while it is not.
 */
fun conditions(draft: JSONObject, fields: Map<String, FieldDefinition>, text: (String) -> String): JSONArray? {
    val path = Conditions.fieldOf(draft) ?: return null
    draft.optJSONObject(PERIOD)?.let { json ->
        return periodConditions(path, EntryPeriod.fromJson(json, text)).takeIf { it.length() > 0 }
    }
    if (FilterOperator.of(draft.optString(Conditions.OP)) == null) return null
    val single = JSONArray().put(draft)
    return single.takeIf { EntryFilters.parse(it, fields, text) is EntryFilters.Parsed.Ready }
}

/** The key of a date field's period in the condition being written. */
private const val PERIOD = "period"

/** A period on the date field [path] as conditions: ">=" its start and "<=" its end, each only when set. */
internal fun periodConditions(path: String, period: EntryPeriod): JSONArray = JSONArray().apply {
    period.start?.let { put(Conditions.onField(path, ">=", it.toJson())) }
    period.end?.let { put(Conditions.onField(path, "<=", it.toJson())) }
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
            LogManager.ui("ConditionPicker: values of $path not read: ${result.error}", "ERROR")
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
 * The written value of a condition, entered as its field is: none for "no answer" and
 * "answered", two for a range, the options to tick for a choice. [value] is as Conditions.right
 * gives it: a JSONArray of the two bounds for BETWEEN, of the options for IN.
 */
@Composable
private fun ValueInput(field: FieldDefinition, op: FilterOperator, value: Any?, onChange: (Any?) -> Unit) {
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
                    onCheckedChange = { on -> onChange((if (on) ticked + option else ticked - option).takeIf { it.isNotEmpty() }) },
                    label = choice.labelOf(option)
                )
            }
        }
        op == FilterOperator.BETWEEN -> {
            val bounds = value as? JSONArray ?: JSONArray().put(JSONObject.NULL).put(JSONObject.NULL)
            fun bound(i: Int) = JsonUtils.toValue(bounds.opt(i).takeIf { it != JSONObject.NULL })
            FieldInput(field.copy(displayName = s.shared("filter_low")), bound(0), { onChange(listOf(it, bound(1))) }, context, required = true)
            FieldInput(field.copy(displayName = s.shared("filter_high")), bound(1), { onChange(listOf(bound(0), it)) }, context, required = true)
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
        else -> FieldInput(field, JsonUtils.toValue(value), onChange, context, required = true)
    }
}
