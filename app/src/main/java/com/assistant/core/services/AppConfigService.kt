package com.assistant.core.services

import android.content.Context
import com.assistant.core.config.DateTimeConfig
import com.assistant.core.config.FormatDefaults
import com.assistant.core.ai.domain.AILimitsConfig
import com.assistant.core.config.ValidationConfig
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.database.entities.DefaultFormatSettings
import com.assistant.core.database.entities.DefaultValidationSettings
import com.assistant.core.database.entities.DefaultMainScreenSettings
import com.assistant.core.schemas.AppConfigSchemaProvider
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.strings.Strings
import org.json.JSONObject
import org.json.JSONArray

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
        // Store lowercase in DB for consistency with FormatDefaults
        updateFormatSetting("week_start_day", day.lowercase())
    }

    suspend fun setDayStartHour(hour: Int) {
        updateFormatSetting("day_start_hour", hour)
    }

    /**
     * Relative label limits for period display. Required by the format schema: a missing
     * key is a corrupted config and fails here rather than falling back.
     */
    suspend fun getRelativeLabelLimits(): org.json.JSONObject {
        return getFormatSettings().getJSONObject("relative_label_limits")
    }

    /**
     * Set relative label limits for period display.
     */
    suspend fun setRelativeLabelLimits(
        hourLimit: Int,
        dayLimit: Int,
        weekLimit: Int,
        monthLimit: Int,
        yearLimit: Int
    ) {
        val limits = org.json.JSONObject().apply {
            put("hour_limit", hourLimit)
            put("day_limit", dayLimit)
            put("week_limit", weekLimit)
            put("month_limit", monthLimit)
            put("year_limit", yearLimit)
        }
        updateFormatSetting("relative_label_limits", limits)
    }

    suspend fun setLocaleOverride(locale: String?) {
        updateFormatSetting("locale_override", locale)
    }

    /**
     * Main screen configuration
     */
    suspend fun getZoneGroups(): List<String> {
        val settings = getMainScreenSettings()
        val groupsJson = settings.optJSONArray("zone_groups")
        return if (groupsJson != null) {
            (0 until groupsJson.length()).map { groupsJson.getString(it) }
        } else {
            emptyList()
        }
    }

    suspend fun setZoneGroups(groups: List<String>) {
        updateMainScreenSetting("zone_groups", JSONArray(groups))
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
        LogManager.service("Creating default format settings with system-detected values")
        @Suppress("DEPRECATION")
        val defaultSettings = JSONObject(DefaultFormatSettings.getJson(context))
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

        // Validation with SchemaValidator - include ALL required fields
        val dataMap = mutableMapOf<String, Any>()
        dataMap["week_start_day"] = settings.optString("week_start_day")
        dataMap["day_start_hour"] = settings.optInt("day_start_hour")

        // Optional overrides
        settings.optString("locale_override").takeIf { it != "null" && it.isNotBlank() }?.let {
            dataMap["locale_override"] = it
        }
        settings.optString("timezone_override").takeIf { it != "null" && it.isNotBlank() }?.let {
            dataMap["timezone_override"] = it
        }
        settings.optString("date_format_pattern").takeIf { it != "null" && it.isNotBlank() }?.let {
            dataMap["date_format_pattern"] = it
        }

        // Boolean fields
        if (settings.has("use_24_hour_format")) {
            dataMap["use_24_hour_format"] = settings.opt("use_24_hour_format")
        }

        // Time separator
        dataMap["time_separator"] = settings.optString("time_separator", ":")

        // Relative label limits (required)
        val relativeLimits = settings.optJSONObject("relative_label_limits")
        if (relativeLimits != null) {
            dataMap["relative_label_limits"] = mapOf(
                "hour_limit" to relativeLimits.optInt("hour_limit", 12),
                "day_limit" to relativeLimits.optInt("day_limit", 7),
                "week_limit" to relativeLimits.optInt("week_limit", 4),
                "month_limit" to relativeLimits.optInt("month_limit", 6),
                "year_limit" to relativeLimits.optInt("year_limit", 3)
            )
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
     * Get comprehensive date/time configuration.
     * Includes timezone, locale, display formats, and business logic parameters.
     *
     * Note: week_start_day is stored lowercase in DB but returned uppercase for DayOfWeek compatibility.
     *
     * @return DateTimeConfig with all date/time related settings
     */
    suspend fun getDateTimeConfig(): DateTimeConfig {
        val settings = getFormatSettings()
        return DateTimeConfig(
            timezoneOverride = settings.optString("timezone_override").takeIf { it != "null" && it.isNotBlank() },
            localeOverride = settings.optString("locale_override").takeIf { it != "null" && it.isNotBlank() },
            use24HourFormat = when (val value = settings.opt("use_24_hour_format")) {
                is Boolean -> value
                "true" -> true
                "false" -> false
                else -> null
            },
            dateFormatPattern = settings.optString("date_format_pattern").takeIf { it != "null" && it.isNotBlank() },
            timeSeparator = settings.optString("time_separator", FormatDefaults.TIME_SEPARATOR),
            dayStartHour = settings.optInt("day_start_hour", FormatDefaults.DAY_START_HOUR),
            weekStartDay = settings.optString("week_start_day", FormatDefaults.WEEK_START_DAY).uppercase()  // Convert to uppercase for DayOfWeek
        )
    }

    /**
     * Set timezone override for date/time display and conversion.
     * If null, uses system default timezone.
     *
     * @param timezone Timezone ID (e.g., "Europe/Paris", "UTC") or null for system default
     */
    suspend fun setTimezoneOverride(timezone: String?) {
        updateFormatSetting("timezone_override", timezone)
    }

    /**
     * Set whether to use 24-hour format for time display.
     * If null, uses system/locale default.
     *
     * @param use24h True for 24h, false for 12h, null for system default
     */
    suspend fun setUse24HourFormat(use24h: Boolean?) {
        updateFormatSetting("use_24_hour_format", use24h)
    }

    /**
     * Set date format pattern for display.
     * If null, uses locale default.
     *
     * @param pattern Date pattern (e.g., "dd/MM/yyyy", "MM/dd/yyyy") or null for locale default
     */
    suspend fun setDateFormatPattern(pattern: String?) {
        updateFormatSetting("date_format_pattern", pattern)
    }

    /**
     * Set time separator for display.
     *
     * @param separator Time separator (e.g., ":", "h")
     */
    suspend fun setTimeSeparator(separator: String) {
        updateFormatSetting("time_separator", separator)
    }

    /**
     * Get structured AI limits configuration
     */
    suspend fun getAILimits(): AILimitsConfig =
        AILimitsConfig.fromSettingsJson(getAILimitsSettings())

    /**
     * The stored ai_limits settings, written from AILimitsConfig.default() on first use.
     * Stored settings that cannot be read throw instead of being replaced by the defaults,
     * which would overwrite what the database holds.
     */
    private suspend fun getAILimitsSettings(): JSONObject {
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.AI_LIMITS)
            ?: return createDefaultAILimitsSettings()
        return JSONObject(settingsJson)
    }

    private suspend fun createDefaultAILimitsSettings(): JSONObject {
        LogManager.service("No AI limits settings found, writing the defaults")
        val defaultSettings = AILimitsConfig.default().toSettingsJson()
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.AI_LIMITS,
                settings = defaultSettings
            )
        )
        return JSONObject(defaultSettings)
    }

    /**
     * Get structured validation configuration
     * Hierarchy: app > zone > tool > session > AI request (OR logic)
     */
    suspend fun getValidationConfig(): ValidationConfig {
        val settings = getValidationSettings()
        return ValidationConfig(
            validateAppConfigChanges = settings.optBoolean("validate_app_config_changes", false),
            validateZoneConfigChanges = settings.optBoolean("validate_zone_config_changes", false),
            validateToolConfigChanges = settings.optBoolean("validate_tool_config_changes", false),
            validateToolDataChanges = settings.optBoolean("validate_tool_data_changes", false)
        )
    }

    /**
     * Set validation configuration
     */
    suspend fun setValidationConfig(config: ValidationConfig) {
        val settings = JSONObject().apply {
            put("validate_app_config_changes", config.validateAppConfigChanges)
            put("validate_zone_config_changes", config.validateZoneConfigChanges)
            put("validate_tool_config_changes", config.validateToolConfigChanges)
            put("validate_tool_data_changes", config.validateToolDataChanges)
        }

        settingsDao.updateSettings(AppSettingCategories.VALIDATION_CONFIG, settings.toString())
    }

    /**
     * Validation settings management with automatic defaults creation
     */
    private suspend fun getValidationSettings(): JSONObject {
        LogManager.service("Getting validation settings from database")
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.VALIDATION_CONFIG)
        return if (settingsJson != null) {
            try {
                LogManager.service("Found existing validation settings: $settingsJson")
                JSONObject(settingsJson)
            } catch (e: Exception) {
                LogManager.service("Error parsing validation settings JSON: ${e.message}", "ERROR", e)
                createDefaultValidationSettings()
            }
        } else {
            LogManager.service("No validation settings found, creating defaults")
            createDefaultValidationSettings()
        }
    }

    private suspend fun createDefaultValidationSettings(): JSONObject {
        LogManager.service("Creating default validation settings")
        val defaultSettings = JSONObject(DefaultValidationSettings.JSON.trimIndent())
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.VALIDATION_CONFIG,
                settings = defaultSettings.toString()
            )
        )
        LogManager.service("Default validation settings inserted: $defaultSettings")
        return defaultSettings
    }

    /**
     * Main screen settings management with automatic defaults creation
     */
    private suspend fun getMainScreenSettings(): JSONObject {
        LogManager.service("Getting main screen settings from database")
        val settingsJson = settingsDao.getSettingsJsonForCategory(AppSettingCategories.MAIN_SCREEN)
        return if (settingsJson != null) {
            try {
                LogManager.service("Found existing main screen settings: $settingsJson")
                JSONObject(settingsJson)
            } catch (e: Exception) {
                LogManager.service("Error parsing main screen settings JSON: ${e.message}", "ERROR", e)
                createDefaultMainScreenSettings()
            }
        } else {
            LogManager.service("No main screen settings found, creating defaults")
            createDefaultMainScreenSettings()
        }
    }

    private suspend fun createDefaultMainScreenSettings(): JSONObject {
        LogManager.service("Creating default main screen settings")
        val defaultSettings = JSONObject(DefaultMainScreenSettings.JSON.trimIndent())
        settingsDao.insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.MAIN_SCREEN,
                settings = defaultSettings.toString()
            )
        )
        LogManager.service("Default main screen settings inserted: $defaultSettings")
        return defaultSettings
    }

    private suspend fun updateMainScreenSetting(key: String, value: Any?) {
        val settings = getMainScreenSettings()
        settings.put(key, value)

        // No validation schema for main screen settings yet - simple storage
        settingsDao.updateSettings(AppSettingCategories.MAIN_SCREEN, settings.toString())
        LogManager.service("Updated main screen setting: $key = $value")
    }

    /**
     * Generic utilities
     */
    suspend fun getCategorySettings(category: String): JSONObject? {
        return when (category) {
            AppSettingCategories.FORMAT -> getFormatSettings()
            AppSettingCategories.AI_LIMITS -> getAILimitsSettings()
            AppSettingCategories.VALIDATION_CONFIG -> getValidationSettings()
            AppSettingCategories.MAIN_SCREEN -> getMainScreenSettings()
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
                @Suppress("DEPRECATION")
                settingsDao.updateSettings(category, DefaultFormatSettings.getJson(context))
            }
            AppSettingCategories.AI_LIMITS -> {
                settingsDao.updateSettings(category, AILimitsConfig.default().toSettingsJson())
            }
            AppSettingCategories.VALIDATION_CONFIG -> {
                settingsDao.updateSettings(category, DefaultValidationSettings.JSON.trimIndent())
            }
            AppSettingCategories.MAIN_SCREEN -> {
                settingsDao.updateSettings(category, DefaultMainScreenSettings.JSON.trimIndent())
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
            "get_current_datetime" -> {
                // Milliseconds, as everything inside speaks: CommandExecutor turns the timestamp
                // into the ISO 8601 the model reads, in the app's timezone
                val currentTimestamp = System.currentTimeMillis()
                LogManager.service("Current datetime: $currentTimestamp")
                OperationResult.success(mapOf("timestamp" to currentTimestamp))
            }
            "get_zone_groups" -> {
                val groups = getZoneGroups()
                LogManager.service("Zone groups retrieved: $groups")
                OperationResult.success(mapOf("zone_groups" to groups))
            }
            "set_zone_groups" -> {
                val groupsParam = params.opt("zone_groups")
                val groups = when (groupsParam) {
                    is JSONArray -> (0 until groupsParam.length()).map { groupsParam.getString(it) }
                    is List<*> -> groupsParam.filterIsInstance<String>()
                    else -> {
                        LogManager.service("Invalid zone_groups parameter type", "ERROR")
                        return OperationResult.error(s.shared("service_error_invalid_zone_groups"))
                    }
                }
                setZoneGroups(groups)
                DataChangeNotifier.notifyAppConfigChanged()
                LogManager.service("Zone groups updated: $groups")
                OperationResult.success(mapOf("zone_groups" to groups))
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

    /**
     * Verbalize AppConfig operation
     * Format: substantive form (e.g., "Modification de la configuration de l'application")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "get" -> {
                // get is a read operation, not typically verbalized for validation
                s.shared("action_verbalize_unknown")
            }
            else -> {
                // Any write operation to app config
                s.shared("action_verbalize_update_app_config")
            }
        }
    }

}