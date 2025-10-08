package com.assistant.core.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.Size
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * Card commune pour interactions utilisateur (validation, communication modules)
 *
 * Pattern réutilisable pour toutes les interactions nécessitant une réponse user.
 * Utilisé pour : validation des actions IA, communication modules, etc.
 */
@Composable
fun InteractionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    UI.Card(
        type = CardType.DEFAULT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            UI.Text(
                text = title,
                type = TextType.TITLE,
                fillMaxWidth = true,
                textAlign = TextAlign.Center
            )

            // Content
            content()

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions()
            }
        }
    }
}

/**
 * Boutons d'actions standardisés pour interactions utilisateur
 *
 * Pattern réutilisable avec boutons CANCEL (gauche) et CONFIRM (droite).
 * Utilisé dans InteractionCard pour cohérence visuelle.
 */
@Composable
fun RowScope.InteractionActions(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmLabel: String,
    cancelLabel: String,
    confirmEnabled: Boolean = true
) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
        UI.Button(
            onClick = onCancel,
            type = ButtonType.DEFAULT,
            size = Size.M
        ) {
            UI.Text(text = cancelLabel, type = TextType.BODY)
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
        UI.Button(
            onClick = onConfirm,
            type = ButtonType.PRIMARY,
            size = Size.M,
            state = if (confirmEnabled) ComponentState.NORMAL else ComponentState.DISABLED
        ) {
            UI.Text(text = confirmLabel, type = TextType.BODY)
        }
    }
}
