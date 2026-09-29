package com.assistant.core.ui.variables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ToolFields
import com.assistant.core.fields.formatValue
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.utils.JsonUtils
import com.assistant.core.variables.Formula
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A variable seen and changed (docs/DATA.md, « Variables »): its name, a constant or a formula
 * written in text with buttons inserting the names and a check at each keystroke, its terms each
 * with its period relative to the instant it is read at, the type of its value, and its value now.
 * The service checks it again when it is saved, as it checks the AI's.
 *
 * The draft is the definition the service takes, written with names, kept as JSON across a rotation.
 *
 * @param variableId The variable changed; null to create one in [zoneId], in [group]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariableScreen(zoneId: String, variableId: String?, group: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf(JSONObject().put("kind", "FORMULA").put("formula", "").put("terms", JSONObject()).toString()) }
    var loaded by rememberSaveable { mutableStateOf(variableId == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<String?>(null) }
    var others by remember { mutableStateOf<List<VariableRow>>(emptyList()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(variableId) {
        val all = coordinator.processUserAction("variables.list_all", emptyMap())
        others = (all.data?.get("variables") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { VariableRow.of(it) }.filter { it.id != variableId }
        if (variableId != null && !loaded) {
            val result = coordinator.processUserAction("variables.get", mapOf("variable_id" to variableId))
            val variable = result.data?.get("variable") as? Map<*, *>
            if (variable != null) {
                name = variable["name"] as String
                @Suppress("UNCHECKED_CAST")
                val definition = JsonUtils.toJSONObject(variable["definition"] as Map<String, Any?>)
                // Shown with each function in the app's language, as it is written here
                fun localized(text: String) = (Formula.parse(text) as? Formula.Parsed.Read)?.formula?.text({ it }, { f -> s.shared("formula_function_${f.canonical}") }) ?: text
                definition.optString("formula").takeIf { it.isNotEmpty() }?.let { definition.put("formula", localized(it)) }
                definition.optJSONObject("terms")?.let { terms ->
                    terms.keys().forEach { key ->
                        terms.getJSONObject(key).optJSONObject("reading")?.let { r -> r.optString("per_entry").takeIf { it.isNotEmpty() }?.let { r.put("per_entry", localized(it)) } }
                    }
                }
                draft = definition.toString()
                loaded = true
            } else error = result.error
        }
        if (variableId != null) {
            val value = coordinator.processUserAction("variables.evaluate", mapOf("variable_id" to variableId, "at" to listOf(System.currentTimeMillis())))
            val row = (value.data?.get("values") as? List<*>)?.firstOrNull() as? Map<*, *>
            val field = (value.data?.get("field") as? Map<*, *>)?.let { fieldOf(name, it) }
            current = when {
                !value.isSuccess -> value.error
                row?.get("failure") != null -> s.shared("variable_no_value").format((row["failure"] as Map<*, *>)["message"] as? String ?: "")
                field != null && row?.get("value") != null -> field.formatValue(row["value"], context)
                else -> null
            }
        }
    }

    val json = JSONObject(draft)
    fun edit(change: JSONObject.() -> Unit) { draft = JSONObject(draft).apply(change).toString() }
    val isFormula = json.optString("kind") == "FORMULA"
    val terms = json.optJSONObject("terms") ?: JSONObject()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.PageHeader(
            title = name.ifBlank { s.shared("variable_new") },
            leftButton = ButtonAction.BACK,
            onLeftClick = onDone
        )
        if (!loaded) {
            UI.LoadingIndicator()
            return@Column
        }
        current?.let { UI.Text(text = s.shared("variable_value_now").format(it), type = TextType.SUBTITLE) }

        UI.FormField(label = s.shared("label_name"), value = name, onChange = { name = it }, required = true)

        val kinds = listOf("FORMULA" to s.shared("variable_kind_formula"), "CONSTANT" to s.shared("variable_kind_constant"))
        UI.FormSelection(
            label = s.shared("variable_kind"),
            options = kinds.map { it.second },
            selected = kinds.first { it.first == json.optString("kind") }.second,
            onSelect = { label ->
                val kind = kinds.first { it.second == label }.first
                draft = if (kind == "CONSTANT") JSONObject().put("kind", "CONSTANT").put("value", 0)
                    .put("field", JSONObject().put("type", "NUMERIC").put("config", JSONObject().put("decimals", 0))).toString()
                else JSONObject().put("kind", "FORMULA").put("formula", "").put("terms", JSONObject()).toString()
            },
            required = true
        )

        if (!isFormula) {
            val field = fieldOf(name, JsonUtils.toMap(json.optJSONObject("field") ?: JSONObject()))
            if (field != null) {
                FieldInput(field.copy(displayName = s.shared("variable_value")), json.opt("value"), { v -> edit { put("value", v ?: 0) } }, context, required = true)
            }
        } else {
            FormulaEditor(json.optString("formula"), terms.keys().asSequence().toList(), others.map { it.name }, s) { text -> edit { put("formula", text) } }

            UI.Text(text = s.shared("variable_terms"), type = TextType.SUBTITLE)
            terms.keys().asSequence().toList().forEach { termName ->
                TermEditor(
                    name = termName,
                    term = terms.getJSONObject(termName),
                    others = others,
                    s = s,
                    onRename = { newName -> edit { val t = getJSONObject("terms"); val v = t.remove(termName); t.put(newName, v) } },
                    onChange = { term -> edit { getJSONObject("terms").put(termName, term) } },
                    onRemove = { edit { getJSONObject("terms").remove(termName) } }
                )
            }
            UI.Button(type = ButtonType.SECONDARY, onClick = {
                edit {
                    val t = getJSONObject("terms")
                    var n = 1
                    while (t.has("t$n")) n++
                    t.put("t$n", JSONObject().put("constant", 0))
                }
            }) { UI.Text(text = s.shared("variable_add_term"), type = TextType.LABEL) }
        }

        ValueTypeEditor(json.optJSONObject("field"), isFormula, s) { field -> edit { if (field == null) remove("field") else put("field", field) } }

        error?.let { UI.Text(text = it, type = TextType.ERROR) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UI.Button(type = ButtonType.PRIMARY, onClick = {
                scope.launch {
                    val params = mutableMapOf<String, Any>("name" to name, "definition" to JsonUtils.toMap(JSONObject(draft)))
                    val result = if (variableId == null) {
                        coordinator.processUserAction("variables.create", params + ("zone_id" to zoneId) + (group?.let { mapOf("group" to it) } ?: emptyMap()))
                    } else coordinator.processUserAction("variables.update", params + ("variable_id" to variableId))
                    if (result.isSuccess) onDone() else error = result.error
                }
            }) { UI.Text(text = s.shared("action_save"), type = TextType.LABEL) }
            if (variableId != null) {
                UI.ActionButton(action = ButtonAction.DELETE, onClick = { confirmDelete = true })
            }
        }
    }

    if (confirmDelete && variableId != null) {
        UI.Dialog(type = DialogType.DANGER, onCancel = { confirmDelete = false }, onConfirm = {
            scope.launch {
                val result = coordinator.processUserAction("variables.delete", mapOf("variable_id" to variableId))
                confirmDelete = false
                if (result.isSuccess) onDone() else error = result.error
            }
        }) { UI.Text(text = s.shared("variable_delete_confirm").format(name), type = TextType.BODY) }
    }
}

/**
 * The formula in text, the names to insert, and what stops it from computing, said at each
 * keystroke: where it does not read, or the names that are neither a term nor a variable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaEditor(formula: String, terms: List<String>, variables: List<String>, s: StringsContext, onChange: (String) -> Unit) {
    UI.FormField(label = s.shared("variable_formula"), value = formula, onChange = onChange, required = true,
        fieldType = com.assistant.core.ui.FieldType.TEXT_MEDIUM)
    val problem = when (val parsed = Formula.parse(formula, Formula.Function.localized { s.shared(it) })) {
        is Formula.Parsed.Unreadable -> if (formula.isBlank()) null
            else s.shared("variable_error_formula").format(parsed.position + 1, s.shared("formula_problem_${parsed.problem.name.lowercase()}"), parsed.detail)
        is Formula.Parsed.Read -> parsed.formula.names().filter { it !in terms && it !in variables }.distinct()
            .takeIf { it.isNotEmpty() }?.let { s.shared("variable_unknown_names").format(it.joinToString(", ")) }
    }
    problem?.let { UI.Text(text = it, type = TextType.ERROR) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (terms + variables + listOf("+", "-", "×", "÷", "(", ")") + Formula.Function.entries.map { s.shared("formula_function_${it.canonical}") + "(" }).forEach { token ->
            UI.Button(type = ButtonType.SECONDARY, onClick = { onChange(formula.trimEnd() + (if (formula.isBlank()) "" else " ") + token) }) {
                UI.Text(text = token, type = TextType.LABEL)
            }
        }
    }
}

/** One term: its name, and what it is — a reading, a constant, another variable. */
@Composable
private fun TermEditor(
    name: String,
    term: JSONObject,
    others: List<VariableRow>,
    s: StringsContext,
    onRename: (String) -> Unit,
    onChange: (JSONObject) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var shownName by remember(name) { mutableStateOf(name) }
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(label = s.shared("variable_term_name"), value = shownName, onChange = { shownName = it }, required = true,
                        fieldModifier = com.assistant.core.ui.FieldModifier(onFocusChanged = { state -> if (!state.isFocused && shownName != name && shownName.isNotBlank()) onRename(shownName) }))
                }
                UI.ActionButton(action = ButtonAction.DELETE, display = com.assistant.core.ui.ButtonDisplay.ICON, onClick = onRemove)
            }
            val kinds = listOf("reading" to s.shared("variable_term_reading"), "constant" to s.shared("variable_term_constant"), "variable" to s.shared("variable_term_variable"))
            val kind = kinds.firstOrNull { term.has(it.first) }?.first ?: "constant"
            UI.FormSelection(label = s.shared("variable_term_kind"), options = kinds.map { it.second }, selected = kinds.first { it.first == kind }.second, onSelect = { label ->
                onChange(when (kinds.first { it.second == label }.first) {
                    "reading" -> JSONObject().put("reading", JSONObject().put("selection", JSONObject()).put("reduction", Reduction.COUNT.name))
                    "variable" -> JSONObject().put("variable", others.firstOrNull()?.id ?: "")
                    else -> JSONObject().put("constant", 0)
                })
            }, required = true)
            when (kind) {
                "constant" -> UI.FormField(label = s.shared("variable_value"), value = term.opt("constant")?.toString() ?: "",
                    onChange = { text -> text.replace(',', '.').toDoubleOrNull()?.let { onChange(JSONObject().put("constant", it)) } },
                    fieldType = com.assistant.core.ui.FieldType.NUMERIC, required = true)
                "variable" -> if (others.isEmpty()) UI.Text(text = s.shared("scope_no_options"), type = TextType.BODY) else UI.FormSelection(
                    label = s.shared("variable_term_variable"),
                    options = others.map { it.name },
                    selected = others.firstOrNull { it.id == term.optString("variable") }?.name ?: "",
                    onSelect = { label -> onChange(JSONObject().put("variable", others.first { it.name == label }.id)) },
                    required = true
                )
                else -> ReadingEditor(term.getJSONObject("reading"), s) { reading -> onChange(JSONObject().put("reading", reading)) }
            }
        }
    }
}

