package com.assistant.core.ai.processing

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The values the AI side writes in a TOOL_DATA filter, put in the stored form the service
 * compares: which edge of a relative period each condition takes, what a day is when the day
 * starts at 4:00, and that only the field's type decides what gets converted.
 */
class FilterValuesTest {

    private val zone = ZoneId.of("Europe/Paris")

    // Wednesday 2026-09-16, 15:00 in Paris; days start at 4:00, weeks on Monday
    private val reference = at(2026, 9, 16, 15, 0)
    private val calendar = FilterValues.Calendar(reference, zone, dayStartHour = 4, weekStartDay = "MONDAY")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun field(name: String, type: FieldType) = FieldDefinition(name, name, null, type, false, null)

    private val fields = mapOf(
        "timestamp" to field("timestamp", FieldType.DATETIME),
        "data.sleep" to field("sleep", FieldType.DURATION),
        "extra.due" to field("due", FieldType.DATE),
        "extra.note" to field("note", FieldType.TEXT)
    )

    private fun stored(field: String, op: String, value: Any?): Map<*, *> =
        FilterValues.toStored(listOf(mapOf("field" to field, "op" to op, "value" to value)), fields, calendar) { it }
            .single() as Map<*, *>

    private val yesterdayStart = at(2026, 9, 15, 4, 0)
    private val yesterdayEnd = at(2026, 9, 16, 4, 0) - 1

    @Test
    fun `a lower bound takes the start of a relative period, an upper bound its end`() {
        assertEquals(yesterdayStart, stored("timestamp", ">=", "-1_DAY")["value"])
        assertEquals(yesterdayStart, stored("timestamp", "<", "-1_DAY")["value"])
        assertEquals(yesterdayEnd, stored("timestamp", "<=", "-1_DAY")["value"])
        assertEquals(yesterdayEnd, stored("timestamp", ">", "-1_DAY")["value"])
    }

    @Test
    fun `equal to a relative period is the whole of it`() {
        val filter = stored("timestamp", "=", "-1_DAY")
        assertEquals("between", filter["op"])
        assertEquals(listOf(yesterdayStart, yesterdayEnd), filter["value"])
    }

    @Test
    fun `between runs from the start of the first value to the end of the second, NOW included`() {
        val filter = stored("timestamp", "between", listOf("-7_DAY", "NOW"))
        assertEquals(listOf(at(2026, 9, 9, 4, 0), reference), filter["value"])
    }

    @Test
    fun `an ISO moment is a point, whatever the condition`() {
        val moment = at(2026, 9, 1, 8, 0)
        assertEquals(moment, stored("timestamp", "<=", "2026-09-01T08:00:00+02:00")["value"])
        assertEquals("=", stored("timestamp", "=", "2026-09-01T08:00:00+02:00")["op"])
    }

    @Test
    fun `milliseconds are already stored and stay as they are`() {
        assertEquals(1000L, stored("timestamp", ">=", 1000L)["value"])
    }

    @Test
    fun `on a day field a relative period is days, the day starting at 4 00 or not`() {
        // Yesterday is one day, so the condition stays "="
        assertEquals(mapOf("field" to "extra.due", "op" to "=", "value" to "2026-09-15"), stored("extra.due", "=", "-1_DAY"))
        // This week, Monday to Sunday
        assertEquals(listOf("2026-09-14", "2026-09-20"), stored("extra.due", "=", "0_WEEK")["value"])
        assertEquals("2026-09-16", stored("extra.due", "<=", "NOW")["value"])
        assertEquals("2026-09-01", stored("extra.due", ">=", "2026-09-01")["value"])
    }

    @Test
    fun `a duration in ISO 8601 becomes milliseconds`() {
        assertEquals(6 * 3_600_000L, stored("data.sleep", "<", "PT6H")["value"])
        assertEquals(listOf(3_600_000L, 7_200_000L), stored("data.sleep", "between", listOf("PT1H", "PT2H"))["value"])
    }

    @Test
    fun `a text is left as it is, even one that reads like a period`() {
        assertEquals("NOW", stored("extra.note", "=", "NOW")["value"])
        assertEquals("-1_DAY", stored("extra.note", "contains", "-1_DAY")["value"])
    }

    @Test
    fun `a filter on a field the tool does not have is left for the service to refuse`() {
        assertEquals("-1_DAY", stored("data.unknown", ">=", "-1_DAY")["value"])
    }

    @Test
    fun `an unreadable period or date is refused, naming it`() {
        val error = assertThrows(IllegalArgumentException::class.java) { stored("timestamp", ">=", "yesterday") }
        assertEquals("ai_error_period_unknown_format", error.message)
        assertThrows(IllegalArgumentException::class.java) { stored("timestamp", ">=", "-1_FORTNIGHT") }
    }
}
