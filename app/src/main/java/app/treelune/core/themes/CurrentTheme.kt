package app.treelune.core.themes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The look the app is shown in: a theme, a mode, a hue shift, a size step. UI.* calls go to
 * [current], which draws in its light or dark colours turned by [hueShift], the phone's dark
 * theme setting deciding when the mode is SYSTEM.
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

    /** Degrees the theme's colours are turned round the hue circle (Appearance.hueShift). */
    var hueShift: Int by mutableStateOf(0)
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

    /** Whether the grids show in one column (Appearance.oneColumn). */
    var oneColumn: Boolean by mutableStateOf(false)
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

    /** Why a look is applied, written to the log with it. */
    enum class Reason {
        /** Read from the database: at startup, and after the settings are saved. */
        STORED,
        /** Chosen on the interface settings, not saved yet. */
        PREVIEW,
        /** The interface settings left unsaved: the stored look back. */
        LEFT_UNSAVED
    }

    /**
     * Shows the app in [appearance]. A theme that does not exist is a bug: the settings' schema
     * offers only those ThemeScanner knows.
     *
     * Logged, so which call put a theme on the screen can be read afterwards; a preview only when
     * it changes the theme, not at each move of the hue slider.
     */
    fun apply(appearance: Appearance, reason: Reason) {
        val theme = ThemeScanner.getTheme(appearance.theme) ?: error("No theme '${appearance.theme}'")
        if (reason != Reason.PREVIEW || appearance.theme != themeId) {
            app.treelune.core.utils.LogManager.ui("Theme applied ($reason): $appearance, was $themeId")
        }
        current = theme
        themeId = appearance.theme
        hueShift = appearance.hueShift
        mode = appearance.mode
        sizeStep = appearance.sizeStep
        oneColumn = appearance.oneColumn
    }

    /** Every theme, by id. */
    fun getAvailableThemes(): Map<String, ThemeContract> = ThemeScanner.scanForThemes()

    /** The Material colours shown, for what Material still draws at the app's root. */
    fun getCurrentColorScheme(): androidx.compose.material3.ColorScheme = current.getColorScheme(paletteMode, hueShift)
}
