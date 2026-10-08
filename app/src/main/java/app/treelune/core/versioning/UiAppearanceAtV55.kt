package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the interface settings to their v55 form: the appearance (a theme's palette) and the
 * size step join the sounds, at what the app showed until then: the default theme's dark palette,
 * at its own size.
 *
 * Shared by the database migration and the backup import.
 */
object UiAppearanceAtV55 {

    /** The appearance's key at v55, gone at v57 (UiThemeModeAtV57). */
    const val KEY = "appearance"
    const val APPEARANCE = "default_dark"
    const val SIZE_STEP = 0

    /** [settings] of [category], with the appearance and the size step. */
    fun settings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI) return settings
        val out = JSONObject(settings.toString())
        if (!out.has(KEY)) out.put(KEY, APPEARANCE)
        if (!out.has(AppSettings.UI_SIZE_STEP)) out.put(AppSettings.UI_SIZE_STEP, SIZE_STEP)
        return out
    }

    /** Rewrites the backup document's interface settings in place. */
    fun backup(data: JSONObject) {
        val categories = data.optJSONArray("app_settings_categories") ?: return
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            category.put("settings", settings(category.getString("category"), JSONObject(category.getString("settings"))).toString())
        }
    }
}