/** A reading term: its tool, its field (or a formula per entry, or a count), its reduction, its period. */
@Composable
private fun ReadingEditor(reading: JSONObject, s: StringsContext, onChange: (JSONObject) -> Unit) {
    val context = LocalContext.current
    fun edit(change: JSONObject.() -> Unit) = onChange(JSONObject(reading.toString()).apply(change))
    val selection = reading.optJSONObject("selection") ?: JSONObject()
    val target = selection.optJSONObject("target")
    val toolId = target?.optString("id")?.takeIf { it.isNotEmpty() }
    var fields by remember { mutableStateOf<Map<String, FieldDefinition>>(emptyMap()) }
    var fieldsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toolId) {
        fieldsError = null
        fields = if (toolId == null) emptyMap() else try { ToolFields.filterable(toolId, context, Strings.`for`(context = context)) } catch (e: IllegalStateException) { fieldsError = e.message; emptyMap() }
    }

    FieldInput(
        FieldDefinition("tool", s.shared("reference_kind_tool_instance"), null, FieldType.REFERENCE, false,
            mapOf("target" to mapOf("kinds" to listOf(ReferenceKind.TOOL_INSTANCE.name)))),
        target?.let { JsonUtils.toMap(it) },
        { value -> edit { put("selection", JSONObject(selection.toString()).put("target", JsonUtils.toJSONObject((value as Map<*, *>).entries.associate { it.key.toString() to it.value }))); remove("field") } },
        context, required = true
    )

    fieldsError?.let { UI.Text(it, TextType.ERROR) }

    // What is reduced: a field, a formula per entry, or the entries themselves counted
    val perEntry = reading.has("per_entry")
    val count = s.shared("variable_reading_count")
    val formula = s.shared("variable_reading_per_entry")
    val numeric = fields.filterValues { Reduction.forType(it.type).isNotEmpty() }
    val options = listOf(count, formula) + numeric.map { (path, f) -> "${f.displayName} ($path)" }
    UI.FormSelection(
        label = s.shared("variable_reading_what"),
        options = options,
        selected = when {
            perEntry -> formula
            reading.has("field") -> numeric[reading.getString("field")]?.let { "${it.displayName} (${reading.getString("field")})" } ?: reading.getString("field")
            else -> count
        },
        onSelect = { label ->
            edit {
                remove("field"); remove("per_entry")
                when (label) {
                    count -> put("reduction", Reduction.COUNT.name)
                    formula -> { put("per_entry", ""); put("reduction", Reduction.SUM.name) }
                    else -> {
                        val path = numeric.keys.first { "${numeric.getValue(it).displayName} ($it)" == label }
                        put("field", path)
                        put("reduction", Reduction.forType(numeric.getValue(path).type).first().name)
                    }
                }
            }
        },
        required = true
    )
    if (perEntry) {
        UI.FormField(label = s.shared("variable_reading_per_entry"), value = reading.optString("per_entry"), onChange = { v -> edit { put("per_entry", v) } }, required = true)
    }
    val fieldType = reading.optString("field").takeIf { it.isNotEmpty() }?.let { fields[it]?.type }
    val reductions = when {
        perEntry -> listOf(Reduction.SUM, Reduction.AVERAGE, Reduction.MIN, Reduction.MAX, Reduction.LAST, Reduction.COUNT)
        reading.has("field") -> Reduction.forType(fieldType).toList()
        else -> listOf(Reduction.COUNT)
    }
    if (reductions.size > 1) {
        UI.FormSelection(
            label = s.shared("variable_reading_reduction"),
            options = reductions.map { s.shared("reduction_${it.name.lowercase()}") },
            selected = s.shared("reduction_${reading.optString("reduction").lowercase()}"),
            onSelect = { label -> edit { put("reduction", reductions.first { s.shared("reduction_${it.name.lowercase()}") == label }.name) } },
            required = true
        )
    }

    // Its period, each bound relative to the instant the variable is read at
    val period = selection.optJSONObject("period") ?: JSONObject()
    listOf("start" to s.shared("variable_period_start"), "end" to s.shared("variable_period_end")).forEach { (key, label) ->
        BoundEditor(label, period.opt(key), s) { bound ->
            val nextPeriod = JSONObject(period.toString()).apply { if (bound == null) remove(key) else put(key, bound) }
            edit { put("selection", JSONObject(selection.toString()).apply { if (nextPeriod.length() == 0) remove("period") else put("period", nextPeriod) }) }
        }
    }
    // Its filters on the entries' values, relative dates resolved at each reading
    var editingFilters by rememberSaveable { mutableStateOf(false) }
    val filters = selection.optJSONArray("filters") ?: org.json.JSONArray()
    for (i in 0 until filters.length()) {
        UI.Text(text = com.assistant.core.ui.selectors.PointerDescription.filter(filters.getJSONObject(i), fields, s), type = TextType.CAPTION)
    }
    if (toolId != null) {
        UI.Button(type = ButtonType.DEFAULT, onClick = { editingFilters = true }) {
            UI.Text(text = s.shared("variable_reading_filters").format(filters.length()), type = TextType.LABEL)
        }
    }
    if (editingFilters && toolId != null) {
        com.assistant.core.ui.selectors.PointerFiltersDialog(
            toolInstanceId = toolId,
            fields = fields,
            filters = filters,
            chosenFields = null,
            relative = true,
            onDismiss = { editingFilters = false },
            onConfirm = { chosen, _ ->
                editingFilters = false
                edit { put("selection", JSONObject(selection.toString()).apply { if (chosen.length() == 0) remove("filters") else put("filters", chosen) }) }
            }
        )
    }
}

