package com.assistant.core.ui.selectors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.UI
import org.json.JSONObject

/** The reading choices beside the fields: count the entries, or reduce a formula per entry. */
private const val COUNT = "count"
private const val PER_ENTRY = "per_entry"

/** What a reading reads: the entries of a tool. */
private val READ = ReferenceTarget(setOf(ReferenceKind.TOOL_INSTANCE), emptyList())

/** The selection being edited across a rotation, null before the stored one is named. */
private val DraftSaver: Saver<SelectionDraft?, String> = Saver(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { SelectionDraft.fromJson(JSONObject(it)) } }
)

/**
 * The Lecture brick's selector (docs/BRICKS.md): a selection of a tool's entries (SelectionPicker),
 * its period relative to the instant the reading is made, then what is reduced (a field, a formula
 * per entry, or the entries counted) and how (ReductionPicker). [reading] is its stored form,
 * `{"selection", "field" | "per_entry", "reduction"}`.
 */
@Composable
fun ReadingPicker(reading: JSONObject, s: StringsContext, onChange: (JSONObject) -> Unit) {
    val context = LocalContext.current
    fun edit(change: JSONObject.() -> Unit) = onChange(JSONObject(reading.toString()).apply(change))

    // The stored selection, its tool and zone named as they are now; a tool deleted since starts over
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<SelectionDraft?>(null) }
    LaunchedEffect(Unit) {
        if (draft != null) return@LaunchedEffect
        val stored = reading.optJSONObject("selection")?.takeIf { it.has("target") }?.let { EntrySelection.fromJson(it) { key -> s.shared(key) } }
        draft = stored?.target?.id?.let { toolPath(it, context) }?.let { SelectionDraft.of(stored, it) } ?: SelectionDraft()
    }
    val current = draft ?: return UI.LoadingIndicator()
    val fields = rememberToolFields(current.tool?.id)

    SelectionPicker(current, { next ->
        draft = next
        edit {
            put("selection", if (next.level == ReferenceKind.TOOL_INSTANCE) next.selection().toJson() else JSONObject())
            // Another tool's fields are not this one's
            if (next.tool != current.tool) remove("field")
        }
    }, READ, fields, s.shared("instant_reference_reading"), offerFields = false)

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
}
