package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the interface settings to their v67 form: the « One column » setting joins them, off,
 * the grids showing as they did.
 *
 * Shared by the database migration and the backup import (JsonTransformers.transformAppConfig).
 */
object UiOneColumnAtV67 {

    /** [settings] of [category], with the one-column setting. */
    fun rewrite(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI) return settings
        val out = JSONObject(settings.toString())
        if (!out.has(AppSettings.UI_ONE_COLUMN)) out.put(AppSettings.UI_ONE_COLUMN, false)
        return out
    }
}
