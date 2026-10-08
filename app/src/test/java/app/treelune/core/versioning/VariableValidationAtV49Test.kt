package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The variables' validation switch starts as the zones' one, which covered them until v49. */
class VariableValidationAtV49Test {

    @Test
    fun `the variables take the zones' switch, set or not`() {
        val on = JSONObject().put("validate_zone_config_changes", true)
        assertEquals(true, VariableValidationAtV49.settings(AppSettingCategories.VALIDATION_CONFIG, on).getBoolean(VariableValidationAtV49.KEY))
        val off = JSONObject().put("validate_zone_config_changes", false)
        assertEquals(false, VariableValidationAtV49.settings(AppSettingCategories.VALIDATION_CONFIG, off).getBoolean(VariableValidationAtV49.KEY))
    }

    @Test
    fun `another category, or one already at v49, is left as it is`() {
        val format = JSONObject().put("timezone_override", "Europe/Paris")
        assertEquals(format.toString(), VariableValidationAtV49.settings(AppSettingCategories.FORMAT, format).toString())
        val done = JSONObject().put("validate_zone_config_changes", true).put(VariableValidationAtV49.KEY, false)
        assertEquals(false, VariableValidationAtV49.settings(AppSettingCategories.VALIDATION_CONFIG, done).getBoolean(VariableValidationAtV49.KEY))
    }
}
