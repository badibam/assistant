package app.treelune.core.ui.selectors

import androidx.compose.runtime.Composable
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.ui.UI

/**
 * What a field picker holds: a field of the tool by its path, or one of the choices a context
 * offers beside the fields (a reading counts the entries, or reduces a formula per entry).
 */
sealed interface FieldPick {
    data class Path(val path: String) : FieldPick
    data class Other(val key: String) : FieldPick
}

/**
 * The Champ brick's selector (docs/BRICKS.md): one field among [fields], the fields of a tool by
 * path (ToolFields.filterable), those [accepts] refuses left out. A field shows by its name, and by
 * its name and path when another field of the list has the same name (fieldLabels). The choice is
 * kept by path, never by what is shown.
 *
 * @param others Choices shown before the fields, by key and label
 * @param selected The choice held, null for none yet; a path no longer in [fields] shows as it is
 */
@Composable
fun FieldPicker(
    label: String,
    fields: Map<String, FieldDefinition>,
    selected: FieldPick?,
    onSelect: (FieldPick) -> Unit,
    accepts: (FieldDefinition) -> Boolean = { true },
    others: Map<String, String> = emptyMap(),
    required: Boolean = true
) {
    val labels = fieldLabels(fields.filterValues(accepts))
    val choices: List<Pair<FieldPick, String>> =
        others.map { (key, text) -> FieldPick.Other(key) to text } + labels.map { (path, text) -> FieldPick.Path(path) to text }
    UI.FormSelection(
        label = label,
        options = choices.map { it.second },
        selected = when (selected) {
            null -> ""
            is FieldPick.Other -> others[selected.key] ?: selected.key
            is FieldPick.Path -> labels[selected.path] ?: selected.path
        },
        onSelect = { text -> onSelect(choices.first { it.second == text }.first) },
        required = required
    )
}

/**
 * How each of [fields] is shown in a list of them, by path: its name, followed by its path when
 * another field of the list has the same name, so no two lines read alike.
 */
fun fieldLabels(fields: Map<String, FieldDefinition>): Map<String, String> {
    val repeated = fields.values.groupingBy { it.displayName }.eachCount().filterValues { it > 1 }.keys
    return fields.mapValues { (path, field) ->
        if (field.displayName in repeated) "${field.displayName} ($path)" else field.displayName
    }
}
