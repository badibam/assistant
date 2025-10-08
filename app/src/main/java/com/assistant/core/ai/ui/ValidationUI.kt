package com.assistant.core.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.validation.ValidationContext
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ai.ui.components.InteractionActions
import com.assistant.core.ai.ui.components.InteractionCard
import com.assistant.core.strings.Strings

/**
 * UI de validation des actions IA
 *
 * Affiche la liste des actions que l'IA souhaite effectuer avec :
 * - Description de chaque action (verbalisée)
 * - Warning icon (⚠️) si action sensible (validée par config)
 * - Raison de validation si applicable
 * - Boutons Refuser/Autoriser
 *
 * Pattern similaire aux communication modules pour cohérence UX.
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
            // Liste des actions
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                context.verbalizedActions.forEach { action ->
                    ActionItem(
                        description = action.description,
                        showWarning = action.requiresWarning,
                        validationReason = action.validationReason
                    )
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
 * Item d'action individuel dans la liste de validation
 *
 * @param description Description verbalisée de l'action (substantif)
 * @param showWarning true si warning icon à afficher (action validée par config)
 * @param validationReason Raison de validation (null si action non validée)
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
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Description avec warning icon si nécessaire
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Warning icon si action sensible (config)
            if (showWarning) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Action sensible",
                    tint = Color(0xFFFF9800),  // Orange
                    modifier = Modifier.size(20.dp)
                )
            }

            // Description de l'action
            UI.Text(
                text = "• $description",
                type = TextType.BODY
            )
        }

        // Raison de validation (si présente)
        if (validationReason != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(start = if (showWarning) 28.dp else 12.dp)
            ) {
                UI.Text(
                    text = "  $validationReason",
                    type = TextType.CAPTION
                )
            }
        }
    }
}
