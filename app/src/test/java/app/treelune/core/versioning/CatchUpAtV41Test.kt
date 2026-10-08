package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The catch-up settings of an automation at v41: an explicit choice, a limited window in
 * milliseconds, and nothing for an automation without schedule. The database migration and the
 * backup import both read their rows through here.
 */
class CatchUpAtV41Test {

    /** A scheduled automation with no window had chosen "no limit": the screen refused anything else. */
    @Test
    fun aScheduledAutomationWithoutWindowIsUnlimited() {
        assertEquals("unlimited" to null, CatchUpAtV41.of(scheduled = true, windowMinutes = null))
    }

    @Test
    fun aWindowInMinutesBecomesALimitedWindowInMilliseconds() {
        assertEquals("limited" to 3 * 3_600_000L, CatchUpAtV41.of(scheduled = true, windowMinutes = 180))
    }

    /** Nothing to catch up without a schedule, whatever an old row carries. */
    @Test
    fun anAutomationWithoutScheduleHasNoCatchUp() {
        assertEquals(null to null, CatchUpAtV41.of(scheduled = false, windowMinutes = 60))
    }

    /** A backup older than the catch-up settings is read as LegacyCatchUp: no limit, latest run only. */
    @Test
    fun aBackupWithoutWindowTakesTheLegacyReading() {
        val automation = JSONObject().put("schedule_json", "{}").put("dismiss_older_instances", false)
        val data = JSONObject().put("automations", JSONArray().put(automation))

        CatchUpAtV41.backup(data)

        assertEquals("unlimited", automation.getString("catch_up"))
        assertFalse(automation.has("catch_up_window"))
        assertEquals(true, automation.getBoolean("dismiss_older_instances"))
    }

    @Test
    fun aBackupWindowIsConvertedAndItsOldKeyLeaves() {
        val automation = JSONObject().put("schedule_json", "{}").put("catch_up_window_minutes", 30).put("dismiss_older_instances", false)
        CatchUpAtV41.backup(JSONObject().put("automations", JSONArray().put(automation)))

        assertEquals(1_800_000L, automation.getLong("catch_up_window"))
        assertFalse(automation.has("catch_up_window_minutes"))
        assertEquals(false, automation.getBoolean("dismiss_older_instances"))
    }
}
