package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.prompts.ModelValues
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FieldValueSchema
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.selection.TimePoint
import com.assistant.core.selection.TimeResolver
import com.assistant.core.strings.StringsContext
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.JsonUtils

/**
 * The filters of a TOOL_DATA query with their values in the stored form, from the forms the AI
 * side writes them in.
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
        val filter = raw as? Map<*, *> ?: return@map raw
        val field = fields[filter["field"] as? String] ?: return@map raw
        val value = filter["value"]

        when (field.type) {
            FieldType.DATETIME, FieldType.DATE -> filter.toMutableMap().apply {
                put("value", eachValue(value) { date(field.type, it, resolver, text) })
            }
            FieldType.DURATION -> filter.toMutableMap().apply {
                put("value", eachValue(value) { ModelValues.fromModel(it, FieldValueSchema.of(field), resolver.zone) })
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
