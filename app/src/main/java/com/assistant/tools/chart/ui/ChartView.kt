package com.assistant.tools.chart.ui

import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.assistant.core.coordinator.Coordinator
import com.assistant.tools.chart.Cell
import com.assistant.tools.chart.ChartDetail
import com.assistant.tools.chart.ChartMetrics
import com.assistant.tools.chart.ChartSceneBuilder
import com.assistant.tools.chart.ChartSpec
import com.assistant.tools.chart.ChartTable
import com.assistant.tools.chart.ChartText
import com.assistant.tools.chart.ChartTicks
import com.assistant.tools.chart.Hit
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FieldValue
import com.assistant.core.fields.NumericPrecision
import com.assistant.core.fields.formatValue
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolRequests
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DateUtils
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/**
 * A chart on a screen or a tile: laid out at the width it is given (ChartSceneBuilder), its texts
 * measured in the theme's style for drawings, drawn by the theme (UI.Drawing). On a screen, a
 * touch hands back what it found, or null beside every mark; on a tile ([onTap] null) the touch
 * is the tile's, which opens the tool.
 *
 * @param tables The table of each layer, in the order of ChartSpec.layers
 * @param period The displayed period's instants, resolved at [now]
 * @param detail How much of the chart is drawn; reduced or as a strip, it fills the height given
 */
@Composable
fun ChartView(spec: ChartSpec, tables: List<ChartTable>, period: Pair<Long?, Long?>, now: Long, onTap: ((Hit?) -> Unit)?,
              modifier: Modifier = Modifier, detail: ChartDetail = ChartDetail.WHOLE) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val style = UI.drawingTextStyle()
    val measurer = rememberTextMeasurer()
    val text = remember(style, measurer) { AppChartText(context, measurer, style) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        val height = if (detail == ChartDetail.WHOLE) null else constraints.maxHeight.toFloat()
        val metrics = remember(density) { ChartMetrics(density) }
        val layout = remember(spec, tables, period, width, height, detail, text) {
            val calendar = com.assistant.core.utils.AppConfigManager
            ChartSceneBuilder(metrics, text, calendar.getDateTimeConfig().getZoneId(), calendar.getWeekStartDay(), now)
                .build(spec, tables, period, width, detail, height)
        }
        UI.Drawing(layout.drawing, if (onTap == null) Modifier else Modifier.pointerInput(layout) {
            detectTapGestures { offset -> onTap(layout.hitAt(offset.x, offset.y, metrics.slop)) }
        })
    }
}

/**
 * The texts of a chart as the app writes them: a value by its field's format, an instant of a
 * time axis by the calendar step it stands on, a number with the app's decimals; measured in the
 * theme's chart style.
 */
private class AppChartText(private val context: Context, private val measurer: TextMeasurer, private val style: TextStyle) : ChartText {
    private val s = Strings.`for`(tool = "chart", context = context)

    override fun width(text: String): Float = measurer.measure(text, style).size.width.toFloat()

    override val height: Float = measurer.measure("Ag", style).size.height.toFloat()

    // A reference's text is the name of what it designates, which a read gives, not a format
    override fun value(field: FieldDefinition, value: Any): String =
        if (field.type == FieldType.REFERENCE) ((value as? Map<*, *>)?.get("name") ?: (value as? Map<*, *>)?.get("id") ?: value).toString()
        else field.formatValue(value, context)

    override fun instant(instant: Long, step: ChartTicks.CalendarStep): String = DateUtils.format(instant, when (step.unit) {
        ChronoUnit.MINUTES, ChronoUnit.HOURS -> s.tool("format_hour")
        ChronoUnit.DAYS, ChronoUnit.WEEKS -> s.tool("format_day")
        ChronoUnit.MONTHS -> s.tool("format_month")
        else -> s.tool("format_year")
    })

    override fun number(value: Double, decimals: Int): String = NumericPrecision.format(value, decimals)

    override fun shared(key: String): String = s.shared(key)

    override fun own(key: String): String = s.tool(key)
}

/**
 * What a touch found, under the chart: every column of its row with its value as its field shows
 * it, unit included; a cell that could not be read says why, and each entry to correct opens in
 * its tool.
 */
@Composable
fun ChartDetails(hit: Hit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "chart", context = context) }
    val scope = rememberCoroutineScope()
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
            hit.columns.forEach { (name, field) ->
                val cell = hit.row.cells[name] ?: return@forEach
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(0.4f)) { UI.Text(field.displayName, TextType.LABEL) }
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(0.6f)) {
                        when (cell) {
                            is Cell.Value -> FieldValue(field, cell.value, context)
                            is Cell.Failed -> Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                                UI.Text(cell.message, TextType.ERROR)
                                FailedEntries(cell.entries)
                            }
                        }
                    }
                }
            }
            if (hit.row.entryId != null) {
                UI.Button(type = ButtonType.DEFAULT, onClick = { scope.launch { openEntry(context, hit.row.entryId) } }) {
                    UI.Text(s.tool("open_entry"), TextType.LABEL)
                }
            }
        }
    }
}

/** The entries a failure names, each by its name, opening in its tool. */
@Composable
private fun FailedEntries(ids: List<String>) {
    if (ids.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var names by remember(ids) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(ids) {
        val result = Coordinator(context).processUserAction("references.names", mapOf("references" to ids.map { mapOf("kind" to "ENTRY", "id" to it) }))
        if (result.isSuccess) names = (result.data?.get("references") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            .mapNotNull { r -> (r["id"] as? String)?.let { id -> (r["name"] as? String)?.let { id to it } } }.toMap()
    }
    ids.forEach { id ->
        UI.Button(type = ButtonType.DEFAULT, onClick = { scope.launch { openEntry(context, id) } }) {
            UI.Text(names[id] ?: id, TextType.LABEL)
        }
    }
}

/** Opens the tool holding the entry [id], on it. */
private suspend fun openEntry(context: Context, id: String) {
    val result = Coordinator(context).processUserAction("tool_data.get_single", mapOf("entry_id" to id))
    val toolId = (result.data?.get("entry") as? Map<*, *>)?.get("tool_instance_id") as? String
    if (toolId != null) ToolRequests.open(toolId, id)
    else UI.Toast(context, result.error ?: Strings.`for`(tool = "chart", context = context).tool("entry_not_found"))
}
