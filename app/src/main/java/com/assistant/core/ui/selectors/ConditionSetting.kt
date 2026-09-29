package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.conditions.Conditions
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FilterOperator
import com.assistant.core.reading.Reduction
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * A condition judged once, as a setting (SettingNode.Condition): its left side, the operators
 * its type takes, then the side or two on the right, each a term (TermPicker) whose constant is
 * entered as the left side's field.
 *
 * With [entered], the condition is put on that value, entered in each entry: its left side is not
 * chosen, the app writes it.
 *
 * @param condition Its stored form, null while none is set
 */
@Composable
fun ConditionSetting(
    label: String,
    condition: JSONObject?,
    entered: FieldDefinition?,
    onChange: (JSONObject) -> Unit,
    s: StringsContext,
    where: ReadingContext
) {
    val current = condition ?: JSONObject()
    val left = current.optJSONObject(Conditions.LEFT)?.takeIf { entered == null }
        ?: JSONObject().put("reading", JSONObject().put("selection", JSONObject()).put("reduction", Reduction.COUNT.name))
    fun with(change: JSONObject.() -> Unit) = onChange(JSONObject(current.toString()).apply(change))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(text = label, type = TextType.SUBTITLE)
        if (entered == null) {
            TermPicker(left, remember { FieldDefinition("left", label, null, FieldType.NUMERIC, false, null) }, { term ->
                // Another left side is another type: its operator and right side start over
                onChange(JSONObject().put(Conditions.LEFT, term))
            }, s, where)
        }
        val field = entered ?: rememberTermField(left) ?: return@Column

        val ops = EntryFilters.operatorsFor(field.type).toList()
        val op = FilterOperator.of(current.optString(Conditions.OP))?.takeIf { it in ops }
        UI.FormSelection(
            label = s.shared("pointer_filter_condition"),
            options = ops.map { PointerDescription.operator(it, s) },
            selected = op?.let { PointerDescription.operator(it, s) } ?: "",
            onSelect = { text ->
                val next = ops.first { PointerDescription.operator(it, s) == text }
                with {
                    put(Conditions.OP, next.key)
                    if (entered == null) put(Conditions.LEFT, left)
                    // The right side keeps its shape when the new operator takes the same
                    when (next) {
                        FilterOperator.ABSENT, FilterOperator.PRESENT -> remove(Conditions.RIGHT)
                        FilterOperator.BETWEEN -> if (opt(Conditions.RIGHT) !is JSONArray) put(Conditions.RIGHT, JSONArray().put(constant()).put(constant()))
                        else -> if (opt(Conditions.RIGHT) !is JSONObject) put(Conditions.RIGHT, constant())
                    }
                }
            },
            required = true
        )
        val constantField = field.copy(displayName = s.shared("variable_value"))
        when (val right = current.opt(Conditions.RIGHT)) {
            is JSONObject -> TermPicker(right, constantField, { term -> with { put(Conditions.RIGHT, term) } }, s, where)
            is JSONArray -> (0 until right.length()).forEach { i ->
                UI.Text(text = s.shared(if (i == 0) "filter_low" else "filter_high"), type = TextType.LABEL)
                TermPicker(right.optJSONObject(i) ?: constant(), constantField, { term ->
                    with { put(Conditions.RIGHT, JSONArray(right.toString()).put(i, term)) }
                }, s, where)
            }
            else -> Unit
        }
    }
}

private fun constant() = JSONObject().put(Conditions.CONSTANT, JSONObject.NULL)

/**
 * The field [term] reads values of, which a condition compares them as: a reading's field (a
 * count is a number), a variable's; null for a constant, which has no type of its own, and while
 * it is read.
 */
@Composable
fun rememberTermField(term: JSONObject): FieldDefinition? {
    val context = LocalContext.current
    val reading = term.optJSONObject("reading")
    val toolId = reading?.optJSONObject("selection")?.optJSONObject("target")?.optString("id")?.takeIf { it.isNotEmpty() }
    val fields = rememberToolFields(toolId)
    val variableId = term.optString("variable").takeIf { it.isNotEmpty() }
    var variableField by remember(variableId) { mutableStateOf<FieldDefinition?>(null) }
    LaunchedEffect(variableId) {
        if (variableId == null) return@LaunchedEffect
        val result = Coordinator(context).processUserAction("variables.get", mapOf("variable_id" to variableId))
        if (!result.isSuccess) return@LaunchedEffect
        val definition = (result.data?.get("definition") as? Map<*, *>)?.let { JsonUtils.toJSONObject(it.entries.associate { e -> e.key.toString() to e.value }) }
        val declared = definition?.optJSONObject("field")
        // A formula whose type is deduced reads numbers
        variableField = FieldDefinition(variableId, variableId, null,
            declared?.optString("type")?.let { t -> FieldType.entries.firstOrNull { it.name == t } } ?: FieldType.NUMERIC, false,
            declared?.optJSONObject("config")?.let { JsonUtils.toMap(it).mapValues { v -> v.value!! } })
    }
    return when {
        reading != null -> reading.optString("field").takeIf { it.isNotEmpty() }?.let { fields[it] }
            ?: FieldDefinition("count", "count", null, FieldType.NUMERIC, false, mapOf("decimals" to 0)).takeIf { !reading.has("field") }
        variableId != null -> variableField
        else -> null
    }
}
