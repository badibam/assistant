package com.assistant.core.themes

/** Whether a palette is drawn on a light ground with dark ink, or the other way round. */
enum class PaletteMode { LIGHT, DARK }

/**
 * The mode the user asks for: one of the two, or the phone's, which follows Android's dark theme
 * setting as it changes.
 */
enum class AppearanceMode { LIGHT, DARK, SYSTEM }

/**
 * One palette of a theme: a [family] (the colours' character, "prune", "cream") in one [mode].
 * Every family of a theme exists in both modes, so that any family can be shown in any mode.
 *
 * @property id Unique across the themes, "<theme>_<family>_<mode>": what a theme's colours are looked up by
 */
data class ThemePalette(
    val id: String,
    val family: String,
    val mode: PaletteMode
) {
    companion object {
        /** The palette of [family] in [mode] for [themeId], under its conventional id. */
        fun of(themeId: String, family: String, mode: PaletteMode): ThemePalette =
            ThemePalette("${themeId}_${family}_${mode.name.lowercase()}", family, mode)
    }
}
