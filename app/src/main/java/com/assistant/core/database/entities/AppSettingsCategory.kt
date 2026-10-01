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
    const val DEMO = "demo"
    const val UI = "ui"

    // Future categories:
    // const val DATA = "data"
}
