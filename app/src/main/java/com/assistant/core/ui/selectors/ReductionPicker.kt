package com.assistant.core.ui.selectors

import androidx.compose.runtime.Composable
import com.assistant.core.reading.Reduction
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * The Réduction brick's selector (docs/BRICKS.md): one of [reductions], those the field chosen
 * takes (Reduction.forType). A single one is said rather than offered in a list; none, nothing shows.
 */
@Composable
fun ReductionPicker(
    label: String,
    reductions: List<Reduction>,
    selected: Reduction?,
    onSelect: (Reduction) -> Unit,
    s: StringsContext
) {
    fun name(reduction: Reduction) = s.shared("reduction_${reduction.name.lowercase()}")
    if (reductions.isEmpty()) return
    if (reductions.size == 1) {
        UI.Text(s.shared("label_value").format(label, name(reductions.single())), TextType.CAPTION)
        return
    }
    UI.FormSelection(
        label = label,
        options = reductions.map(::name),
        selected = selected?.let(::name) ?: "",
        onSelect = { text -> onSelect(reductions.first { name(it) == text }) },
        required = true
    )
}
