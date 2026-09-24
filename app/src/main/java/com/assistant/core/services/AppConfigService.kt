package com.assistant.core.services

import android.content.Context
import com.assistant.core.config.DateTimeConfig
import com.assistant.core.ai.domain.AILimitsConfig
import com.assistant.core.config.ValidationConfig
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.config.AppSettingsDefaults
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
        return settings.getString("week_start_day")
    }

    suspend fun getDayStartHour(): Int {
        val settings = getFormatSettings()
        return settings.getInt("day_start_hour")
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
        val groupsJson = settings.getJSONArray("zone_groups")
        return (0 until groupsJson.length()).map { groupsJson.getString(it) }
    }

    suspend fun setZoneGroups(groups: List<String>) {
        updateMainScreenSetting("zone_groups", JSONArray(groups))
    }

    private suspend fun getFormatSettings(): JSONObject = readSettings(AppSettingCategories.FORMAT)

    /**
     * Change one format setting, then check the whole stored object against the format
     * schema before writing it. JSON nulls are left out of the check: they are how an
     * optional override says "follow the phone", and the schema reads a missing key the same.
     */
    private suspend fun updateFormatSetting(key: String, value: Any?) {
        val settings = getFormatSettings()
        settings.put(key, value ?: JSONObject.NULL)

        @Suppress("UNCHECKED_CAST")
        val data = com.assistant.core.utils.JsonUtils.toMap(settings).filterValues { it != null } as Map<String, Any>
        val schema = AppConfigSchemaProvider.getSchema("app_config_format", context)
            ?: throw IllegalStateException("App config format schema not found")
        val validation = SchemaValidator.validate(schema, data, context)
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
            // null or absent follows the phone; anything but a boolean is a corrupted setting
            use24HourFormat = when (val value = settings.opt("use_24_hour_format")) {
                null, JSONObject.NULL -> null
                is Boolean -> value
                else -> throw IllegalStateException("use_24_hour_format is neither a boolean nor null: $value")
            },
            dateFormatPattern = settings.optString("date_format_pattern").takeIf { it != "null" && it.isNotBlank() },
            timeSeparator = settings.getString("time_separator"),
            dayStartHour = settings.getInt("day_start_hour"),
            weekStartDay = settings.getString("week_start_day").uppercase()  // Convert to uppercase for DayOfWeek
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

    private suspend fun getAILimitsSettings(): JSONObject = readSettings(AppSettingCategories.AI_LIMITS)

    /**
     * Get structured validation configuration
     * Hierarchy: app > zone > tool > session > AI request (OR logic)
     */
    suspend fun getValidationConfig(): ValidationConfig =
        ValidationConfig.fromSettingsJson(readSettings(AppSettingCategories.VALIDATION_CONFIG))

    private suspend fun getMainScreenSettings(): JSONObject = readSettings(AppSettingCategories.MAIN_SCREEN)

    private suspend fun updateMainScreenSetting(key: String, value: Any?) {
        val settings = getMainScreenSettings()
        settings.put(key, value)

        // No validation schema for main screen settings yet - simple storage
        settingsDao.updateSettings(AppSettingCategories.MAIN_SCREEN, settings.toString())
        LogManager.service("Updated main screen setting: $key = $value")
    }

    /**
     * A settings category as stored. A category with no row yet gets its defaults written
     * (AppSettingsDefaults), which is what a first launch looks like. A stored row that is
     * not readable JSON throws: replacing it with the defaults would overwrite what the
     * database holds, and the failure would go unseen.
     */
    private suspend fun readSettings(category: String): JSONObject {
        val stored = settingsDao.getSettingsJsonForCategory(category)
        if (stored != null) return JSONObject(stored)

        LogManager.service("No $category settings found, writing the defaults", "INFO")
        val defaults = AppSettingsDefaults.forCategory(category, context)
        settingsDao.insertOrUpdateSettings(AppSettingsCategory(category = category, settings = defaults))
        return JSONObject(defaults)
    }

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        return when (operation) {
            "get" -> {
                val category = params.optString("category")
                when (category) {
                    AppSettingCategories.FORMAT -> {
                        val settings = getFormatSettings()
                        OperationResult.success(mapOf("settings" to settings.toMap()))
                    }
                    AppSettingCategories.AI_LIMITS -> {
                        val settings = getAILimitsSettings()
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
                OperationResult.success(mapOf("timestamp" to currentTimestamp))
            }
            "get_zone_groups" -> {
                val groups = getZoneGroups()
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