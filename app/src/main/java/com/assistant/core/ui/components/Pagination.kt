package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.assistant.core.ui.UI
import com.assistant.core.ui.TextType
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay

/**
 * Composant de pagination réutilisable.
 * Affiche les boutons LEFT/RIGHT et l'indicateur de page actuelle.
 */
@Composable
fun Pagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    showPageInfo: Boolean = true
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Previous page button
        Box(modifier = Modifier.weight(1f)) {
            if (currentPage > 1) {
                UI.ActionButton(
                    action = ButtonAction.LEFT,
                    display = ButtonDisplay.ICON,
                    onClick = { 
                        if (currentPage > 1) {
                            onPageChange(currentPage - 1)
                        }
                    }
                )
            }
        }
        
        // Page indicator
        if (showPageInfo) {
            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                UI.CenteredText(
                    "Page $currentPage sur $totalPages",
                    TextType.CAPTION
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(2f))
        }
        
        // Next page button
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (currentPage < totalPages) {
                UI.ActionButton(
                    action = ButtonAction.RIGHT,
                    display = ButtonDisplay.ICON,
                    onClick = { 
                        if (currentPage < totalPages) {
                            onPageChange(currentPage + 1)
                        }
                    }
                )
            }
        }
    }
}