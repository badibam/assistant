package com.assistant.core.themes

import com.assistant.core.config.AppSettings
import org.json.JSONObject

/** Whether a theme's colours are drawn on a light ground with dark ink, or the other way round. */
enum class PaletteMode { LIGHT, DARK }

/**
 * The mode the user asks for: one of the two, or the phone's, which follows Android's dark theme
 * setting as it changes.
 */
enum class AppearanceMode { LIGHT, DARK, SYSTEM }

/**
 * The look the interface settings choose (AppSettings, category UI): a theme, the mode, how far
 * round the hue circle its colours are turned, the size step.
 *
 * @property hueShift Degrees, 0 to 359, added to the hue of every colour of the theme but those
 *   that say something whatever the hue (states, tags): 0 shows the theme's own colours
 */
data class Appearance(
    val theme: String,
    val mode: AppearanceMode,
    val hueShift: Int,
    val sizeStep: Int
) {
    companion object {
        /** The look [settings] choose, the interface settings as stored or as being edited; null while a choice is missing. */
        fun from(settings: JSONObject): Appearance? {
            val theme = settings.optString(AppSettings.UI_THEME).ifEmpty { return null }
            val mode = settings.optString(AppSettings.UI_MODE).ifEmpty { return null }
            if (!settings.has(AppSettings.UI_HUE_SHIFT) || !settings.has(AppSettings.UI_SIZE_STEP)) return null
            return Appearance(theme, AppearanceMode.valueOf(mode), settings.getInt(AppSettings.UI_HUE_SHIFT), settings.getInt(AppSettings.UI_SIZE_STEP))
        }
    }
}
