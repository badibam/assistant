package app.treelune.core.grid

import app.treelune.core.database.entities.ToolInstance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tool's grid is the group section the zone screen shows it in: a tool naming a group the zone
 * does not have is shown ungrouped, so it is placed there, and moves when the zone's groups
 * change. Otherwise two grids would lay tiles over each other in the same section.
 */
class ToolPositionsTest {

    private fun tool(id: String, group: String?, x: Int, y: Int, mode: String = "LINE") = ToolInstance(
        id = id, zone_id = "z", tooltype = "notes",
        config_json = JSONObject().put("name", id).put("display_mode", mode).apply { group?.let { put("group", it) } }.toString(),
        grid_x = x, grid_y = y
    )

    @Test
    fun aToolIsInTheSectionItIsShownIn() {
        assertEquals("Health", ToolPositions.section("Health", listOf("Health")))
        assertNull(ToolPositions.section("Gone", listOf("Health")))
        assertNull(ToolPositions.section(null, listOf("Health")))
        assertNull(ToolPositions.section("", listOf("Health")))
    }

    @Test
    fun aGroupTheZoneLoses_sendsItsToolsToTheBottomOfTheUngroupedGrid() {
        val tools = listOf(
            tool("u", null, 0, 0),
            tool("h1", "Health", 0, 0, "MINIMAL"), tool("h2", "Health", 2, 0, "MINIMAL"),
            tool("w", "Work", 0, 0)
        )
        val moved = ToolPositions.regroup(tools, listOf("Health", "Work"), listOf("Work")).associateBy { it.id }
        assertEquals(setOf("h1", "h2"), moved.keys)
        // In their order on the screen, each at the bottom on a row of its own
        assertEquals(0 to 1, moved.getValue("h1").let { it.grid_x to it.grid_y })
        assertEquals(0 to 2, moved.getValue("h2").let { it.grid_x to it.grid_y })
    }

    @Test
    fun aGroupTheZoneGains_takesBackItsTools_andTheirRowsClose() {
        val tools = listOf(tool("u", null, 0, 0), tool("o", "Later", 0, 1), tool("v", null, 0, 2))
        val moved = ToolPositions.regroup(tools, emptyList(), listOf("Later")).associateBy { it.id }
        assertEquals(0 to 0, moved.getValue("o").let { it.grid_x to it.grid_y })
        assertEquals(0 to 1, moved.getValue("v").let { it.grid_x to it.grid_y })
    }

    @Test
    fun groupsThatChangeNoSection_moveNothing() {
        val tools = listOf(tool("h", "Health", 0, 0))
        assertEquals(emptyList<ToolInstance>(), ToolPositions.regroup(tools, listOf("Health"), listOf("Health", "Work")))
    }
}
