package com.assistant.core.ui.components

import com.assistant.core.ui.UI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Items laid in a tile's body (ToolTile.Body): row by row in [columns] columns, each item
 * [itemLines] lines high, a row of cells holding two lines. Given [rows], the body shows what
 * they hold and no more, each slot taking its share of the height; null (FULL) shows every item.
 */
@Composable
fun <T> TileGrid(rows: Int?, items: List<T>, columns: Int, itemLines: Int = 1, item: @Composable (T) -> Unit) {
    val slotRows = rows?.let { it * 2 / itemLines } ?: ((items.size + columns - 1) / columns)
    Column(modifier = if (rows != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        for (slotRow in 0 until slotRows) {
            Row(
                modifier = (if (rows != null) Modifier.weight(1f) else Modifier).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (column in 0 until columns) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        items.getOrNull(slotRow * columns + column)?.let { item(it) }
                    }
                }
            }
        }
    }
}
