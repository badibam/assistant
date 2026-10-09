package app.treelune.core.ai.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.access.AccessGrant
import app.treelune.core.access.AccessLevel
import app.treelune.core.access.AccessMask
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferencePicker
import app.treelune.core.fields.rememberReferenceName
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * What an automation's AI may reach (AccessMask, docs/design/validation.md): its zones and tools,
 * each with its level and a way to take it out, and a way to add one, picked as a pointer picks
 * (the Chose brick, a zone or a tool). Added, it is read only: one widens it knowingly.
 */
@Composable
fun AutomationAccessCard(access: AccessMask, onChange: (AccessMask) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val levels = remember {
        val values = AccessLevel.entries.map { it.key }
        FieldDefinition("level", s.shared("automation_access_level"), null, FieldType.CHOICE, false,
            mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith { s.shared("automation_access_level_$it") })))
    }

    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.shared("automation_access_title"), TextType.SUBTITLE)
            UI.Text(s.shared("automation_access_help"), TextType.CAPTION)
            // What it reads all the same: the tools sent to every AI, the user's own choice
            UI.Text(s.shared("automation_access_always_sent"), TextType.CAPTION)

            access.grants.forEachIndexed { index, grant ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val kind = s.shared(if (grant.target.kind == ReferenceKind.ZONE) "ai_enrichment_pointer_zone" else "ai_enrichment_pointer_tool")
                    val name = rememberReferenceName(grant.target, context) ?: s.shared("tools_loading")
                    Box(modifier = Modifier.weight(1f)) { UI.Text("$kind · $name", TextType.BODY) }
                    Box(modifier = Modifier.weight(1f)) {
                        FieldInput(levels, grant.level.key, { chosen ->
                            (chosen as? String)?.let { key ->
                                onChange(access.copy(grants = access.grants.mapIndexed { i, g -> if (i == index) g.copy(level = AccessLevel.of(key)) else g }))
                            }
                        }, context, required = true)
                    }
                    UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                        onChange(access.copy(grants = access.grants.filterIndexed { i, _ -> i != index }))
                    })
                }
            }

            UI.Button(type = ButtonType.DEFAULT, onClick = { picking = true }) {
                UI.Text(s.shared("automation_access_add"), TextType.BODY)
            }
            UI.Text(s.shared("automation_access_levels"), TextType.CAPTION)
        }
    }

    if (picking) {
        ReferencePicker(
            target = ReferenceTarget(AccessGrant.KINDS, emptyList()),
            onDismiss = { picking = false },
            onPick = { picked ->
                picking = false
                // A thing already listed keeps its level
                if (access.grants.none { it.target == picked }) onChange(access.copy(grants = access.grants + AccessGrant(picked, AccessLevel.READ)))
            }
        )
    }
}
