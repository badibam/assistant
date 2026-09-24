package com.assistant.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Application configuration organized by categories
 * Each category contains its parameters in JSON format
 */
@Entity(tableName = "app_settings_categories")
data class AppSettingsCategory(
    @PrimaryKey val category: String,
    @ColumnInfo(name = "settings") val settings: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Available configuration categories
 */
object AppSettingCategories {
    const val FORMAT = "format"
    const val AI_LIMITS = "ai_limits"
    const val VALIDATION_CONFIG = "validation_config"
    const val MAIN_SCREEN = "main_screen"

    // Future categories:
    // const val UI = "ui"
    // const val DATA = "data"
}

/**
 * Default format configuration
 * Includes date/time settings, locale overrides, and period calculation parameters
 *
 * DEPRECATED: Use FormatDefaults.toJson(context) instead.
 * This object is kept for backward compatibility but delegates to FormatDefaults.
 */
@Deprecated(
    message = "Use FormatDefaults.toJson(context) instead",
    replaceWith = ReplaceWith("FormatDefaults.toJson(context)", "com.assistant.core.config.FormatDefaults")
)
object DefaultFormatSettings {
    /**
     * Generate default format settings JSON with system-detected values.
     * Delegates to FormatDefaults for single source of truth.
     *
     * @param context Context for detecting system preferences
     * @return JSON string with all default format settings
     */
    fun getJson(context: android.content.Context): String {
        return com.assistant.core.config.FormatDefaults.toJson(context)
    }
}

/**
 * Default validation configuration
 * Hierarchy: app > zone > tool > session > AI request (OR logic)
 */
object DefaultValidationSettings {
    const val JSON = """
    {
        "validate_app_config_changes": false,
        "validate_zone_config_changes": false,
        "validate_tool_config_changes": false,
        "validate_tool_data_changes": false
    }
    """
}

/**
 * Default main screen configuration
 */
object DefaultMainScreenSettings {
    const val JSON = """
    {
        "zone_groups": []
    }
    """
}