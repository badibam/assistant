package com.assistant.core.conditions

import com.assistant.core.fields.FilterOperator
import com.assistant.core.terms.Term
import org.json.JSONArray
import org.json.JSONObject

/**
 * A condition read from its stored form (Conditions): a side, an operator, the sides on the right
 * (none for absent and present, two for between).
 */
data class Condition(val left: Side, val op: FilterOperator, val right: List<Side>) {

    /** One side: a field of the entry the condition is put on, or a term. */
    sealed interface Side {
        data class Field(val path: String) : Side
        data class Of(val term: Term) : Side
    }

    companion object {
        /**
         * @param name What the condition belongs to, for the error
         * @throws IllegalArgumentException naming what does not read
         */
        fun fromJson(json: JSONObject, name: String, text: (String) -> String): Condition {
            fun side(raw: Any?): Side {
                val obj = raw as? JSONObject ?: throw IllegalArgumentException(text("condition_error_side").format(name, raw.toString()))
                return Conditions.fieldOf(JSONObject().put(Conditions.LEFT, obj))?.let { Side.Field(it) } ?: Side.Of(Term.fromJson(obj, name, text))
            }
            val op = FilterOperator.of(json.optString(Conditions.OP))
                ?: throw IllegalArgumentException(text("condition_error_op").format(name, json.optString(Conditions.OP)))
            val right = when (val raw = json.opt(Conditions.RIGHT)?.takeIf { it != JSONObject.NULL }) {
                null -> emptyList()
                is JSONArray -> (0 until raw.length()).map { side(raw.opt(it)) }
                else -> listOf(side(raw))
            }
            val expected = when (op) {
                FilterOperator.ABSENT, FilterOperator.PRESENT -> 0
                FilterOperator.BETWEEN -> 2
                else -> 1
            }
            if (right.size != expected) throw IllegalArgumentException(text("condition_error_right").format(name, op.key, expected))
            return Condition(side(json.opt(Conditions.LEFT)), op, right)
        }
    }
}
