package app.treelune.core.fields

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers how a DURATION's milliseconds are cut into the units a form offers, so that what is
 * typed into the boxes comes back as the same boxes.
 */
class DurationsTest {

    @Test
    fun composedUnits_runFromTheHourDownToThePrecision() {
        assertEquals(listOf(DurationUnit.HOUR, DurationUnit.MINUTE), Durations.composedUnits(DurationUnit.MINUTE))
        assertEquals(listOf(DurationUnit.HOUR), Durations.composedUnits(DurationUnit.HOUR))
        assertEquals(
            listOf(DurationUnit.HOUR, DurationUnit.MINUTE, DurationUnit.SECOND, DurationUnit.MILLISECOND),
            Durations.composedUnits(DurationUnit.MILLISECOND)
        )
        // Days are only ever a precision of their own
        assertEquals(listOf(DurationUnit.DAY), Durations.composedUnits(DurationUnit.DAY))
    }

    @Test
    fun split_dropsWhatIsFinerThanTheSmallestUnit() {
        // 1 h 25 min 40 s, shown to the minute
        val millis = 3_600_000L + 25 * 60_000L + 40_000L

        assertEquals(
            listOf(DurationUnit.HOUR to 1L, DurationUnit.MINUTE to 25L),
            Durations.split(millis, Durations.composedUnits(DurationUnit.MINUTE))
        )
        assertEquals(listOf(DurationUnit.MINUTE to 85L), Durations.split(millis, listOf(DurationUnit.MINUTE)))
    }

    /** Hours are not capped at 24: fifty hours stay fifty hours. */
    @Test
    fun split_keepsHoursPastADay() {
        assertEquals(
            listOf(DurationUnit.HOUR to 50L, DurationUnit.MINUTE to 0L),
            Durations.split(50 * 3_600_000L, Durations.composedUnits(DurationUnit.MINUTE))
        )
    }

    @Test
    fun join_isTheInverseOfSplit() {
        val units = Durations.composedUnits(DurationUnit.SECOND)
        val millis = 2 * 3_600_000L + 3 * 60_000L + 4_000L

        assertEquals(millis, Durations.join(Durations.split(millis, units).toMap()))
    }
}
