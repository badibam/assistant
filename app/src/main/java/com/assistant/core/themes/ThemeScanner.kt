package com.assistant.core.themes

import com.assistant.themes.default.DefaultTheme
import com.assistant.themes.dflt.DfltTheme

/**
 * ThemeScanner - Discovery pattern for themes
 * 
 * Registry for available themes, similar to ToolTypeScanner pattern.
 * Enables extensibility without Core modification.
 * 
 * TODO: Replace with annotation processor scanning later
 */
object ThemeScanner {
    
    /**
     * Scans and returns all available themes
     * 
     * @return Map<themeId, ThemeContract> where:
     *   - themeId: unique theme identifier (ex: "default", "retro", "dark")
     *   - ThemeContract: theme instance
     */
    fun scanForThemes(): Map<String, ThemeContract> {
        return mapOf(
            "default" to DefaultTheme,
            // New themes to be added here only
            // "retro" to RetroTheme,
            // "dark" to DarkTheme
        )
    }
    
    /**
     * Gets a theme by its ID
     * 
     * @param themeId The theme identifier
     * @return The theme or null if not found
     */
    fun getTheme(themeId: String): ThemeContract? {
        return scanForThemes()[themeId]
    }
    
    /**
     * Gets the list of available theme IDs
     * 
     * @return List of theme identifiers
     */
    fun getAvailableThemeIds(): List<String> {
        return scanForThemes().keys.toList()
    }
    
    /**
     * Gets the default theme
     * Used as fallback if requested theme doesn't exist
     * 
     * @return The default theme (DefaultTheme)
     */
    fun getDefaultTheme(): ThemeContract {
        return DefaultTheme
    }
}