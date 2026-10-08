package app.treelune.tools.tracking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers how a tracking tool's shortcuts are read and kept, and what a quick entry writes.
 */
class TrackingConfigTest {

    private val config = JSONObject("""{ "type": "numeric", "units": ["g", "kg"],
        "items": [ { "name": "Apple", "value": 150, "unit": "g" }, { "name": "Water" } ] }""")

    @Test
    fun shortcuts_areReadWithTheirValueAndUnit() {
        assertEquals(
            listOf(TrackingShortcut("Apple", 150, "g"), TrackingShortcut("Water")),
            TrackingConfig.shortcuts(config)
        )
        assertEquals(listOf("g", "kg"), TrackingConfig.units(config))
    }

    /** A unit typed with an entry joins the units, last, the rest of the config as it was. */
    @Test
    fun aNewUnit_joinsTheUnits() {
        val grown = TrackingToolType.configWithOptionsAdded(config, "unit", listOf("lb"))

        assertEquals(listOf("g", "kg", "lb"), TrackingConfig.units(grown))
        assertEquals(TrackingConfig.shortcuts(config), TrackingConfig.shortcuts(grown))
        assertEquals(listOf("km"), TrackingConfig.units(TrackingToolType.configWithOptionsAdded(JSONObject("""{ "type": "numeric" }"""), "unit", listOf("km"))))
    }

    @Test
    fun aNewShortcut_goesLast_andTheConfigHandedInStaysAsItWas() {
        val grown = TrackingConfig.withShortcut(config, TrackingShortcut("Bread", 50, "g"))

        assertEquals("Bread", TrackingConfig.shortcuts(grown).last().name)
        assertEquals(2, TrackingConfig.shortcuts(config).size)
    }

    /** A counter step is a positive amount; the buttons give the sign. */
    @Test
    fun counterStep_isOneWithoutValue_andPositive() {
        assertEquals(1, TrackingConfig.counterStep(TrackingShortcut("Glass")))
        assertEquals(2, TrackingConfig.counterStep(TrackingShortcut("Glass", -2)))
    }

    /** An occurrence, or a timer before it stops, writes no value at all rather than a null. */
    @Test
    fun entryData_leavesOutWhatIsAbsent() {
        assertEquals(0, TrackingConfig.entryData(null, null).length())
        assertEquals("""{"value":150,"unit":"g"}""".length, TrackingConfig.entryData(150, "g").toString().length)
    }
}
