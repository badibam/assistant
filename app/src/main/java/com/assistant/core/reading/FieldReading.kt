package com.assistant.core.reading

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.RunningDurations
import com.assistant.core.utils.JsonUtils

/** How a reading reduces the values of a field across entries to one. */
enum class Reduction {
    LAST, SUM, AVERAGE, MIN, MAX, COUNT, EARLIEST, LATEST;

    companion object {
        /**
         * The reductions a field of [type] takes; null is no field, which is counting entries.
         * An average of hours is refused: 23:30 and 00:30 would give 12:00.
         */
        fun forType(type: FieldType?): Set<Reduction> = when (type) {
            null -> setOf(COUNT)
            FieldType.NUMERIC, FieldType.DURATION -> setOf(LAST, SUM, AVERAGE, MIN, MAX)
            FieldType.SCALE -> setOf(LAST, AVERAGE, MIN, MAX)
            FieldType.BOOLEAN, FieldType.CHOICE, FieldType.TEXT -> setOf(LAST)
            FieldType.DATE, FieldType.DATETIME, FieldType.TIME -> setOf(LAST, EARLIEST, LATEST)
            FieldType.RANGE, FieldType.REFERENCE -> emptySet()
        }
    }
}

/** Why a reading has no value. */
enum class FailureReason {
    /** No entry in the selection, for a reduction that needs one: not weighing is not weighing more */
    NO_ENTRY,
    /** Entries without an answer to the field reduced: left out, they would bend the value */
    MISSING_VALUE,
    /** The tool read no longer exists */
    SOURCE_NOT_FOUND
}

/** What a reading gives: a value of a field, or a failure that carries its causes. */
sealed interface ReadingResult {
    /** [value] in the stored form of [field], whose type and settings are the result's. */
    data class Value(val value: Any, val field: FieldDefinition) : ReadingResult

    /** No value, and why: which field, and which entries to correct. */
    data class Failure(val reason: FailureReason, val field: String?, val entries: List<String> = emptyList()) : ReadingResult
}

/**
 * A field reduced across entries (docs/DATA.md, « Lecture du cœur »). Pure: the entries are
 * given, newest first, as tool_data hands them out.
 *
 * A reduction keeps its source's type and settings (the average of a 1-10 scale is a 1-10 scale,
 * whose step only matters to entering it); counting gives a whole number. Without an entry, a sum
 * and a count are 0 and anything else fails; an entry without an answer to the field fails the
 * reading, naming it -- the reader leaves it out with a filter "present" if that is what it means.
 * A DURATION running counts up to the instant read: its stopwatch's time is part of it.
 */
object FieldReading {

    /** The field COUNT gives: a whole number. */
    val COUNT_FIELD = FieldDefinition(
        name = "count", displayName = "count", description = null, type = FieldType.NUMERIC,
        alwaysVisible = false, config = mapOf("decimals" to 0)
    )

    /**
     * @param path The field's path ("data.weight", "extra.mood", "timestamp"), null to count
     * @param field Its definition, null to count
     * @param at The instant read at, up to which a running DURATION counts
     */
    fun reduce(entries: List<Map<String, Any?>>, path: String?, field: FieldDefinition?, reduction: Reduction, at: Long): ReadingResult {
        require(reduction in Reduction.forType(field?.type)) { "$reduction does not reduce a ${field?.type ?: "count"}" }
        if (reduction == Reduction.COUNT) return ReadingResult.Value(entries.size, COUNT_FIELD)
        path!!; field!!

        val values = entries.map { entry ->
            entry["id"] as String to if (field.type == FieldType.DURATION) durationAt(entry, path, at) else valueAt(entry, path)
        }
        values.filter { it.second == null }.map { it.first }.takeIf { it.isNotEmpty() }
            ?.let { return ReadingResult.Failure(FailureReason.MISSING_VALUE, path, it) }
        val present = values.map { it.second!! }

        if (present.isEmpty()) {
            return if (reduction == Reduction.SUM) ReadingResult.Value(zero(field.type), field)
            else ReadingResult.Failure(FailureReason.NO_ENTRY, path)
        }

        val value: Any = when (reduction) {
            Reduction.LAST -> present.first()
            Reduction.SUM -> if (field.type == FieldType.DURATION) present.sumOf { (it as Number).toLong() } else present.sumOf { (it as Number).toDouble() }
            Reduction.AVERAGE -> present.map { (it as Number).toDouble() }.average()
                .let { if (field.type == FieldType.DURATION) Math.round(it) else it }
            Reduction.MIN, Reduction.EARLIEST -> present.minWith(order(field.type))
            Reduction.MAX, Reduction.LATEST -> present.maxWith(order(field.type))
            Reduction.COUNT -> error("counted above")
        }
        return ReadingResult.Value(value, field)
    }

    /** The value at [path] in an entry: a column, or a key of data, extra or state. */
    fun valueAt(entry: Map<String, Any?>, path: String): Any? {
        val container = path.substringBefore('.', "")
        if (container.isEmpty()) return entry[path]
        val value = (entry[container] as? Map<*, *>)?.get(path.substringAfter('.'))
        // An empty text or list is what a form leaves when emptied: no answer
        return value.takeUnless { it == "" || (it is List<*> && it.isEmpty()) }
    }

    /** A duration's value at [at]: stored, plus the time its stopwatch has run when it runs. */
    private fun durationAt(entry: Map<String, Any?>, path: String, at: Long): Any? {
        val stored = valueAt(entry, path)
        val container = FieldContainer.entries.firstOrNull { it.key == path.substringBefore('.', "") } ?: return stored
        @Suppress("UNCHECKED_CAST")
        val state = (entry["state"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        return RunningDurations.currentValue((stored as? Number)?.toLong(), state, container, path.substringAfter('.'), at) ?: stored
    }

    private fun zero(type: FieldType): Any = if (type == FieldType.DURATION) 0L else 0.0

    /** How values of [type] compare: numbers as numbers, days as text, hours as minutes since midnight. */
    private fun order(type: FieldType): Comparator<Any> = when (type) {
        FieldType.DATE -> compareBy { it as String }
        FieldType.TIME -> compareBy { (it as String).split(":").let { (h, m) -> h.toInt() * 60 + m.toInt() } }
        else -> compareBy { (it as Number).toDouble() }
    }
}
