package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.assistant.core.database.entities.AppSettingsCategory

/**
 * DAO pour la gestion des catégories de configuration de l'application
 */
@Dao
interface AppSettingsCategoryDao {

    @Query("SELECT * FROM app_settings_categories WHERE category = :category")
    suspend fun getSettingsForCategory(category: String): AppSettingsCategory?

    @Query("SELECT settings FROM app_settings_categories WHERE category = :category")
    suspend fun getSettingsJsonForCategory(category: String): String?

    @Query("SELECT * FROM app_settings_categories")
    suspend fun getAllSettings(): List<AppSettingsCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettingsCategory)

    @Query("UPDATE app_settings_categories SET settings = :settingsJson, updated_at = :updatedAt WHERE category = :category")
    suspend fun updateSettings(category: String, settingsJson: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM app_settings_categories WHERE category = :category")
    suspend fun deleteCategory(category: String)
}