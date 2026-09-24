package com.assistant.core.ui.selectors.data

import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what the scope selector keeps across a rotation: a state saved and read back is the
 * same state, so the user finds the zone, tool, context, resources and period they had chosen.
 */
class ZoneScopeStateJsonTest {

    private val zone = SchemaNode("zones.health", "Health", NodeType.ZONE, hasChildren = true)
    private val tool = SchemaNode("tools.weight", "Weight", NodeType.TOOL, hasChildren = true, toolType = "tracking")

    /** A state halfway through: zone and tool chosen, data context, one absolute and one custom bound. */
    @Test
    fun aStateHalfwayThrough_comesBackWhole() {
        val state = ZoneScopeState(
            selectionChain = listOf(SelectionStep("Zone", "Health", zone), SelectionStep("Tool", "Weight", tool)),
            selectedPath = "tools.weight",
            currentOptions = listOf(tool),
            currentLevel = 2,
            optionsByLevel = mapOf(0 to listOf(zone), 1 to listOf(tool)),
            selectedContext = PointerContext.DATA,
            selectedResources = listOf("data", "schemas"),
            timestampSelection = TimestampSelection(
                minPeriodType = PeriodType.WEEK,
                minPeriod = Period(1_727_000_000_000L, PeriodType.WEEK),
                maxCustomDateTime = 1_727_600_000_000L,
                maxIsNow = false
            ),
            isComplete = true
        )

        assertEquals(state, ZoneScopeStateJson.fromJson(ZoneScopeStateJson.toJson(state)))
    }

    /** The relative bounds an automation template uses come back too. */
    @Test
    fun relativePeriods_comeBack() {
        val state = ZoneScopeState(
            timestampSelection = TimestampSelection(
                minPeriodType = PeriodType.DAY,
                minRelativePeriod = RelativePeriod(-7, PeriodType.DAY),
                maxRelativePeriod = RelativePeriod(0, PeriodType.DAY),
                minIsNow = true
            )
        )

        assertEquals(state, ZoneScopeStateJson.fromJson(ZoneScopeStateJson.toJson(state)))
    }

    /** A fresh selector, with nothing chosen, comes back empty, which is what lets it load the zones. */
    @Test
    fun aFreshState_comesBackEmpty() {
        val fresh = ZoneScopeState()

        val restored = ZoneScopeStateJson.fromJson(ZoneScopeStateJson.toJson(fresh))

        assertEquals(fresh, restored)
        assertEquals(emptyMap<Int, List<SchemaNode>>(), restored.optionsByLevel)
    }
}
