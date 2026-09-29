package com.assistant.core.ui.selectors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ToolFields
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.PeriodPicker
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/** The reading choices beside the fields: count the entries, or reduce a formula per entry. */
private const val COUNT = "count"
private const val PER_ENTRY = "per_entry"

/**
 * A reading term: its tool, its field (or a formula per entry, or a count), its reduction, its
 * period relative to the instant it is read at, its filters.
 */
@Composable
fun ReadingEditor(reading: JSONObject, s: StringsContext, onChange: (JSONObject) -> Unit) {
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
    FieldPicker(
        label = s.shared("variable_reading_what"),
        fields = fields,
        selected = when {
            perEntry -> FieldPick.Other(PER_ENTRY)
            reading.has("field") -> FieldPick.Path(reading.getString("field"))
            else -> FieldPick.Other(COUNT)
        },
        onSelect = { pick ->
            edit {
                remove("field"); remove("per_entry")
                when (pick) {
                    FieldPick.Other(COUNT) -> put("reduction", Reduction.COUNT.name)
                    FieldPick.Other(PER_ENTRY) -> { put("per_entry", ""); put("reduction", Reduction.SUM.name) }
                    is FieldPick.Path -> {
                        put("field", pick.path)
                        put("reduction", Reduction.forType(fields.getValue(pick.path).type).first().name)
                    }
                    is FieldPick.Other -> error("No reading choice '${pick.key}'")
                }
            }
        },
        accepts = { Reduction.forType(it.type).isNotEmpty() },
        others = mapOf(COUNT to s.shared("variable_reading_count"), PER_ENTRY to s.shared("variable_reading_per_entry"))
    )
    if (perEntry) {
        UI.FormField(label = s.shared("variable_reading_per_entry"), value = reading.optString("per_entry"), onChange = { v -> edit { put("per_entry", v) } }, required = true)
    }
    val fieldType = reading.optString("field").takeIf { it.isNotEmpty() }?.let { fields[it]?.type }
    val reductions = when {
        perEntry -> listOf(Reduction.SUM, Reduction.AVERAGE, Reduction.MIN, Reduction.MAX, Reduction.LAST, Reduction.COUNT)
        // Nothing to offer until the field is read
        reading.has("field") -> fieldType?.let { Reduction.forType(it).toList() } ?: emptyList()
        else -> listOf(Reduction.COUNT)
    }
    ReductionPicker(
        label = s.shared("variable_reading_reduction"),
        reductions = reductions,
        selected = reductions.firstOrNull { it.name == reading.optString("reduction") },
        onSelect = { reduction -> edit { put("reduction", reduction.name) } },
        s = s
    )

    // Its period, relative to the instant the variable is read at; one that does not read says why
    val period = try {
        EntryPeriod.fromJson(selection.optJSONObject("period") ?: JSONObject()) { s.shared(it) }
    } catch (e: IllegalArgumentException) {
        UI.Text(e.message ?: "", TextType.ERROR)
        null
    }
    if (period != null) {
        PeriodPicker(period, { next ->
            edit { put("selection", JSONObject(selection.toString()).apply { if (next.isEmpty) remove("period") else put("period", next.toJson()) }) }
        }, FieldType.DATETIME, s.shared("instant_reference_reading"))
    }
    // Its filters on the entries' values, relative dates resolved at each reading
    var editingFilters by rememberSaveable { mutableStateOf(false) }
    val filters = selection.optJSONArray("filters") ?: org.json.JSONArray()
    for (i in 0 until filters.length()) {
        UI.Text(text = PointerDescription.filter(filters.getJSONObject(i), fields, s), type = TextType.CAPTION)
    }
    if (toolId != null) {
        UI.Button(type = ButtonType.DEFAULT, onClick = { editingFilters = true }) {
            UI.Text(text = s.shared("variable_reading_filters").format(filters.length()), type = TextType.LABEL)
        }
    }
    if (editingFilters && toolId != null) {
        PointerFiltersDialog(
            toolInstanceId = toolId,
            fields = fields,
            filters = filters,
            chosenFields = null,
            reference = s.shared("instant_reference_reading"),
            onDismiss = { editingFilters = false },
            onConfirm = { chosen, _ ->
                editingFilters = false
                edit { put("selection", JSONObject(selection.toString()).apply { if (chosen.length() == 0) remove("filters") else put("filters", chosen) }) }
            }
        )
    }
}
