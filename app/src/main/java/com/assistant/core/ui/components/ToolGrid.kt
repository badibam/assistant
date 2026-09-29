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
import androidx.compose.ui.unit.min
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.grid.Grid
import com.assistant.core.grid.ToolPositions
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.UI
import org.json.JSONObject

/**
 * A group section in edit mode (docs/design/grid-layout.md, « Le mode d'édition »): its tiles at
 * the [places] of the move in progress, [selectedId] the tile being moved, [onSelect] what a touch
 * on a tile does instead of its own action.
 */
data class GridEdit(val places: List<Grid.Tile>, val selectedId: String?, val onSelect: (ToolInstance) -> Unit)

/** How far the tiles not being moved fade while one is. */
private const val FADED = 0.4f

/**
 * The tools of one group section laid on their grid: four columns of square cells, each tool at
 * its grid_x and grid_y, as many cells as its mode takes. The grid is as wide as the screen up to
 * the theme's gridMaxWidth, centered beyond.
 *
 * A row is one cell high, except the row of a FULL tile, which is as tall as what the tile shows,
 * rounded up to whole cells. An empty cell stays empty.
 *
 * In edit mode ([edit]), the theme's cells show under the tiles, which keep their content but no
 * longer react to their own gestures: a touch selects. The tile being moved is kept in view, a
 * line of margin above and below, the screen scrolling only as much as that takes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolGrid(
    tools: List<ToolInstance>,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, com.assistant.core.tools.EntryToOpen) -> Unit,
    edit: GridEdit? = null
) {
    if (tools.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val modes = tools.map { DisplayMode.valueOf(JSONObject(it.config_json).getString("display_mode")) }
    val placesById = edit?.places?.associateBy { it.id }
    val tiles = tools.map { placesById?.get(it.id) ?: ToolPositions.tile(it) }
    val rowCount = Grid.rowCount(tiles)
    val cells = if (edit != null) rowCount * Grid.COLUMNS else 0

    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth, CurrentTheme.current.gridMaxWidth)
        val cellPx = with(LocalDensity.current) { width.roundToPx() } / Grid.COLUMNS
        Layout(
            content = {
                // In edit mode, first the cells, one per cell of the grid, then the tiles over them
                repeat(cells) { CurrentTheme.current.GridCell() }
                tools.forEachIndexed { i, tool ->
                    key(tool.id) {
                        val selected = edit?.selectedId == tool.id
                        val requester = remember { BringIntoViewRequester() }
                        if (selected) {
                            val place = tiles[i]
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
                            UI.ToolCard(
                                tool = tool,
                                displayMode = modes[i],
                                context = context,
                                onClick = { onToolClick(tool) },
                                onLongClick = { onToolLongClick(tool) },
                                onOpenEntry = { entry -> onOpenEntry(tool, entry) }
                            )
                            // In edit mode the tile's own gestures give way to a touch that selects
                            if (edit != null) {
                                Box(
                                    modifier = Modifier.matchParentSize().clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { edit.onSelect(tool) }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { measurables, _ ->
            val cell = cellPx

            // A FULL tile's row takes the height of what it shows, in whole cells
            val rowHeights = IntArray(rowCount) { cell }
            tiles.forEachIndexed { i, tile ->
                if (modes[i] == DisplayMode.FULL) {
                    val content = measurables[cells + i].maxIntrinsicHeight(tile.width * cell)
                    rowHeights[tile.row] = maxOf(1, (content + cell - 1) / cell) * cell
                }
            }
            val rowTops = IntArray(rowCount + 1)
            for (row in 0 until rowCount) rowTops[row + 1] = rowTops[row] + rowHeights[row]

            val cellPlaceables = (0 until cells).map { index ->
                measurables[index].measure(Constraints.fixed(cell, rowHeights[index / Grid.COLUMNS]))
            }
            val tilePlaceables = tiles.mapIndexed { i, tile ->
                measurables[cells + i].measure(Constraints.fixed(tile.width * cell, rowTops[tile.bottom] - rowTops[tile.row]))
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
