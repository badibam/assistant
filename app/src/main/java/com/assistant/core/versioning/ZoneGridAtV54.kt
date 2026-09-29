package com.assistant.core.versioning

import com.assistant.core.database.entities.AppSettingCategories
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the zones to their v54 form: each one stands at grid_x and grid_y in the grid of its zone
 * group section on the home screen, where order_index ordered them in a column before, with its
 * display_mode, LINE.
 *
 * The zones are placed one by one in their former order, as a zone arriving is placed: at the
 * bottom of its section's grid, on a row of its own, in column 0 — each LINE tile on its own row,
 * as the home screen showed them. Shared by the database migration and the backup import.
 */
object ZoneGridAtV54 {

    const val MODE = "LINE"

    /** A zone as the step reads it, in its former order. */
    data class Zone(val id: String, val group: String?)

    /** Each of [zones], given in their former order, with its row; [zoneGroups] the home screen's groups. */
    fun place(zones: List<Zone>, zoneGroups: List<String>): Map<String, Int> {
        val bottoms = mutableMapOf<String?, Int>()
        return zones.associate { zone ->
            val section = zone.group?.takeIf { it.isNotEmpty() && it in zoneGroups }
            val row = bottoms[section] ?: 0
            bottoms[section] = row + 1
            zone.id to row
        }
    }

    /** The home screen's groups in the settings of its category, stored as [json]; none when absent. */
    fun zoneGroups(json: String?): List<String> {
        val groups = json?.let { JSONObject(it).optJSONArray("zone_groups") } ?: return emptyList()
        return (0 until groups.length()).map { groups.getString(it) }
    }

    /** The zones of a backup [data] placed, their order_index gone. */
    fun backup(data: JSONObject) {
        val rows = data.optJSONArray("zones") ?: return
        val settings = data.optJSONArray("app_settings_categories") ?: JSONArray()
        val mainScreen = (0 until settings.length()).map { settings.getJSONObject(it) }
            .firstOrNull { it.optString("category") == AppSettingCategories.MAIN_SCREEN }?.optString("settings")
        val items = (0 until rows.length()).map { rows.getJSONObject(it) }
        val ordered = items.sortedBy { it.getInt("order_index") }
        val placed = place(ordered.map { Zone(it.getString("id"), if (it.isNull("group")) null else it.optString("group").takeIf { g -> g.isNotEmpty() }) }, zoneGroups(mainScreen))
        for (item in items) {
            item.put("display_mode", MODE)
            item.put("grid_x", 0)
            item.put("grid_y", placed.getValue(item.getString("id")))
            item.remove("order_index")
        }
    }
}
