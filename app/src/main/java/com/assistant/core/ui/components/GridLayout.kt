package com.assistant.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.grid.Grid
import com.assistant.core.grid.ToolPositions
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.UI
import org.json.JSONObject

/**
 * A group section in edit mode: its tiles at the [places] of the move in progress, [selectedId]
 * the tile being moved, [onSelect] what a touch on a tile does instead of its own action, by the
 * tile's id.
 */
data class GridEdit(val places: List<Grid.Tile>, val selectedId: String?, val onSelect: (String) -> Unit)

/** The side of a cell of the grid a tile stands in. */
val LocalGridCell = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.unit.Dp> { error("No grid around this tile") }

/** How far the tiles not being moved fade while one is. */
private const val FADED = 0.4f

/**
 * Tiles laid on a grid: four columns of square cells, each [tiles] at its place, [item] drawing
 * the one at an index. The theme sizes a cell from the width there is (gridCellPx), and the grid
 * is centered in what it leaves.
 *
 * A row is one cell high, except the row of a tile that grows with its content ([grows]: a
 * FULL tile), which is as tall as what it shows, rounded up to whole cells. Such a tile is measured
 * once, its width fixed and its height free, and keeps the height it takes: the rest of its row
 * stays empty under it. It is never asked for its intrinsic height, which a tile holding a layout
 * measured by its constraints (BoxWithConstraints, a lazy list) cannot give. An empty cell stays
 * empty.
 *
 * In edit mode ([edit]), the tiles stand at its places, the theme's cells show under them, and
 * the tiles keep their content but no longer react to their own gestures: a touch selects. The
 * tile being moved is kept in view, a line of margin above and below, the screen scrolling only
 * as much as that takes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridLayout(stored: List<Grid.Tile>, grows: List<Boolean>, edit: GridEdit?, item: @Composable (Int) -> Unit) {
    if (stored.isEmpty()) return
    val placesById = edit?.places?.associateBy { it.id }
    val tiles = stored.map { placesById?.get(it.id) ?: it }
    val rowCount = Grid.rowCount(tiles)
    val cells = if (edit != null) rowCount * Grid.COLUMNS else 0

    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val cellPx = CurrentTheme.current.gridCellPx(constraints.maxWidth)
        Layout(
            content = {
                // In edit mode, first the cells, one per cell of the grid, then the tiles over them
                repeat(cells) { CurrentTheme.current.GridCell() }
                tiles.forEachIndexed { i, place ->
                    key(place.id) {
                        val selected = edit?.selectedId == place.id
                        val requester = remember { BringIntoViewRequester() }
                        if (selected) {
                            LaunchedEffect(place) {
                                val line = cellPx / 2f
                                requester.bringIntoView(Rect(0f, -line, place.width * cellPx.toFloat(), place.height * cellPx + line))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .bringIntoViewRequester(requester)
                                .alpha(if (edit?.selectedId != null && !selected) FADED else 1f)
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(LocalGridCell provides with(LocalDensity.current) { cellPx.toDp() }) { item(i) }
                            // In edit mode the tile's own gestures give way to a touch that selects
                            if (edit != null) {
                                Box(
                                    modifier = Modifier.matchParentSize().clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { edit.onSelect(place.id) }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { measurables, _ ->
            val cell = cellPx

            // A tile that grows is measured first, at its width and a free height; its row takes
            // that height in whole cells
            val rowHeights = IntArray(rowCount) { cell }
            val grown = tiles.mapIndexedNotNull { i, tile ->
                if (!grows[i]) return@mapIndexedNotNull null
                val width = tile.width * cell
                val placeable = measurables[cells + i].measure(Constraints(minWidth = width, maxWidth = width, minHeight = cell))
                rowHeights[tile.row] = maxOf(rowHeights[tile.row], (placeable.height + cell - 1) / cell * cell)
                i to placeable
            }.toMap()
            val rowTops = IntArray(rowCount + 1)
            for (row in 0 until rowCount) rowTops[row + 1] = rowTops[row] + rowHeights[row]

            val cellPlaceables = (0 until cells).map { index ->
                measurables[index].measure(Constraints.fixed(cell, rowHeights[index / Grid.COLUMNS]))
            }
            val tilePlaceables = tiles.mapIndexed { i, tile ->
                grown[i] ?: measurables[cells + i].measure(Constraints.fixed(tile.width * cell, rowTops[tile.bottom] - rowTops[tile.row]))
            }
            layout(cell * Grid.COLUMNS, rowTops[rowCount]) {
                cellPlaceables.forEachIndexed { index, placeable ->
                    placeable.place((index % Grid.COLUMNS) * cell, rowTops[index / Grid.COLUMNS])
                }
                tilePlaceables.forEachIndexed { i, placeable ->
                    placeable.place(tiles[i].column * cell, rowTops[tiles[i].row])
                }
            }
        }
    }
}
