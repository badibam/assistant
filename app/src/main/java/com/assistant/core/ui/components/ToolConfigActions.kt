package com.assistant.core.ui.components

import androidx.compose.runtime.Composable
import com.assistant.core.ui.UI
import com.assistant.core.ui.ButtonAction

/**
 * Actions standard pour tous les écrans de configuration d'outils.
 * Composant réutilisable qui gère la logique des boutons CREATE/SAVE/CANCEL/DELETE.
 */
@Composable
fun ToolConfigActions(
    isEditing: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    saveEnabled: Boolean = true
) {
    UI.FormActions {
        UI.ActionButton(
            action = if (isEditing) ButtonAction.SAVE else ButtonAction.CREATE,
            onClick = onSave,
            enabled = saveEnabled
        )
        
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            onClick = onCancel
        )
        
        if (isEditing && onDelete != null) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                requireConfirmation = true,
                onClick = onDelete
            )
        }
    }
}