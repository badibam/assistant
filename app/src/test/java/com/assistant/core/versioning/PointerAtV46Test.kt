package com.assistant.core.versioning

import com.assistant.core.ai.enrichments.PointerConfig
import com.assistant.core.fields.FieldType
import com.assistant.core.selection.Edge
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.selection.TimePoint
import com.assistant.core.ui.components.PeriodType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pointers stored at v45 read the same at v46: the same target, what they attached, the
 * same period now apart from the filters, and each relative date at the edge its condition took.
 */
class PointerAtV46Test {

    private val types = mapOf(
        "timestamp" to FieldType.DATETIME,
        "extra.due" to FieldType.DATE,
        "data.at" to FieldType.DATETIME,
        "extra.note" to FieldType.TEXT
    )
    private val fieldTypes: (String) -> Map<String, FieldType>? = { id -> if (id == "t1") types else null }

    private fun filter(field: String, op: String, value: Any) = JSONObject().put("field", field).put("op", op).put("value", value)

    private fun old(kind: String, id: String, vararg filters: JSONObject, entries: Boolean = true, fields: List<String>? = null) = JSONObject()
        .put("target", JSONObject().put("kind", kind).put("id", id))
        .put("attach", JSONObject().put("config", false).put("entries", entries))
        .apply {
            if (filters.isNotEmpty()) put("filters", JSONArray(filters.toList()))
            fields?.let { put("fields", JSONArray(it)) }
        }

    private fun converted(old: JSONObject): PointerConfig = PointerConfig.fromJson(PointerAtV46.config(old, fieldTypes)!!) { it }

    private fun valueOf(pointer: PointerConfig, i: Int) = pointer.selection.filters.getJSONObject(i).get("value")

    private fun point(value: Any) = TimePoint.read(value) { it }

    @Test
    fun `a tool becomes a tool instance, what it attaches and its fields kept`() {
        val pointer = converted(old("TOOL", "t1", fields = listOf("data.at")))
        assertEquals(ReferenceKind.TOOL_INSTANCE, pointer.target.kind)
        assertEquals("t1", pointer.target.id)
        assertTrue(pointer.entries)
        assertEquals(listOf("data.at"), pointer.selection.fields)
    }

    @Test
    fun `the bounds on timestamp become the period, each relative date at its side`() {
        val pointer = converted(old("TOOL", "t1", filter("timestamp", ">=", "-7_DAY"), filter("timestamp", "<=", "NOW")))
        assertEquals(TimePoint.Relative(PeriodType.DAY, -7, Edge.START), pointer.selection.period.start)
        assertEquals(TimePoint.Now, pointer.selection.period.end)
        assertEquals(0, pointer.selection.filters.length())
    }

    @Test
    fun `a zone's period moves the same way, fixed bounds unchanged`() {
        val pointer = converted(old("ZONE", "z1", filter("timestamp", ">=", 1000L), filter("timestamp", "<=", 2000L)))
        assertEquals(ReferenceKind.ZONE, pointer.target.kind)
        assertEquals(TimePoint.Fixed(1000L), pointer.selection.period.start)
        assertEquals(TimePoint.Fixed(2000L), pointer.selection.period.end)
    }

    @Test
    fun `equal to a relative period on timestamp is the whole of it`() {
        val pointer = converted(old("TOOL", "t1", filter("timestamp", "=", "-1_WEEK")))
        assertEquals(TimePoint.Relative(PeriodType.WEEK, -1, Edge.START), pointer.selection.period.start)
        assertEquals(TimePoint.Relative(PeriodType.WEEK, -1, Edge.END), pointer.selection.period.end)
    }

    @Test
    fun `a condition on timestamp that is no bound stays a filter, its relative date at the side it took`() {
        val pointer = converted(old("TOOL", "t1", filter("timestamp", ">", "-1_DAY")))
        assertEquals(1, pointer.selection.filters.length())
        assertEquals(TimePoint.Relative(PeriodType.DAY, -1, Edge.END), point(valueOf(pointer, 0)))
        assertTrue(pointer.selection.period.isEmpty)
    }

    @Test
    fun `on a date field each condition keeps the edge it took, an equal becoming a between`() {
        val pointer = converted(old("TOOL", "t1",
            filter("extra.due", "<", "0_MONTH"),
            filter("data.at", "<=", "-1_DAY"),
            filter("extra.due", "=", "0_WEEK"),
            filter("extra.due", "between", JSONArray().put("-7_DAY").put("NOW"))
        ))
        assertEquals(TimePoint.Relative(PeriodType.MONTH, 0, Edge.START), point(valueOf(pointer, 0)))
        assertEquals(TimePoint.Relative(PeriodType.DAY, -1, Edge.END), point(valueOf(pointer, 1)))
        val week = pointer.selection.filters.getJSONObject(2)
        assertEquals("between", week.getString("op"))
        assertEquals(TimePoint.Relative(PeriodType.WEEK, 0, Edge.START), point(week.getJSONArray("value").get(0)))
        assertEquals(TimePoint.Relative(PeriodType.WEEK, 0, Edge.END), point(week.getJSONArray("value").get(1)))
        val between = valueOf(pointer, 3) as JSONArray
        assertEquals(TimePoint.Relative(PeriodType.DAY, -7, Edge.START), point(between.get(0)))
        assertEquals(TimePoint.Now, point(between.get(1)))
    }

    @Test
    fun `a text that reads like a date is left as it is`() {
        val pointer = converted(old("TOOL", "t1", filter("extra.note", "=", "NOW"), filter("extra.due", ">=", "2026-09-01")))
        assertEquals("NOW", valueOf(pointer, 0))
        assertEquals("2026-09-01", valueOf(pointer, 1))
    }

    @Test
    fun `the filters of a tool deleted since are left as they are`() {
        val pointer = PointerAtV46.config(old("TOOL", "gone", filter("extra.due", ">=", "-1_DAY")), fieldTypes)!!
        assertEquals("-1_DAY", pointer.getJSONObject("selection").getJSONArray("filters").getJSONObject(0).getString("value"))
    }

    @Test
    fun `a pointer already at v46 is left alone`() {
        assertNull(PointerAtV46.config(PointerAtV46.config(old("TOOL", "t1"), fieldTypes)!!, fieldTypes))
    }

    @Test
    fun `a message's pointers are rewritten, a broken one reported and left as it was`() {
        fun segment(config: JSONObject) = JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER").put("config", config.toString())
        val message = JSONObject().put("segments", JSONArray()
            .put(JSONObject().put("type", "text").put("content", "look"))
            .put(segment(old("TOOL", "t1")))
            .put(segment(JSONObject().put("attach", JSONObject()))))
        val failures = mutableListOf<Exception>()
        val next = JSONObject(PointerAtV46.richContent(message.toString(), fieldTypes) { failures.add(it) }!!)
        val segments = next.getJSONArray("segments")
        assertEquals("look", segments.getJSONObject(0).getString("content"))
        assertTrue(JSONObject(segments.getJSONObject(1).getString("config")).has("selection"))
        assertEquals("{\"attach\":{}}", segments.getJSONObject(2).getString("config"))
        assertEquals(1, failures.size)
    }
}
