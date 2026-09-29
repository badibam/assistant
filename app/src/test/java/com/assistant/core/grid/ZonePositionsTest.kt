package com.assistant.core.grid

import com.assistant.core.database.entities.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A zone's grid is the zone group section the home screen shows it in: a group the home screen
 * loses sends its zones to the bottom of the ungrouped grid, as a tool's group does in its zone.
 */
class ZonePositionsTest {

    private fun zone(id: String, group: String?, y: Int, mode: String = "LINE") =
        Zone(id = id, name = id, group = group, display_mode = mode, grid_x = 0, grid_y = y)

    @Test
    fun aGroupTheHomeScreenLoses_sendsItsZonesUnderTheUngrouped() {
        val zones = listOf(zone("u", null, 0), zone("h1", "Health", 0), zone("h2", "Health", 1))
        val moved = ZonePositions.regroup(zones, listOf("Health"), emptyList()).associateBy { it.id }
        assertEquals(setOf("h1", "h2"), moved.keys)
        assertEquals(1, moved.getValue("h1").grid_y)
        assertEquals(2, moved.getValue("h2").grid_y)
    }

    @Test
    fun eachModeOfAZone_takesItsCells() {
        assertEquals(Grid.Size(1, 1), ZonePositions.size(zone("a", null, 0, "ICON")))
        assertEquals(Grid.Size(2, 2), ZonePositions.size(zone("a", null, 0, "CONDENSED")))
    }
}
