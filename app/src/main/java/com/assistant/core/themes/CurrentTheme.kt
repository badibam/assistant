package com.assistant.core.themes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * CurrentTheme - Global theme manager with discovery pattern + palette system
 * 
 * Central point for current theme and palette management:
 * - UI.* → CurrentTheme.current.* → ThemeScanner.getTheme().*
 * - Transparent theme and palette switching for the app
 * - One active theme + one active palette at a time
 * - Automatic discovery of available themes and their palettes
 */
object CurrentTheme {
    
    /**
     * Currently active theme
     * Default: "default" theme via ThemeScanner
     * Can be changed at runtime via switchTheme()
     */
    var current: ThemeContract by mutableStateOf(ThemeScanner.getDefaultTheme())
        private set
    
    /**
     * Currently active palette ID
     * Default: first DARK palette of current theme
     * Can be changed at runtime via switchPalette()
     */
    var currentPaletteId: String by mutableStateOf(getDefaultPaletteId())
        private set
    
    /**
     * Changes current theme by ID
     * All UI components will be automatically re-rendered
     * 
     * @param themeId The theme identifier to activate
     * @return true if theme was changed, false if theme not found
     */
    fun switchTheme(themeId: String): Boolean {
        val theme = ThemeScanner.getTheme(themeId)
        return if (theme != null) {
            current = theme
            true
        } else {
            false
        }
    }
    
    
    /**
     * Gets the list of available themes
     * 
     * @return Map<themeId, ThemeContract> of all discovered themes
     */
    fun getAvailableThemes(): Map<String, ThemeContract> {
        return ThemeScanner.scanForThemes()
    }
    
    /**
     * Gets the list of available theme IDs
     * Useful for theme selection interfaces
     * 
     * @return List of theme identifiers
     */
    fun getAvailableThemeIds(): List<String> {
        return ThemeScanner.getAvailableThemeIds()
    }
    
    /**
     * Gets the currently active theme ID
     * 
     * @return Current theme ID or "unknown" if not found
     */
    fun getCurrentThemeId(): String {
        return getAvailableThemes()
            .entries
            .firstOrNull { it.value === current }
            ?.key ?: "unknown"
    }
    
    // =====================================
    // PALETTE MANAGEMENT
    // =====================================
    
    /**
     * Changes current palette by ID
     * Palette must belong to current theme
     * 
     * @param paletteId The palette identifier to activate
     * @return true if palette was changed, false if palette not found or doesn't belong to current theme
     */
    fun switchPalette(paletteId: String): Boolean {
        val availablePalettes = current.getAllPalettes().map { it.id }
        return if (paletteId in availablePalettes) {
            currentPaletteId = paletteId
            true
        } else {
            false
        }
    }
    
    /**
     * Gets all palettes available for current theme
     * 
     * @return List of all palettes (base + custom) for current theme
     */
    fun getCurrentThemePalettes(): List<ThemePalette> {
        return current.getAllPalettes()
    }
    
    /**
     * Gets base palettes for current theme
     * 
     * @return List of base palettes (LIGHT, DARK) for current theme
     */
    fun getCurrentThemeBasePalettes(): List<ThemePalette> {
        return current.getBasePalettes()
    }
    
    /**
     * Gets custom palettes for current theme
     * 
     * @return List of custom palettes for current theme
     */
    fun getCurrentThemeCustomPalettes(): List<ThemePalette> {
        return current.getCustomPalettes()
    }
    
    /**
     * Gets the currently active ColorScheme
     * Based on current theme + current palette
     * 
     * @return ColorScheme for current theme and palette
     */
    fun getCurrentColorScheme(): androidx.compose.material3.ColorScheme {
        return current.getColorScheme(currentPaletteId)
    }
    
    /**
     * Gets the currently active palette info
     * 
     * @return ThemePalette info for current palette, or null if not found
     */
    fun getCurrentPalette(): ThemePalette? {
        return current.getAllPalettes().firstOrNull { it.id == currentPaletteId }
    }
    
    /**
     * Helper to get default palette ID for theme
     * Used for initialization - defaults to DARK as specified
     */
    private fun getDefaultPaletteId(): String {
        return ThemeScanner.getDefaultTheme()
            .getBasePalettes()
            .firstOrNull { it.base == BasePalette.DARK }
            ?.id ?: "default_dark"
    }
    
    /**
     * Resets palette to default when theme changes
     * Called internally when switching themes
     */
    private fun resetPaletteToDefault() {
        val darkPalette = current.getBasePalettes()
            .firstOrNull { it.base == BasePalette.DARK }
        currentPaletteId = darkPalette?.id ?: current.getAllPalettes().firstOrNull()?.id ?: "default_dark"
    }
}