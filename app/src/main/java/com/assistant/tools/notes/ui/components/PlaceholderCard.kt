package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.themes.ThemeIconManager

/**
 * Placeholder card component for adding new notes
 * Shows "+" icon in standard card layout
 */
@Composable
fun PlaceholderCard(
    onClick: () -> Unit = {}
) {
    UI.Card(type = CardType.DEFAULT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp) // Standard card height
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            UI.Icon("add", size = 48.dp)
        }
    }
}