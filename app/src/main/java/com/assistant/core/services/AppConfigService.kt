package com.assistant.core.services

import android.content.Context
import com.assistant.core.config.TimeConfig
import com.assistant.core.config.AILimitsConfig
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.database.entities.DefaultFormatSettings
import com.assistant.core.database.entities.DefaultAILimitsSettings
import com.assistant.core.schemas.AppConfigSchemaProvider
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.utils.LogManager
import com.assistant.core.strings.Strings
import org.json.JSONObject

/**
 * Centralized service for application configuration management
 * Provides typed access to parameters stored by category in database
 */
class AppConfigService(private val context: Context) : ExecutableService {

    private val database = AppDatabase.getDatabase(context)
    private val settingsDao = database.appSettingsCategoryDao()
    private val s = Strings.`for`(context = context)

    /**
     * Format configuration
     */
    suspend fun getWeekStartDay(): String {
        val settings = getFormatSettings()
        return settings.optString("week_start_day", "monday")
    }

    suspend fun getDayStartHour(): Int {
        val settings = getFormatSettings()
        return settings.optInt("day_start_hour", 4)
    }

    suspend fun getLocaleOverride(): String? {
        val settings = getFormatSettings()
        return settings.optString("locale_override").takeIf { it != "null" && it.isNotBlank() }
    }

    suspend fun setWeekStartDay(day: String) {
        updateFormatSetting("week_start_day", day)
    }

    suspend fun setDayStartHour(hour: Int) {
        updateFormatSetting("day_start_hour", hour)
    }

    suspend fun setLocaleOverride(locale: String?) {
        updateFormatSetting("locale_override", locale)
    }

