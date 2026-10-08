package app.treelune.core.ui.selectors

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the zones offered in the home screen's order, under their sections' titles. */
class ZoneOrderTest {

    private fun zone(name: String, group: String?, row: Int, column: Int = 0) = ZoneOrder.Zone(name, name, group, row, column)

    @Test
    fun theZonesFollowTheHomeScreen() {
        val zones = listOf(
            zone("Loose", null, 0),
            zone("Run", "Body", 1),
            zone("Eat", "Body", 0, 1),
            zone("Sleep", "Body", 0, 0),
            zone("Desk", "Work", 0)
        )

        val sorted = ZoneOrder.sorted(zones, listOf("Work", "Body"), "Ungrouped")

        assertEquals(listOf("Desk", "Sleep", "Eat", "Run", "Loose"), sorted.map { it.first.name })
        assertEquals(listOf("Work", "Body", "Body", "Body", "Ungrouped"), sorted.map { it.second })
    }

    @Test
    fun withoutAGroupHoldingAZoneThereIsNoTitle() {
        val sorted = ZoneOrder.sorted(listOf(zone("B", null, 1), zone("A", null, 0)), listOf("Empty"), "Ungrouped")

        assertEquals(listOf("A", "B"), sorted.map { it.first.name })
        assertEquals(listOf(null, null), sorted.map { it.second })
    }
}
