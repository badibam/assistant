package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The pointers stored before v44 read the same after: the same target, what they attached, and
 * the same period, now as filters on timestamp.
 */
class PointerAtV44Test {

    private val zone = ZoneId.of("Europe/Paris")
    private val calendar = PointerAtV44.Calendar(zone, dayStartHour = 4, weekStartDay = "MONDAY")

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun old(level: String, path: String, context: String, vararg resources: String, period: JSONObject? = null) =
        JSONObject()
            .put("selected_path", path)
            .put("selection_level", level)
            .put("selected_context", context)
            .put("selected_resources", JSONArray(resources.toList()))
            .apply { period?.let { put("timestamp_selection", it) } }

    private fun converted(old: JSONObject): JSONObject = PointerAtV44.config(old, calendar)!!

    private fun filter(pointer: JSONObject, i: Int) = pointer.getJSONArray("filters").getJSONObject(i)
    private fun kind(pointer: JSONObject) = pointer.getJSONObject("target").getString("kind")
    private fun id(pointer: JSONObject) = pointer.getJSONObject("target").getString("id")
    private fun attached(pointer: JSONObject, what: String) = pointer.getJSONObject("attach").getBoolean(what)
    private fun isMention(pointer: JSONObject) = !attached(pointer, "config") && !attached(pointer, "entries")

    @Test
    fun `a tool with its data ticked attaches its entries`() {
        val pointer = converted(old("INSTANCE", "tools.t1", "DATA", "data", "data_schema"))
        assertEquals("TOOL", kind(pointer))
        assertEquals("t1", id(pointer))
        assertTrue(attached(pointer, "entries"))
        assertFalse(attached(pointer, "config"))
    }

    @Test
    fun `a zone with its config ticked attaches its config`() {
        val pointer = converted(old("ZONE", "zones.z1", "CONFIG", "config"))
        assertEquals("ZONE", kind(pointer))
        assertEquals("z1", id(pointer))
        assertTrue(attached(pointer, "config"))
    }

    @Test
    fun `a generic pointer, or a schema ticked alone, becomes a mention`() {
        assertTrue(isMention(converted(old("INSTANCE", "tools.t1", "GENERIC"))))
        assertTrue(isMention(converted(old("INSTANCE", "tools.t1", "DATA", "data_schema"))))
    }

    @Test
    fun `an automation's relative period and NOW stay as they are, resolved at each run`() {
        val period = JSONObject()
            .put("min_relative_period", JSONObject().put("offset", -7).put("type", "DAY"))
            .put("max_is_now", true)
        val pointer = converted(old("INSTANCE", "tools.t1", "DATA", "data", period = period))
        assertEquals(">=", filter(pointer, 0).getString("op"))
        assertEquals("-7_DAY", filter(pointer, 0).getString("value"))
        assertEquals("<=", filter(pointer, 1).getString("op"))
        assertEquals("NOW", filter(pointer, 1).getString("value"))
    }

    @Test
    fun `a period picked in a chat runs from its first instant to its last, by the user's day start`() {
        // The day of 2026-09-15, which starts at 4:00
        val period = JSONObject()
            .put("min_period", JSONObject().put("timestamp", at(2026, 9, 15, 4)).put("type", "DAY"))
            .put("max_period", JSONObject().put("timestamp", at(2026, 9, 15, 4)).put("type", "DAY"))
        val pointer = converted(old("INSTANCE", "tools.t1", "DATA", "data", period = period))
        assertEquals(at(2026, 9, 15, 4), filter(pointer, 0).getLong("value"))
        assertEquals(at(2026, 9, 16, 4) - 1, filter(pointer, 1).getLong("value"))
    }

    @Test
    fun `a date picked is kept in milliseconds`() {
        val period = JSONObject().put("min_custom_date_time", 1234L)
        val pointer = converted(old("INSTANCE", "tools.t1", "DATA", "data", period = period))
        assertEquals(1, pointer.getJSONArray("filters").length())
        assertEquals(1234L, filter(pointer, 0).getLong("value"))
    }

    @Test
    fun `a pointer already at v44 is left alone`() {
        assertNull(PointerAtV44.config(converted(old("INSTANCE", "tools.t1", "GENERIC")), calendar))
    }

    @Test
    fun `a message's pointers are rewritten, its other segments left as they were`() {
        val pointer = JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER")
            .put("config", old("INSTANCE", "tools.t1", "DATA", "data").toString())
            .put("preview", "Tool: Weight").put("prompt_preview", "Tool: Weight (id = t1)")
        val use = JSONObject().put("type", "enrichment").put("enrichment_type", "USE").put("config", "{\"tool_instance_id\": \"t1\"}")
        val message = JSONObject().put("segments", JSONArray().put(JSONObject().put("type", "text").put("content", "look")).put(pointer).put(use))

        val next = JSONObject(PointerAtV44.richContent(message.toString(), calendar) { throw it }!!)
        val segments = next.getJSONArray("segments")
        assertEquals("look", segments.getJSONObject(0).getString("content"))
        assertTrue(attached(JSONObject(segments.getJSONObject(1).getString("config")), "entries"))
        assertEquals("{\"tool_instance_id\": \"t1\"}", segments.getJSONObject(2).getString("config"))
    }

    @Test
    fun `a message without an old pointer is not rewritten`() {
        val message = JSONObject().put("segments", JSONArray().put(JSONObject().put("type", "text").put("content", "hi")))
        assertNull(PointerAtV44.richContent(message.toString(), calendar) { throw it })
    }

    @Test
    fun `a pointer whose path names neither a zone nor a tool is reported and left as it was`() {
        val broken = JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER")
            .put("config", old("INSTANCE", "tools", "GENERIC").toString())
        val message = JSONObject().put("segments", JSONArray().put(broken))
        val failures = mutableListOf<Exception>()
        assertNull(PointerAtV44.richContent(message.toString(), calendar) { failures.add(it) })
        assertEquals(1, failures.size)
    }

    @Test
    fun `the calendar is read from the format settings, their defaults filling what they leave out`() {
        val read = PointerAtV44.Calendar.of(JSONObject().put("timezone_override", "Asia/Tokyo").put("day_start_hour", 6).put("week_start_day", "sunday"))
        assertEquals(PointerAtV44.Calendar(ZoneId.of("Asia/Tokyo"), 6, "SUNDAY"), read)
        assertEquals(4, PointerAtV44.Calendar.of(JSONObject().put("timezone_override", "UTC")).dayStartHour)
    }
}