    /**
     * Internal format settings management
     */
    private suspend fun getFormatSettings(): JSONObject {
        LogManager.service("Getting format settings from database")
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.FORMAT)
        return if (settingsJson != null) {
            try {
                LogManager.service("Found existing format settings: $settingsJson")
                JSONObject(settingsJson)
            } catch (e: Exception) {
                LogManager.service("Error parsing format settings JSON: ${e.message}", "ERROR", e)
                createDefaultFormatSettings()
            }
        } else {
            LogManager.service("No format settings found, creating defaults")
            createDefaultFormatSettings()
        }
    }

    private suspend fun createDefaultFormatSettings(): JSONObject {
        LogManager.service("Creating default format settings")
        val defaultSettings = JSONObject(DefaultFormatSettings.JSON.trimIndent())
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.FORMAT,
                settings = defaultSettings.toString()
            )
        )
        LogManager.service("Default format settings inserted: $defaultSettings")
        return defaultSettings
    }

    private suspend fun updateFormatSetting(key: String, value: Any?) {
        val settings = getFormatSettings()
        settings.put(key, value)
        
        // Validation with SchemaValidator
        val dataMap = mutableMapOf<String, Any>()
        dataMap["week_start_day"] = settings.optString("week_start_day")
        dataMap["day_start_hour"] = settings.optInt("day_start_hour")
        settings.optString("locale_override").takeIf { it != "null" && it.isNotBlank() }?.let { 
            dataMap["locale_override"] = it 
        }
        val schema = AppConfigSchemaProvider.getSchema("app_config_format", context)
        val validation = if (schema != null) {
            SchemaValidator.validate(schema, dataMap, context)
        } else {
            com.assistant.core.validation.ValidationResult.error("App config format schema not found")
        }
        
        if (!validation.isValid) {
            throw IllegalArgumentException("Invalid configuration: ${validation.errorMessage}")
        }
        
        settingsDao.updateSettings(AppSettingCategories.FORMAT, settings.toString())
    }

    /**
     * Get structured time configuration
     */
    suspend fun getTimeConfig(): TimeConfig {
        val settings = getFormatSettings()
        return TimeConfig(
            dayStartHour = settings.optInt("day_start_hour", 4),
            weekStartDay = settings.optString("week_start_day", "MONDAY")
        )
    }

    /**
     * Get structured AI limits configuration
     */
    suspend fun getAILimits(): AILimitsConfig {
        val settings = getAILimitsSettings()
        return AILimitsConfig(
            chatMaxDataQueryIterations = settings.optInt("chatMaxDataQueryIterations", 3),
            chatMaxActionRetries = settings.optInt("chatMaxActionRetries", 3),
            chatMaxAutonomousRoundtrips = settings.optInt("chatMaxAutonomousRoundtrips", 10),
            automationMaxDataQueryIterations = settings.optInt("automationMaxDataQueryIterations", 5),
            automationMaxActionRetries = settings.optInt("automationMaxActionRetries", 5),
            automationMaxAutonomousRoundtrips = settings.optInt("automationMaxAutonomousRoundtrips", 20)
        )
    }

    /**
     * Set AI limits configuration
     */
    suspend fun setAILimits(limits: AILimitsConfig) {
        val settings = JSONObject().apply {
            // Keep existing token limits
            val currentSettings = getAILimitsSettings()
            put("defaultQueryMaxTokens", currentSettings.optInt("defaultQueryMaxTokens", 2000))
            put("defaultCharsPerToken", currentSettings.optDouble("defaultCharsPerToken", 4.5))
            put("defaultPromptMaxTokens", currentSettings.optInt("defaultPromptMaxTokens", 15000))

            // Set new loop limits
            put("chatMaxDataQueryIterations", limits.chatMaxDataQueryIterations)
            put("chatMaxActionRetries", limits.chatMaxActionRetries)
            put("chatMaxAutonomousRoundtrips", limits.chatMaxAutonomousRoundtrips)
            put("automationMaxDataQueryIterations", limits.automationMaxDataQueryIterations)
            put("automationMaxActionRetries", limits.automationMaxActionRetries)
            put("automationMaxAutonomousRoundtrips", limits.automationMaxAutonomousRoundtrips)
        }

        settingsDao.updateSettings(AppSettingCategories.AI_LIMITS, settings.toString())
    }

    /**
     * AI Limits settings management with automatic defaults creation
     */
    private suspend fun getAILimitsSettings(): JSONObject {
        LogManager.service("Getting AI limits settings from database")
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.AI_LIMITS)
        return if (settingsJson != null) {
            try {
                LogManager.service("Found existing AI limits settings: $settingsJson")
                JSONObject(settingsJson)
            } catch (e: Exception) {
                LogManager.service("Error parsing AI limits settings JSON: ${e.message}", "ERROR", e)
                createDefaultAILimitsSettings()
            }
        } else {
            LogManager.service("No AI limits settings found, creating defaults")
            createDefaultAILimitsSettings()
        }
    }

    private suspend fun createDefaultAILimitsSettings(): JSONObject {
        LogManager.service("Creating default AI limits settings")
        val defaultSettings = JSONObject(DefaultAILimitsSettings.JSON.trimIndent())
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.AI_LIMITS,
                settings = defaultSettings.toString()
            )
        )
        LogManager.service("Default AI limits settings inserted: $defaultSettings")
        return defaultSettings
    }

    /**
     * Generic utilities
     */
    suspend fun getCategorySettings(category: String): JSONObject? {
        return when (category) {
            AppSettingCategories.FORMAT -> getFormatSettings()
            AppSettingCategories.AI_LIMITS -> getAILimitsSettings()
            else -> {
                // Generic fallback for unknown categories - no auto-creation
                val settingsJson = settingsDao.getSettingsJsonForCategory(category)
                settingsJson?.let {
                    try {
                        JSONObject(it)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    suspend fun resetToDefaults(category: String) {
        when (category) {
            AppSettingCategories.FORMAT -> {
                settingsDao.updateSettings(category, DefaultFormatSettings.JSON.trimIndent())
            }
            AppSettingCategories.AI_LIMITS -> {
                settingsDao.updateSettings(category, DefaultAILimitsSettings.JSON.trimIndent())
            }
            // Future categories handled here
        }
    }

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        LogManager.service("AppConfigService.execute: operation=$operation, params=$params")
        return when (operation) {
            "get" -> {
                val category = params.optString("category", "format")
                LogManager.service("Getting config for category: $category")
                when (category) {
                    AppSettingCategories.FORMAT -> {
                        val settings = getFormatSettings()
                        LogManager.service("Format settings retrieved: $settings")
                        OperationResult.success(mapOf("settings" to settings.toMap()))
                    }
                    AppSettingCategories.AI_LIMITS -> {
                        val settings = getAILimitsSettings()
                        LogManager.service("AI limits settings retrieved: $settings")
                        OperationResult.success(mapOf("settings" to settings.toMap()))
                    }
                    else -> {
                        LogManager.service("Unknown category: $category", "WARN")
                        OperationResult.error(s.shared("service_error_unknown_category").format(category))
                    }
                }
            }
            else -> {
                LogManager.service("Unknown operation: $operation", "WARN")
                OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        }
    }
    
    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            val value = get(key)
            map[key] = when (value) {
                JSONObject.NULL -> null
                else -> value
            } ?: ""
        }
        return map
    }

}