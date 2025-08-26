package com.assistant.ui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.assistant.themes.default.DefaultTheme

/**
 * Global theme manager - Clean architecture approach
 * 
 * Responsibilities:
 * - Holds reference to the currently active theme
 * - Provides theme switching functionality  
 * - Exposes current theme via 'current' property
 * 
 * Architecture:
 * - CurrentTheme = Theme state manager (this file)
 * - UI = Public API that delegates to current theme (UI.kt)
 * - DefaultTheme/OtherThemes = Actual theme implementations
 * 
 * Benefits:
 * - No circular dependencies (CurrentTheme doesn't implement ThemeContract)
 * - Easy to add new theme methods (just modify ThemeContract + implementations + UI.kt)
 * - Clean separation of concerns
 * - Reactive theme switching with Compose state
 */
object CurrentTheme {
    
    /**
     * Internal mutable state holding the active theme
     * Uses Compose mutableStateOf for automatic recomposition when theme changes
     */
    private var activeTheme: ThemeContract by mutableStateOf(DefaultTheme)
    
    /**
     * Public read-only access to current theme
     * This property is used by UI.kt to delegate all theme calls
     * 
     * Usage: CurrentTheme.current.Text(...) instead of CurrentTheme.Text(...)
     */
    val current: ThemeContract get() = activeTheme
    
    /**
     * Switch to a different theme
     * Automatically triggers recomposition of all UI components using this theme
     * 
     * @param theme The new theme to activate (must implement ThemeContract)
     */
    fun switchTheme(theme: ThemeContract) {
        activeTheme = theme
    }
    
    /**
     * Get current theme name for debugging/settings display
     * Useful for theme selection UI or debug information
     * 
     * @return Simple class name of current theme (e.g. "DefaultTheme", "DarkTheme")
     */
    fun getCurrentThemeName(): String {
        return activeTheme::class.simpleName ?: "Unknown"
    }
}