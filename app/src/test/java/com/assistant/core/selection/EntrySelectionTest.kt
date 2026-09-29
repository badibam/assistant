package com.assistant.core.selection

import com.assistant.core.ui.components.PeriodType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A selection stores its target, its period apart from the filters, and only what narrows; what
 * a target does not take is named rather than ignored.
 */
class EntrySelectionTest {

    private val tool = Reference(ReferenceKind.TOOL_INSTANCE, "t_42")
    private val yesterday = EntryPeriod(
        start = TimePoint.Relative(PeriodType.DAY, -1, Edge.START),
        end = TimePoint.Relative(PeriodType.DAY, -1, Edge.END)
    )

    private fun read(json: JSONObject) = EntrySelection.fromJson(json) { it }

    @Test
    fun `a selection writes and reads back the same`() {
        val filters = JSONArray().put(JSONObject().put("field", "data.kcal").put("op", ">=").put("value", 500))
        val selection = EntrySelection(tool, yesterday, filters, listOf("data.kcal"))
        val json = selection.toJson()
        assertEquals(json.toString(), read(JSONObject(json.toString())).toJson().toString())
        assertEquals("TOOL_INSTANCE", json.getJSONObject("target").getString("kind"))
        assertEquals("START", json.getJSONObject("period").getJSONObject("start").getJSONObject("relative").getString("edge"))
    }

    @Test
    fun `what narrows nothing is not written`() {
        val json = EntrySelection(Reference(ReferenceKind.ZONE, "z_1")).toJson()
        assertEquals(setOf("target"), json.keys().asSequence().toSet())
    }

    @Test
    fun `a bound left out is no limit`() {
        val period = EntryPeriod.fromJson(JSONObject("""{"end":{"relative":"NOW"}}""")) { it }
        assertNull(period.start)
        assertEquals(TimePoint.Now, period.end)
    }

    @Test
    fun `the app has no id, anything else needs one`() {
        assertThrows(IllegalArgumentException::class.java) { Reference(ReferenceKind.APP, "x") }
        assertThrows(IllegalArgumentException::class.java) { Reference(ReferenceKind.ZONE, null) }
    }

    @Test
    fun `filters and fields fit a tool instance, a period a tool instance or a zone`() {
        val zone = Reference(ReferenceKind.ZONE, "z_1")
        assertNull(EntrySelection(tool, yesterday, fields = listOf("data.kcal")).problem())
        assertNull(EntrySelection(zone, yesterday).problem())
        assertNotNull(EntrySelection(zone, fields = listOf("data.kcal")).problem())
        assertNotNull(EntrySelection(Reference(ReferenceKind.APP, null), yesterday).problem())
    }

    @Test
    fun `a period becomes the filters on timestamp of tool_data get, resolved`() {
        val zoneId = ZoneId.of("Europe/Paris")
        fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 9, day, hour, 0).atZone(zoneId).toInstant().toEpochMilli()
        val resolver = TimeResolver(at(16, 15), zoneId, dayStartHour = 4, weekStartDay = "MONDAY")
        assertEquals(
            listOf(
                mapOf("field" to "timestamp", "op" to ">=", "value" to at(15, 4)),
                mapOf("field" to "timestamp", "op" to "<=", "value" to at(16, 4) - 1)
            ),
            yesterday.timestampFilters(resolver)
        )
        assertEquals(1, EntryPeriod(end = TimePoint.Now).timestampFilters(resolver).size)
    }
}
