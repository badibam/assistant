package com.assistant.core.ui.selectors

import com.assistant.core.selection.Edge
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.selection.TimePoint
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the pointer selector stores: the deepest place reached by its id, what is attached, and
 * for a tool its entries narrowed; what going up or into another place leaves behind.
 */
class PointerSelectionTest {

    private val health = Named("z1", "Health")
    private val sleep = Named("t1", "Sleep", "tracking")
    private val shortNights = JSONArray().put(JSONObject().put("field", "data.duration").put("op", "<").put("value", 21600000))

    // A period's end is its start plus a day, for the cases that pick one
    private val end: (Period) -> Long = { it.timestamp + 86_400_000 - 1 }

    @Test
    fun `nothing can be pointed at before a zone is reached`() {
        assertFalse(PointerSelection().complete)
        assertTrue(PointerSelection().intoZone(health).complete)
    }

    @Test
    fun `a tool is pointed at by its id, its period apart from its filters`() {
        val selection = PointerSelection().intoZone(health).intoTool(sleep).copy(
            entries = true,
            period = TimestampSelection(minRelativePeriod = RelativePeriod(-7, PeriodType.DAY), maxIsNow = true),
            filters = shortNights,
            fields = listOf("data.duration")
        )
        val pointer = selection.pointer(end)
        assertEquals(ReferenceKind.TOOL_INSTANCE, pointer.target.kind)
        assertEquals("t1", pointer.target.id)
        assertTrue(pointer.entries)
        assertEquals(TimePoint.Relative(PeriodType.DAY, -7, Edge.START), pointer.selection.period.start)
        assertEquals(TimePoint.Now, pointer.selection.period.end)
        assertEquals(1, pointer.selection.filters.length())
        assertEquals("data.duration", pointer.selection.filters.getJSONObject(0).getString("field"))
        assertEquals(listOf("data.duration"), pointer.selection.fields)
    }

    @Test
    fun `a period picked runs from its start to its last instant`() {
        val day = Period(1000L, PeriodType.DAY)
        val pointer = PointerSelection().intoZone(health).intoTool(sleep)
            .copy(period = TimestampSelection(minPeriod = day, maxPeriod = day)).pointer(end)
        assertEquals(TimePoint.Fixed(1000L), pointer.selection.period.start)
        assertEquals(TimePoint.Fixed(1000L + 86_400_000 - 1), pointer.selection.period.end)
    }

    @Test
    fun `a zone attaches its config and its tools' entries over a period`() {
        val pointer = PointerSelection().intoZone(health)
            .copy(config = true, entries = true, period = TimestampSelection(maxIsNow = true)).pointer(end)
        assertEquals(ReferenceKind.ZONE, pointer.target.kind)
        assertEquals("z1", pointer.target.id)
        assertTrue(pointer.config)
        assertTrue(pointer.entries)
        assertEquals(TimePoint.Now, pointer.selection.period.end)
        assertEquals(0, pointer.selection.filters.length())
        assertNull(pointer.selection.problem())
    }

    @Test
    fun `nothing ticked is a mention, narrowed or not`() {
        val selection = PointerSelection().intoZone(health).intoTool(sleep).copy(filters = shortNights)
        assertTrue(selection.pointer(end).isMention)
        assertTrue(selection.narrowed)
    }

    @Test
    fun `going back up to the zone drops what only the tool offered`() {
        val up = PointerSelection().intoZone(health).intoTool(sleep)
            .copy(config = true, entries = true, filters = shortNights, fields = listOf("data.duration"), period = TimestampSelection(maxIsNow = true))
            .upTo(ReferenceKind.ZONE)
        assertEquals(ReferenceKind.ZONE, up.level)
        assertTrue(up.config)
        assertTrue(up.entries)
        assertTrue(up.period.maxIsNow)
        assertEquals(0, up.filters.length())
        assertNull(up.fields)
    }

    @Test
    fun `another tool keeps the period but not the filters`() {
        val other = PointerSelection().intoZone(health).intoTool(sleep)
            .copy(filters = shortNights, period = TimestampSelection(maxIsNow = true))
            .intoTool(Named("t2", "Mood", "tracking"))
        assertEquals(0, other.filters.length())
        assertTrue(other.period.maxIsNow)
    }

    @Test
    fun `the selection survives a rotation whole`() {
        val selection = PointerSelection().intoZone(health).intoTool(sleep).copy(
            config = true,
            entries = true,
            period = TimestampSelection(minPeriodType = PeriodType.DAY, minPeriod = Period(1000L, PeriodType.DAY), maxIsNow = true),
            filters = shortNights,
            fields = listOf("data.duration")
        )
        val restored = PointerSelection.fromJson(selection.toJson())
        assertEquals(selection.zone, restored.zone)
        assertEquals(selection.tool, restored.tool)
        assertEquals(selection.period, restored.period)
        assertEquals(selection.fields, restored.fields)
        assertEquals(selection.filters.toString(), restored.filters.toString())
        assertEquals(selection.pointer(end).toJson().toString(), restored.pointer(end).toJson().toString())
    }

    // Days in Paris, for the cases on a DATE field
    private val paris = java.time.ZoneId.of("Europe/Paris")
    private val day: (Long) -> String = { java.time.Instant.ofEpochMilli(it).atZone(paris).toLocalDate().toString() }
    private fun at(d: Int, hour: Int) = java.time.LocalDateTime.of(2026, 9, d, hour, 0).atZone(paris).toInstant().toEpochMilli()

    @Test
    fun `a period on a date field is a start and an end on that field, each only when set`() {
        val filters = periodFilters("extra.due", TimestampSelection(minRelativePeriod = RelativePeriod(0, PeriodType.WEEK)), end, day)
        assertEquals(1, filters.length())
        assertEquals("extra.due", filters.getJSONObject(0).getString("field"))
        assertEquals(">=", filters.getJSONObject(0).getString("op"))
        assertEquals("START", filters.getJSONObject(0).getJSONObject("value").getJSONObject("relative").getString("edge"))
    }

    @Test
    fun `a day picked on a DATE field is that day, from its start to its last day, even when days start at 4 00`() {
        // The day of the 15th, from 4:00 to 3:59:59.999 on the 16th
        val picked = Period(at(15, 4), PeriodType.DAY)
        val filters = periodFilters("extra.due", TimestampSelection(minPeriod = picked, maxPeriod = picked), { at(16, 4) - 1 }, day)
        assertEquals("2026-09-15", filters.getJSONObject(0).getString("value"))
        assertEquals("2026-09-15", filters.getJSONObject(1).getString("value"))
    }

    @Test
    fun `a date picked on a DATE field is its day, on a moment its milliseconds`() {
        val date = TimestampSelection(maxCustomDateTime = at(20, 12))
        assertEquals("2026-09-20", periodFilters("extra.due", date, end, day).getJSONObject(0).getString("value"))
        assertEquals(at(20, 12), periodFilters("extra.at", date, end, null).getJSONObject(0).getLong("value"))
    }

    @Test
    fun `now stays now, resolved at each send`() {
        val filters = periodFilters("extra.due", TimestampSelection(maxIsNow = true), end, day)
        assertEquals("NOW", filters.getJSONObject(0).getJSONObject("value").getString("relative"))
        assertEquals("<=", filters.getJSONObject(0).getString("op"))
    }
}
