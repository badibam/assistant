package com.assistant.core.grid

import com.assistant.core.database.entities.Zone
import com.assistant.core.ui.DisplayMode

/**
 * Where the zones stand on the home screen: each one in the grid of the zone group section it is
 * shown in, at its grid_x and grid_y, as many cells as its display mode takes. Pure: the services
 * read the zones, ask here where they go, and write the positions that changed.
 */
object ZonePositions {

    /** The modes a zone's tile takes: it has nothing more to show than its header and description. */
    val MODES = listOf(DisplayMode.ICON, DisplayMode.MINIMAL, DisplayMode.LINE, DisplayMode.CONDENSED)

    /**
     * The zone group section a zone with [group] is shown in, among the home screen's
     * [zoneGroups]: its own, or the ungrouped one (null) when it has none. A group the home screen
     * does not have cannot be held (Groups): one that is anyway is a bug,
     * logged, and its zone shown among the ungrouped rather than lost from the screen.
     */
    fun section(group: String?, zoneGroups: List<String>): String? {
        if (group.isNullOrEmpty()) return null
        if (group in zoneGroups) return group
        com.assistant.core.utils.LogManager.ui("A zone holds the group '$group', which the home screen does not have", "ERROR")
        return null
    }

    fun size(zone: Zone): Grid.Size = Grid.size(DisplayMode.valueOf(zone.display_mode))

    fun tile(zone: Zone): Grid.Tile = size(zone).let { Grid.Tile(zone.id, zone.grid_x, zone.grid_y, it.width, it.height) }

    /** The tiles of the zones of [zones] shown in [section]. */
    fun tiles(zones: List<Zone>, zoneGroups: List<String>, section: String?): List<Grid.Tile> =
        zones.filter { section(it.group, zoneGroups) == section }.map { tile(it) }

    /** The zones of [zones] that [tiles] put elsewhere, at their new place. */
    fun moved(zones: List<Zone>, tiles: List<Grid.Tile>): List<Zone> {
        val byId = zones.associateBy { it.id }
        return tiles.mapNotNull { tile ->
            val zone = byId[tile.id] ?: return@mapNotNull null
            if (zone.grid_x == tile.column && zone.grid_y == tile.row) null
            else zone.copy(grid_x = tile.column, grid_y = tile.row)
        }
    }

    /** The home screen's groups going from [before] to [after]: the zones that change section move, returned at their new place. */
    fun regroup(zones: List<Zone>, before: List<String>, after: List<String>): List<Zone> =
        moved(zones, Grid.regroup(
            zones.map { tile(it) },
            zones.associate { it.id to section(it.group, before) },
            zones.associate { it.id to section(it.group, after) }
        ))
}
