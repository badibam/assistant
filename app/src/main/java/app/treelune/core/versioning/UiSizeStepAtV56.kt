package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the size step to its v56 count: the former step −1 is the new 0, and every step moves
 * up by one (−1..2 become 0..3), so that each keeps the size it gave. A pixel theme's scale went
 * from the density rounded plus the step to one less than that (RetroGrid), the same on every
 * screen.
 *
 * Shared by the database migration and the backup import.
 */
object UiSizeStepAtV56 {

    /** [settings] of [category], their size step moved up by one. */
    fun settings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI || !settings.has(AppSettings.UI_SIZE_STEP)) return settings
        val out = JSONObject(settings.toString())
        out.put(AppSettings.UI_SIZE_STEP, out.getInt(AppSettings.UI_SIZE_STEP) + 1)
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
