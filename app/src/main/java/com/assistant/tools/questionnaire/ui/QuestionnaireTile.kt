package com.assistant.tools.questionnaire.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.formatValue
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.TileLine
import com.assistant.core.ui.components.TileGrid
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DateTimeFormatter
import com.assistant.core.utils.FormatUtils
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.StoredSchedule
import com.assistant.tools.questionnaire.QuestionnaireToolType
import org.json.JSONObject

/** What the tile reads of the entries: those to fill, from the oldest, and the last one filled. */
private data class Passings(val toFill: List<Long>, val lastFilled: QuestionnaireEntry?)

/**
 * A questionnaire's tile. The summary counts the entries to fill and says when the oldest was
 * planned, which a touch on the tile opens; with none to fill it is up to date, and gives the next
 * one when it is planned, else the last one filled. The body is the answers of the last entry
 * filled, one per line, each question and its answer in the questions' order: all of them in FULL.
 */
@Composable
fun rememberQuestionnaireTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "questionnaire", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }
    val questions = remember(config) { config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList() }

    var passings by remember { mutableStateOf<Passings?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id))
        if (!result.isSuccess) {
            LogManager.ui("Questionnaire tile ${tool.id}: entries not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        val entries = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { e ->
            QuestionnaireEntry(
                id = e["id"] as String,
                timestamp = (e["timestamp"] as Number).toLong(),
                status = (e["state"] as? Map<*, *>)?.get(QuestionnaireToolType.STATUS) as? String ?: "",
                answers = (e["extra"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            )
        }
        passings = Passings(
            toFill = entries.filter { it.status == QuestionnaireToolType.Status.TO_FILL }.map { it.timestamp }.sorted(),
            lastFilled = entries.filter { it.status == QuestionnaireToolType.Status.FILLED }.maxByOrNull { it.timestamp }
        )
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++ }
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = passings ?: return
                val (first, second) = if (loaded.toFill.isNotEmpty()) {
                    s.tool("tile_to_fill").format(loaded.toFill.size.toString()) to FormatUtils.formatRelativeTimePast(loaded.toFill.first(), context)
                } else {
                    val next = (StoredSchedule.of(config) as? StoredSchedule.Readable)
                        ?.takeIf { config.optBoolean("enabled", true) }
                        ?.let { ScheduleCalculator.calculateNextExecution(it.schedule.pattern, System.currentTimeMillis()) }
                    s.tool("tile_up_to_date") to when {
                        next != null -> s.tool("tile_next").format(DateTimeFormatter.formatForDisplay(next, context))
                        loaded.lastFilled != null -> s.tool("tile_last").format(FormatUtils.formatRelativeTimePast(loaded.lastFilled.timestamp, context))
                        else -> s.tool("history_empty")
                    }
                }
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    UI.Text(first, TextType.BODY, maxLines = 1)
                    UI.Text(second, TextType.CAPTION, maxLines = 1)
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val answers = passings?.lastFilled?.answers ?: return
                TileGrid(rows, questions, columns = 1) { question ->
                    // The answer under its question: on the right, a long one would leave the question no room
                    TileLine(
                        question.displayName,
                        secondary = answers[question.name]?.let { question.formatValue(it, context) } ?: s.shared("label_no_value")
                    )
                }
            }
        }
    }
}
