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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.formatValue
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.selectors.ReadingContext
import com.assistant.core.ui.selectors.TermPicker
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
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
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

        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
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
 * The formula in text, the names to insert where the cursor stands, and what stops it from
 * computing, said at each keystroke: where it does not read, or the names that are neither a term
 * nor a variable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaEditor(formula: String, terms: List<String>, variables: List<String>, s: StringsContext, onChange: (String) -> Unit) {
    // The cursor is kept here; a formula changed elsewhere puts it at the end
    var field by remember { mutableStateOf(TextFieldValue(formula, TextRange(formula.length))) }
    val shown = if (field.text == formula) field else TextFieldValue(formula, TextRange(formula.length))
    UI.FormField(label = s.shared("variable_formula"), value = shown, onChange = { next ->
        field = next
        if (next.text != formula) onChange(next.text)
    }, required = true, fieldType = com.assistant.core.ui.FieldType.TEXT_MEDIUM)
    val problem = when (val parsed = Formula.parse(formula, Formula.Function.localized { s.shared(it) })) {
        is Formula.Parsed.Unreadable -> if (formula.isBlank()) null
            else s.shared("variable_error_formula").format(parsed.position + 1, s.shared("formula_problem_${parsed.problem.name.lowercase()}"), parsed.detail)
        is Formula.Parsed.Read -> parsed.formula.names().filter { it !in terms && it !in variables }.distinct()
            .takeIf { it.isNotEmpty() }?.let { s.shared("variable_unknown_names").format(it.joinToString(", ")) }
    }
    problem?.let { UI.Text(text = it, type = TextType.ERROR) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        (terms + variables + listOf("+", "-", "×", "÷", "(", ")") + Formula.Function.entries.map { s.shared("formula_function_${it.canonical}") + "(" }).forEach { token ->
            UI.Button(type = ButtonType.SECONDARY, onClick = {
                val (text, cursor) = insertToken(shown.text, shown.selection.min, shown.selection.max, token)
                field = TextFieldValue(text, TextRange(cursor))
                onChange(text)
            }) {
                UI.Text(text = token, type = TextType.LABEL)
            }
        }
    }
}

/**
 * [text] with [token] in place of what lies between [start] and [end] (the cursor, or the
 * selection it replaces), and the cursor just after it. A space sets it apart from its
 * neighbours, except at an edge, inside parentheses, or after a function's opening one:
 * "a + |b" with "×" gives "a + × b".
 */
internal fun insertToken(text: String, start: Int, end: Int, token: String): Pair<String, Int> {
    val before = text.substring(0, start)
    val after = text.substring(end)
    val left = if (before.isEmpty() || before.endsWith(" ") || before.endsWith("(")) "" else " "
    val right = if (after.isEmpty() || after.startsWith(" ") || after.startsWith(")") || token.endsWith("(")) "" else " "
    val inserted = before + left + token
    return (inserted + right + after) to inserted.length
}

/** One term of the formula: its local name, and what it is (TermPicker), a number when a constant. */
@Composable
private fun TermEditor(
    name: String,
    term: JSONObject,
    s: StringsContext,
    onRename: (String) -> Unit,
    onChange: (JSONObject) -> Unit,
    onRemove: () -> Unit
) {
    var shownName by remember(name) { mutableStateOf(name) }
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(label = s.shared("variable_term_name"), value = shownName, onChange = { shownName = it }, required = true,
                        fieldModifier = com.assistant.core.ui.FieldModifier(onFocusChanged = { state -> if (!state.isFocused && shownName != name && shownName.isNotBlank()) onRename(shownName) }))
                }
                UI.ActionButton(action = ButtonAction.DELETE, display = com.assistant.core.ui.ButtonDisplay.ICON, onClick = onRemove)
            }
            TermPicker(term, remember { FieldDefinition(name, name, null, FieldType.NUMERIC, false, null) }, onChange, s, ReadingContext(s.shared("instant_reference_reading")))
        }
    }
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
        FieldType.NUMERIC -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = s.shared("field_config_unit"), value = config.optString("unit"), onChange = { edit("unit", it.takeIf { u -> u.isNotBlank() }) }, required = false)
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = s.shared("field_config_decimals"), value = config.optInt("decimals").toString(),
                    onChange = { t -> t.toIntOrNull()?.let { edit("decimals", it) } }, fieldType = com.assistant.core.ui.FieldType.NUMERIC, required = true)
            }
        }
        FieldType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
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
