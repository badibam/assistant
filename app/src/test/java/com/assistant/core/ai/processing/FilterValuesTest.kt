package com.assistant.core.ai.processing

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.selection.TimeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The values the AI side writes in a TOOL_DATA filter, put in the stored form the service
 * compares: a relative date at the edge it says whatever the condition, what a day is when the
 * day starts at 4:00, and that only the field's type decides what gets converted.
 */
class FilterValuesTest {

    private val zone = ZoneId.of("Europe/Paris")

    // Wednesday 2026-09-16, 15:00 in Paris; days start at 4:00, weeks on Monday
    private val reference = at(2026, 9, 16, 15, 0)
    private val resolver = TimeResolver(reference, zone, dayStartHour = 4, weekStartDay = "MONDAY")

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
        FilterValues.toStored(listOf(mapOf("field" to field, "op" to op, "value" to value)), fields, resolver) { it }
            .single() as Map<*, *>

    private fun relative(unit: String, offset: Int, edge: String) =
        mapOf("relative" to mapOf("unit" to unit, "offset" to offset, "edge" to edge))

    private val now = mapOf("relative" to "NOW")

    private val yesterdayStart = at(2026, 9, 15, 4, 0)
    private val yesterdayEnd = at(2026, 9, 16, 4, 0) - 1

    @Test
    fun `a relative date takes the edge it says, whatever the condition`() {
        assertEquals(yesterdayStart, stored("timestamp", "<=", relative("DAY", -1, "START"))["value"])
        assertEquals(yesterdayEnd, stored("timestamp", ">=", relative("DAY", -1, "END"))["value"])
    }

    @Test
    fun `the condition is kept as it is written`() {
        val filter = stored("timestamp", "=", relative("DAY", -1, "START"))
        assertEquals("=", filter["op"])
        assertEquals(yesterdayStart, filter["value"])
    }

    @Test
    fun `between takes each bound at its own edge, now included`() {
        val filter = stored("timestamp", "between", listOf(relative("DAY", -7, "START"), now))
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
    fun `on a day field a relative date is a day, the day starting at 4 00 or not`() {
        assertEquals("2026-09-15", stored("extra.due", "=", relative("DAY", -1, "START"))["value"])
        // This week, Monday to Sunday
        assertEquals(
            listOf("2026-09-14", "2026-09-20"),
            stored("extra.due", "between", listOf(relative("WEEK", 0, "START"), relative("WEEK", 0, "END")))["value"]
        )
        assertEquals("2026-09-16", stored("extra.due", "<=", now)["value"])
        assertEquals("2026-09-01", stored("extra.due", ">=", "2026-09-01")["value"])
    }

    @Test
    fun `a duration in ISO 8601 becomes milliseconds`() {
        assertEquals(6 * 3_600_000L, stored("data.sleep", "<", "PT6H")["value"])
        assertEquals(listOf(3_600_000L, 7_200_000L), stored("data.sleep", "between", listOf("PT1H", "PT2H"))["value"])
    }

    @Test
    fun `a text is left as it is, even one that reads like a date`() {
        assertEquals("NOW", stored("extra.note", "=", "NOW")["value"])
        assertEquals(now, stored("extra.note", "=", now)["value"])
    }

    @Test
    fun `a filter on a field the tool does not have is left for the service to refuse`() {
        assertEquals(now, stored("data.unknown", ">=", now)["value"])
    }

    @Test
    fun `an unreadable date is refused, naming why`() {
        assertEquals("ai_error_period_invalid_iso", assertThrows(IllegalArgumentException::class.java) { stored("timestamp", ">=", "yesterday") }.message)
        assertEquals("ai_error_date_unreadable", assertThrows(IllegalArgumentException::class.java) { stored("extra.due", ">=", "-1_DAY") }.message)
        assertThrows(IllegalArgumentException::class.java) { stored("timestamp", ">=", relative("FORTNIGHT", -1, "START")) }
        assertThrows(IllegalArgumentException::class.java) { stored("timestamp", ">=", mapOf("relative" to mapOf("unit" to "DAY", "offset" to -1))) }
    }
}
