package app.treelune.core.conditions

import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Condition brick's stored form (docs/BRICKS.md): `{"left", "op", "right"}`, each side a
 * term (`{"constant": …}`, `{"variable": …}`, `{"reading": …}`) or, for a condition put on each
 * entry, a field (`{"field": "data.duration"}`). `right` is absent for `absent` and `present`, and
 * a pair of sides for `between`. « durée < 6 h » is
 * `{"left": {"field": "data.duration"}, "op": "<", "right": {"constant": 21600000}}`.
 *
 * A written value has no type of its own: it is read as the field on the other side.
 */
object Conditions {

    const val LEFT = "left"
    const val OP = "op"
    const val RIGHT = "right"
    const val FIELD = "field"
    const val CONSTANT = "constant"
    const val BETWEEN = "between"

    /**
     * A condition on the field [path] of each entry, compared by [op] with written values:
     * [value] null for none, a list of two for `between`, a list of options for `in`.
     */
    fun onField(path: String, op: String, value: Any?): JSONObject = JSONObject()
        .put(LEFT, JSONObject().put(FIELD, path))
        .put(OP, op)
        .apply {
            when {
                value == null || value == JSONObject.NULL -> {}
                op == BETWEEN -> put(RIGHT, JSONArray(pairOf(value).map { constant(it) }))
                else -> put(RIGHT, constant(value))
            }
        }

    /** The field on the left of [condition], null when its left is no field. */
    fun fieldOf(condition: JSONObject): String? =
        condition.optJSONObject(LEFT)?.optString(FIELD)?.takeIf { it.isNotEmpty() }

    /** What stands on the right of a condition put on each entry. */
    sealed interface Right {
        /** Nothing: `absent`, `present`. */
        object None : Right
        /** Written values: one, or for `between` a JSONArray of the two. */
        data class Written(val value: Any) : Right
        /** A side that is not written but read (a variable, a reading), or that does not read. */
        data class NotWritten(val raw: Any) : Right
    }

    /** The right of [condition]. */
    fun right(condition: JSONObject): Right {
        val raw = condition.opt(RIGHT)?.takeIf { it != JSONObject.NULL } ?: return Right.None
        return when (raw) {
            is JSONObject -> raw.takeIf { it.has(CONSTANT) && !it.isNull(CONSTANT) }?.let { Right.Written(it.get(CONSTANT)) } ?: Right.NotWritten(raw)
            is JSONArray -> {
                val values = (0 until raw.length()).map { i ->
                    (raw.opt(i) as? JSONObject)?.takeIf { it.has(CONSTANT) && !it.isNull(CONSTANT) }?.get(CONSTANT) ?: return Right.NotWritten(raw)
                }
                Right.Written(JSONArray(values))
            }
            else -> Right.NotWritten(raw)
        }
    }

    /**
     * [condition] with each written value on its right through [convert] (each bound of a
     * `between` apart); a side that is not written is left as it is.
     */
    fun withValues(condition: JSONObject, convert: (Any?) -> Any?): JSONObject {
        val next = JSONObject(condition.toString())
        fun converted(side: Any?): Any? = (side as? JSONObject)
            ?.takeIf { it.has(CONSTANT) && !it.isNull(CONSTANT) }
            ?.let { JSONObject(it.toString()).put(CONSTANT, json(convert(it.get(CONSTANT)))) }
            ?: side
        when (val right = next.opt(RIGHT)) {
            is JSONObject -> next.put(RIGHT, converted(right))
            is JSONArray -> next.put(RIGHT, JSONArray((0 until right.length()).map { converted(right.opt(it)) }))
        }
        return next
    }

    private fun constant(value: Any?) = JSONObject().put(CONSTANT, json(value))

    private fun pairOf(value: Any): List<Any?> = when (value) {
        is JSONArray -> (0 until value.length()).map { value.opt(it) }
        is List<*> -> value
        else -> listOf(value)
    }

    /** A Kotlin value as JSON takes it: a list as an array, a map as an object. */
    private fun json(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is List<*> -> JsonUtils.toJSONArray(value)
        is Map<*, *> -> JsonUtils.toJSONObject(value.entries.associate { it.key.toString() to it.value })
        else -> value
    }
}
