package com.assistant.tools.chart.ui

import android.content.Context
import com.assistant.core.selection.TimeResolver
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.tools.chart.ChartSources
import com.assistant.tools.chart.ChartSpec
import com.assistant.tools.chart.ChartTable
import org.json.JSONObject

/** What a chart gives once read: the chart, its tables, its period at the moment read; or why it cannot be drawn. */
sealed interface ChartReading {
    data class Drawn(val spec: ChartSpec, val tables: List<ChartTable>, val period: Pair<Long?, Long?>, val now: Long) : ChartReading {
        /** Whether nothing falls in the period. */
        val empty: Boolean get() = tables.all { it.rows.isEmpty() }
    }
    data class Problem(val message: String) : ChartReading

    companion object {
        /** The chart [config] describes, read now: what its screen and its tile draw alike. */
        suspend fun of(config: JSONObject, context: Context): ChartReading {
            val s = Strings.`for`(tool = "chart", context = context)
            val now = System.currentTimeMillis()
            return try {
                val spec = ChartSpec.of(config, { s.shared(it) }, { s.tool(it) })
                Drawn(spec, ChartSources(context).tables(spec, now), spec.period.instants(TimeResolver.at(now)), now)
            } catch (e: IllegalArgumentException) {
                Problem(e.message ?: "")
            } catch (e: IllegalStateException) {
                LogManager.ui("Chart not read: ${e.message}", "WARN")
                Problem(e.message ?: "")
            }
        }
    }
}
