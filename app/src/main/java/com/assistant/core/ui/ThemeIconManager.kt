package com.assistant.core.ui

import android.content.Context

/**
 * Data class for available icons
 */
data class AvailableIcon(
    val id: String,
    val displayName: String
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
     * Get all available icons for a theme by scanning drawable resources
     * @param context Android context
     * @param themeName Theme name (e.g., "default", "glass")
     * @return List of available icons with their IDs and display names
     */
    fun getAvailableIcons(context: Context, themeName: String): List<AvailableIcon> {
        val availableIcons = mutableListOf<AvailableIcon>()
        val prefix = "${themeName}_"
        
        try {
            val drawableFields = Class.forName("${context.packageName}.R\$drawable").fields
            
            for (field in drawableFields) {
                val resourceName = field.name
                if (resourceName.startsWith(prefix)) {
                    // Extract icon name (remove theme prefix and convert underscores to hyphens)
                    val iconId = resourceName.removePrefix(prefix).replace("_", "-")
                    val displayName = iconId.split("-").joinToString(" ") { 
                        it.replaceFirstChar { char -> char.uppercase() } 
                    }
                    
                    availableIcons.add(AvailableIcon(iconId, displayName))
                }
            }
        } catch (e: Exception) {
            // Si la réflection échoue, retourner liste vide
            return emptyList()
        }
        
        return availableIcons.sortedBy { it.displayName }
    }
}