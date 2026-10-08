package app.treelune.core.fields

import app.treelune.core.conditions.Conditions
import org.json.JSONArray
import org.json.JSONObject

/**
 * A condition an entry filter puts on a field, by the key a query writes it with.
 *
 * Which ones apply depends on the field's type (EntryFilters.operatorsFor). ABSENT and PRESENT
 * take no value: a field left out of an entry means "no answer", and these ask for that.
 */
enum class FilterOperator(val key: String) {
    LESS("<"),
    LESS_OR_EQUAL("<="),
    EQUAL("="),
    GREATER_OR_EQUAL(">="),
    GREATER(">"),
    BETWEEN("between"),
    CONTAINS("contains"),
    IN("in"),
    ABSENT("absent"),
    PRESENT("present");

    companion object {
        fun of(key: String): FilterOperator? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One filter of an entry query, checked against the field it names.
 *
 * @property field The field's path, as FieldPatternGrammar reads it ("timestamp", "data.value")
 * @property operator The condition
 * @property value The value in its stored form: milliseconds for an instant or a duration, a
 *   list of two for BETWEEN, a list of options for IN, null for ABSENT and PRESENT
 */
data class EntryFilter(val field: String, val operator: FilterOperator, val value: Any?)

/** A WHERE clause and the arguments bound to its placeholders, in order. */
data class SqlCondition(val clause: String, val args: List<Any>)

/**
 * The value filters of an entry query: which entries of a tool a read returns, by the values of
 * their fields.
 *
 * A filter is a condition put on each entry (Conditions): a field on the left, written values on
 * the right, `{"left": {"field": "data.duration"}, "op": "<", "right": {"constant": 21600000}}`.
 * The field is any path the entry schema describes and FieldPatternGrammar reads, among the ones
 * a filter may name (filterableFields). A variable or a reading on the right is refused: the
 * context reads it first, and hands the value it gave. Filters combine with AND: an OR exists only within a field, as IN over a
 * CHOICE's options or BETWEEN over a range. A period is a filter on timestamp like any other.
 *
 * Values are in their stored form. The model writes instants and durations in ISO 8601, which
 * the AI side converts before the query reaches the service, as it does for an entry.
 *
 * The filters run in SQL through SQLite's JSON functions, so that a page and the count beside it
 * are those of the filtered entries, not of the tool's whole history.
 */
object EntryFilters {

    private val PRESENCE = setOf(FilterOperator.ABSENT, FilterOperator.PRESENT)
    private val ORDERED = setOf(
        FilterOperator.LESS, FilterOperator.LESS_OR_EQUAL, FilterOperator.EQUAL,
        FilterOperator.GREATER_OR_EQUAL, FilterOperator.GREATER, FilterOperator.BETWEEN
    )

    /** The columns of tool_data a root path names, which are all the root fields a filter takes. */
    private val COLUMNS = setOf("name", "timestamp", "created_at", "updated_at")

    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val TIME_PATTERN = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")

    /** The conditions a field of [type] can be filtered with. */
    fun operatorsFor(type: FieldType): Set<FilterOperator> = when (type) {
        FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION,
        FieldType.DATE, FieldType.TIME -> ORDERED + PRESENCE
        // An instant is never met exactly: "between" says the moment meant
        FieldType.DATETIME -> ORDERED - FilterOperator.EQUAL + PRESENCE
        FieldType.TEXT -> setOf(FilterOperator.EQUAL, FilterOperator.CONTAINS) + PRESENCE
        FieldType.CHOICE -> setOf(FilterOperator.IN) + PRESENCE
        FieldType.BOOLEAN -> setOf(FilterOperator.EQUAL) + PRESENCE
        // A pair of numbers: which of the two a comparison would be about is not said by the field
        FieldType.RANGE -> PRESENCE
        // The same thing, by its id
        FieldType.REFERENCE -> setOf(FilterOperator.EQUAL) + PRESENCE
    }

    /**
     * The fields the entries of a tool can be filtered on, by path: the core's name and
     * timestamp when the tool type uses them, when the entry was created and last changed, the
     * tool type's fields, the user's, and the state keys the tool type offers as filters.
     */
    fun filterableFields(
        declared: EntryFields,
        extra: List<FieldDefinition>,
        text: (String) -> String
    ): Map<String, FieldDefinition> = buildMap {
        if (declared.name != CoreFieldUsage.ABSENT) put("name", CoreFields.name(text, declared.nameUnique))
        if (declared.timestamp != CoreFieldUsage.ABSENT) put("timestamp", CoreFields.timestamp(text))
        put("created_at", CoreFields.createdAt(text))
        put("updated_at", CoreFields.updatedAt(text))
        declared.data.forEach { put("data.${it.definition.name}", it.definition) }
        extra.forEach { put("extra.${it.name}", it) }
        declared.state.filter { it.filterable }.forEach { put("state.${it.definition.name}", it.definition) }
    }

    /** The filters of a query, or why they cannot be run. */
    sealed interface Parsed {
        data class Ready(val filters: List<EntryFilter>) : Parsed
        data class Refused(val error: String) : Parsed
    }

    /**
     * Read and check [filters] against the [fields] of a tool (filterableFields). The first
     * filter that does not hold is refused with what is wrong with it: a filter left out would
     * widen the query in silence, and hand back entries the caller asked to leave out.
     *
     * @param text The shared string of a key, for the errors
     */
    fun parse(filters: JSONArray, fields: Map<String, FieldDefinition>, text: (String) -> String): Parsed {
        val parsed = mutableListOf<EntryFilter>()
        for (i in 0 until filters.length()) {
            val raw = filters.opt(i)
            val filter = raw as? JSONObject
                ?: return Parsed.Refused(text("service_error_filter_unreadable").format(raw.toString()))
            val path = Conditions.fieldOf(filter)
                ?: return Parsed.Refused(text("service_error_filter_unreadable").format(filter.toString()))
            val field = fields[path]
                ?: return Parsed.Refused(text("service_error_filter_unknown_field").format(path, fields.keys.joinToString(", ")))
            val operator = FilterOperator.of(filter.optString(Conditions.OP))
                ?.takeIf { it in operatorsFor(field.type) }
                ?: return Parsed.Refused(text("service_error_filter_operator").format(
                    path, filter.optString(Conditions.OP), field.type.name,
                    operatorsFor(field.type).joinToString(", ") { it.key }
                ))
            val value = when (val right = Conditions.right(filter)) {
                Conditions.Right.None -> null
                is Conditions.Right.Written -> right.value
                is Conditions.Right.NotWritten -> return Parsed.Refused(text("service_error_filter_not_written").format(path, right.raw.toString()))
            }
            // ABSENT and PRESENT take no value, and hold none once read
            val checked = if (operator in PRESENCE && value == null) null else checkValue(field, operator, value)
                ?: return Parsed.Refused(text("service_error_filter_value").format(
                    path, operator.key, value.toString(), text(expectedValueKey(field.type, operator))
                ))
            parsed.add(EntryFilter(path, operator, checked))
        }
        return Parsed.Ready(parsed)
    }

    /**
     * [value] as the query binds it, or null when it does not suit the field and the condition:
     * a list for BETWEEN (two values) and IN (options). ABSENT and PRESENT reach here only with
     * a value, which they do not take.
     */
    private fun checkValue(field: FieldDefinition, operator: FilterOperator, value: Any?): Any? = when (operator) {
        FilterOperator.ABSENT, FilterOperator.PRESENT -> null
        FilterOperator.BETWEEN -> (value as? JSONArray)
            ?.takeIf { it.length() == 2 }
            ?.let { pair -> listOf(pair.opt(0), pair.opt(1)).map { single(field.type, it) ?: return null } }
        FilterOperator.IN -> (value as? JSONArray)
            ?.takeIf { it.length() > 0 }
            ?.let { list -> (0 until list.length()).map { list.opt(it) as? String ?: return null } }
        FilterOperator.CONTAINS -> (value as? String)?.takeIf { it.isNotEmpty() }
        else -> single(field.type, value)
    }

    /** One value of a comparison, in the form its type stores, or null when it is not one. */
    private fun single(type: FieldType, value: Any?): Any? = when (type) {
        FieldType.NUMERIC, FieldType.SCALE -> (value as? Number)?.toDouble()
        FieldType.DURATION, FieldType.DATETIME -> (value as? Number)?.toLong()
        FieldType.DATE -> (value as? String)?.takeIf { DATE_PATTERN.matches(it) }
        FieldType.TIME -> (value as? String)?.takeIf { TIME_PATTERN.matches(it) }
        FieldType.TEXT -> value as? String
        FieldType.BOOLEAN -> value as? Boolean
        // A reference is compared by the id of what it designates, ids being unique across kinds
        FieldType.REFERENCE -> ReferenceTarget.referenceOf(value)?.id
        FieldType.CHOICE, FieldType.RANGE -> null
    }

    /** The key of the string saying what value a condition on a field of [type] takes. */
    private fun expectedValueKey(type: FieldType, operator: FilterOperator): String = when (operator) {
        FilterOperator.ABSENT, FilterOperator.PRESENT -> "filter_value_none"
        FilterOperator.IN -> "filter_value_options"
        FilterOperator.BETWEEN -> "filter_value_pair"
        FilterOperator.CONTAINS -> "filter_value_text"
        else -> when (type) {
            FieldType.NUMERIC, FieldType.SCALE -> "filter_value_number"
            FieldType.DURATION, FieldType.DATETIME -> "filter_value_millis"
            FieldType.DATE -> "filter_value_date"
            FieldType.TIME -> "filter_value_time"
            FieldType.BOOLEAN -> "filter_value_boolean"
            FieldType.REFERENCE -> "filter_value_reference"
            else -> "filter_value_text"
        }
    }

    /**
     * The entries of [toolInstanceId] that pass [filters], newest first, [limit] of them from
     * [offset] (no limit when null).
     */
    fun select(toolInstanceId: String, filters: List<EntryFilter>, fields: Map<String, FieldDefinition>, limit: Int?, offset: Int): SqlCondition {
        val where = where(toolInstanceId, filters, fields)
        val paging = if (limit != null) " LIMIT ? OFFSET ?" else ""
        return SqlCondition(
            "SELECT * FROM tool_data WHERE ${where.clause} ORDER BY timestamp DESC$paging",
            where.args + (if (limit != null) listOf(limit, offset) else emptyList())
        )
    }

    /** How many entries of [toolInstanceId] pass [filters]. */
    fun count(toolInstanceId: String, filters: List<EntryFilter>, fields: Map<String, FieldDefinition>): SqlCondition {
        val where = where(toolInstanceId, filters, fields)
        return SqlCondition("SELECT COUNT(*) FROM tool_data WHERE ${where.clause}", where.args)
    }

    /**
     * The values [path] holds among the entries of [toolInstanceId], the most frequent first, at
     * most [limit]: what a filter on a text field offers to pick. No answer and empty text are
     * left out.
     */
    fun values(toolInstanceId: String, path: String, limit: Int): SqlCondition {
        val (expr, exprArgs) = valueExpr(path)
        return SqlCondition(
            "SELECT $expr AS value FROM tool_data WHERE tool_instance_id = ? GROUP BY value " +
                "HAVING value IS NOT NULL AND value <> '' ORDER BY COUNT(*) DESC, value LIMIT ?",
            exprArgs + toolInstanceId + limit
        )
    }

    /** A field's value as SQL, with the arguments it binds: a column, or a path read through json_extract. */
    private fun valueExpr(path: String): Pair<String, List<Any>> =
        if (path in COLUMNS) path to emptyList()
        else "json_extract(${path.substringBefore('.', "")}, ?)" to listOf("$.\"${path.substringAfter('.')}\"")

    private fun where(toolInstanceId: String, filters: List<EntryFilter>, fields: Map<String, FieldDefinition>): SqlCondition {
        val conditions = listOf(SqlCondition("tool_instance_id = ?", listOf(toolInstanceId))) +
            filters.map { condition(it, fields.getValue(it.field)) }
        return SqlCondition(conditions.joinToString(" AND ") { "(${it.clause})" }, conditions.flatMap { it.args })
    }

    /**
     * One filter as SQL. A path inside data, extra or state reads through json_extract, which
     * gives a JSON true or false as 1 or 0 and a missing key as NULL, like a JSON null: both are
     * "no answer". The key is bound, never written into the SQL.
     */
    private fun condition(filter: EntryFilter, field: FieldDefinition): SqlCondition {
        val container = filter.field.substringBefore('.', "")
        val key = filter.field.substringAfter('.')
        val jsonPath = "$.\"$key\""
        val isList = field.type == FieldType.CHOICE && ChoiceSettings.fromConfig(field.config).shape.isList

        // The value as SQL, with the arguments it binds itself
        val (expr, exprArgs) = valueExpr(filter.field)

        // An hour is compared as minutes since midnight: "9:05" is stored as well as "09:05"
        fun minutes(sql: String) = "(CAST(substr($sql, 1, instr($sql, ':') - 1) AS INTEGER) * 60 + CAST(substr($sql, instr($sql, ':') + 1) AS INTEGER))"
        fun bound(v: Any): Any = when {
            field.type == FieldType.TIME -> (v as String).split(":").let { it[0].toInt() * 60 + it[1].toInt() }
            v is Boolean -> if (v) 1 else 0
            else -> v
        }
        val compared = if (field.type == FieldType.TIME) minutes(expr) else expr
        // The minutes of an hour read its value four times, so its arguments come four times too
        val comparedArgs = if (field.type == FieldType.TIME) List(4) { exprArgs }.flatten() else exprArgs

        return when (filter.operator) {
            FilterOperator.ABSENT -> absent(expr, exprArgs, container, jsonPath, field, isList)
            FilterOperator.PRESENT -> absent(expr, exprArgs, container, jsonPath, field, isList)
                .let { SqlCondition("NOT (${it.clause})", it.args) }
            FilterOperator.BETWEEN -> (filter.value as List<*>).let { (low, high) ->
                SqlCondition("$compared BETWEEN ? AND ?", comparedArgs + bound(low!!) + bound(high!!))
            }
            FilterOperator.IN -> {
                val options = filter.value as List<*>
                val placeholders = options.joinToString(", ") { "?" }
                if (isList) {
                    SqlCondition(
                        "EXISTS (SELECT 1 FROM json_each($container, ?) WHERE json_each.value IN ($placeholders))",
                        listOf(jsonPath) + options.map { it as String }
                    )
                } else {
                    SqlCondition("$expr IN ($placeholders)", exprArgs + options.map { it as String })
                }
            }
            // lower() folds ASCII only: "É" and "é" stay apart
            FilterOperator.CONTAINS -> SqlCondition("instr(lower($expr), lower(?)) > 0", exprArgs + (filter.value as String))
            // A unique name is found as uniqueness compares it: the case and the spaces around not counted
            FilterOperator.EQUAL if field.config?.get(CoreFields.UNIQUE) == true ->
                SqlCondition("lower(trim($expr)) = lower(trim(?))", exprArgs + (filter.value as String))
            // A reference is compared by the id it holds
            else -> if (field.type == FieldType.REFERENCE) {
                SqlCondition("json_extract($container, ?) = ?", listOf("$jsonPath.id", filter.value!!))
            } else {
                SqlCondition("$compared ${filter.operator.key} ?", comparedArgs + bound(filter.value!!))
            }
        }
    }

    /**
     * The field has no answer: nothing stored, or what a form leaves when emptied, an empty text
     * or an empty list.
     */
    private fun absent(expr: String, args: List<Any>, container: String, jsonPath: String, field: FieldDefinition, isList: Boolean): SqlCondition = when {
        isList -> SqlCondition("COALESCE(json_array_length($container, ?), 0) = 0", listOf(jsonPath))
        field.type == FieldType.TEXT -> SqlCondition("$expr IS NULL OR $expr = ''", args + args)
        else -> SqlCondition("$expr IS NULL", args)
    }
}
