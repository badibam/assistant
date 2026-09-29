package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import org.json.JSONObject

/** What a selection setting reads: the entries of a tool. */
private val ENTRIES = ReferenceTarget(setOf(ReferenceKind.TOOL_INSTANCE), emptyList())

/** The selection being edited across a rotation, null before the stored one is named. */
private val DraftSaver: Saver<SelectionDraft?, String> = Saver(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { SelectionDraft.fromJson(JSONObject(it)) } }
)

/**
 * A selection of a tool's entries as a setting (SettingNode.Selection): the tool, the conditions
 * on its entries and the fields kept, by SelectionPicker; its period is the config's own,
 * given elsewhere. [selection] is its stored form, null while none is set.
 *
 * @param reference The name of the instant the filters' relative dates resolve against
 */
@Composable
fun SelectionSetting(label: String, selection: JSONObject?, reference: String, onChange: (JSONObject?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // The stored selection, its tool and zone named as they are now; a tool deleted since starts over
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<SelectionDraft?>(null) }
    LaunchedEffect(Unit) {
        if (draft != null) return@LaunchedEffect
        val stored = selection?.takeIf { it.has("target") }?.let { EntrySelection.fromJson(it) { key -> s.shared(key) } }
        draft = stored?.target?.id?.let { toolPath(it, context) }?.let { SelectionDraft.of(stored, it) } ?: SelectionDraft()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(text = label, type = TextType.SUBTITLE)
        val current = draft ?: return@Column UI.LoadingIndicator()
        SelectionPicker(current, { next ->
            draft = next
            // Only a tool's entries make a selection here; until one is reached, none is set
            onChange(if (next.level == ReferenceKind.TOOL_INSTANCE) next.copy(period = EntryPeriod()).selection().toJson() else null)
        }, ENTRIES, rememberToolFields(current.tool?.id), reference, offerFields = true, offerPeriod = false)
    }
}
