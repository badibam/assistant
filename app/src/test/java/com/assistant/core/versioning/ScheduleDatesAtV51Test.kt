package com.assistant.core.versioning

import com.assistant.core.utils.StoredSchedule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A schedule stored with its start and end dates reads again once they are taken off, wherever it
 * is stored; a schedule the reader now refuses would stop an automation or a tool without a word.
 */
class ScheduleDatesAtV51Test {

    private val dated = """{"pattern":{"type":"DailyMultiple","times":["09:00"]},"start_date":1727000000000,"end_date":null}"""

    /** Before, the dates make the schedule unreadable; after, it reads with its pattern intact. */
    @Test
    fun aDatedSchedule_readsOnceItsDatesAreTakenOff() {
        assertTrue(StoredSchedule.of(JSONObject().put("schedule", JSONObject(dated))) is StoredSchedule.Unreadable)

        val migrated = JSONObject(ScheduleDatesAtV51.schedule(dated))

        assertFalse(migrated.has("start_date"))
        assertFalse(migrated.has("end_date"))
        assertTrue(StoredSchedule.of(JSONObject().put("schedule", migrated)) is StoredSchedule.Readable)
    }

    /** A scheduled tool's config loses the dates of its schedule and nothing else. */
    @Test
    fun aScheduledToolConfig_keepsEverythingButTheDates() {
        for (tooltype in ScheduleDatesAtV51.SCHEDULED_TOOLTYPES) {
            val config = JSONObject(ScheduleDatesAtV51.toolConfig(tooltype, """{"name":"x","schedule":$dated}"""))

            assertEquals("x", config.getString("name"))
            val schedule = config.getJSONObject("schedule")
            assertEquals(listOf("pattern"), schedule.keys().asSequence().toList())
            assertEquals("09:00", schedule.getJSONObject("pattern").getJSONArray("times").getString(0))
        }
    }

    /** A config without schedule, or of a tool type that holds none, is handed back as it was. */
    @Test
    fun aConfigWithoutSchedule_isUntouched() {
        val tracking = """{"schedule":{"start_date":1}}"""
        assertEquals(tracking, ScheduleDatesAtV51.toolConfig("tracking", tracking))
        assertEquals("""{"name":"x"}""", ScheduleDatesAtV51.toolConfig("messages", """{"name":"x"}"""))
    }

    /** A backup's automations and tools are rewritten in place; an automation without schedule stays so. */
    @Test
    fun aBackup_isRewrittenInPlace() {
        val data = JSONObject()
            .put("automations", JSONArray()
                .put(JSONObject().put("id", "a").put("schedule_json", dated))
                .put(JSONObject().put("id", "b").put("schedule_json", JSONObject.NULL)))
            .put("tool_instances", JSONArray()
                .put(JSONObject().put("tooltype", "goal").put("config_json", """{"schedule":$dated}""")))

        ScheduleDatesAtV51.backup(data)

        val automations = data.getJSONArray("automations")
        assertFalse(JSONObject(automations.getJSONObject(0).getString("schedule_json")).has("start_date"))
        assertTrue(automations.getJSONObject(1).isNull("schedule_json"))
        val config = JSONObject(data.getJSONArray("tool_instances").getJSONObject(0).getString("config_json"))
        assertFalse(config.getJSONObject("schedule").has("start_date"))
    }
}
