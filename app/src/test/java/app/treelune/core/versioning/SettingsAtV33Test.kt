package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the rewrite the 32 -> 33 migration and the backup import both apply to the format,
 * validation and main screen settings: required keys filled, undeclared keys gone, optional
 * format keys left as they were.
 */
class SettingsAtV33Test {

    private fun keys(json: JSONObject) = json.keys().asSequence().toSet()

    /** Required format keys come back with the values the reads used to fall back to. */
    @Test
    fun anEmptyFormat_getsTheRequiredKeys() {
        val rewritten = SettingsAtV33.rewrite("format", JSONObject())!!

        assertEquals("monday", rewritten.getString("week_start_day"))
        assertEquals(4, rewritten.getInt("day_start_hour"))
        assertEquals(":", rewritten.getString("time_separator"))
        assertEquals(12, rewritten.getJSONObject("relative_label_limits").getInt("hour_limit"))
        assertEquals(3, rewritten.getJSONObject("relative_label_limits").getInt("year_limit"))
    }

    /** What the user set stays, including a half-filled limits object. */
    @Test
    fun storedFormatValues_areKept() {
        val stored = JSONObject()
            .put("week_start_day", "sunday")
            .put("day_start_hour", 6)
            .put("timezone_override", "Europe/Paris")
            .put("relative_label_limits", JSONObject().put("day_limit", 3))

        val rewritten = SettingsAtV33.rewrite("format", stored)!!

        assertEquals("sunday", rewritten.getString("week_start_day"))
        assertEquals(6, rewritten.getInt("day_start_hour"))
        assertEquals("Europe/Paris", rewritten.getString("timezone_override"))
        assertEquals(3, rewritten.getJSONObject("relative_label_limits").getInt("day_limit"))
        assertEquals(4, rewritten.getJSONObject("relative_label_limits").getInt("week_limit"))
    }

    /** An absent override stays absent: that is how it says "follow the phone". */
    @Test
    fun absentOverrides_stayAbsent() {
        val rewritten = SettingsAtV33.rewrite("format", JSONObject())!!

        assertFalse(rewritten.has("timezone_override"))
        assertFalse(rewritten.has("use_24_hour_format"))
    }

    /** The format schema refuses undeclared keys, so they go. */
    @Test
    fun undeclaredFormatKeys_areDropped() {
        val rewritten = SettingsAtV33.rewrite("format", JSONObject().put("zone_display_mode", "grid"))!!

        assertFalse(rewritten.has("zone_display_mode"))
    }

    /** A 24h choice stored as a string becomes the boolean the reads took it for. */
    @Test
    fun a24HourChoiceStoredAsText_becomesABoolean() {
        val rewritten = SettingsAtV33.rewrite("format", JSONObject().put("use_24_hour_format", "false"))!!

        assertEquals(false, rewritten.get("use_24_hour_format"))
    }

    /** A missing validation flag was read as false, and is written as false. */
    @Test
    fun missingValidationFlags_areWrittenFalse() {
        val rewritten = SettingsAtV33.rewrite("validation_config", JSONObject().put("validate_tool_data_changes", true))!!

        assertEquals(
            setOf("validate_app_config_changes", "validate_zone_config_changes", "validate_tool_config_changes", "validate_tool_data_changes"),
            keys(rewritten)
        )
        assertEquals(true, rewritten.getBoolean("validate_tool_data_changes"))
        assertEquals(false, rewritten.getBoolean("validate_app_config_changes"))
    }

    /** The main screen keeps its zone groups and nothing else. */
    @Test
    fun mainScreen_keepsItsZoneGroupsOnly() {
        val stored = JSONObject().put("zone_groups", org.json.JSONArray().put("Health")).put("zones_per_row", 2)

        val rewritten = SettingsAtV33.rewrite("main_screen", stored)!!

        assertEquals(setOf("zone_groups"), keys(rewritten))
        assertEquals("Health", rewritten.getJSONArray("zone_groups").getString(0))
    }

    /** ai_limits went through its own rewrite at v32 and is not touched here. */
    @Test
    fun otherCategories_areLeftAlone() {
        assertNull(SettingsAtV33.rewrite("ai_limits", JSONObject()))
    }
}
