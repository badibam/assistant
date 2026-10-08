package app.treelune.core.utils

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers how a Messages config's recurrence is read: none, readable, or unreadable. The last two
 * must never be confused with the first, which makes the scheduler delete what is pending.
 */
class StoredScheduleTest {

    @Test
    fun noSchedule_readsAsNone() {
        assertSame(StoredSchedule.None, StoredSchedule.of(JSONObject("""{ "name": "Water" }""")))
    }

    @Test
    fun aValidSchedule_readsAsItIs() {
        val stored = StoredSchedule.of(JSONObject("""{ "schedule": { "pattern": { "type": "DailyMultiple", "times": ["09:00"] } } }"""))

        assertEquals(SchedulePattern.DailyMultiple(listOf("09:00")), (stored as StoredSchedule.Readable).schedule.pattern)
    }

    /** Keys the reader refuses -- the switch a schedule no longer has, a former camelCase name -- make it unreadable, raw JSON kept. */
    @Test
    fun aScheduleTheReaderRefuses_isUnreadableAndKept() {
        val shapes = listOf(
            """{ "pattern": { "type": "DailyMultiple", "times": ["09:00"] }, "enabled": true }""",
            """{ "pattern": { "type": "DailyMultiple", "times": ["09:00"] }, "startDate": 0 }""",
            """{ "pattern": { "type": "Hourly" } }"""
        )
        for (shape in shapes) {
            val stored = StoredSchedule.of(JSONObject("""{ "schedule": $shape }"""))
            assertTrue(shape, stored is StoredSchedule.Unreadable)
            assertEquals(JSONObject(shape).toString(), (stored as StoredSchedule.Unreadable).raw.toString())
        }
    }
}
