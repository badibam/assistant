package com.assistant.core.versioning

import com.assistant.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the interface settings to their v57 form: the appearance, one palette's id, becomes three
 * choices — the theme, its palette family (under the theme's own key), the mode — and each former
 * palette becomes the family it was drawn from, in the mode it had. The palettes the retro theme
 * held as trials at v56 go to the families they became.
 *
 * Shared by the database migration and the backup import.
 */
object UiThemeModeAtV57 {

    /** Every appearance a v56 database can hold: theme, family, mode. */
    private val APPEARANCES = mapOf(
        "default_dark" to Triple("default", "blue", "DARK"),
        "default_light" to Triple("default", "blue", "LIGHT"),
        "retro_dark" to Triple("retro", "prune", "DARK"),
        "retro_light" to Triple("retro", "prune", "LIGHT"),
        "retro_cream" to Triple("retro", "cream", "LIGHT"),
        "retro_mid" to Triple("retro", "prune", "LIGHT"),
    )

    /** [settings] of [category], the appearance as theme, family and mode; an unknown one throws. */
    fun settings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI || !settings.has(UiAppearanceAtV55.KEY)) return settings
        val out = JSONObject(settings.toString())
        val former = out.remove(UiAppearanceAtV55.KEY) as String
        val (theme, family, mode) = APPEARANCES[former] ?: error("Unknown appearance '$former'")
        out.put("theme", theme)
        out.put("${theme}_palette", family)
        out.put("mode", mode)
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
