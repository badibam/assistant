package app.treelune.core.config

import android.content.Context
import app.treelune.core.ai.domain.AILimitsConfig
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONArray
import org.json.JSONObject

/**
 * What each settings category holds before anyone changes it.
 *
 * The one place a category's defaults are written from: on the first read of a category that
 * has no row yet, and after a reset. Reading never falls back to them -- a stored category is
 * read as stored, and a required key it lacks is an error.
 */
object AppSettingsDefaults {

    val CATEGORIES = listOf(
        AppSettingCategories.FORMAT,
        AppSettingCategories.AI_LIMITS,
        AppSettingCategories.VALIDATION_CONFIG,
        AppSettingCategories.MAIN_SCREEN,
        AppSettingCategories.DEMO,
        AppSettingCategories.UI,
        AppSettingCategories.EXTERNAL_ACCESS
    )

    /** The default settings JSON of [category]; format takes the phone's 24h and date habits */
    fun forCategory(category: String, context: Context): String = when (category) {
        AppSettingCategories.FORMAT -> FormatDefaults.toJson(context)
        AppSettingCategories.AI_LIMITS -> AILimitsConfig.default().toSettingsJson()
        AppSettingCategories.VALIDATION_CONFIG -> ValidationConfig().toSettingsJson()
        AppSettingCategories.MAIN_SCREEN -> JSONObject().put("zone_groups", JSONArray()).toString()
        AppSettingCategories.DEMO -> JSONObject().put(app.treelune.core.demo.DemoStartup.INSTALL_ON_UPDATE, true).toString()
        // The default theme in its own colours, light or dark as the phone is, at its own size
        AppSettingCategories.UI -> JSONObject().put(AppSettings.UI_SOUNDS, true)
            .put(AppSettings.UI_THEME, DEFAULT_THEME)
            .put(AppSettings.UI_MODE, app.treelune.core.themes.AppearanceMode.SYSTEM.name)
            .put(AppSettings.UI_HUE_SHIFT, 0)
            .put(AppSettings.UI_SIZE_STEP, 0).toString()
        // No relay until the user gives one
        AppSettingCategories.EXTERNAL_ACCESS -> JSONObject().toString()
        else -> throw IllegalArgumentException("No defaults for settings category '$category'")
    }

    private const val DEFAULT_THEME = "default"
}
