package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Covers the v39 rewrite of tool configs to the form their declaration describes. */
class ToolConfigsAtV39Test {

    @Test
    fun theSchemaIdsGoAndTheRestStays() {
        val config = ToolConfigsAtV39.config("tracking", JSONObject(
            """{ "name": "Weight", "schema_id": "tracking_config_numeric", "data_schema_id": "tracking_data_numeric", "type": "numeric" }"""
        ))

        assertFalse(config.has("schema_id"))
        assertFalse(config.has("data_schema_id"))
        assertEquals("Weight", config.getString("name"))
        assertEquals("numeric", config.getString("type"))
    }

    @Test
    fun aMessagesToolsDelaysBecomeMilliseconds() {
        val config = ToolConfigsAtV39.config("messages", JSONObject(
            """{ "creation_horizon_days": 2, "validity_window_minutes": 60 }"""
        ))

        assertEquals(172_800_000L, config.getLong("creation_horizon"))
        assertEquals(3_600_000L, config.getLong("validity_window"))
        assertFalse(config.has("creation_horizon_days"))
        assertFalse(config.has("validity_window_minutes"))
    }

    /** A null that said "no setting" is gone, at any depth: absent says it. */
    @Test
    fun aNullIsAnAbsence() {
        val config = ToolConfigsAtV39.config("messages", JSONObject(
            """{ "schedule": { "pattern": { "type": "DailyMultiple", "times": ["09:00"] }, "end_date": null } }"""
        ))

        assertFalse(config.getJSONObject("schedule").has("end_date"))
    }
}
