package app.treelune.core.ai.processing

import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.JsonUtils

/**
 * The filters of a TOOL_DATA query, conditions on a field of each entry (Conditions), with their
 * written values in the stored form, from the forms the AI side writes them in.
 *
 * On a DATETIME field (timestamp included) or a DATE field, a value can be:
 * - a relative date, `{"relative": {"unit": "DAY", "offset": -1, "edge": "START"}}` or
 *   `{"relative": "NOW"}`, resolved against the reference instant (an automation's scheduled
 *   time, the clock for a chat) with the user's day start hour and first day of the week: a
 *   point, the edge it says, whatever the condition;
 * - ISO 8601 for a DATETIME ("2026-09-15T08:00:00+02:00"), a day for a DATE ("2026-09-15");
 * - milliseconds for a DATETIME, already stored.
 * On a DURATION field, ISO 8601 ("PT6H"). Everything else is already in its stored form.
 *
 * Where to convert is decided by the type of the field the filter names, never by the look of
 * a value: a TEXT field holding "NOW" is compared with "NOW".
 */
object FilterValues {

    /**
     * [filters] with their values stored. A filter that is not an object, or names no field of
     * the tool, is left as it is: the service refuses it and says why.
     *
     * @throws IllegalArgumentException on a date or a duration that does not read
     */
    fun toStored(
        filters: List<*>,
        fields: Map<String, FieldDefinition>,
        resolver: TimeResolver,
        text: (String) -> String
    ): List<Any?> = filters.map { raw ->
        val filter = (raw as? Map<*, *>)?.let { map -> JsonUtils.toJSONObject(map.entries.associate { it.key.toString() to it.value }) } ?: return@map raw
        val field = Conditions.fieldOf(filter)?.let { fields[it] } ?: return@map raw
        // Each written value apart, each bound of a between included
        val stored = when (field.type) {
            FieldType.DATETIME, FieldType.DATE -> Conditions.withValues(filter) { date(field.type, JsonUtils.toValue(it), resolver, text) }
            FieldType.DURATION -> Conditions.withValues(filter) { ModelValues.fromModel(JsonUtils.toValue(it), FieldValueSchema.of(field), resolver.zone) }
            else -> return@map raw
        }
        JsonUtils.toMap(stored)
    }

    /** A value on a field of [type]: milliseconds for a DATETIME, a day ("2026-09-15") for a DATE. */
    private fun date(type: FieldType, value: Any?, resolver: TimeResolver, text: (String) -> String): Any? = when {
        value == null -> null
        TimePoint.isRelative(value) -> resolver.resolve(TimePoint.read(value, text), type)
        value is String && type == FieldType.DATE -> value.takeIf { DATE_PATTERN.matches(it) }
            ?: throw IllegalArgumentException(text("ai_error_date_unreadable").format(value))
        value is String -> try {
            DateTimeConverter.isoToTimestamp(value, resolver.zone)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(text("ai_error_period_invalid_iso").format(value), e)
        }
        else -> value
    }

    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
}
