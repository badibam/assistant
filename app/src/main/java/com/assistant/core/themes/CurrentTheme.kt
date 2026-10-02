package com.assistant.core.themes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The look the app is shown in: a theme, one of its palette families, a mode, a size step. UI.*
 * calls go to [current]; the palette shown is derived from the family and the mode, the phone's
 * dark theme setting deciding when the mode is SYSTEM.
 *
 * Everything here is Compose state: a screen that reads it is drawn again when it changes, which
 * is how the interface settings show a choice before it is saved (Appearance).
 */
object CurrentTheme {

    /** The theme every UI.* call is drawn by. */
    var current: ThemeContract by mutableStateOf(ThemeScanner.getDefaultTheme())
        private set

    /** [current]'s id. */
    var themeId: String by mutableStateOf(ThemeScanner.scanForThemes().entries.first { it.value === ThemeScanner.getDefaultTheme() }.key)
        private set

    /** The family of [current]'s palettes shown. */
    var family: String by mutableStateOf(ThemeScanner.getDefaultTheme().paletteFamilies().first())
        private set

    /** The mode asked for. */
    var mode: AppearanceMode by mutableStateOf(AppearanceMode.SYSTEM)
        private set

    /** Whether the phone is in its dark theme: set by the activity, read when [mode] is SYSTEM. */
    var systemDark: Boolean by mutableStateOf(true)

    /**
     * How many whole steps the interface's size is moved by: a pixel theme changes its integer
     * factor by that much, so everything grows together and nothing falls between two pixels.
     */
    var sizeStep: Int by mutableStateOf(0)
        private set

    /** The mode shown, the phone's resolved. */
    val paletteMode: PaletteMode
        get() = when (mode) {
            AppearanceMode.LIGHT -> PaletteMode.LIGHT
            AppearanceMode.DARK -> PaletteMode.DARK
            AppearanceMode.SYSTEM -> if (systemDark) PaletteMode.DARK else PaletteMode.LIGHT
        }

    /** Whether the palette shown is a dark one. */
    val isDark: Boolean
        get() = paletteMode == PaletteMode.DARK

    /** The id of the palette shown, which the theme looks its colours up by. */
    val currentPaletteId: String
        get() = current.palette(family, paletteMode).id

    /**
     * Shows the app in [appearance]. A theme or a family that does not exist is a bug: the
     * settings' schema offers only those the themes declare.
     */
    fun apply(appearance: Appearance) {
        val theme = ThemeScanner.getTheme(appearance.theme) ?: error("No theme '${appearance.theme}'")
        require(appearance.family in theme.paletteFamilies()) { "The theme '${appearance.theme}' has no palette '${appearance.family}'" }
        current = theme
        themeId = appearance.theme
        family = appearance.family
        mode = appearance.mode
        sizeStep = appearance.sizeStep
    }

    /** Every theme, by id. */
    fun getAvailableThemes(): Map<String, ThemeContract> = ThemeScanner.scanForThemes()

    /** The Material colours of the palette shown, for what Material still draws at the app's root. */
    fun getCurrentColorScheme(): androidx.compose.material3.ColorScheme = current.getColorScheme(currentPaletteId)
}
