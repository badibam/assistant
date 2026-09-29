package com.assistant.core.ui.components

import androidx.compose.runtime.Composable
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.grid.ToolPositions
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.UI
import org.json.JSONObject

/**
 * The tools of one group section laid on their grid (GridLayout), each tool at its grid_x and
 * grid_y as many cells as its mode takes, in edit mode when [edit] is given.
 */
@Composable
fun ToolGrid(
    tools: List<ToolInstance>,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, com.assistant.core.tools.EntryToOpen) -> Unit,
    edit: GridEdit? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modes = tools.map { DisplayMode.valueOf(JSONObject(it.config_json).getString("display_mode")) }
    GridLayout(tools.map { ToolPositions.tile(it) }, modes.map { it == DisplayMode.FULL }, edit) { i ->
        val tool = tools[i]
        UI.ToolCard(
            tool = tool,
            displayMode = modes[i],
            context = context,
            onClick = { onToolClick(tool) },
            onLongClick = { onToolLongClick(tool) },
            onOpenEntry = { entry -> onOpenEntry(tool, entry) }
        )
    }
}

