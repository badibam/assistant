package app.treelune.tools.tracking

import app.treelune.core.database.entities.ToolDataEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers "one activity at a time in a tracking tool", kept by the service after every write so
 * that a start from the AI obeys it as a start from the tool's buttons does.
 */
class TrackingStopwatchTest {

    private fun activity(id: String, value: Long?, runningSince: Long?) = ToolDataEntity(
        id = id,
        toolInstanceId = "tracking-1",
        tooltype = "tracking",
        timestamp = 0,
        name = id,
        data = JSONObject().apply { value?.let { put("value", it) } }.toString(),
        state = runningSince?.let {
            JSONObject().put("running", JSONObject().put("data", JSONObject().put("value", it))).toString()
        },
        createdAt = 0,
        updatedAt = 0
    )

    /** The one running stops at the instant the new one started, its time added to its value. */
    @Test
    fun startingOneStopsTheOneRunning() {
        val entries = listOf(activity("reading", 60_000, 1_000), activity("sport", null, 11_000))

        val settled = TrackingStopwatch.settle(entries, "sport")

        assertEquals(1, settled.size)
        val reading = settled.single()
        assertEquals("reading", reading.id)
        assertEquals(70_000L, JSONObject(reading.data).getLong("value"))
        assertNull("its start leaves the state", reading.state)
    }

    /** A write that does not start anything touches nothing. */
    @Test
    fun aWriteThatStartsNothingLeavesTheOthers() {
        val entries = listOf(activity("reading", 0, 1_000), activity("note", 5, null))

        assertTrue(TrackingStopwatch.settle(entries, "note").isEmpty())
    }

    /** Only the other running entries are stopped; stopped ones are left as they are. */
    @Test
    fun onlyTheRunningOnesAreStopped() {
        val entries = listOf(activity("done", 30_000, null), activity("sport", null, 5_000))

        assertTrue(TrackingStopwatch.settle(entries, "sport").isEmpty())
    }
}
