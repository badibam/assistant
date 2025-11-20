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
     * If null, uses system/locale default.
     */
    val use24HourFormat: Boolean? = null,

    /**
     * Date format pattern for display.
     * If null, uses locale default.
     * Examples: "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd"
     */
    val dateFormatPattern: String? = null,

    /**
     * Time separator for display.
     * Examples: ":" (14:30), "h" (14h30)
     */
    val timeSeparator: String = ":",

    // ===== BUSINESS LOGIC (PERIOD NORMALIZATION) =====
    /**
     * Hour at which a new day starts (for period calculations).
     * Used by PeriodUtils for normalizing timestamps to day/week/month boundaries.
     * Independent of timezone setting.
     */
    val dayStartHour: Int = 4,

    /**
     * Day on which a week starts.
     * Used by PeriodUtils for week calculations.
     * Must be a valid DayOfWeek name in uppercase.
     */
    val weekStartDay: String = "MONDAY"
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

    // ===== CHAT : DURÉE MAX INACTIVITÉ AVANT ÉVICTION PAR AUTOMATION (ms) =====
    // Si AUTOMATION demande la main et CHAT inactive depuis > cette durée → arrêt forcé CHAT
    // Si CHAT inactive depuis < cette durée → AUTOMATION attend en queue
    val chatMaxInactivityBeforeAutomationEviction: Long = 5 * 60 * 1000, // 5 min

    // ===== AUTOMATION : DURÉE MAX OCCUPATION SESSION (ms) =====
    // Watchdog pour éviter boucles infinies (CHAT n'a pas de timeout - bouton UI)
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
