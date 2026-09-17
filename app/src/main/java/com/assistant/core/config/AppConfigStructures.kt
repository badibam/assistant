package com.assistant.core.config

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
 * AI autonomous loop limits configuration
 * Separate limits for CHAT vs AUTOMATION sessions
 */
data class AILimitsConfig(
    // ===== CHAT LIMITS =====
    val chatMaxDataQueryIterations: Int = 3,
    val chatMaxActionRetries: Int = 3,
    val chatMaxFormatErrorRetries: Int = 3,
    val chatMaxAutonomousRoundtrips: Int = 10,

    // ===== AUTOMATION LIMITS =====
    val automationMaxDataQueryIterations: Int = 5,
    val automationMaxActionRetries: Int = 5,
    val automationMaxFormatErrorRetries: Int = 5,
    val automationMaxAutonomousRoundtrips: Int = 20,

    // ===== CHAT: HOW LONG IT MAY SIT IDLE BEFORE AUTOMATION EVICTS IT (ms) =====
    // AUTOMATION asks for the slot and CHAT has been idle longer than this -> CHAT is stopped
    // CHAT has been idle for less than this -> AUTOMATION waits in the queue
    val chatMaxInactivityBeforeAutomationEviction: Long = 5 * 60 * 1000, // 5 min

    // ===== AUTOMATION : DURÉE MAX OCCUPATION SESSION (ms) =====
    // Watchdog against runaway loops (CHAT never times out: the user has the stop button)
    val automationMaxSessionDuration: Long = 10 * 60 * 1000 // 10 min
)

/**
 * AI action validation configuration
 * Hierarchy: app > zone > tool > session > AI request (OR logic)
 */
data class ValidationConfig(
    val validateAppConfigChanges: Boolean = false,      // Modif config app
    val validateZoneConfigChanges: Boolean = false,     // Modif config zones
    val validateToolConfigChanges: Boolean = false,     // Modif config outils
    val validateToolDataChanges: Boolean = false        // Modif données outils
)
