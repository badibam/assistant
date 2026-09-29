package com.assistant.core.charts

import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import com.assistant.core.ui.components.getPeriodEndTimestamp
import com.assistant.core.ui.components.resolveRelativePeriod
import java.time.Instant
import java.time.ZoneId

/** A cell of a layer's table: a value, or the failure read in its place. */
sealed interface Cell {
    /** [value] in the stored form of its column's field; null for no answer. */
    data class Value(val value: Any?) : Cell

    /** No value, and why: its words, and the entries to correct, by id. Drawn as a marked hole. */
    data class Failed(val message: String, val entries: List<String>) : Cell
}

/**
 * One row of a layer's table.
 *
 * @property span The instants a grid's step covers, which a bar or a rect spans on a time axis
 * @property entryId The entry the row stands for, from an Entries source
 */
data class Row(val cells: Map<String, Cell>, val span: Pair<Long, Long>? = null, val entryId: String? = null) {
    fun value(column: String): Any? = (cells[column] as? Cell.Value)?.value
    fun failure(column: String): Cell.Failed? = cells[column] as? Cell.Failed
}

/**
 * What a layer draws: named columns, each with the field it holds values of (its type, unit,
 * labels, options), and its rows. The drawing sees nothing else.
 */
data class ChartTable(val columns: Map<String, FieldDefinition>, val rows: List<Row>) {

    /** The table once [transform] has turned it. */
    fun transformed(transform: Transform): ChartTable = when (transform) {
        is Transform.Fold -> fold(transform)
        is Transform.Flatten -> flatten(transform)
    }

    /**
     * The folded fields turned into rows: each row gives one per field, its name under the key
     * column, a choice whose options are the fields and their labels, and its value under the
     * value column, of the first field's type — the fields folded are of one type, which the
     * config's check holds.
     */
    private fun fold(fold: Transform.Fold): ChartTable {
        val folded = fold.fields.map { name -> name to (columns[name] ?: error("fold: no column $name")) }
        val keyField = FieldDefinition(fold.key, fold.key, null, FieldType.CHOICE, false,
            mapOf("options" to ChoiceSettings.storedOptions(fold.fields, folded.associate { (name, field) -> name to field.displayName }, emptyMap())))
        val valueField = folded.first().second.copy(name = fold.value, displayName = folded.map { it.second.displayName }.distinct().joinToString(", "))
        val kept = columns - fold.fields.toSet()
        return ChartTable(
            columns = kept + (fold.key to keyField) + (fold.value to valueField),
            rows = rows.flatMap { row ->
                val rest = row.cells - fold.fields.toSet()
                fold.fields.map { name -> row.copy(cells = rest + (fold.key to Cell.Value(name)) + (fold.value to (row.cells[name] ?: Cell.Value(null)))) }
            }
        )
    }

    /**
     * A multiple choice spread into a row per option; several fields are spread side by side,
     * the shorter lists leaving no answer. A row without any option stays, with no answer.
     */
    private fun flatten(flatten: Transform.Flatten): ChartTable {
        val spread = flatten.fields.associateWith { name -> columns[name] ?: error("flatten: no column $name") }
        return ChartTable(
            columns = columns + spread.mapValues { (_, field) ->
                // One option per row now: the choice is single
                field.copy(config = field.config?.minus(setOf("multiple", "ordered")))
            },
            rows = rows.flatMap { row ->
                val lists = flatten.fields.associateWith { name ->
                    when (val value = row.value(name)) {
                        is List<*> -> value
                        null -> emptyList()
                        else -> listOf(value)
                    }
                }
                val count = lists.values.maxOf { it.size }.coerceAtLeast(1)
                (0 until count).map { i ->
                    row.copy(cells = row.cells + flatten.fields.filter { row.failure(it) == null }.associateWith { Cell.Value(lists.getValue(it).getOrNull(i)) })
                }
            }
        )
    }
}

/**
 * The steps of a grid: the calendar's hours, days, weeks, months or years met by a period, with
 * the day start and week start the user set, each from its first to its last millisecond.
 */
object GridSteps {

    /**
     * The steps of [unit] from the one [start] falls in up to the one [end] falls in, those that
     * start after [now] left out: a step still to come has nothing to read yet.
     */
    fun of(unit: PeriodType, start: Long, end: Long, now: Long, dayStartHour: Int, weekStartDay: String, zone: ZoneId): List<Pair<Long, Long>> {
        val steps = mutableListOf<Pair<Long, Long>>()
        var offset = 0
        val last = minOf(end, now)
        while (true) {
            val period = resolveRelativePeriod(RelativePeriod(offset++, unit), start, dayStartHour, weekStartDay, zone)
            if (period.timestamp > last) break
            steps.add(period.timestamp to getPeriodEndTimestamp(period, dayStartHour, weekStartDay, zone))
            // A grid of hours over years is not a chart: past this, nobody reads it
            check(steps.size <= MAX_STEPS) { "more than $MAX_STEPS steps" }
        }
        return steps
    }

    /** The day of the week of [instant], 1 for the week's first day as the user set it. */
    fun weekday(instant: Long, weekStartDay: String, zone: ZoneId): Int {
        val day = Instant.ofEpochMilli(instant).atZone(zone).dayOfWeek
        val first = java.time.DayOfWeek.valueOf(weekStartDay.uppercase())
        return (day.value - first.value + 7) % 7 + 1
    }

    /** The most steps a grid reads. */
    const val MAX_STEPS = 2000
}
