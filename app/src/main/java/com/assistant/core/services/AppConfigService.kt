package com.assistant.core.services

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.database.entities.DefaultTemporalSettings
import com.assistant.core.schemas.AppConfigSchemaProvider
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.coordinator.CancellationToken
import android.util.Log
import org.json.JSONObject

/**
 * Centralized service for application configuration management
 * Provides typed access to parameters stored by category in database
 */
class AppConfigService(private val context: Context) : ExecutableService {

    private val database = AppDatabase.getDatabase(context)
    private val settingsDao = database.appSettingsCategoryDao()

    /**
     * Temporal configuration
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
     * Internal temporal settings management
     */
    private suspend fun getTemporalSettings(): JSONObject {
        Log.d("CONFIGDEBUG", "Getting temporal settings from database")
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.TEMPORAL)
        return if (settingsJson != null) {
            try {
                Log.d("CONFIGDEBUG", "Found existing temporal settings: $settingsJson")
                JSONObject(settingsJson)
            } catch (e: Exception) {
                Log.e("CONFIGDEBUG", "Error parsing temporal settings JSON: ${e.message}", e)
                createDefaultTemporalSettings()
            }
        } else {
            Log.d("CONFIGDEBUG", "No temporal settings found, creating defaults")
            createDefaultTemporalSettings()
        }
    }

    private suspend fun createDefaultTemporalSettings(): JSONObject {
        Log.d("CONFIGDEBUG", "Creating default temporal settings")
        val defaultSettings = JSONObject(DefaultTemporalSettings.JSON.trimIndent())
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.TEMPORAL,
                settings = defaultSettings.toString()
            )
        )
        Log.d("CONFIGDEBUG", "Default temporal settings inserted: $defaultSettings")
        return defaultSettings
    }

    private suspend fun updateTemporalSetting(key: String, value: Any) {
        val settings = getTemporalSettings()
        settings.put(key, value)
        
        // Validation with SchemaValidator
        val dataMap = mapOf(
            "week_start_day" to settings.optString("week_start_day"),
            "day_start_hour" to settings.optInt("day_start_hour")
        )
        val schemaProvider = AppConfigSchemaProvider.create(context)
        val validation = SchemaValidator.validate(schemaProvider, dataMap, context, schemaType = "temporal")
        
        if (!validation.isValid) {
            throw IllegalArgumentException("Invalid configuration: ${validation.errorMessage}")
        }
        
        settingsDao.updateSettings(AppSettingCategories.TEMPORAL, settings.toString())
    }

    /**
     * Generic utilities
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

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        Log.d("CONFIGDEBUG", "AppConfigService.execute: operation=$operation, params=$params")
        return when (operation) {
            "get_config" -> {
                val category = params.optString("category", "temporal")
                Log.d("CONFIGDEBUG", "Getting config for category: $category")
                when (category) {
                    AppSettingCategories.TEMPORAL -> {
                        val settings = getTemporalSettings()
                        Log.d("CONFIGDEBUG", "Temporal settings retrieved: $settings")
                        OperationResult.success(mapOf("settings" to settings.toMap()))
                    }
                    else -> {
                        Log.w("CONFIGDEBUG", "Unknown category: $category")
                        OperationResult.error("Unknown category: $category")
                    }
                }
            }
            else -> {
                Log.w("CONFIGDEBUG", "Unknown operation: $operation")
                OperationResult.error("Unknown operation: $operation")
            }
        }
    }
    
    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            map[key] = get(key)
        }
        return map
    }

}