/**
 * One bound of a term's period: none, the instant read at itself ("the moment itself"), or a
 * relative date — a unit, an offset, and its start or end.
 */
@Composable
private fun BoundEditor(label: String, bound: Any?, s: StringsContext, onChange: (Any?) -> Unit) {
    val none = s.shared("variable_bound_none")
    val now = s.shared("variable_bound_now")
    val relative = s.shared("variable_bound_relative")
    val inner = (bound as? JSONObject)?.opt("relative")
    val mode = when { bound == null -> none; inner == "NOW" -> now; else -> relative }
    UI.FormSelection(label = label, options = listOf(none, now, relative), selected = mode, onSelect = { choice ->
        onChange(when (choice) {
            none -> null
            now -> JSONObject().put("relative", "NOW")
            else -> JSONObject().put("relative", JSONObject().put("unit", "DAY").put("offset", 0).put("edge", if (label == s.shared("variable_period_end")) "END" else "START"))
        })
    }, required = false)
    val parts = inner as? JSONObject ?: return
    fun edit(key: String, value: Any) = onChange(JSONObject().put("relative", JSONObject(parts.toString()).put(key, value)))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            UI.FormField(label = s.shared("variable_bound_offset"), value = parts.optInt("offset").toString(),
                onChange = { text -> text.toIntOrNull()?.let { edit("offset", it) } }, fieldType = com.assistant.core.ui.FieldType.NUMERIC, required = true)
        }
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("variable_bound_unit"), options = PeriodType.entries.map { s.shared("period_type_${it.name.lowercase()}") },
                selected = s.shared("period_type_${parts.optString("unit").lowercase()}"),
                onSelect = { l -> edit("unit", PeriodType.entries.first { s.shared("period_type_${it.name.lowercase()}") == l }.name) }, required = true)
        }
    }
    val edges = listOf("START" to s.shared("variable_bound_start"), "END" to s.shared("variable_bound_end"))
    UI.FormSelection(label = s.shared("variable_bound_edge"), options = edges.map { it.second },
        selected = edges.first { it.first == parts.optString("edge", "START") }.second,
        onSelect = { l -> edit("edge", edges.first { it.second == l }.first) }, required = true)
}

