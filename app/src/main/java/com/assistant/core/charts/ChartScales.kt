package com.assistant.core.charts

import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** How the drawing reads the values of a column: as a quantity, an instant, or a category. */
object ChartValues {

    /** The measure a field's values are drawn as, unless a channel says another. */
    fun measureOf(field: FieldDefinition): Measure = when (field.type) {
        FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION, FieldType.TIME, FieldType.RANGE -> Measure.QUANTITATIVE
        FieldType.DATETIME, FieldType.DATE -> Measure.TEMPORAL
        FieldType.TEXT, FieldType.CHOICE, FieldType.BOOLEAN, FieldType.REFERENCE -> Measure.NOMINAL
    }

    /**
     * [value] as a number on a continuous axis: a quantity as it is (a duration in milliseconds),
     * an instant in milliseconds, a day at its first millisecond, an hour of the day in minutes,
     * a RANGE by its start; null for none, or for a value that is no number.
     */
    fun number(field: FieldDefinition, value: Any?, zone: ZoneId): Double? = when {
        value == null -> null
        field.type == FieldType.DATE -> (value as? String)?.let { runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli().toDouble() }.getOrNull() }
        field.type == FieldType.TIME -> (value as? String)?.split(":")?.takeIf { it.size == 2 }?.let { (h, m) -> h.toIntOrNull()?.let { hh -> m.toIntOrNull()?.let { mm -> hh * 60.0 + mm } } }
        field.type == FieldType.RANGE -> rangeEnd(value, "start")
        else -> (value as? Number)?.toDouble()
    }

    /** The end of a RANGE value; null for any other. */
    fun end(field: FieldDefinition, value: Any?): Double? = if (field.type == FieldType.RANGE) rangeEnd(value, "end") else null

    private fun rangeEnd(value: Any?, key: String): Double? = when (value) {
        is Map<*, *> -> (value[key] as? Number)?.toDouble()
        is org.json.JSONObject -> (value.opt(key) as? Number)?.toDouble()
        else -> null
    }

    /** The key a value is known by as a category; null for none. */
    fun category(value: Any?): String? = when (value) {
        null -> null
        is List<*> -> value.joinToString(", ")
        is Map<*, *> -> value["id"]?.toString() ?: value.toString()
        is Number -> if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString().takeIf { it.isNotEmpty() }
    }

    /**
     * The order of [values] as categories: [domain] when given, then a choice's options in their
     * order (a weekday's, a mood's), then the others — sorted when [ordered], as they come otherwise.
     */
    fun categories(values: List<String>, field: FieldDefinition?, domain: List<Any>?, ordered: Boolean): List<String> {
        val given = domain?.map { category(it) ?: "" } ?: emptyList()
        val options = if (field?.type == FieldType.CHOICE) ChoiceSettings.fromConfig(field.config).options else emptyList()
        val booleans = if (field?.type == FieldType.BOOLEAN) listOf("false", "true") else emptyList()
        val rest = values.distinct().filter { it !in given && it !in options && it !in booleans }
        val restOrdered = if (ordered) rest.sortedWith(compareBy<String> { it.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it }) else rest
        return (given + options.filter { it in values } + booleans.filter { it in values } + restOrdered).distinct()
    }
}

/** A graduation of an axis: where it stands, in the axis's numbers, and its text. */
data class Tick(val value: Double, val label: String)

/**
 * A continuous scale: from [min]..[max] (a quantity, milliseconds, minutes) to [start]..[end] in
 * pixels, reversed when [reverse].
 */
data class LinearScale(val min: Double, val max: Double, val start: Float, val end: Float, val reverse: Boolean = false) {
    fun position(value: Double): Float {
        val t = if (max == min) 0.5 else (value - min) / (max - min)
        val u = if (reverse) 1 - t else t
        return (start + (end - start) * u).toFloat()
    }

    fun contains(value: Double) = value >= min - EPSILON * abs(max - min) && value <= max + EPSILON * abs(max - min)

    companion object {
        private const val EPSILON = 1e-9
    }
}

/**
 * Categories on an axis, each a band of equal width from [start] to [end], [padding] of the step
 * left empty on each side of a band.
 */
data class BandScale(val categories: List<String>, val start: Float, val end: Float, val padding: Float = 0.1f) {
    val step: Float get() = if (categories.isEmpty()) 0f else (end - start) / categories.size
    val bandwidth: Float get() = step * (1 - 2 * padding)
    fun bandStart(category: String): Float? = categories.indexOf(category).takeIf { it >= 0 }?.let { start + it * step + step * padding }
    fun center(category: String): Float? = bandStart(category)?.let { it + bandwidth / 2 }
}

/** The graduations of axes: round quantities, round durations, hours of the day, the calendar. */
object ChartTicks {

    /**
     * Round numbers covering [min]..[max], about [count] of them: steps of 1, 2 or 5 times a
     * power of ten.
     */
    fun numberStep(min: Double, max: Double, count: Int): Double {
        val span = (max - min).takeIf { it > 0 } ?: abs(max).takeIf { it > 0 } ?: 1.0
        val raw = span / count.coerceAtLeast(1)
        val power = 10.0.pow(floor(log10(raw)))
        val fraction = raw / power
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * power
    }

