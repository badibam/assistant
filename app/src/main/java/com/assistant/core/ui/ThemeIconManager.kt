package com.assistant.core.ui

import android.content.Context

/**
 * Data class for available icons
 */
data class AvailableIcon(
    val id: String,
    val displayName: String,
    val resourceId: Int
)

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
    
    /**
     * Get all available icons for a theme using generated resources
     * Uses direct R.drawable references instead of getIdentifier()
     */
    fun getAvailableIcons(context: Context, themeName: String): List<AvailableIcon> {
        return when (themeName) {
            "default" -> GeneratedThemeResources.getDefaultThemeIcons()
            else -> emptyList() // Add other themes here as needed
        }
    }
    
    /**
     * Format icon ID to display name
     * "trending-up" -> "Trending Up"
     */
    private fun formatDisplayName(iconId: String): String {
        return iconId.split("-").joinToString(" ") { 
            it.replaceFirstChar { char -> char.uppercase() } 
        }
    }
}