package com.assistant.tools.structured.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.strings.Strings
import com.assistant.core.tools.EntryToOpen
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.TileLine
import com.assistant.core.ui.components.TileGrid
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.LogManager

/** How many sheets a FULL tile shows. */
private const val FULL_SHEETS = 10

/** A sheet as the tile shows it. */
private data class TileSheet(val id: String, val name: String, val updatedAt: Long)

/**
 * Structured data's tile. The summary is how many sheets it holds and a button that opens the
 * tool on a new sheet; the body the sheets last changed, one per line, their name alone, ten in
 * FULL. Touching a sheet opens it.
 */
@Composable
fun rememberStructuredTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    var sheets by remember { mutableStateOf<List<TileSheet>?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "fields" to listOf("id", "name", "updated_at")))
        if (!result.isSuccess) {
            LogManager.ui("Structured tile ${tool.id}: sheets not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        sheets = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            .map { TileSheet(it["id"] as String, it["name"] as? String ?: "", (it["updated_at"] as Number).toLong()) }
            .sortedByDescending { it.updatedAt }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }

    return remember(tool.id) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = sheets ?: return
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    UI.Text(
                        if (loaded.size == 1) s.tool("tile_count_one") else s.tool("tile_count_many").format(loaded.size.toString()),
                        TextType.BODY, maxLines = 1
                    )
                    UI.Button(type = ButtonType.PRIMARY, size = Size.S, onClick = { open(EntryToOpen.New) }) {
                        UI.Text(s.tool("new_sheet"), TextType.LABEL, maxLines = 1)
                    }
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = sheets ?: return
                TileGrid(rows, if (rows == null) loaded.take(FULL_SHEETS) else loaded, columns = 1) { sheet ->
                    TileLine(sheet.name, modifier = Modifier.clickable { open(EntryToOpen.Existing(sheet.id)) })
                }
            }
        }
    }
}
