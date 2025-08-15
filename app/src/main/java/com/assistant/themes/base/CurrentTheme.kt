package com.assistant.themes.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.assistant.themes.default.DefaultTheme

/**
 * Global theme manager - holds reference to current active theme
 */
object CurrentTheme : ThemeContract by activeTheme {
    private var activeTheme: ThemeContract by mutableStateOf(DefaultTheme)
    
    /**
     * Switch to a different theme
     */
    fun switchTheme(theme: ThemeContract) {
        activeTheme = theme
    }
    
    /**
     * Get current theme name for debugging/settings
     */
    fun getCurrentThemeName(): String {
        return activeTheme::class.simpleName ?: "Unknown"
    }
}