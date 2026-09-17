package com.assistant.core.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.assistant.core.ui.Size
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * Shared card for user interactions (validation, communication modules)
 *
 * Reusable pattern for every interaction that needs an answer from the user.
 * Used for AI action validation, communication modules, and the like.
 *
 * Design: a primary-bordered card, so the interaction being asked for stands out.
 * Appearance is delegated to the active theme through UI.kt.
 */
@Composable
fun InteractionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    // Appearance delegated to the active theme through UI.kt
    UI.InteractionCard(
        title = title,
        content = content,
        actions = actions
    )
}

/**
 * Standard action buttons for user interactions
 *
 * Reusable pattern with CANCEL on the left and CONFIRM on the right.
 * Used by InteractionCard so every interaction looks the same.
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
