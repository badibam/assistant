package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The validation settings at v69 (docs/design/validation.md): nothing that was protected stops
 * being so, what covered the whole app goes, the dead settings leave the tools.
 */
class ValidationAtV69Test {

    private fun v67(app: Boolean = false, zones: Boolean = false, data: Boolean = false) = JSONObject()
        .put("validate_app_config_changes", app).put("validate_zone_config_changes", zones)
        .put("validate_tool_config_changes", true).put("validate_tool_data_changes", data).put("validate_variable_changes", true)

    @Test
    fun theAppKeepsOneSwitchOnWhenItsSettingsOrTheZonesWereValidated() {
        val category = AppSettingCategories.VALIDATION_CONFIG
        assertEquals("{\"validate_app\":false}", ValidationAtV69.appSettings(category, v67(data = true)).toString())
        assertTrue(ValidationAtV69.appSettings(category, v67(app = true)).getBoolean("validate_app"))
        assertTrue(ValidationAtV69.appSettings(category, v67(zones = true)).getBoolean("validate_app"))
    }

    @Test
    fun otherCategoriesAndSettingsAlreadyAtV69StayAsTheyAre() {
        val format = JSONObject().put("validate_app_config_changes", true)
        assertEquals(format.toString(), ValidationAtV69.appSettings(AppSettingCategories.FORMAT, format).toString())
        val done = JSONObject().put("validate_app", true)
        assertEquals(done.toString(), ValidationAtV69.appSettings(AppSettingCategories.VALIDATION_CONFIG, done).toString())
    }

    @Test
    fun aToolLosesItsConfigValidationAndManagementAndKeepsItsDataProtection() {
        val config = JSONObject().put("name", "Poids").put("validate_config", true).put("management", "ai").put("validate_data", true)
        val after = ValidationAtV69.toolConfig(config)
        assertFalse(after.has("validate_config"))
        assertFalse(after.has("management"))
        assertTrue(after.getBoolean("validate_data"))
        assertEquals("Poids", after.getString("name"))
    }

    @Test
    fun aBackupProtectsTheZonesWhoseToolsValidatedTheirConfigAndSplitsTheSessionSwitch() {
        val data = JSONObject()
            .put("tool_instances", JSONArray()
                .put(JSONObject().put("zone_id", "z1").put("config_json", JSONObject().put("validate_config", true).put("management", "manual").toString()))
                .put(JSONObject().put("zone_id", "z2").put("config_json", JSONObject().put("validate_config", false).toString())))
            .put("zones", JSONArray().put(JSONObject().put("id", "z1")).put(JSONObject().put("id", "z2")))
            .put("ai_sessions", JSONArray().put(JSONObject().put("require_validation", true)).put(JSONObject().put("require_validation", false)))

        ValidationAtV69.backup(data)

        val zones = data.getJSONArray("zones")
        assertTrue(zones.getJSONObject(0).getBoolean("validate"))
        assertFalse(zones.getJSONObject(1).getBoolean("validate"))
        val config = JSONObject(data.getJSONArray("tool_instances").getJSONObject(0).getString("config_json"))
        assertFalse(config.has("validate_config") || config.has("management"))
        val on = data.getJSONArray("ai_sessions").getJSONObject(0)
        assertFalse(on.has("require_validation"))
        assertTrue(on.getBoolean("validate_app") && on.getBoolean("validate_zones") && on.getBoolean("validate_data"))
        val off = data.getJSONArray("ai_sessions").getJSONObject(1)
        assertFalse(off.getBoolean("validate_app") || off.getBoolean("validate_zones") || off.getBoolean("validate_data"))
    }
}
