package com.assistant.core.fields

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A NUMERIC value holds as many decimals as its field says, no more: a value written with more is
 * rounded (half up), 72.456 with 1 decimal is 72.5, and with 0 decimals a whole number. The
 * precision belongs to the field type, like a column's, so a write is rounded rather than refused.
 * Shown, the value is written with exactly its decimals (72 with 2 is 72.00).
 */
object NumericPrecision {

    /** [value] rounded to the decimals of [field]; a value that is not a number is left as it is. */
    fun round(value: Any?, field: FieldDefinition): Any? {
        if (value !is Number) return value
        val decimals = decimalsOf(field)
        val rounded = BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP)
        return if (decimals == 0) rounded.toLong() else rounded.toDouble()
    }

    /** [values] with the value of every NUMERIC field of [fields] rounded; null stays null. */
    fun roundAll(values: String?, fields: List<FieldDefinition>): String? {
        if (values == null) return null
        val json = JSONObject(values)
        fields.filter { it.type == FieldType.NUMERIC && json.has(it.name) && !json.isNull(it.name) }
            .forEach { json.put(it.name, round(json.get(it.name), it)) }
        return json.toString()
    }

    /** The decimals a NUMERIC field declares; every one declares them. */
    fun decimalsOf(field: FieldDefinition): Int =
        (field.config?.get("decimals") as? Number)?.toInt()
            ?: error("NUMERIC field '${field.name}' declares no decimals")
}
