package com.assistant.core.fields

import com.assistant.core.strings.StringsContext

/**
 * The units a DURATION field is entered and shown in, largest first.
 *
 * A DURATION value is a number of milliseconds and carries no unit: the unit lives in the
 * field's config, as its precision, and only decides what the form offers and what the screen
 * shows. There is no month or year: neither has a fixed length, so neither is a duration.
 */
enum class DurationUnit(val millis: Long) {
    DAY(86_400_000L),
    HOUR(3_600_000L),
    MINUTE(60_000L),
    SECOND(1_000L),
    MILLISECOND(1L);

    /** The short symbol written after a number: "h", "min". */
    fun symbol(s: StringsContext): String = s.shared("duration_unit_${name.lowercase()}")

    /** The name shown when choosing a precision: "Minute". */
    fun displayName(s: StringsContext): String = s.shared("duration_unit_${name.lowercase()}_display_name")

    companion object {
        /** The precision a field takes when its config names none. */
        val DEFAULT_PRECISION = MINUTE

        fun fromConfig(config: Map<String, Any>?): DurationUnit =
            (config?.get("precision") as? String)?.let { valueOf(it) } ?: DEFAULT_PRECISION
    }
}

/**
 * How a DURATION is written: in several units ("1h 25m") or in its precision alone ("85m").
 */
enum class DurationForm {
    COMPOSED,
    SINGLE;

    fun displayName(s: StringsContext): String = s.shared("duration_form_${name.lowercase()}_display_name")

    companion object {
        val DEFAULT = COMPOSED

        fun fromConfig(config: Map<String, Any>?): DurationForm =
            (config?.get("form") as? String)?.let { valueOf(it) } ?: DEFAULT
    }
}

/**
 * The arithmetic of a DURATION field, shared by its input, its display and its text form.
 */
object Durations {

    /**
     * The units a composed duration is split into, from the hour down to the precision.
     *
     * Days are left out on purpose, except as the precision itself: a duration past a day reads
     * better as "50 h" than as "2 d 2 h" for what the app records (sleep, work, activity), and
     * the input and the display must offer the same units or a value typed in one box would
     * come back in another.
     */
    fun composedUnits(precision: DurationUnit): List<DurationUnit> =
        if (precision == DurationUnit.DAY) listOf(DurationUnit.DAY)
        else DurationUnit.entries.filter { it.ordinal >= DurationUnit.HOUR.ordinal && it.ordinal <= precision.ordinal }

    /**
     * Splits [millis] into whole amounts of [units], largest first. What is left below the
     * smallest unit is dropped: the precision is the finest thing the field shows.
     */
    fun split(millis: Long, units: List<DurationUnit>): List<Pair<DurationUnit, Long>> {
        var rest = millis
        return units.map { unit ->
            val amount = rest / unit.millis
            rest -= amount * unit.millis
            unit to amount
        }
    }

    /** The milliseconds that amounts in several units add up to. */
    fun join(amounts: Map<DurationUnit, Long>): Long =
        amounts.entries.sumOf { (unit, amount) -> unit.millis * amount }

    /**
     * The text form of a duration: "1h 25m" when composed, "85m" in a single unit.
     *
     * Composed, the units worth zero are left out, and a duration below the precision is
     * "0" in that precision, so that nothing is ever written as an empty string.
     */
    fun format(millis: Long, config: Map<String, Any>?, s: StringsContext): String {
        val precision = DurationUnit.fromConfig(config)

        return when (DurationForm.fromConfig(config)) {
            DurationForm.SINGLE -> amount(millis / precision.millis, precision, s)

            DurationForm.COMPOSED -> {
                val parts = split(millis, composedUnits(precision)).filter { it.second > 0 }
                if (parts.isEmpty()) amount(0, precision, s)
                else parts.joinToString(" ") { (unit, n) -> amount(n, unit, s) }
            }
        }
    }

    private fun amount(n: Long, unit: DurationUnit, s: StringsContext): String =
        s.shared("duration_amount").format(n.toString(), unit.symbol(s))
}
