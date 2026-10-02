package com.assistant.core.themes

import com.assistant.core.config.AppSettings
import org.json.JSONObject

/**
 * The look the interface settings choose (AppSettings, category UI): a theme, the family of its
 * palettes, the mode, the size step.
 */
data class Appearance(
    val theme: String,
    val family: String,
    val mode: AppearanceMode,
    val sizeStep: Int
) {
    companion object {
        /**
         * The look [settings] choose, the interface settings as stored or as being edited; null
         * while a choice is missing, as when the theme has just changed and its family is not
         * chosen yet.
         */
        fun from(settings: JSONObject): Appearance? {
            val theme = settings.optString(AppSettings.UI_THEME).ifEmpty { return null }
            val family = settings.optString(AppSettings.uiPaletteKey(theme)).ifEmpty { return null }
            val mode = settings.optString(AppSettings.UI_MODE).ifEmpty { return null }
            if (!settings.has(AppSettings.UI_SIZE_STEP)) return null
            return Appearance(theme, family, AppearanceMode.valueOf(mode), settings.getInt(AppSettings.UI_SIZE_STEP))
        }
    }
}
