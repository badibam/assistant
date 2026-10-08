package app.treelune.tools.chart

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ToolFields
import app.treelune.core.reading.FieldReading
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.Edge
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.ui.components.RelativePeriod
import app.treelune.core.ui.components.resolveRelativePeriod
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.JsonUtils

/**
 * Where a chart's tables come from: the app's own doors, read the same way wherever they serve.
 *
 * - An Entries source reads its tool's entries in the displayed period through tool_data.get,
 *   filtered as its selection says: a row per entry, its instant, its name and its fields by path.
 * - A Grid source steps through the displayed period (GridSteps) and reads each column at the end
 *   of every step, a step still running at now, in one call per column: a variable through
 *   variables.evaluate, a reading through readings.read with `at`. A reading without a period of
 *   its own reads its step. Besides its columns, a row gives `timestamp` (its step's start),
 *   `weekday` and `week`, what a calendar of rects is drawn on.
 *
 * A cell that could not be read is a failure with its words and the entries to correct.
 *
 * @throws IllegalStateException from each read when a source cannot be read at all: a tool gone,
 *   a variable deleted, a field no longer there
 */
class ChartSources(private val context: Context) {

    private val s = Strings.`for`(tool = "chart", context = context)
    private val coordinator = Coordinator(context)

    /** The columns a layer gives, its transforms before [upTo] applied. */
    suspend fun columns(layer: Layer, upTo: Int = layer.transforms.size): Map<String, FieldDefinition> =
        layer.transforms.take(upTo).fold(ChartTable(sourceColumns(layer.source), emptyList())) { table, transform -> table.transformed(transform) }.columns

    /** The columns a source gives, before any transform. */
    suspend fun sourceColumns(source: Source): Map<String, FieldDefinition> = when (source) {
        is Source.Entries -> {
            val fields = ToolFields.filterable(source.selection.target.id ?: error("a selection's tool"), context, s)
            source.selection.fields?.let { kept -> fields.filterKeys { it in kept || it == TIMESTAMP } } ?: fields
        }
        is Source.Grid -> gridColumns() + source.columns.associate { it.name to columnField(it) }
    }