    /** The multiples of [step] from [min] to [max]. */
    fun multiples(min: Double, max: Double, step: Double): List<Double> {
        if (step <= 0) return listOf(min)
        val first = ceil(min / step - 1e-9) * step
        val values = mutableListOf<Double>()
        var value = first
        while (value <= max + step * 1e-9 && values.size < 500) {
            // -0.0 and 0.30000000000000004 read badly
            values.add(if (abs(value) < step * 1e-9) 0.0 else Math.round(value / step) * step)
            value += step
        }
        return values
    }

    /** [min]..[max] widened to multiples of [step]. */
    fun nice(min: Double, max: Double, step: Double): Pair<Double, Double> =
        floor(min / step + 1e-9) * step to ceil(max / step - 1e-9) * step

    /** The decimals a graduation of [step] shows: none for whole steps, enough for the others. */
    fun decimals(step: Double): Int = if (step >= 1) 0 else ceil(-log10(step) - 1e-9).toInt().coerceAtLeast(0)

    /** Durations a graduation steps by, in milliseconds: seconds, minutes, hours, days. */
    val DURATION_STEPS: List<Long> = listOf(1, 5, 10, 15, 30).map { it * 1_000L } +
        listOf(1, 2, 5, 10, 15, 20, 30).map { it * 60_000L } +
        listOf(1, 2, 3, 4, 6, 12).map { it * 3_600_000L } +
        listOf(1, 2, 7, 14, 30).map { it * 86_400_000L }

    /** Hours of the day a graduation steps by, in minutes. */
    val CLOCK_STEPS: List<Long> = listOf(15, 30, 60, 120, 180, 240, 360, 720)

    /** The first of [steps] giving no more than [count] graduations over [span]. */
    fun roundStep(span: Double, count: Int, steps: List<Long>): Long =
        steps.firstOrNull { span / it <= count.coerceAtLeast(1) } ?: steps.last()

    /** A calendar's step: how many of which unit. */
    data class CalendarStep(val unit: ChronoUnit, val amount: Long)

    /** The calendar steps a time axis graduates by, finest first. */
    val CALENDAR_STEPS = listOf(
        CalendarStep(ChronoUnit.MINUTES, 15), CalendarStep(ChronoUnit.MINUTES, 30),
        CalendarStep(ChronoUnit.HOURS, 1), CalendarStep(ChronoUnit.HOURS, 3), CalendarStep(ChronoUnit.HOURS, 6), CalendarStep(ChronoUnit.HOURS, 12),
        CalendarStep(ChronoUnit.DAYS, 1), CalendarStep(ChronoUnit.DAYS, 2), CalendarStep(ChronoUnit.WEEKS, 1), CalendarStep(ChronoUnit.WEEKS, 2),
        CalendarStep(ChronoUnit.MONTHS, 1), CalendarStep(ChronoUnit.MONTHS, 3), CalendarStep(ChronoUnit.MONTHS, 6),
        CalendarStep(ChronoUnit.YEARS, 1), CalendarStep(ChronoUnit.YEARS, 5), CalendarStep(ChronoUnit.YEARS, 10)
    )

    /** The finest calendar step giving no more than [count] graduations over [min]..[max] milliseconds. */
    fun calendarStep(min: Long, max: Long, count: Int): CalendarStep {
        val span = (max - min).coerceAtLeast(1)
        return CALENDAR_STEPS.firstOrNull { span / (it.unit.duration.toMillis() * it.amount) <= count.coerceAtLeast(1) } ?: CALENDAR_STEPS.last()
    }

    /**
     * The instants of [step] from [min] to [max] in [zone]: the calendar's own (midnights, the
     * week's first day as the user set it, the first of a month, of a year), not every so many
     * milliseconds, which daylight saving time would shift.
     */
    fun calendar(min: Long, max: Long, step: CalendarStep, zone: ZoneId, weekStartDay: String): List<Long> {
        val start = Instant.ofEpochMilli(min).atZone(zone)
        var at: ZonedDateTime = when (step.unit) {
            ChronoUnit.MINUTES -> start.truncatedTo(ChronoUnit.HOURS)
            ChronoUnit.HOURS -> start.truncatedTo(ChronoUnit.DAYS)
            ChronoUnit.DAYS -> start.truncatedTo(ChronoUnit.DAYS)
            ChronoUnit.WEEKS -> start.truncatedTo(ChronoUnit.DAYS).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.valueOf(weekStartDay.uppercase())))
            ChronoUnit.MONTHS -> start.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1)
            else -> start.truncatedTo(ChronoUnit.DAYS).withDayOfYear(1)
        }
        // A month step starts on a month its amount divides, a year step likewise
        if (step.unit == ChronoUnit.MONTHS) at = at.withMonth(((at.monthValue - 1) / step.amount.toInt()) * step.amount.toInt() + 1)
        if (step.unit == ChronoUnit.YEARS) at = at.withYear((at.year / step.amount.toInt()) * step.amount.toInt())
        val instants = mutableListOf<Long>()
        while (at.toInstant().toEpochMilli() <= max && instants.size < 500) {
            val millis = at.toInstant().toEpochMilli()
            if (millis >= min) instants.add(millis)
            at = at.plus(step.amount, step.unit)
        }
        return instants
    }
}