/**
 * The type of the value: deduced from what the formula reads, or set — a number with its unit and
 * decimals, a scale with its bounds, a duration. A constant always says it.
 */
@Composable
private fun ValueTypeEditor(field: JSONObject?, deducible: Boolean, s: StringsContext, onChange: (JSONObject?) -> Unit) {
    val deduced = s.shared("variable_type_deduced")
    val types = listOf(FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION)
    val options = (if (deducible) listOf(deduced) else emptyList()) + types.map { s.shared("field_type_${it.name.lowercase()}_display_name") }
    val type = field?.optString("type")?.let { t -> types.firstOrNull { it.name == t } }
    UI.FormSelection(
        label = s.shared("variable_type"),
        options = options,
        selected = type?.let { s.shared("field_type_${it.name.lowercase()}_display_name") } ?: deduced,
        onSelect = { label ->
            val chosen = types.firstOrNull { s.shared("field_type_${it.name.lowercase()}_display_name") == label }
            onChange(when (chosen) {
                null -> null
                FieldType.NUMERIC -> JSONObject().put("type", "NUMERIC").put("config", JSONObject().put("decimals", 0))
                FieldType.SCALE -> JSONObject().put("type", "SCALE").put("config", JSONObject().put("min", 1).put("max", 10))
                else -> JSONObject().put("type", "DURATION")
            })
        },
        required = true
    )
    val config = field?.optJSONObject("config") ?: return
    fun edit(key: String, value: Any?) = onChange(JSONObject(field.toString()).put("config", JSONObject(config.toString()).apply { if (value == null) remove(key) else put(key, value) }))
    when (type) {
        FieldType.NUMERIC -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = s.shared("field_config_unit"), value = config.optString("unit"), onChange = { edit("unit", it.takeIf { u -> u.isNotBlank() }) }, required = false)
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = s.shared("field_config_decimals"), value = config.optInt("decimals").toString(),
                    onChange = { t -> t.toIntOrNull()?.let { edit("decimals", it) } }, fieldType = com.assistant.core.ui.FieldType.NUMERIC, required = true)
            }
        }
        FieldType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("min" to "field_config_min", "max" to "field_config_max").forEach { (key, labelKey) ->
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(label = s.shared(labelKey), value = config.opt(key)?.toString() ?: "",
                        onChange = { t -> t.toDoubleOrNull()?.let { edit(key, it) } }, fieldType = com.assistant.core.ui.FieldType.NUMERIC, required = true)
                }
            }
        }
        else -> {}
    }
}
