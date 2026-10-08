package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the validation settings to their v49 form: the AI's changes to variables get their own
 * switch, which takes the value the zones' switch had, since it covered them until then.
 *
 * Shared by the database migration and the backup import.
 */
object VariableValidationAtV49 {

    const val KEY = "validate_variable_changes"

    /** [settings] of [category], with the variables' switch. */
    fun settings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.VALIDATION_CONFIG || settings.has(KEY)) return settings
        return JSONObject(settings.toString()).put(KEY, settings.optBoolean("validate_zone_config_changes", false))
    }

    /** Rewrites the backup document's validation settings in place. */
    fun backup(data: JSONObject) {
        val categories = data.optJSONArray("app_settings_categories") ?: return
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            category.put("settings", settings(category.getString("category"), JSONObject(category.getString("settings"))).toString())
        }
    }
}
