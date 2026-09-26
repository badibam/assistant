package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.prompts.ModelValues
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FieldValueSchema
import com.assistant.core.fields.FilterOperator
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.strings.StringsContext
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import com.assistant.core.ui.components.getPeriodEndTimestamp
import com.assistant.core.ui.components.resolveRelativePeriod
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.JsonUtils
import java.time.Instant
import java.time.ZoneId

/**
 * The filters of a TOOL_DATA query with their values in the stored form, from the forms the AI
 * side writes them in.
 *
 * On a DATETIME field (timestamp included) or a DATE field, a value can be:
 * - "NOW": the reference instant, or its day;
 * - a relative period, "offset_TYPE" ("-7_DAY", "0_WEEK"), with the user's day start hour and
 *   first day of the week, against the reference instant: an automation's scheduled time, the
 *   clock for a chat;
 * - ISO 8601 for a DATETIME ("2026-09-15T08:00:00+02:00"), a day for a DATE ("2026-09-15").
 * On a DURATION field, ISO 8601 ("PT6H"). Everything else is already in its stored form.
 *
 * A relative period is a span, and the condition says which of its edges it compares with:
 * ">=" and "<" its start, ">" and "<=" its end, so "timestamp <= -1_DAY" reaches the end of
 * yesterday. "=" asks for the span itself and becomes "between" its two edges, and "between"
 * takes the start of its first value and the end of its second.
 *
 * Where to convert is decided by the type of the field the filter names, never by the look of
 * a value: a TEXT field holding "NOW" is compared with "NOW".
 */
object FilterValues {

    /**
     * [filters] with their values stored. A filter that is not an object, or names no field of
     * the tool, is left as it is: the service refuses it and says why.
     *
     * @throws IllegalArgumentException on a date, period or duration that does not read
     */
    fun toStored(
        filters: List<*>,
        fields: Map<String, FieldDefinition>,
        calendar: Calendar,
        text: (String) -> String
    ): List<Any?> = filters.map { raw ->
        val filter = raw as? Map<*, *> ?: return@map raw
        val field = fields[filter["field"] as? String] ?: return@map raw
        val operator = FilterOperator.of(filter["op"] as? String ?: "") ?: return@map raw
        val value = filter["value"]

        when (field.type) {
            FieldType.DATETIME, FieldType.DATE -> {
                val (storedOperator, storedValue) = dateCondition(field.type, operator, value, calendar, text)
                filter.toMutableMap().apply {
                    put("op", storedOperator.key)
                    put("value", storedValue)
                }
            }
            FieldType.DURATION -> filter.toMutableMap().apply {
                put("value", eachValue(value) { ModelValues.fromModel(it, FieldValueSchema.of(field), calendar.zone) })
            }
            else -> filter
        }
    }

    /** The fields a filter on the entries of [toolInstanceId] may name, read from its current config. */
    suspend fun filterableFields(toolInstanceId: String, context: Context, s: StringsContext): Map<String, FieldDefinition> {
        val result = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
        }
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        return EntryFilters.filterableFields(toolType.getEntryFields(config, context), extra) { s.shared(it) }
    }

    /** [value], or each of the values of a list (between, in), through [convert]. */
    private fun eachValue(value: Any?, convert: (Any?) -> Any?): Any? =
        if (value is List<*>) value.map(convert) else convert(value)

    /** The condition and value a filter on a date or an instant stores. */
    private fun dateCondition(
        type: FieldType,
        operator: FilterOperator,
        value: Any?,
        calendar: Calendar,
        text: (String) -> String
    ): Pair<FilterOperator, Any?> {
        // Each bound is read as a span; a fixed date or instant is a span of one point
        fun span(bound: Any?): Pair<Any?, Any?> = when (bound) {
            is String -> read(type, bound, calendar, text)
            else -> bound to bound
        }
        return when (operator) {
            FilterOperator.GREATER_OR_EQUAL, FilterOperator.LESS -> operator to span(value).first
            FilterOperator.GREATER, FilterOperator.LESS_OR_EQUAL -> operator to span(value).second
            FilterOperator.EQUAL -> {
                val (start, end) = span(value)
                if (start == end) operator to start else FilterOperator.BETWEEN to listOf(start, end)
            }
            FilterOperator.BETWEEN -> {
                val bounds = value as? List<*>
                if (bounds == null || bounds.size != 2) operator to value
                else operator to listOf(span(bounds[0]).first, span(bounds[1]).second)
            }
            else -> operator to value
        }
    }

    /**
     * The first and last point of what [value] designates on a field of [type]: milliseconds for
     * a DATETIME, a day ("2026-09-15") for a DATE.
     */
    private fun read(type: FieldType, value: String, calendar: Calendar, text: (String) -> String): Pair<Any, Any> {
        val reference = calendar.reference
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(calendar.zone).toLocalDate()

        if (value == "NOW") {
            return if (type == FieldType.DATE) day(reference).toString().let { it to it } else reference to reference
        }

        if (type == FieldType.DATE && DATE_PATTERN.matches(value)) return value to value
        if (type == FieldType.DATETIME && DateTimeConverter.looksLikeISO8601(value)) {
            val instant = try {
                DateTimeConverter.isoToTimestamp(value, calendar.zone)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(text("ai_error_period_invalid_iso").format(value), e)
            }
            return instant to instant
        }

        val period = relative(value, text)
        val start = calendar.resolve(period)
        if (type == FieldType.DATETIME) return start.timestamp to calendar.end(start)

        // A day is the one the period starts in, to the eve of the next period's: with a day
        // starting at 4:00, the next period begins at 4:00 on the day after the last one
        val next = calendar.resolve(RelativePeriod(period.offset + 1, period.type))
        val first = day(start.timestamp)
        val last = maxOf(first, day(next.timestamp).minusDays(1))
        return first.toString() to last.toString()
    }

    /** A relative period, "offset_TYPE". */
    private fun relative(value: String, text: (String) -> String): RelativePeriod {
        val parts = value.split("_")
        val offset = parts.takeIf { it.size == 2 }?.get(0)?.toIntOrNull()
            ?: throw IllegalArgumentException(text("ai_error_period_unknown_format").format(value))
        val type = PeriodType.entries.firstOrNull { it.name == parts[1] }
            ?: throw IllegalArgumentException(
                text("ai_error_period_unknown_type").format(parts[1], PeriodType.entries.joinToString(", ") { it.name })
            )
        return RelativePeriod(offset = offset, type = type)
    }

    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * What a relative period and "NOW" are read against: the reference instant (an automation's
     * scheduled time, the clock for a chat), and the user's calendar settings.
     */
    data class Calendar(val reference: Long, val zone: ZoneId, val dayStartHour: Int, val weekStartDay: String) {
        fun resolve(period: RelativePeriod): Period = resolveRelativePeriod(period, reference, dayStartHour, weekStartDay, zone)
        fun end(period: Period): Long = getPeriodEndTimestamp(period, dayStartHour, weekStartDay, zone)
    }
}
