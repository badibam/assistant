package com.assistant.core.ui

/**
 * Base palette categories that all themes should support
 * Ensures minimum consistency across themes
 */
enum class BasePalette {
    LIGHT,      // Light background, dark text
    DARK        // Dark background, light text
}

/**
 * Theme palette definition - combines base categories with custom theme variations
 * 
 * Examples:
 * - BasePalette.LIGHT → "default_light", "glass_light", "retro_light"
 * - Custom → "glass_frosted_blue", "retro_neon_pink"
 */
data class ThemePalette(
    val id: String,              // Unique identifier: "default_light", "glass_frosted_blue"
    val displayName: String,     // User-friendly name: "Light", "Frosted Blue"
    val description: String?,    // Optional description: "Cool blue tones with transparency"
    val base: BasePalette?,      // Base category if applicable (null for pure custom)
    val isCustom: Boolean        // true = theme-specific, false = standard base
) {
    companion object {
        /**
         * Creates a standard base palette for a theme
         */
        fun createBase(themeId: String, base: BasePalette): ThemePalette {
            return ThemePalette(
                id = "${themeId}_${base.name.lowercase()}",
                displayName = base.name.lowercase().replaceFirstChar { it.uppercase() },
                description = null,
                base = base,
                isCustom = false
            )
        }
        
        /**
         * Creates a custom palette for a theme
         */
        fun createCustom(
            themeId: String,
            name: String,
            displayName: String,
            description: String? = null,
            basedOn: BasePalette? = null
        ): ThemePalette {
            return ThemePalette(
                id = "${themeId}_${name}",
                displayName = displayName,
                description = description,
                base = basedOn,
                isCustom = true
            )
        }
    }
}