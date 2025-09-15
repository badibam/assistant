package com.assistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex

/**
 * Unified spotlight wrapper component that handles overlay/focus management
 *
 * Implements the standard Box/Box pattern with conditional overlay:
 * - Box(zIndex) { Box(content) + Box(overlay) }
 *
 * @param isActive Whether this element is currently active (above overlay)
 * @param editingNoteId Global editing state (spotlight trigger)
 * @param contextMenuNoteId Global context menu state (spotlight trigger)
 * @param onCloseSpotlight Callback to close any active spotlight interactions
 * @param modifier Modifier for the outer container
 * @param fillParentSize Forces overlay to fill parent size even if content is smaller (for external scroll)
 * @param content The content to be wrapped
 */
@Composable
fun WithSpotlight(
    isActive: Boolean = false,
    editingNoteId: String?,
    contextMenuNoteId: String?,
    onCloseSpotlight: () -> Unit = {},
    modifier: Modifier = Modifier,
    fillParentSize: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.zIndex(if (isActive) 2f else 0f)
    ) {
        // Content container
        Box {
            content()
        }

        // Conditional overlay (voile) - only shows when spotlight is active and this element is NOT active
        if (!isActive && (editingNoteId != null || contextMenuNoteId != null)) {
            Box(
                modifier = Modifier
                    .let {
                        if (fillParentSize) it.fillMaxSize()
                        else it.matchParentSize()
                    }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onCloseSpotlight()
                    }
            )
        }
    }
}