package app.treelune.core.fields

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers a running DURATION field: its start kept in the entry's state, the time counted on
 * reading, and a stop that adds what elapsed and leaves nothing behind.
 */
class RunningDurationsTest {

    private val start = 1_727_000_000_000L

    @Test
    fun start_keepsTheInstantInState_byContainer() {
        val state = RunningDurations.start(JSONObject("""{ "read": true }"""), FieldContainer.EXTRA, "pause", start)

        assertEquals(start, RunningDurations.startedAt(state, FieldContainer.EXTRA, "pause"))
        assertNull(RunningDurations.startedAt(state, FieldContainer.DATA, "pause"))
        assertTrue(state.getBoolean("read"))
        assertTrue(RunningDurations.anyRunning(state))
    }

    /** Starting twice would lose the first start silently. */
    @Test(expected = IllegalStateException::class)
    fun start_refusesAFieldAlreadyRunning() {
        val state = RunningDurations.start(null, FieldContainer.DATA, "value", start)
        RunningDurations.start(state, FieldContainer.DATA, "value", start + 1)
    }

    @Test
    fun currentValue_countsFromTheStart_onReading() {
        val state = RunningDurations.start(null, FieldContainer.DATA, "value", start)

        assertEquals(90_000L, RunningDurations.currentValue(null, state, FieldContainer.DATA, "value", start + 90_000))
        assertEquals(150_000L, RunningDurations.currentValue(60_000, state, FieldContainer.DATA, "value", start + 90_000))
        assertEquals(60_000L, RunningDurations.currentValue(60_000, null, FieldContainer.DATA, "value", start + 90_000))
    }

    @Test
    fun stop_addsTheElapsedTime_andLeavesNoKeyBehind() {
        val running = RunningDurations.start(JSONObject("""{ "read": true }"""), FieldContainer.DATA, "value", start)

        val stopped = RunningDurations.stop(running, FieldContainer.DATA, "value", 60_000, start + 90_000)

        assertEquals(150_000L, stopped.value)
        assertFalse(stopped.state.has(EntrySchemaGenerator.RUNNING_KEY))
        assertFalse(RunningDurations.anyRunning(stopped.state))
        assertTrue(stopped.state.getBoolean("read"))
    }

    /** Stopping one field leaves the others running. */
    @Test
    fun stop_leavesTheOtherFieldsRunning() {
        val both = RunningDurations.start(
            RunningDurations.start(null, FieldContainer.DATA, "value", start),
            FieldContainer.EXTRA, "pause", start + 10
        )

        val stopped = RunningDurations.stop(both, FieldContainer.DATA, "value", null, start + 100)

        assertEquals(100L, stopped.value)
        assertEquals(start + 10, RunningDurations.startedAt(stopped.state, FieldContainer.EXTRA, "pause"))
    }

    @Test(expected = IllegalStateException::class)
    fun stop_refusesAFieldNotRunning() {
        RunningDurations.stop(JSONObject(), FieldContainer.DATA, "value", null, start)
    }
}