    /** The columns every row of a grid gives, beside its own. */
    fun gridColumns(): Map<String, FieldDefinition> {
        val order = GridSteps.weekOrder(AppConfigManager.getWeekStartDay()).map { it.toString() }
        return mapOf(
            TIMESTAMP to FieldDefinition(TIMESTAMP, s.tool("column_timestamp"), null, FieldType.DATETIME, false, null),
            WEEKDAY to FieldDefinition(WEEKDAY, s.tool("column_weekday"), null, FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(order, order.associateWith { s.shared("day_of_week_$it") }))),
            WEEK to FieldDefinition(WEEK, s.tool("column_week"), null, FieldType.DATE, false, null)
        )
    }

    /** The field a grid's column holds values of: its variable's, or its reading's, named as the column. */
    private suspend fun columnField(column: GridColumn): FieldDefinition {
        val field = when (val term = column.term) {
            is Term.Variable -> {
                val result = coordinator.processUserAction("variables.get", mapOf("variable_id" to term.id))
                if (!result.isSuccess) throw IllegalStateException(result.error ?: s.tool("error_variable").format(column.name))
                val declared = (result.data?.get("definition") as? Map<*, *>)?.get("field") as? Map<*, *>
                // A formula whose type is deduced computes numbers
                FieldDefinition(column.name, column.name, null,
                    FieldType.entries.firstOrNull { it.name == declared?.get("type") } ?: FieldType.NUMERIC, false,
                    (declared?.get("config") as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> v?.let { k.toString() to it } }?.toMap())
            }
            is Term.Reading -> {
                if (term.reduction == Reduction.COUNT || term.field == null) FieldReading.COUNT_FIELD
                else ToolFields.filterable(term.selection.target.id ?: error("a reading's tool"), context, s)[term.field]
                    ?: throw IllegalStateException(s.tool("error_reading_field").format(column.name, term.field))
            }
            is Term.Constant -> throw IllegalStateException(s.tool("error_column_term").format(column.name))
        }
        return field.copy(name = column.name, displayName = column.name, description = null)
    }

    /**
     * The table of each layer of [spec], its transforms applied, the displayed period resolved at
     * [now]; in the order of ChartSpec.layers.
     */
    suspend fun tables(spec: ChartSpec, now: Long): List<ChartTable> {
        val resolver = TimeResolver.at(now)
        val (start, end) = spec.period.instants(resolver)
        return spec.layers.map { layer ->
            val table = when (val source = layer.source) {
                is Source.Entries -> entries(source, spec.period, now)
                is Source.Grid -> grid(source, start ?: throw IllegalStateException(s.tool("error_grid_start")), end ?: now, now, resolver)
            }
            layer.transforms.fold(table) { t, transform -> t.transformed(transform) }
        }
    }

    private suspend fun entries(source: Source.Entries, period: EntryPeriod, now: Long): ChartTable {
        val toolId = source.selection.target.id ?: error("a selection's tool")
        val fields = ToolFields.filterable(toolId, context, s)
        val columns = source.selection.fields?.let { kept -> fields.filterKeys { it in kept || it == TIMESTAMP } } ?: fields
        // Entries without a date are all shown: the period says when, and they are of no time
        val selection = source.selection.copy(period = if (TIMESTAMP in fields) period else EntryPeriod())
        val result = coordinator.processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolId,
            "filters" to JsonUtils.toList(selection.storedFilters(fields, TimeResolver.at(now)) { s.shared(it) })
        ))
        if (!result.isSuccess) throw IllegalStateException(result.error ?: s.shared("service_error_operation_failed"))
        val rows = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>().map { entry ->
            Row(columns.keys.associateWith { path -> Cell.Value(FieldReading.valueAt(entry, path)) }, entryId = entry["id"] as? String)
        }
        return ChartTable(columns, rows)
    }

    private suspend fun grid(source: Source.Grid, start: Long, end: Long, now: Long, resolver: TimeResolver): ChartTable {
        val steps = GridSteps.of(source.step, start, end, now, AppConfigManager.getDayStartHour(), AppConfigManager.getWeekStartDay(), resolver.zone)
        // Each step is read at its end, the one running at now
        val instants = steps.map { minOf(it.second, now) }
        val columns = gridColumns() + source.columns.associate { it.name to columnField(it) }
        val read = source.columns.associate { column -> column.name to readColumn(column, source, instants) }
        val rows = steps.mapIndexed { i, (stepStart, stepEnd) ->
            val week = resolveRelativePeriod(RelativePeriod(0, app.treelune.core.ui.components.PeriodType.WEEK), stepStart,
                resolver.dayStartHour, resolver.weekStartDay, resolver.zone).timestamp
            Row(
                cells = mapOf(
                    TIMESTAMP to Cell.Value(stepStart),
                    WEEKDAY to Cell.Value(GridSteps.weekday(stepStart, resolver.zone).toString()),
                    WEEK to Cell.Value(app.treelune.core.utils.DateUtils.timestampToIso8601Date(week, resolver.zone))
                ) + read.mapValues { (_, cells) -> cells[i] },
                span = stepStart to stepEnd
            )
        }
        return ChartTable(columns, rows)
    }

    /** A column read at every instant: a value or a failure each. */
    private suspend fun readColumn(column: GridColumn, source: Source.Grid, instants: List<Long>): List<Cell> {
        if (instants.isEmpty()) return emptyList()
        val result = when (val term = column.term) {
            is Term.Variable -> coordinator.processUserAction("variables.evaluate", mapOf("variable_id" to term.id, "at" to instants))
            is Term.Reading -> {
                // Without a period of its own, a reading reads its step: the step's unit around the instant read
                val selection = if (term.selection.period.isEmpty) term.selection.copy(period = EntryPeriod(
                    TimePoint.Relative(source.step, 0, Edge.START), TimePoint.Relative(source.step, 0, Edge.END))) else term.selection
                coordinator.processUserAction("readings.read", buildMap {
                    put("selection", JsonUtils.toMap(selection.toJson()))
                    term.field?.let { put("field", it) }
                    put("reduction", term.reduction.name)
                    put("at", instants)
                })
            }
            is Term.Constant -> throw IllegalStateException(s.tool("error_column_term").format(column.name))
        }
        if (!result.isSuccess) throw IllegalStateException(s.tool("error_column_read").format(column.name, result.error ?: ""))
        return (result.data?.get("values") as? List<*>).orEmpty().map { row ->
            val value = row as? Map<*, *> ?: return@map Cell.Value(null)
            (value["failure"] as? Map<*, *>)?.let { failure ->
                val entries = ((failure["entries"] as? List<*>).orEmpty() +
                    (failure["causes"] as? List<*>).orEmpty().flatMap { (it as? Map<*, *>)?.get("entries") as? List<*> ?: emptyList<Any>() })
                    .map { it.toString() }.distinct()
                Cell.Failed(failure["message"] as? String ?: "", entries)
            } ?: Cell.Value(value["value"])
        }
    }

    companion object {
        /** The columns of a grid's row beside its own, which a grid's column may not be named. */
        const val TIMESTAMP = "timestamp"
        const val WEEKDAY = "weekday"
        const val WEEK = "week"
        val RESERVED = setOf(TIMESTAMP, WEEKDAY, WEEK, ChartKeys.REPEAT)
    }
}
