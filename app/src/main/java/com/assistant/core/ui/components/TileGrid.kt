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
 * Items laid in a tile's body (ToolTile.Body): row by row in [columns] columns, [perRow] items
 * stacked in a row of cells (2: an item has room for two lines of text; 4: for one; 1: for a
 * tall item). Given [rows], the body shows what they hold and no more, each slot taking its share
 * of the height; null (FULL) shows every item.
 */
@Composable
fun <T> TileGrid(rows: Int?, items: List<T>, columns: Int, perRow: Int = 2, item: @Composable (T) -> Unit) {
    val slotRows = tileGridSlotRows(rows, items.size, columns, perRow)
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

/** The rows of slots a tile's body lays: [perRow] per row of cells in [rows], or what [count] items need (FULL). */
fun tileGridSlotRows(rows: Int?, count: Int, columns: Int, perRow: Int): Int =
    rows?.let { it * perRow } ?: ((count + columns - 1) / columns)
