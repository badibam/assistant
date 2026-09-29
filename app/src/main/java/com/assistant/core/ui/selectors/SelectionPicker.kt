package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.PeriodPicker

/**
 * The Sélection d'entrées brick's selector (docs/BRICKS.md): the thing (ThingBrowser), its period,
 * and for a tool the conditions on its entries and, when [offerFields], the fields kept.
 *
 * The browser is open while nothing final is chosen; once the thing reached has nothing deeper
 * [target] takes (a tool, for the pointer and a reading), it folds into a line that names it, and
 * "change" opens it again in place. A place [target] takes is chosen as soon as it is reached, so
 * a zone's period is offered while its tools are still listed.
 *
 * @param fields The fields of the tool reached, by path (rememberToolFields)
 * @param reference The name of what relative dates resolve against, null where a date is fixed
 *   when it is chosen
 */
@Composable
fun SelectionPicker(
    draft: SelectionDraft,
    onChange: (SelectionDraft) -> Unit,
    target: ReferenceTarget,
    fields: Map<String, FieldDefinition>,
    reference: String?,
    offerFields: Boolean
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    fun final(path: ThingPath) = path.kind in target.kinds && target.kinds.none { it in path.kind.below }

    // Where the browser stands: the draft's place, or while changing, the zone above it
    var browsing by rememberSaveable(stateSaver = ThingPathSaver) { mutableStateOf(if (final(draft.path)) null else draft.path) }
    val chosen = draft.level in target.kinds

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val open = browsing
        if (open != null) {
            ThingBrowser(open, { path ->
                if (path.kind in target.kinds || path.kind == ReferenceKind.APP) onChange(draft.at(path))
                browsing = if (final(path)) null else path
            }, target)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(text = listOfNotNull(draft.tool?.name, draft.zone?.name).let { names ->
                        if (names.size == 2) s.shared("selection_place_in_zone").format(names[0], names[1]) else names.firstOrNull() ?: ""
                    }, type = TextType.SUBTITLE)
                }
                UI.Button(type = ButtonType.DEFAULT, onClick = { browsing = draft.path.upTo(ReferenceKind.ZONE) }) {
                    UI.Text(text = s.shared("selection_change"), type = TextType.LABEL)
                }
            }
        }
        if (!chosen) return@Column

        UI.Text(text = s.shared("pointer_period"), type = TextType.SUBTITLE)
        PeriodPicker(draft.period, { onChange(draft.copy(period = it)) }, FieldType.DATETIME, reference)

        val tool = draft.tool
        if (tool != null) {
            var editing by rememberSaveable { mutableStateOf(false) }
            UI.Button(type = ButtonType.DEFAULT, onClick = { editing = true }) {
                UI.Text(text = s.shared(if (offerFields) "pointer_filters_and_fields" else "selection_filters"), type = TextType.BODY)
            }
            // The period is said above: only the conditions and the fields here
            PointerDescription.narrowing(draft.copy(period = com.assistant.core.selection.EntryPeriod()), fields, s).forEach {
                UI.Text(text = it, type = TextType.CAPTION)
            }
            if (editing) {
                PointerFiltersDialog(
                    toolInstanceId = tool.id,
                    fields = fields,
                    filters = draft.filters,
                    chosenFields = draft.fields,
                    offerFields = offerFields,
                    reference = reference,
                    onDismiss = { editing = false },
                    onConfirm = { filters, kept ->
                        onChange(draft.copy(filters = filters, fields = kept))
                        editing = false
                    }
                )
            }
        }
    }
}

/** The browser's place across a rotation, null while folded. */
private val ThingPathSaver: Saver<ThingPath?, String> = Saver(
    save = { it?.toJson() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { ThingPath.fromJson(it) } }
)
