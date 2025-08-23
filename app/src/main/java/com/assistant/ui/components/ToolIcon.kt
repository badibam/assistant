package com.assistant.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Generic tool icon component
 * Uses drawable resources for theme-based icons
 */
@Composable
fun ToolIcon(
    iconResource: Int,
    size: Dp = 32.dp,
    contentDescription: String = "Tool icon"
) {
    Icon(
        painter = painterResource(id = iconResource),
        contentDescription = contentDescription,
        modifier = Modifier.size(size)
    )
}