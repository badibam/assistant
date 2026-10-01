package com.assistant.core.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.validation.ValidationContext
import com.assistant.core.ui.StatusColor
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ai.ui.components.InteractionActions
import com.assistant.core.ai.ui.components.InteractionCard
import com.assistant.core.strings.Strings

/**
 * Validation UI for AI actions
 *
 * Lists the actions the AI wants to run, each with:
 * - The description of each action, in verbalized form
 * - A warning icon when the action is sensitive, as decided by the config
 * - Raison de validation si applicable
 * - Boutons Refuser/Autoriser
 *
 * Same pattern as the communication modules, for a consistent experience.
 */
@Composable
fun ValidationUI(
    context: ValidationContext,
    onValidate: () -> Unit,
    onRefuse: () -> Unit
) {
    val localContext = LocalContext.current
    val s = androidx.compose.runtime.remember { Strings.`for`(context = localContext) }

    InteractionCard(
        title = s.shared("validation_title"),
        content = {
            // The action list
            Column(
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                context.verbalizedActions.forEach { action ->
                    ActionItem(
                        description = action.description,
                        showWarning = action.requiresWarning,
                        validationReason = action.validationReason
                    )
                    action.entries.forEach { entry -> ProposedEntryItem(entry) }
                    action.entriesError?.let { UI.Text(text = it, type = TextType.CAPTION) }
                }
            }
        },
        actions = {
            InteractionActions(
                onConfirm = onValidate,
                onCancel = onRefuse,
                confirmLabel = s.shared("validation_action_confirm"),
                cancelLabel = s.shared("validation_action_refuse")
            )
        }
    )
}

/**
 * One action row inside the validation list
 *
 * @param description The verbalized action description, in substantive form
 * @param showWarning true when the warning icon must be shown (action validated by config)
 * @param validationReason Why validation is required, null when the action needs none
 */
@Composable
private fun ActionItem(
    description: String,
    showWarning: Boolean,
    validationReason: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UI.Space.XS),
        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
    ) {
        // Description, with the warning icon when needed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Warning icon si action sensible (config)
            if (showWarning) {
                UI.Icon(
                    iconName = "triangle-alert",
                    size = 20.dp,
                    tint = UI.statusColor(StatusColor.WARNING)
                )
            }

            // Description de l'action
            UI.Text(
                text = "• $description",
                type = TextType.BODY
            )
        }

        // Validation reason, when there is one
        if (validationReason != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(start = if (showWarning) UI.Space.XL else UI.Space.M)
            ) {
                UI.Text(
                    text = "  $validationReason",
                    type = TextType.CAPTION
                )
            }
        }
    }
}

/**
 * One entry an action proposes to write: each value under its field's label, shown by its field
 * type's display, as everywhere else in the app.
 */
@Composable
fun ProposedEntryItem(entry: com.assistant.core.ai.validation.ProposedEntry) {
    val context = LocalContext.current
    UI.Card(type = com.assistant.core.ui.CardType.DEFAULT) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(UI.Space.S),
            verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
        ) {
            entry.values.forEach { proposed ->
                UI.Text(text = proposed.field.displayName, type = TextType.LABEL)
                com.assistant.core.fields.FieldValue(proposed.field, proposed.value, context)
            }
        }
    }
}
