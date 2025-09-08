package com.assistant.core.services

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.database.entities.DefaultTemporalSettings
import com.assistant.core.schemas.AppConfigSchemaProvider
import com.assistant.core.validation.SchemaValidator
import org.json.JSONObject

/**
 * Service centralisé pour la gestion de la configuration de l'application
 * Fournit un accès typé aux paramètres stockés par catégorie en base
 */
class AppConfigService(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val settingsDao = database.appSettingsCategoryDao()

    /**
     * Configuration temporelle
     */
    suspend fun getWeekStartDay(): String {
        val settings = getTemporalSettings()
        return settings.optString("week_start_day", "monday")
    }

    suspend fun getDayStartHour(): Int {
        val settings = getTemporalSettings()
        return settings.optInt("day_start_hour", 4)
    }

    suspend fun setWeekStartDay(day: String) {
        updateTemporalSetting("week_start_day", day)
    }

    suspend fun setDayStartHour(hour: Int) {
        updateTemporalSetting("day_start_hour", hour)
    }

    /**
     * Gestion interne des paramètres temporels
     */
    private suspend fun getTemporalSettings(): JSONObject {
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.TEMPORAL)
        return if (settingsJson != null) {
            try {
                JSONObject(settingsJson)
            } catch (e: Exception) {
                createDefaultTemporalSettings()
            }
        } else {
            createDefaultTemporalSettings()
        }
    }

    private suspend fun createDefaultTemporalSettings(): JSONObject {
        val defaultSettings = JSONObject(DefaultTemporalSettings.JSON.trimIndent())
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.TEMPORAL,
                settings = defaultSettings.toString()
            )
        )
        return defaultSettings
    }

    private suspend fun updateTemporalSetting(key: String, value: Any) {
        val settings = getTemporalSettings()
        settings.put(key, value)
        
        // Validation avec SchemaValidator
        val dataMap = mapOf(
            "week_start_day" to settings.optString("week_start_day"),
            "day_start_hour" to settings.optInt("day_start_hour")
        )
        val schemaProvider = AppConfigSchemaProvider.create(context)
        val validation = SchemaValidator.validate(schemaProvider, dataMap, context, schemaType = "temporal")
        
        if (!validation.isValid) {
            throw IllegalArgumentException("Configuration invalide: ${validation.errorMessage}")
        }
        
        settingsDao.updateSettings(AppSettingCategories.TEMPORAL, settings.toString())
    }

    /**
     * Utilitaires génériques
     */
    suspend fun getCategorySettings(category: String): JSONObject? {
        val settingsJson = settingsDao.getSettingsJsonForCategory(category)
        return settingsJson?.let { 
            try { 
                JSONObject(it) 
            } catch (e: Exception) { 
                null 
            } 
        }
    }

    suspend fun resetToDefaults(category: String) {
        when (category) {
            AppSettingCategories.TEMPORAL -> {
                settingsDao.updateSettings(category, DefaultTemporalSettings.JSON.trimIndent())
            }
            // Future categories handled here
        }
    }

}