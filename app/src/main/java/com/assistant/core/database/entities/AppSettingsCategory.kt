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
    const val TEMPORAL = "temporal"
    
    // Future categories:
    // const val UI = "ui"
    // const val DATA = "data"
}

/**
 * Default temporal configuration
 */
object DefaultTemporalSettings {
    const val JSON = """
    {
        "week_start_day": "monday",
        "day_start_hour": 4,
        "relative_label_limits": {
            "hour_limit": 12,
            "day_limit": 7,
            "week_limit": 4,
            "month_limit": 6,
            "year_limit": 3
        }
    }
    """
    
    // Future temporal settings:
    // "timezone": "Europe/Paris"
    // "date_format": "dd/MM/yyyy" 
    // "time_format": "24h"
}