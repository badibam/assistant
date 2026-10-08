package app.treelune.core.fields

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A NUMERIC value, and each bound of a RANGE, holds as many decimals as its field says, no more:
 * a value written with more is rounded (half up), 72.456 with 1 decimal is 72.5, and with 0
 * decimals a whole number. The precision belongs to the field type, like a column's, so a write
 * is rounded rather than refused. Shown, a value is written with exactly its decimals (72 with 2
 * is 72.00).
 */
object NumericPrecision {

    private val TYPES = setOf(FieldType.NUMERIC, FieldType.RANGE)

    /** [value] rounded to the decimals of [field]: a number, or a range's two bounds. */
    fun round(value: Any?, field: FieldDefinition): Any? {
        val decimals = decimalsOf(field)
        return when {
            field.type == FieldType.RANGE && value is JSONObject -> JSONObject(value.toString()).also { range ->
                listOf("start", "end").filter { range.opt(it) is Number }.forEach { range.put(it, round(range.get(it) as Number, decimals)) }
            }
            value is Number -> round(value, decimals)
            else -> value
        }
    }

    /** [values] with the value of every NUMERIC and RANGE field of [fields] rounded; null stays null. */
    fun roundAll(values: String?, fields: List<FieldDefinition>): String? {
        if (values == null) return null
        val json = JSONObject(values)
        fields.filter { it.type in TYPES && json.has(it.name) && !json.isNull(it.name) }
            .forEach { json.put(it.name, round(json.get(it.name), it)) }
        return json.toString()
    }

    /** The decimals a NUMERIC or RANGE field declares; every one declares them. */
    fun decimalsOf(field: FieldDefinition): Int =
        (field.config?.get("decimals") as? Number)?.toInt()
            ?: error("${field.type} field '${field.name}' declares no decimals")

    /** [value] written with exactly [decimals] decimals, for the screen. */
    fun format(value: Double, decimals: Int): String = String.format("%.${decimals}f", value)

    private fun round(value: Number, decimals: Int): Number {
        val rounded = BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP)
        return if (decimals == 0) rounded.toLong() else rounded.toDouble()
    }
}
