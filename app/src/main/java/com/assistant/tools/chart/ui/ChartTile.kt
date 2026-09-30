package com.assistant.tools.chart.ui

import android.content.Context
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
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.formatValue
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.Edge
import com.assistant.core.selection.TimePoint
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.selectors.PointerDescription
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DateUtils
import com.assistant.core.utils.FormatUtils
import com.assistant.tools.chart.Channel
import com.assistant.tools.chart.ChartDetail
import com.assistant.tools.chart.ChartSources
import com.assistant.tools.chart.ChartValues
import com.assistant.tools.chart.Layer
import com.assistant.tools.chart.Source
import org.json.JSONObject

/**
 * A chart's tile (docs/design/missing-tools.md, « La tuile »). The summary: the period shown, then
 * the last value of the first series with its unit and when — or that nothing falls in the period,
 * or that the chart cannot be drawn. The body: a strip of its marks in EXTENDED, the chart reduced
 * in SQUARE, its first view only in both; the whole chart in FULL. Where the tile has no body
 * (LINE, CONDENSED), the strip stands in the summary's place, the summary staying there when there
 * is nothing to draw, which it says. A touch opens the tool: the detail of a row is the screen's.
 *
 * Read again when a tool's entries change, like the screen: what it reads is in them.
 */
@Composable
fun rememberChartTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "chart", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }

    var reading by remember { mutableStateOf<ChartReading?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool.id, config.toString(), version) { reading = ChartReading.of(config, context) }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event is DataChangeEvent.ToolDataChanged) version++ }
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val (first, second) = when (val read = reading ?: return) {
                    is ChartReading.Problem -> s.tool("display_name") to s.tool("tile_problem")
                    is ChartReading.Drawn -> periodText(read.spec.period, s) to (lastValue(read, s, context) ?: s.tool("screen_empty"))
                }
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    UI.Text(first, TextType.BODY, maxLines = 1)
                    UI.Text(second, TextType.CAPTION, maxLines = 1)
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val read = reading as? ChartReading.Drawn ?: return
                if (read.empty) return
                val detail = when {
                    rows == null -> ChartDetail.WHOLE
                    rows <= 1 -> ChartDetail.STRIP
                    else -> ChartDetail.REDUCED
                }
                ChartView(read.spec, read.tables, read.period, read.now, onTap = null,
                    modifier = if (detail == ChartDetail.WHOLE) Modifier else Modifier.fillMaxSize(), detail = detail)
            }

            @Composable
            override fun Glance() {
                val read = reading
                if (read is ChartReading.Drawn && !read.empty) Body(1) else Summary()
            }
        }
    }
}

/**
 * The period shown, short: « 30 derniers jours » for the last so many units up to now, « Cette
 * semaine » for the current one; any other in the words of the pointer, cut by the tile.
 */
private fun periodText(period: EntryPeriod, s: StringsContext): String {
    val start = period.start as? TimePoint.Relative
    val end = period.end
    val upToNow = end == TimePoint.Now || (end is TimePoint.Relative && end.offset == 0 && end.edge == Edge.END && end.unit == start?.unit)
    if (start != null && start.edge == Edge.START && start.offset <= 0 && upToNow) {
        val unit = start.unit.name.lowercase()
        val count = 1 - start.offset
        return if (count == 1) s.tool("tile_period_current_$unit") else s.tool("tile_period_last_$unit").format(count)
    }
    return PointerDescription.period(period, s) ?: s.tool("tile_period_all")
}

/**
 * The last value of the first series: the first layer that shows a value (y, else theta, else a
 * quantity on x), its first color's rows when colored, the latest by its instant; with its unit
 * and when, relative for an entry, the step for a grid. Null when there is none.
 */
private fun lastValue(read: ChartReading.Drawn, s: StringsContext, context: Context): String? {
    read.spec.layers.forEachIndexed { i, layer ->
        val table = read.tables[i]
        val channel = listOf(Channel.Y, Channel.THETA, Channel.X).firstOrNull { c ->
            val field = layer.channel(c)?.field?.let { table.columns[it] } ?: return@firstOrNull false
            c != Channel.X || ChartValues.measureOf(field) == com.assistant.tools.chart.Measure.QUANTITATIVE
        } ?: return@forEachIndexed
        val name = layer.channel(channel)!!.field!!
        val field = table.columns.getValue(name)
        val colorField = layer.channel(Channel.COLOR)?.field?.takeIf { it in table.columns }
        val rows = colorField?.let { cf ->
            val first = ChartValues.categories(table.rows.mapNotNull { ChartValues.category(it.value(cf)) }, table.columns[cf], layer.channel(Channel.COLOR)?.scale?.domain, false).firstOrNull()
            table.rows.filter { ChartValues.category(it.value(cf)) == first }
        } ?: table.rows
        val last = rows.filter { it.value(name) != null }
            .maxByOrNull { (it.value(ChartSources.TIMESTAMP) as? Number)?.toLong() ?: Long.MIN_VALUE } ?: return@forEachIndexed
        val value = field.formatValue(last.value(name), context)
        val at = (last.value(ChartSources.TIMESTAMP) as? Number)?.toLong() ?: return value
        return s.tool("tile_value_at").format(value, whenText(layer, at, s, context))
    }
    return null
}

/** When a value stands: a grid's step by its date, an entry relative to now. */
private fun whenText(layer: Layer, at: Long, s: StringsContext, context: Context): String = when (val source = layer.source) {
    is Source.Grid -> DateUtils.format(at, s.tool(if (source.step == PeriodType.HOUR) "format_hour" else "format_day"))
    is Source.Entries -> FormatUtils.formatRelativeTimePast(at, context)
}
