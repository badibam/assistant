package com.assistant.tools.structured.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.strings.Strings
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier

/**
 * Structured data's tile on a zone, one line for now (the other modes wait for the grid): how
 * many sheets it holds, the table's name being the tile's header.
 */
@Composable
fun StructuredTile(tool: ToolInstance, displayMode: DisplayMode) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    var count by remember { mutableStateOf<Int?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "fields" to listOf("id")))
        if (result.isSuccess) count = (result.data?.get("entries") as? List<*>)?.size
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }
    val loaded = count ?: return
    if (displayMode == DisplayMode.ICON || displayMode == DisplayMode.MINIMAL) return
    UI.Text(
        text = if (loaded == 1) s.tool("tile_count_one") else s.tool("tile_count_many").format(loaded.toString()),
        type = TextType.BODY,
        fillMaxWidth = true,
        textAlign = TextAlign.Center
    )
}
