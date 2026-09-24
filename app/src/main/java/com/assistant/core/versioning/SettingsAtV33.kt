package com.assistant.core.versioning

import com.assistant.core.database.entities.AppSettingCategories
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the format, validation and main screen settings to their v33 form.
 *
 * From v33 these categories are read strictly: a required key that is missing is an error
 * instead of a value written at the read site, and a format change is checked against the
 * whole stored object, which the format schema closes to undeclared keys. So each stored
 * category gets the required keys it lacks, with the values the reads used to fall back to,
 * and loses the keys nothing declares. Optional format keys (the overrides, the date pattern,
 * the 24h choice) stay absent when absent: that means "follow the phone". A 24h choice stored
 * as the string "true" or "false" becomes the boolean the reads took it for.
 *
 * This is the single place that says so, shared by the database migration and the backup
 * import. The values are written as they stood at v33, not read from the current defaults.
 */
object SettingsAtV33 {

    /** @return the settings of [category] in their v33 form, or null for a category this does not touch */
    fun rewrite(category: String, settings: JSONObject): JSONObject? = when (category) {
        AppSettingCategories.FORMAT -> format(settings)
        AppSettingCategories.VALIDATION_CONFIG -> validation(settings)
        AppSettingCategories.MAIN_SCREEN -> mainScreen(settings)
        else -> null
    }

    private fun format(settings: JSONObject): JSONObject = JSONObject().apply {
        put("week_start_day", settings.optString("week_start_day").ifEmpty { "monday" })
        put("day_start_hour", if (settings.has("day_start_hour")) settings.getInt("day_start_hour") else 4)
        put("time_separator", settings.optString("time_separator").ifEmpty { ":" })

        val limits = settings.optJSONObject("relative_label_limits") ?: JSONObject()
        put("relative_label_limits", JSONObject().apply {
            put("hour_limit", if (limits.has("hour_limit")) limits.getInt("hour_limit") else 12)
            put("day_limit", if (limits.has("day_limit")) limits.getInt("day_limit") else 7)
            put("week_limit", if (limits.has("week_limit")) limits.getInt("week_limit") else 4)
            put("month_limit", if (limits.has("month_limit")) limits.getInt("month_limit") else 6)
            put("year_limit", if (limits.has("year_limit")) limits.getInt("year_limit") else 3)
        })

        for (key in listOf("locale_override", "timezone_override", "date_format_pattern")) {
            if (settings.has(key)) put(key, settings.get(key))
        }
        if (settings.has("use_24_hour_format")) {
            put("use_24_hour_format", when (val value = settings.get("use_24_hour_format")) {
                "true" -> true
                "false" -> false
                else -> value
            })
        }
    }

    private fun validation(settings: JSONObject): JSONObject = JSONObject().apply {
        for (key in listOf(
            "validate_app_config_changes",
            "validate_zone_config_changes",
            "validate_tool_config_changes",
            "validate_tool_data_changes"
        )) {
            put(key, if (settings.has(key)) settings.getBoolean(key) else false)
        }
    }

    private fun mainScreen(settings: JSONObject): JSONObject = JSONObject().apply {
        put("zone_groups", settings.optJSONArray("zone_groups") ?: JSONArray())
    }
}
