package com.assistant.core.grid

import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.ui.DisplayMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where the tools of a zone stand: each one in the grid of the group section it is shown in, at
 * its grid_x and grid_y. Pure: the services read the tools, ask here where they go, and write the
 * positions that changed.
 */
object ToolPositions {

    /**
     * The group section a tool with [group] is shown in, among the zone's [zoneGroups]: its own,
     * or the ungrouped one (null) when it has none or names a group the zone does not have.
     */
    fun section(group: String?, zoneGroups: List<String>): String? =
        group?.takeIf { it.isNotEmpty() && it in zoneGroups }

    fun section(tool: ToolInstance, zoneGroups: List<String>): String? =
        section(JSONObject(tool.config_json).optString("group").takeIf { it.isNotEmpty() }, zoneGroups)

    /** A zone's tool_groups column as the list of its groups. */
    fun zoneGroups(toolGroups: String?): List<String> =
        toolGroups?.let { json -> JSONArray(json).let { array -> (0 until array.length()).map { array.getString(it) } } } ?: emptyList()

    /** The cells of a tool, from the display mode its config always holds. */
    fun size(configJson: String): Grid.Size =
        Grid.size(DisplayMode.valueOf(JSONObject(configJson).getString("display_mode")))

    fun tile(tool: ToolInstance): Grid.Tile =
        size(tool.config_json).let { Grid.Tile(tool.id, tool.grid_x, tool.grid_y, it.width, it.height) }

    /** The tiles of the tools of [tools] shown in [section]. */
    fun tiles(tools: List<ToolInstance>, zoneGroups: List<String>, section: String?): List<Grid.Tile> =
        tools.filter { section(it, zoneGroups) == section }.map { tile(it) }

    /** The tools of [tools] that [tiles] put elsewhere, at their new place. */
    fun moved(tools: List<ToolInstance>, tiles: List<Grid.Tile>): List<ToolInstance> {
        val byId = tools.associateBy { it.id }
        return tiles.mapNotNull { tile ->
            val tool = byId[tile.id] ?: return@mapNotNull null
            if (tool.grid_x == tile.column && tool.grid_y == tile.row) null
            else tool.copy(grid_x = tile.column, grid_y = tile.row)
        }
    }

    /**
     * The zone's groups going from [before] to [after]: each tool whose section changes leaves its
     * grid and arrives in the other one, in their order on the screen. Returns the tools that
     * moved, at their new place.
     */
    fun regroup(tools: List<ToolInstance>, before: List<String>, after: List<String>): List<ToolInstance> {
        val leaving = tools
            .filter { section(it, before) != section(it, after) }
            .sortedWith(compareBy({ it.grid_y }, { it.grid_x }))
        if (leaving.isEmpty()) return emptyList()

        // Each section's grid as the groups stand now: the ones they had, then the ones they get
        val grids = tools.groupBy { section(it, before) }.mapValues { (_, list) -> list.map { tile(it) } }.toMutableMap()
        for (tool in leaving) {
            val from = section(tool, before)
            val to = section(tool, after)
            grids[from] = Grid.leave(grids.getValue(from), tool.id)
            grids[to] = Grid.arrive(grids[to] ?: emptyList(), tool.id, size(tool.config_json))
        }
        return moved(tools, grids.values.flatten())
    }
}
