package app.treelune.core.config

import java.time.ZoneId

/**
 * Comprehensive date/time configuration for the application.
 *
 * Handles:
 * - Timezone configuration (for display and conversion)
 * - Locale configuration (for formatting)
 * - Display format preferences (24h/12h, date patterns, separators)
 * - Business logic settings (day start hour, week start day)
 *
 * Architecture:
 * - Internal storage: UTC timestamps (Long)
 * - External interfaces (UI + AI): ISO 8601 with timezone offset
 * - Conversion handled by DateTimeConverter in services
 * - Display formatting via DateTimeFormatter
 *
 * Default values: Detected from system at first app launch (see FormatDefaults)
 * Constructor defaults below are for documentation only - actual defaults come from DB.
 */
data class DateTimeConfig(
    // ===== TIMEZONE / LOCALE =====
    /**
     * Timezone override for display and conversion.
     * If null, uses system default timezone.
     * Examples: "Europe/Paris", "America/New_York", "UTC"
     */
    val timezoneOverride: String? = null,

    /**
     * Locale override for formatting.
     * If null, uses system default locale.
     * Examples: "fr-FR", "en-US", "de-DE"
     */
    val localeOverride: String? = null,

    // ===== DISPLAY FORMATS =====
    /**
     * Whether to use 24-hour format for time display.
     * Detected from system at first launch (see FormatDefaults.getSystemDefault24HourFormat).
     */
    val use24HourFormat: Boolean? = true,  // Common default, actual value from DB

    /**
     * Date format pattern for display.
     * Detected from system locale at first launch (see FormatDefaults.getSystemDefaultDatePattern).
     * Examples: "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd"
     */
    val dateFormatPattern: String? = "dd/MM/yyyy",  // Common default, actual value from DB

    /**
     * Time separator for display.
     * Examples: ":" (14:30), "h" (14h30)
     */
    val timeSeparator: String = FormatDefaults.TIME_SEPARATOR,

    // ===== BUSINESS LOGIC (PERIOD NORMALIZATION) =====
    /**
     * Hour at which a new day starts (for period calculations).
     * Used by PeriodUtils for normalizing timestamps to day/week/month boundaries.
     * Independent of timezone setting.
     */
    val dayStartHour: Int = FormatDefaults.DAY_START_HOUR,

    /**
     * Day on which a week starts.
     * Used by PeriodUtils for week calculations.
     * Must be a valid DayOfWeek name in uppercase.
     */
    val weekStartDay: String = FormatDefaults.getWeekStartDayUppercase()
) {
    /**
     * Get ZoneId for app timezone.
     * Returns override if set, otherwise system default.
     *
     * @return ZoneId to use for all date/time conversions and display
     */
    fun getZoneId(): ZoneId {
        return timezoneOverride?.let { ZoneId.of(it) }
            ?: ZoneId.systemDefault()
    }
}

/**
 * AI action validation configuration
 * Hierarchy: app > zone > tool > session > AI request (OR logic)
 */
data class ValidationConfig(
    val validateAppConfigChanges: Boolean = false,      // App config changes
    val validateZoneConfigChanges: Boolean = false,     // Zone config changes
    val validateToolConfigChanges: Boolean = false,     // Tool config changes
    val validateToolDataChanges: Boolean = false,       // Tool data changes
    val validateVariableChanges: Boolean = false        // Variables created, changed, deleted
) {
    /** The validation_config settings as stored in the database */
    fun toSettingsJson(): String = org.json.JSONObject().apply {
        put(KEY_APP_CONFIG, validateAppConfigChanges)
        put(KEY_ZONE_CONFIG, validateZoneConfigChanges)
        put(KEY_TOOL_CONFIG, validateToolConfigChanges)
        put(KEY_TOOL_DATA, validateToolDataChanges)
        put(KEY_VARIABLES, validateVariableChanges)
    }.toString()

    companion object {
        const val KEY_APP_CONFIG = "validate_app_config_changes"
        const val KEY_ZONE_CONFIG = "validate_zone_config_changes"
        const val KEY_TOOL_CONFIG = "validate_tool_config_changes"
        const val KEY_TOOL_DATA = "validate_tool_data_changes"
        const val KEY_VARIABLES = "validate_variable_changes"

        /**
         * Read the stored validation_config settings. Every key is required: a missing
         * one throws rather than silently meaning "no validation".
         */
        fun fromSettingsJson(settings: org.json.JSONObject) = ValidationConfig(
            validateAppConfigChanges = settings.getBoolean(KEY_APP_CONFIG),
            validateZoneConfigChanges = settings.getBoolean(KEY_ZONE_CONFIG),
            validateToolConfigChanges = settings.getBoolean(KEY_TOOL_CONFIG),
            validateToolDataChanges = settings.getBoolean(KEY_TOOL_DATA),
            validateVariableChanges = settings.getBoolean(KEY_VARIABLES)
        )
    }
}
