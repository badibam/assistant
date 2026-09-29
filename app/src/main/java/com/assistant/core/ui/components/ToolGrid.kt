package com.assistant.core.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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
 * The tools of one group section laid on their grid: four columns of square cells, each tool at
 * its grid_x and grid_y, as many cells as its mode takes. The grid is as wide as the screen up to
 * the theme's gridMaxWidth, centered beyond.
 *
 * A row is one cell high, except the row of a FULL tile, which is as tall as what the tile shows,
 * rounded up to whole cells. An empty cell stays empty.
 */
@Composable
fun ToolGrid(
    tools: List<ToolInstance>,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, String) -> Unit
) {
    if (tools.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val modes = tools.map { DisplayMode.valueOf(JSONObject(it.config_json).getString("display_mode")) }
    val tiles = tools.map { ToolPositions.tile(it) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = min(maxWidth, CurrentTheme.current.gridMaxWidth)
        Layout(
            content = {
                tools.forEachIndexed { i, tool ->
                    key(tool.id) {
                        UI.ToolCard(
                            tool = tool,
                            displayMode = modes[i],
                            context = context,
                            onClick = { onToolClick(tool) },
                            onLongClick = { onToolLongClick(tool) },
                            onOpenEntry = { entryId -> onOpenEntry(tool, entryId) }
                        )
                    }
                }
            }
        ) { measurables, _ ->
            val cell = width.roundToPx() / Grid.COLUMNS
            val rowCount = Grid.rowCount(tiles)

            // A FULL tile's row takes the height of what it shows, in whole cells
            val rowHeights = IntArray(rowCount) { cell }
            tiles.forEachIndexed { i, tile ->
                if (modes[i] == DisplayMode.FULL) {
                    val content = measurables[i].maxIntrinsicHeight(tile.width * cell)
                    rowHeights[tile.row] = maxOf(1, (content + cell - 1) / cell) * cell
                }
            }
            val rowTops = IntArray(rowCount + 1)
            for (row in 0 until rowCount) rowTops[row + 1] = rowTops[row] + rowHeights[row]

            val placeables = measurables.mapIndexed { i, measurable ->
                val tile = tiles[i]
                measurable.measure(Constraints.fixed(tile.width * cell, rowTops[tile.bottom] - rowTops[tile.row]))
            }
            layout(cell * Grid.COLUMNS, rowTops[rowCount]) {
                placeables.forEachIndexed { i, placeable ->
                    placeable.place(tiles[i].column * cell, rowTops[tiles[i].row])
                }
            }
        }
    }
}
