package com.assistant.tools.goal.ui

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
import com.assistant.tools.goal.GoalToolType

/**
 * A goal's tile, one line for now (the other modes wait for the grid): the attempt running
 * counted against what it asks with its verdict so far, the last verdicts' dots, and how many
 * attempts wait to be validated.
 */
@Composable
fun GoalTile(tool: ToolInstance, displayMode: DisplayMode) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "goal", context = context) }
    var line by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "fields" to listOf("id", "timestamp", "state")))
        if (!result.isSuccess) return@LaunchedEffect
        val attempts = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
        fun status(e: Map<*, *>) = (e["state"] as? Map<*, *>)?.get(GoalToolType.STATUS) as? String ?: ""
        val current = attempts.filter { status(it) == GoalToolType.Status.ACTIVE }.maxByOrNull { (it["timestamp"] as Number).toLong() }
        val count = current?.let { attempt ->
            val judged = coordinator.processUserAction("goal.evaluate", mapOf("tool_instance_id" to tool.id, "id" to attempt["id"]))
            judged.data?.takeIf { judged.isSuccess }?.let { s.tool("count").format(it["met"], it["required"]) + " · " + s.tool("verdict_${(it["verdict"] as? String ?: "UNKNOWN").lowercase()}") }
        }
        val dots = attempts.filter { status(it) in GoalToolType.Status.LOCKED }.sortedByDescending { (it["timestamp"] as Number).toLong() }
            .take(7).joinToString(" ") { dot(status(it)) }
        val waiting = attempts.count { status(it) == GoalToolType.Status.TO_VALIDATE }
        line = listOfNotNull(count, dots.takeIf { it.isNotEmpty() }, s.tool("tab_to_validate").format(waiting).takeIf { waiting > 0 })
            .joinToString(" · ").ifEmpty { s.tool("no_attempt") }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if ((event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) || event is DataChangeEvent.VariablesChanged) version++
        }
    }
    if (displayMode == DisplayMode.ICON || displayMode == DisplayMode.MINIMAL) return
    UI.Text(text = line ?: return, type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
}
