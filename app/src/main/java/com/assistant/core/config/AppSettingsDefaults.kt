package com.assistant.core.config

import android.content.Context
import com.assistant.core.ai.domain.AILimitsConfig
import com.assistant.core.database.entities.AppSettingCategories
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
        AppSettingCategories.UI
    )

    /** The default settings JSON of [category]; format takes the phone's 24h and date habits */
    fun forCategory(category: String, context: Context): String = when (category) {
        AppSettingCategories.FORMAT -> FormatDefaults.toJson(context)
        AppSettingCategories.AI_LIMITS -> AILimitsConfig.default().toSettingsJson()
        AppSettingCategories.VALIDATION_CONFIG -> ValidationConfig().toSettingsJson()
        AppSettingCategories.MAIN_SCREEN -> JSONObject().put("zone_groups", JSONArray()).toString()
        AppSettingCategories.DEMO -> JSONObject().put(com.assistant.core.demo.DemoStartup.INSTALL_ON_UPDATE, true).toString()
        AppSettingCategories.UI -> JSONObject().put(AppSettings.UI_SOUNDS, true).toString()
        else -> throw IllegalArgumentException("No defaults for settings category '$category'")
    }
}
