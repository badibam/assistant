package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the tools to their v53 form: each one stands at grid_x and grid_y in the grid of its
 * group section, where order_index ordered them in a column before. Every config holds its
 * display_mode, the one its tooltype gave by default written where it was missing.
 *
 * The tools are placed one by one in their former order, as a tool arriving is placed: at the
 * bottom of its section's grid, on a row of its own, in column 0. The screen showed them that
 * way, each on its own row.
 *
 * The defaults and the heights are those of v53, written here so that later changes to the tool
 * types or to the grid leave this step as it was. Shared by the database migration and the backup
 * import.
 */
object GridAtV53 {

    private val DEFAULT_MODES = mapOf(
        "goal" to "LINE",
        "journal" to "EXTENDED",
        "list" to "EXTENDED",
        "messages" to "LINE",
        "notes" to "EXTENDED",
        "questionnaire" to "LINE",
        "structured" to "LINE",
        "tracking" to "LINE"
    )

    /** Rows each mode takes; FULL takes one, alone on it whatever its height. */
    private val HEIGHTS = mapOf(
        "ICON" to 1, "MINIMAL" to 1, "LINE" to 1, "CONDENSED" to 2, "EXTENDED" to 2, "SQUARE" to 4, "FULL" to 1
    )

    /** A tool as the step reads it, in its former order. */
    data class Tool(val id: String, val zoneId: String, val tooltype: String, val configJson: String)

    data class Placed(val configJson: String, val gridX: Int, val gridY: Int)

    /**
     * Each of [tools], given in their former order, with its display mode written and its place.
     * [zoneGroups] holds each zone's tool_groups column.
     *
     * @throws IllegalStateException for a tool whose mode cannot be known: its tooltype has no
     *   default and its config gives none, or gives one the grid does not have
     */
    fun place(tools: List<Tool>, zoneGroups: Map<String, String?>): Map<String, Placed> {
        val groups = zoneGroups.mapValues { (_, json) ->
            json?.let { JSONArray(it).let { array -> (0 until array.length()).map { i -> array.getString(i) } } } ?: emptyList()
        }
        // The next free row of each section's grid, by zone and section
        val bottoms = mutableMapOf<Pair<String, String?>, Int>()
        return tools.associate { tool ->
            val config = JSONObject(tool.configJson)
            if (!config.has("display_mode")) {
                config.put("display_mode", DEFAULT_MODES[tool.tooltype]
                    ?: throw IllegalStateException("Tool ${tool.id}: no display mode for tooltype ${tool.tooltype}"))
            }
            val mode = config.getString("display_mode")
            val height = HEIGHTS[mode] ?: throw IllegalStateException("Tool ${tool.id}: unknown display mode $mode")
            val group = config.optString("group").takeIf { it.isNotEmpty() && it in (groups[tool.zoneId] ?: emptyList()) }
            val section = tool.zoneId to group
            val row = bottoms[section] ?: 0
            bottoms[section] = row + height
            tool.id to Placed(config.toString(), 0, row)
        }
    }

    /** The tool instances of a backup [data] placed, their order_index gone. */
    fun backup(data: JSONObject) {
        val rows = data.optJSONArray("tool_instances") ?: return
        val zones = data.optJSONArray("zones")
        val zoneGroups = (0 until (zones?.length() ?: 0)).associate { i ->
            val zone = zones!!.getJSONObject(i)
            zone.getString("id") to zone.optString("tool_groups").takeIf { !zone.isNull("tool_groups") && it.isNotEmpty() }
        }
        val items = (0 until rows.length()).map { rows.getJSONObject(it) }
        // A backup lists its tools by zone then order_index; the sort keeps that order for ties
        val ordered = items.sortedWith(compareBy({ it.getString("zone_id") }, { it.getInt("order_index") }))
        val placed = place(ordered.map { Tool(it.getString("id"), it.getString("zone_id"), it.getString("tooltype"), it.getString("config_json")) }, zoneGroups)
        for (item in items) {
            val place = placed.getValue(item.getString("id"))
            item.put("config_json", place.configJson)
            item.put("grid_x", place.gridX)
            item.put("grid_y", place.gridY)
            item.remove("order_index")
        }
    }
}
