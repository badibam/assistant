package com.assistant.tools.questionnaire.ui

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
import com.assistant.core.utils.FormatUtils
import com.assistant.tools.questionnaire.QuestionnaireToolType

/**
 * A questionnaire's tile, one line: how many entries wait to be filled, or else when it was last
 * answered, relative.
 */
@Composable
fun QuestionnaireTile(tool: ToolInstance, displayMode: DisplayMode) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "questionnaire", context = context) }
    var line by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "fields" to listOf("id", "timestamp", "state")))
        if (!result.isSuccess) return@LaunchedEffect
        val entries = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
        fun status(e: Map<*, *>) = (e["state"] as? Map<*, *>)?.get(QuestionnaireToolType.STATUS) as? String
        val waiting = entries.count { status(it) == QuestionnaireToolType.Status.TO_FILL }
        val last = entries.filter { status(it) == QuestionnaireToolType.Status.FILLED }.maxOfOrNull { (it["timestamp"] as Number).toLong() }
        line = when {
            waiting > 0 -> s.tool("tab_to_fill").format(waiting)
            last != null -> s.tool("tile_last").format(FormatUtils.formatRelativeTimePast(last, context))
            else -> s.tool("history_empty")
        }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++ }
    }
    if (displayMode == DisplayMode.ICON || displayMode == DisplayMode.MINIMAL) return
    UI.Text(text = line ?: return, type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
}
