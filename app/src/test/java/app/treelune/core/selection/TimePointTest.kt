package app.treelune.core.selection

import app.treelune.core.fields.FieldType
import app.treelune.core.ui.components.PeriodType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A date stored to compare with: a fixed one as its field stores it, a relative one as an object
 * that says its edge, resolved against the reference of its context -- with days starting at
 * 4:00, a day runs to 3:59 the day after.
 */
class TimePointTest {

    private val zone = ZoneId.of("Europe/Paris")

    // Wednesday 2026-09-16, 15:00 in Paris; days start at 4:00, weeks on Monday
    private val reference = at(2026, 9, 16, 15, 0)
    private val resolver = TimeResolver(reference, zone, dayStartHour = 4, weekStartDay = "MONDAY")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun read(json: String): TimePoint = TimePoint.read(JSONObject(json)) { it }

    private val yesterdayStart = TimePoint.Relative(PeriodType.DAY, -1, Edge.START)
    private val yesterdayEnd = TimePoint.Relative(PeriodType.DAY, -1, Edge.END)

    @Test
    fun `a relative date reads from its object, and writes back the same`() {
        val json = """{"relative":{"unit":"DAY","offset":-1,"edge":"START"}}"""
        assertEquals(yesterdayStart, read(json))
        assertEquals(JSONObject(json).toString(), yesterdayStart.toJson().toString())
        assertEquals(TimePoint.Now, read("""{"relative":"NOW"}"""))
        assertEquals("""{"relative":"NOW"}""", TimePoint.Now.toJson().toString())
    }

    @Test
    fun `the command's map reads as the stored object does`() {
        val map = mapOf("relative" to mapOf("unit" to "WEEK", "offset" to 0.0, "edge" to "END"))
        assertEquals(TimePoint.Relative(PeriodType.WEEK, 0, Edge.END), TimePoint.read(map) { it })
    }

    @Test
    fun `only the relative key makes a value relative, whatever it looks like`() {
        assertEquals(TimePoint.Fixed("NOW"), TimePoint.read("NOW") { it })
        assertEquals(TimePoint.Fixed("-1_DAY"), TimePoint.read("-1_DAY") { it })
        assertEquals(TimePoint.Fixed(1000L), TimePoint.read(1000L) { it })
        assertFalse(TimePoint.isRelative(JSONObject("""{"unit":"DAY"}""")))
        assertTrue(TimePoint.isRelative(JSONObject("""{"relative":"NOW"}""")))
    }

    @Test
    fun `a relative date without its edge, or with an unknown unit, is refused`() {
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"DAY","offset":-1}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"FORTNIGHT","offset":0,"edge":"START"}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"DAY","offset":0.5,"edge":"START"}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":"TODAY"}""") }
    }

    @Test
    fun `an instant takes the edge it says, the end being the last millisecond`() {
        assertEquals(at(2026, 9, 15, 4, 0), resolver.instant(yesterdayStart))
        assertEquals(at(2026, 9, 16, 4, 0) - 1, resolver.instant(yesterdayEnd))
        assertEquals(reference, resolver.instant(TimePoint.Now))
        assertEquals(1000L, resolver.instant(TimePoint.Fixed(1000L)))
    }

    @Test
    fun `a day is the one a period starts in, or the eve of the next one's start`() {
        assertEquals("2026-09-15", resolver.day(yesterdayStart))
        assertEquals("2026-09-15", resolver.day(yesterdayEnd))
        val thisWeekStart = TimePoint.Relative(PeriodType.WEEK, 0, Edge.START)
        val thisWeekEnd = TimePoint.Relative(PeriodType.WEEK, 0, Edge.END)
        assertEquals("2026-09-14", resolver.day(thisWeekStart))
        assertEquals("2026-09-20", resolver.day(thisWeekEnd))
        assertEquals("2026-09-16", resolver.day(TimePoint.Now))
        assertEquals("2026-01-02", resolver.day(TimePoint.Fixed("2026-01-02")))
    }

    @Test
    fun `the field's type decides between a day and an instant`() {
        assertEquals("2026-09-15", resolver.resolve(yesterdayStart, FieldType.DATE))
        assertEquals(at(2026, 9, 15, 4, 0), resolver.resolve(yesterdayStart, FieldType.DATETIME))
    }
}
