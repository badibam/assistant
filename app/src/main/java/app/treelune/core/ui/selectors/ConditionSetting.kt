package app.treelune.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.conditions.Conditions
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.EntryFilters
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.reading.Reduction
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
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

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
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

/**
 * A condition put on each row a config describes, as a setting (SettingNode.Condition.onRow): a
 * field of the row on the left (FieldPicker), the operators its type takes, and on the right a
 * written value, entered as that field, or another field of the row of the same type
 * (« kcal > objectif_calorique »).
 *
 * @param condition Its stored form, null while none is set
 * @param fields The fields of the row, by name
 */
@Composable
fun RowConditionSetting(
    label: String,
    condition: JSONObject?,
    fields: Map<String, FieldDefinition>,
    onChange: (JSONObject) -> Unit,
    s: StringsContext
) {
    val current = condition ?: JSONObject()
    val path = Conditions.fieldOf(current)
    val field = path?.let { fields[it] }
    fun with(change: JSONObject.() -> Unit) = onChange(JSONObject(current.toString()).apply(change))

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(text = label, type = TextType.SUBTITLE)
        FieldPicker(
            label = s.shared("pointer_filter_field"),
            fields = fields,
            selected = path?.let { FieldPick.Path(it) },
            // Another field is another type: its operator and right side start over
            onSelect = { pick -> onChange(JSONObject().put(Conditions.LEFT, JSONObject().put(Conditions.FIELD, (pick as FieldPick.Path).path))) }
        )
        if (field == null) return@Column

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
        // The fields of the row a side may name beside a written value: those of the same type
        val others = fields.filter { (name, other) -> name != path && other.type == field.type }
        when (val right = current.opt(Conditions.RIGHT)) {
            is JSONObject -> RowSide(right, field, others, s) { side -> with { put(Conditions.RIGHT, side) } }
            is JSONArray -> (0 until right.length()).forEach { i ->
                UI.Text(text = s.shared(if (i == 0) "filter_low" else "filter_high"), type = TextType.LABEL)
                RowSide(right.optJSONObject(i) ?: constant(), field, others, s) { side ->
                    with { put(Conditions.RIGHT, JSONArray(right.toString()).put(i, side)) }
                }
            }
            else -> Unit
        }
    }
}

/** One side on the right of a condition on each row: a written value, or another field of the row. */
@Composable
private fun RowSide(side: JSONObject, field: FieldDefinition, others: Map<String, FieldDefinition>, s: StringsContext, onChange: (JSONObject) -> Unit) {
    val context = LocalContext.current
    val sideField = Conditions.fieldOf(JSONObject().put(Conditions.LEFT, side))
    if (others.isNotEmpty()) {
        FieldPicker(
            label = s.shared("condition_side"),
            fields = others,
            selected = sideField?.let { FieldPick.Path(it) } ?: FieldPick.Other(Conditions.CONSTANT),
            onSelect = { pick ->
                onChange(when (pick) {
                    is FieldPick.Path -> JSONObject().put(Conditions.FIELD, pick.path)
                    is FieldPick.Other -> constant()
                })
            },
            others = mapOf(Conditions.CONSTANT to s.shared("variable_term_constant"))
        )
    }
    if (sideField == null) {
        app.treelune.core.fields.FieldInput(
            field.copy(displayName = s.shared("variable_value")),
            JsonUtils.toValue(side.opt(Conditions.CONSTANT)?.takeIf { it != JSONObject.NULL }),
            { value -> onChange(value?.let { app.treelune.core.terms.Term.Constant(it).toJson() } ?: constant()) },
            context, required = true
        )
    }
}
