package app.treelune.core.conditions

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import org.json.JSONArray

/**
 * A condition judged once (the Condition brick, docs/BRICKS.md): two values already read, compared
 * as the field they are values of, the way EntryFilters compares them in SQL for each entry. A
 * written value on a date resolves its relative form against the context's reference.
 */
object ConditionJudge {

    private val TIME_PATTERN = Regex("^([01]?[0-9]|2[0-3]):([0-5][0-9])$")

    /**
     * Whether [left] [op] [right] holds, compared as [field]; null when a value it needs is
     * missing: not knowing is not failing.
     *
     * @param right The values on the right: none for absent and present, two for between
     * @throws IllegalArgumentException on a relative date that does not read
     */
    fun holds(field: FieldDefinition, op: FilterOperator, left: Any?, right: List<Any?>, resolver: TimeResolver, text: (String) -> String): Boolean? {
        when (op) {
            FilterOperator.ABSENT -> return isEmpty(left)
            FilterOperator.PRESENT -> return !isEmpty(left)
            else -> {}
        }
        if (isEmpty(left) || right.isEmpty() || right.any { it == null }) return null
        val type = field.type
        val values = right.map { resolved(type, it!!, resolver, text) }
        return when (op) {
            FilterOperator.IN -> {
                val options = list(values.single())
                list(left).any { it in options }
            }
            FilterOperator.CONTAINS -> left.toString().lowercase().contains(values.single().toString().lowercase())
            FilterOperator.EQUAL -> when (type) {
                FieldType.REFERENCE -> ReferenceTarget.referenceOf(left)?.id == ReferenceTarget.referenceOf(values.single())?.id
                FieldType.CHOICE -> list(left) == list(values.single())
                FieldType.BOOLEAN, FieldType.TEXT -> left == values.single()
                else -> compare(type, left!!, values.single()) == 0
            }
            FilterOperator.BETWEEN -> compare(type, left!!, values[0]) >= 0 && compare(type, left, values[1]) <= 0
            FilterOperator.LESS -> compare(type, left!!, values.single()) < 0
            FilterOperator.LESS_OR_EQUAL -> compare(type, left!!, values.single()) <= 0
            FilterOperator.GREATER_OR_EQUAL -> compare(type, left!!, values.single()) >= 0
            FilterOperator.GREATER -> compare(type, left!!, values.single()) > 0
            FilterOperator.ABSENT, FilterOperator.PRESENT -> error("answered above")
        }
    }

    /** No answer: nothing, an empty text or an empty list, as EntryFilters reads it. */
    private fun isEmpty(value: Any?): Boolean = when (value) {
        null -> true
        is String -> value.isEmpty()
        is Collection<*> -> value.isEmpty()
        is JSONArray -> value.length() == 0
        else -> false
    }

    /** A written date resolved against the reference, anything else as it is. */
    private fun resolved(type: FieldType, value: Any, resolver: TimeResolver, text: (String) -> String): Any =
        if ((type == FieldType.DATE || type == FieldType.DATETIME) && TimePoint.isRelative(value)) resolver.resolve(TimePoint.read(value, text), type)
        else value

    private fun list(value: Any?): List<Any?> = when (value) {
        is Collection<*> -> value.toList()
        is JSONArray -> (0 until value.length()).map { value.get(it) }
        null -> emptyList()
        else -> listOf(value)
    }

    /** Two values of an ordered type: numbers as numbers, days as their ISO text, hours by the minute. */
    private fun compare(type: FieldType, a: Any, b: Any): Int = when (type) {
        FieldType.DATE -> a.toString().compareTo(b.toString())
        FieldType.TIME -> minutes(a).compareTo(minutes(b))
        else -> number(a).compareTo(number(b))
    }

    private fun number(value: Any): Double = (value as? Number)?.toDouble()
        ?: throw IllegalArgumentException("not a number: $value")

    private fun minutes(value: Any): Int = TIME_PATTERN.find(value.toString())?.destructured?.let { (h, m) -> h.toInt() * 60 + m.toInt() }
        ?: throw IllegalArgumentException("not a time of day: $value")

}
