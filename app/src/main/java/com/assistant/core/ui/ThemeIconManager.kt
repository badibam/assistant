package com.assistant.core.ui

import android.content.Context

/**
 * Universal icon manager for all themes
 * Uses convention: {themeName}_{iconName} for drawable resources
 */
object ThemeIconManager {
    
    /**
     * Get icon resource for theme and icon name
     * @param context Android context
     * @param themeName Theme name (e.g., "default", "glass")
     * @param iconName Icon semantic name (e.g., "activity", "trending-up")
     * @throws IllegalArgumentException if icon not found
     */
    fun getIconResource(context: Context, themeName: String, iconName: String): Int {
        val resourceName = "${themeName}_${iconName.replace("-", "_")}"
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        
        if (resourceId == 0) {
            throw IllegalArgumentException("Icon not found: $resourceName (theme: $themeName, icon: $iconName)")
        }
        
        return resourceId
    }
    
    /**
     * Check if icon exists for theme
     */
    fun iconExists(context: Context, themeName: String, iconName: String): Boolean {
        val resourceName = "${themeName}_${iconName.replace("-", "_")}"
        return context.resources.getIdentifier(resourceName, "drawable", context.packageName) != 0
    }
}