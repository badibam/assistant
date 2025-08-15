package com.assistant.themes.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.assistant.themes.default.DefaultTheme

/**
 * Theme delegate wrapper that forwards all ThemeContract calls to a dynamically resolved theme.
 * This allows CurrentTheme to delegate to a mutable theme reference without circular dependency.
 */
private class ThemeDelegate(private val getTheme: () -> ThemeContract) : ThemeContract by getTheme()

/**
 * Global theme manager - holds reference to current active theme
 * Uses delegation pattern to forward all ThemeContract calls to the active theme
 */
object CurrentTheme : ThemeContract by ThemeDelegate({ CurrentTheme.activeTheme }) {
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