package app.treelune.core.selection

import app.treelune.core.ui.components.PeriodType
import org.json.JSONObject

/** Which side of a period a relative date stands on. */
enum class Edge { START, END }

/**
 * A date or an instant as it is stored wherever the app keeps one to compare with: a period's
 * bound, a filter's value on a date field.
 *
 * A fixed date keeps the stored form of its field (milliseconds for a DATETIME, "2026-09-15" for
 * a DATE). A relative one is an object, resolved each time against the reference its context
 * gives (TimeResolver): `{"relative": {"unit": "DAY", "offset": -1, "edge": "START"}}` for the
 * start of the day before, `{"relative": "NOW"}` for the reference itself. The presence of
 * `relative` alone says a value is to be resolved, never the type of a field nor the look of a
 * string. An absent bound is no bound: nothing stands for it here.
 *
 * The AI writes the same objects; its fixed dates alone are ISO 8601, turned into the stored
 * form on the way in (FilterValues).
 */
sealed interface TimePoint {

    /** A date or an instant in the stored form of its field. */
    data class Fixed(val value: Any) : TimePoint

    /** The [edge] of the [unit] [offset] units away from the one the reference falls in. */
    data class Relative(val unit: PeriodType, val offset: Int, val edge: Edge) : TimePoint

    /** The reference itself, labelled "the moment itself" where there is one. */
    data object Now : TimePoint

    /** The stored form: the fixed value as it is, a relative one as its object. */
    fun toJson(): Any = when (this) {
        is Fixed -> value
        is Relative -> JSONObject().put(RELATIVE, JSONObject()
            .put("unit", unit.name)
            .put("offset", offset)
            .put("edge", edge.name))
        Now -> JSONObject().put(RELATIVE, NOW)
    }

    companion object {
        const val RELATIVE = "relative"
        const val NOW = "NOW"

        /** Whether [value] is a relative date, as JSON or as the map a command carries. */
        fun isRelative(value: Any?): Boolean = when (value) {
            is JSONObject -> value.has(RELATIVE)
            is Map<*, *> -> value.containsKey(RELATIVE)
            else -> false
        }

        /**
         * [value] read: a relative date when it is one, fixed otherwise.
         *
         * @param text The string system, for the error the AI or the log reads
         * @throws IllegalArgumentException on a relative date that does not read
         */
        fun read(value: Any, text: (String) -> String): TimePoint {
            if (!isRelative(value)) return Fixed(value)
            val relative = when (value) {
                is JSONObject -> value.get(RELATIVE)
                else -> (value as Map<*, *>)[RELATIVE]
            }
            if (relative == NOW) return Now

            val fields: Map<*, *> = when (relative) {
                is JSONObject -> relative.keys().asSequence().associateWith { relative.get(it) }
                is Map<*, *> -> relative
                else -> throw invalid(value, text)
            }
            val unitName = fields["unit"] as? String ?: throw invalid(value, text)
            val unit = PeriodType.entries.firstOrNull { it.name == unitName }
                ?: throw IllegalArgumentException(text("ai_error_period_unknown_type").format(unitName, PeriodType.entries.joinToString(", ") { it.name }))
            // An offset is a whole number, which a JSON reader may hand over as 1.0
            val offset = (fields["offset"] as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt() ?: throw invalid(value, text)
            val edge = Edge.entries.firstOrNull { it.name == fields["edge"] } ?: throw invalid(value, text)
            return Relative(unit, offset, edge)
        }

        private fun invalid(value: Any, text: (String) -> String) =
            IllegalArgumentException(text("error_relative_date_invalid").format(value.toString()))
    }
}
