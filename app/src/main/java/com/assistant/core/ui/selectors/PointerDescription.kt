package com.assistant.core.ui.selectors

import com.assistant.core.ai.enrichments.PointerConfig
import com.assistant.core.ai.enrichments.PointerKind
import com.assistant.core.ai.enrichments.PointerPlace
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.Durations
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FilterOperator
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import com.assistant.core.ui.components.generatePeriodLabel
import com.assistant.core.ui.components.generateRelativePeriodLabel
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.DateUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * A pointer in words: the sentence under the selector's boxes that says what will go, the text
 * of its block in a message, and what the AI reads of it. The last two are written from the
 * stored pointer each time a message is read (EnrichmentText).
 */
object PointerDescription {

    /** The label of a condition. */
    fun operator(op: FilterOperator, s: StringsContext): String = s.shared("filter_op_${op.name.lowercase()}")

    private val RELATIVE = Regex("^-?\\d+_[A-Z]+$")

    private fun isDate(field: FieldDefinition) = field.type == FieldType.DATE || field.type == FieldType.DATETIME

    /** A stored value of [field] as text. */
    fun value(field: FieldDefinition, value: Any?, s: StringsContext): String = when {
        value == null -> s.shared("label_no_value")
        value is List<*> && field.type == FieldType.CHOICE -> {
            val choice = ChoiceSettings.fromConfig(field.config)
            value.joinToString(", ") { choice.labelOf(it.toString()) }
        }
        field.type == FieldType.CHOICE -> ChoiceSettings.fromConfig(field.config).labelOf(value.toString())
        // A date bound resolved at each send: now, or a period relative to it
        isDate(field) && value == "NOW" -> s.shared("period_now_label")
        isDate(field) && value is String && RELATIVE.matches(value) -> value.split("_").let { (offset, type) ->
            generateRelativePeriodLabel(RelativePeriod(offset.toInt(), PeriodType.valueOf(type)), s)
        }
        field.type == FieldType.DURATION && value is Number -> Durations.format(value.toLong(), field.config, s)
        field.type == FieldType.DATETIME && value is Number -> DateUtils.formatFullDateTime(value.toLong())
        field.type == FieldType.BOOLEAN -> s.shared(if (value == true) "label_yes" else "label_no")
        field.type == FieldType.TEXT -> "« $value »"
        value is Double && value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    /** One filter: "Duration < 6 h", "Place is one of Home, Work", "Mood no answer". */
    fun filter(filter: JSONObject, fields: Map<String, FieldDefinition>, s: StringsContext): String {
        val path = filter.optString("field")
        val field = fields[path]
        val op = FilterOperator.of(filter.optString("op"))
        val label = field?.displayName ?: path
        if (field == null || op == null) return "$label ${filter.optString("op")} ${filter.opt("value")}"
        val raw = if (filter.isNull("value")) null else filter.get("value")
        return when (op) {
            FilterOperator.ABSENT, FilterOperator.PRESENT -> "$label ${operator(op, s)}"
            FilterOperator.BETWEEN -> {
                val bounds = raw as? JSONArray
                val low = bounds?.opt(0)?.let { value(field, it, s) } ?: "?"
                val high = bounds?.opt(1)?.let { value(field, it, s) } ?: "?"
                s.shared("filter_between").format(label, low, high)
            }
            FilterOperator.IN -> "$label ${operator(op, s)} ${value(field, (raw as? JSONArray)?.let { a -> (0 until a.length()).map { a.get(it) } }, s)}"
            else -> "$label ${operator(op, s)} ${value(field, raw, s)}"
        }
    }

    /** The period in words, or null when it has no bound. */
    fun period(period: TimestampSelection, s: StringsContext): String? {
        fun bound(isNow: Boolean, relative: com.assistant.core.ui.components.RelativePeriod?, custom: Long?, picked: com.assistant.core.ui.components.Period?): String? = when {
            isNow -> s.shared("period_now_label")
            relative != null -> generateRelativePeriodLabel(relative, s)
            custom != null -> DateUtils.formatFullDateTime(custom)
            picked != null -> generatePeriodLabel(picked, AppConfigManager.getDayStartHour(), AppConfigManager.getWeekStartDay(), s)
            else -> null
        }
        val start = bound(period.minIsNow, period.minRelativePeriod, period.minCustomDateTime, period.minPeriod)
        val end = bound(period.maxIsNow, period.maxRelativePeriod, period.maxCustomDateTime, period.maxPeriod)
        return when {
            start != null && end != null -> s.shared("ai_enrichment_pointer_period_range").format(start, end)
            start != null -> s.shared("ai_enrichment_pointer_period_from").format(start)
            end != null -> s.shared("ai_enrichment_pointer_period_until").format(end)
            else -> null
        }
    }

    /** What narrows the entries, in words: the period, then each filter, then the fields kept. */
    private fun narrowing(selection: PointerSelection, fields: Map<String, FieldDefinition>, s: StringsContext): List<String> = buildList {
        period(selection.period, s)?.let { add(it) }
        for (i in 0 until selection.filters.length()) add(filter(selection.filters.getJSONObject(i), fields, s))
        selection.fields?.let { kept -> add(s.shared("pointer_part_fields").format(kept.joinToString(", ") { fields[it]?.displayName ?: it })) }
    }

    /** The name of the place the selection reached. */
    private fun targetName(selection: PointerSelection): String = selection.tool?.name ?: selection.zone?.name ?: ""

    /**
     * The sentence that says what the pointer will send: a mention alone, a mention of the
     * entries narrowed (which the AI may then read), or what is attached.
     */
    fun summary(selection: PointerSelection, fields: Map<String, FieldDefinition>, s: StringsContext): String {
        val name = targetName(selection)
        val narrowing = narrowing(selection, fields, s)
        val entries = (listOf(s.shared("pointer_part_entries").format(name)) + narrowing).joinToString(", ")
        return when {
            !selection.config && !selection.entries && narrowing.isEmpty() -> s.shared("pointer_summary_mention")
            !selection.config && !selection.entries -> s.shared("pointer_summary_mention_entries").format(entries)
            else -> s.shared("pointer_summary_attached").format(listOfNotNull(
                s.shared("pointer_part_config").format(name).takeIf { selection.config },
                entries.takeIf { selection.entries }
            ).joinToString(" ; "))
        }
    }

    /**
     * The text of a pointer's block: the kind and the name of its target as it is now ([place],
     * null once deleted, a tool with its type), then what goes with it and what narrows it.
     */
    fun block(pointer: PointerConfig, place: PointerPlace?, s: StringsContext): String {
        val kind = s.shared(if (pointer.target.kind == PointerKind.TOOL) "ai_enrichment_pointer_tool" else "ai_enrichment_pointer_zone")
        val name = when {
            place == null -> s.shared("pointer_target_deleted")
            place.typeName != null -> "${place.name} (${place.typeName})"
            else -> place.name
        }
        // A period is a filter on timestamp; any other narrows the entries by their values
        val filtered = (0 until pointer.filters.length()).map { pointer.filters.getJSONObject(it).optString("field") }
        return listOfNotNull(
            "$kind : $name",
            s.shared("ai_enrichment_pointer_context_config").takeIf { pointer.config },
            s.shared("ai_enrichment_pointer_context_data").takeIf { pointer.entries },
            s.shared("ai_period_filtered").takeIf { "timestamp" in filtered },
            s.shared("ai_values_filtered").takeIf { filtered.any { it != "timestamp" } }
        ).joinToString(", ")
    }

    /**
     * What the AI reads of a pointer: the block's text with the target's id, and, for entries
     * narrowed but not attached, the query that reads them, for the AI to run if it needs to.
     * [fields] are the tool's, which say how each filter's value is written for the AI.
     */
    fun prompt(pointer: PointerConfig, place: PointerPlace?, fields: Map<String, FieldDefinition>, s: StringsContext): String {
        val base = "${block(pointer, place, s)} (id = ${pointer.target.id})"
        if (place == null || !pointer.isMention || pointer.filters.length() == 0 || pointer.target.kind != PointerKind.TOOL) return base
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        // Instants and durations as the AI writes them, ISO 8601, by the type of their field
        fun model(type: FieldType?, value: Any?): Any? = when {
            value is JSONArray -> JSONArray((0 until value.length()).map { model(type, value.get(it)) })
            value is Number && type == FieldType.DATETIME -> DateTimeConverter.timestampToISO(value.toLong(), zone)
            value is Number && type == FieldType.DURATION -> java.time.Duration.ofMillis(value.toLong()).toString()
            else -> value
        }
        val filters = JSONArray((0 until pointer.filters.length()).map { i ->
            val filter = JSONObject(pointer.filters.getJSONObject(i).toString())
            if (filter.has("value")) filter.put("value", model(fields[filter.optString("field")]?.type, filter.get("value")))
            filter
        })
        val query = JSONObject().put("type", "TOOL_DATA").put("params", JSONObject().apply {
            put("id", pointer.target.id)
            put("filters", filters)
            pointer.fields?.let { put("fields", JSONArray((listOf("id") + it).distinct())) }
        })
        return "$base — ${s.shared("pointer_prompt_query").format(query.toString())}"
    }
}
