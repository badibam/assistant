package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the interface settings to their v58 form: a theme's palette family gives way to a hue
 * shift common to the themes. Each theme keeps one light and one dark series of colours (the
 * retro theme its cream ones), and a family becomes the shift that turns them nearest to it:
 * the retro theme's prune, its night at 300° where cream's is at 27°, a shift of 273.
 *
 * Shared by the database migration and the backup import.
 */
object UiHueShiftAtV58 {

    /** The hue shift each v57 family becomes. */
    private val SHIFTS = mapOf(
        "default" to mapOf("blue" to 0),
        "retro" to mapOf("cream" to 0, "prune" to 273),
    )

    /** [settings] of [category], the family replaced by its hue shift; an unknown one throws. */
    fun settings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI || settings.has("hue_shift") || !settings.has("theme")) return settings
        val out = JSONObject(settings.toString())
        val theme = out.getString("theme")
        val family = out.optString("${theme}_palette")
        val shift = SHIFTS[theme]?.get(family) ?: error("Unknown palette '$family' of theme '$theme'")
        SHIFTS.keys.forEach { out.remove("${it}_palette") }
        out.put("hue_shift", shift)
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
