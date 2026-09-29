package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The tools of a database or a backup older than v53 keep the order they were shown in, each on
 * its own row of its section's grid, and every config ends with a display mode: the live code
 * reads it without a default, so one left missing would stop the zone from showing.
 */
class GridAtV53Test {

    private fun tool(id: String, zone: String, tooltype: String, config: JSONObject) =
        GridAtV53.Tool(id, zone, tooltype, config.toString())

    @Test
    fun toolsStack_inTheirOrder_eachOnItsRows_bySection() {
        val groups = mapOf("z" to """["Health"]""", "y" to null)
        val placed = GridAtV53.place(
            listOf(
                tool("a", "z", "notes", JSONObject()),                                        // EXTENDED by default, two rows
                tool("b", "z", "tracking", JSONObject().put("display_mode", "SQUARE")),       // four rows
                tool("c", "z", "tracking", JSONObject().put("group", "Health")),
                tool("d", "z", "tracking", JSONObject().put("group", "Gone")),                // shown ungrouped
                tool("e", "y", "tracking", JSONObject())
            ),
            groups
        )
        assertEquals(mapOf("a" to 0, "b" to 2, "c" to 0, "d" to 6, "e" to 0), placed.mapValues { it.value.gridY })
        placed.values.forEach { assertEquals(0, it.gridX) }
        assertEquals("EXTENDED", JSONObject(placed.getValue("a").configJson).getString("display_mode"))
        assertEquals("SQUARE", JSONObject(placed.getValue("b").configJson).getString("display_mode"))
    }

    @Test(expected = IllegalStateException::class)
    fun aToolWhoseModeCannotBeKnown_isRefused() {
        GridAtV53.place(listOf(tool("a", "z", "unknown", JSONObject())), mapOf("z" to null))
    }

    @Test
    fun aBackup_placesItsTools_byOrderIndexWithinTheirZone() {
        val data = JSONObject()
            .put("zones", JSONArray().put(JSONObject().put("id", "z").put("tool_groups", JSONObject.NULL)))
            .put("tool_instances", JSONArray()
                .put(JSONObject().put("id", "second").put("zone_id", "z").put("tooltype", "tracking").put("config_json", "{}").put("order_index", 1))
                .put(JSONObject().put("id", "first").put("zone_id", "z").put("tooltype", "tracking").put("config_json", "{}").put("order_index", 0)))
        GridAtV53.backup(data)
        val rows = data.getJSONArray("tool_instances")
        val byId = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { it.getString("id") }
        assertEquals(0, byId.getValue("first").getInt("grid_y"))
        assertEquals(1, byId.getValue("second").getInt("grid_y"))
        assertFalse(byId.getValue("first").has("order_index"))
        assertEquals("LINE", JSONObject(byId.getValue("first").getString("config_json")).getString("display_mode"))
    }
}
