package com.assistant.core.ui.components

import androidx.compose.runtime.Composable
import com.assistant.core.ui.UI
import com.assistant.core.ui.ButtonAction

/**
 * Standard actions for all tool configuration screens.
 * Reusable component that handles CREATE/SAVE/CANCEL/DELETE/RESET button logic.
 *
 * Tools keep CREATE vs SAVE logic for clarity (isEditing determines the button).
 * Reset button is optional and can be added via onReset parameter.
 */
@Composable
fun ToolConfigActions(
    isEditing: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    saveEnabled: Boolean = true
) {
    UI.FormActions {
        // Tools keep CREATE vs SAVE logic based on isEditing
        UI.ActionButton(
            action = if (isEditing) ButtonAction.SAVE else ButtonAction.CREATE,
            onClick = onSave,
            enabled = saveEnabled
        )

        UI.ActionButton(
            action = ButtonAction.CANCEL,
            onClick = onCancel
        )

        // Reset button - available when onReset is provided
        if (onReset != null) {
            UI.ActionButton(
                action = ButtonAction.RESET,
                requireConfirmation = true,
                onClick = onReset
            )
        }

        // Delete button - only in editing mode
        if (isEditing && onDelete != null) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                requireConfirmation = true,
                onClick = onDelete
            )
        }
    }
}