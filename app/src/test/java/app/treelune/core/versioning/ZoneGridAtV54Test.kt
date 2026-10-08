package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The zones of a database or a backup older than v54 keep the order the home screen showed
 * them in, each on its own row of its zone group's grid, shown in LINE.
 */
class ZoneGridAtV54Test {

    @Test
    fun zonesStack_inTheirOrder_bySection_aGroupTheHomeScreenLacksUngrouped() {
        val placed = ZoneGridAtV54.place(
            listOf(ZoneGridAtV54.Zone("a", null), ZoneGridAtV54.Zone("b", "Health"), ZoneGridAtV54.Zone("c", "Gone"), ZoneGridAtV54.Zone("d", "Health")),
            listOf("Health")
        )
        assertEquals(mapOf("a" to 0, "b" to 0, "c" to 1, "d" to 1), placed)
    }

    @Test
    fun aBackup_placesItsZones_byOrderIndex_withTheGroupsOfItsSettings() {
        val data = JSONObject()
            .put("app_settings_categories", JSONArray().put(JSONObject().put("category", AppSettingCategories.MAIN_SCREEN)
                .put("settings", JSONObject().put("zone_groups", JSONArray().put("Health")).toString())))
            .put("zones", JSONArray()
                .put(JSONObject().put("id", "late").put("order_index", 5).put("group", "Health"))
                .put(JSONObject().put("id", "early").put("order_index", 1).put("group", "Health"))
                .put(JSONObject().put("id", "alone").put("order_index", 0)))
        ZoneGridAtV54.backup(data)
        val rows = data.getJSONArray("zones")
        val byId = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { it.getString("id") }
        assertEquals(0, byId.getValue("early").getInt("grid_y"))
        assertEquals(1, byId.getValue("late").getInt("grid_y"))
        assertEquals(0, byId.getValue("alone").getInt("grid_y"))
        assertEquals("LINE", byId.getValue("alone").getString("display_mode"))
        assertFalse(byId.getValue("alone").has("order_index"))
    }
}
