package com.assistant.core.versioning

import com.assistant.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the format settings to their v42 form (docs/DATA.md): a
 * null that said "follow the phone" (locale_override, timezone_override, use_24_hour_format,
 * date_format_pattern) becomes an absence, which the format declaration reads the same way.
 *
 * Shared by the database migration and the backup import.
 */
object FormatNullsAtV42 {

    /** [settings] of [category], without the nulls a format row carries. */
    fun settings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.FORMAT) return settings
        val next = JSONObject(settings.toString())
        settings.keys().forEach { key -> if (settings.isNull(key)) next.remove(key) }
        return next
    }

    /** Rewrites the backup document's format settings in place. */
    fun backup(data: JSONObject) {
        val categories = data.optJSONArray("app_settings_categories") ?: return
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            val name = category.getString("category")
            category.put("settings", settings(name, JSONObject(category.getString("settings"))).toString())
        }
    }
}
