package app.treelune.core.utils

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date/time formatting utilities for UI display.
 *
 * Applies user preferences from DateTimeConfig:
 * - Timezone (display in user's timezone)
 * - Locale (formatting patterns)
 * - 24h/12h format preference
 * - Date pattern preference
 * - Time separator preference
 *
 * Usage:
 * - UI components: Format timestamps or ISO strings for display
 * - Always pass context to access config
 * - Handles both Long timestamps and String ISO inputs
 */
object DateTimeFormatter {

    /**
     * Format ISO string or timestamp for user display.
     *
     * Applies all user preferences from config:
     * - Timezone for conversion
     * - Locale for patterns
     * - Date/time format preferences
     *
     * @param value ISO 8601 string or Long timestamp
     * @param context Android context for config access
     * @param includeTime Whether to include time in output
     * @param includeDate Whether to include date in output
     * @return Formatted string for display (e.g., "15/03/2025 14h30" or "15/03/2025" or "14h30")
     */
    fun formatForDisplay(
        value: Any,
        context: Context,
        includeTime: Boolean = true,
        includeDate: Boolean = true
    ): String {
        // Get config and locale
        val config = AppConfigManager.getDateTimeConfig()
        val locale = LocaleUtils.getAppLocale(context)
        val timezone = config.getZoneId()

        // Convert to ZonedDateTime
        val zonedDateTime = when (value) {
            is Number -> Instant.ofEpochMilli(value.toLong()).atZone(timezone)
            else -> throw IllegalArgumentException("Value must be a timestamp in milliseconds, got: ${value::class.simpleName}")
        }

        // Build formatted string
        val parts = mutableListOf<String>()

        if (includeDate) {
            parts.add(formatDatePart(zonedDateTime, config, locale))
        }

        if (includeTime) {
            parts.add(formatTimePart(zonedDateTime, config, locale))
        }

        return parts.joinToString(" ")
    }

    /**
     * Format date only (no time).
     *
     * @param value ISO 8601 string or Long timestamp
     * @param context Android context for config access
     * @return Formatted date string (e.g., "15/03/2025")
     */
    fun formatDateOnly(value: Any, context: Context): String {
        return formatForDisplay(value, context, includeTime = false, includeDate = true)
    }

    /**
     * Format time only (no date).
     *
     * @param value ISO 8601 string or Long timestamp
     * @param context Android context for config access
     * @return Formatted time string (e.g., "14h30" or "2:30 PM")
     */
    fun formatTimeOnly(value: Any, context: Context): String {
        return formatForDisplay(value, context, includeTime = true, includeDate = false)
    }

    /**
     * Get current timestamp (UTC milliseconds).
     *
     * @return Current timestamp in milliseconds
     */
    fun nowTimestamp(): Long {
        return System.currentTimeMillis()
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Format date part according to config preferences.
     */
    private fun formatDatePart(
        zonedDateTime: ZonedDateTime,
        config: app.treelune.core.config.DateTimeConfig,
        locale: Locale
    ): String {
        // Use custom pattern if configured, otherwise locale default
        val pattern = config.dateFormatPattern

        return if (pattern != null) {
            // Custom pattern provided
            val formatter = JavaDateTimeFormatter.ofPattern(pattern, locale)
            zonedDateTime.format(formatter)
        } else {
            // Locale default (MEDIUM style)
            val formatter = JavaDateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
            zonedDateTime.format(formatter)
        }
    }

    /**
     * Format time part according to config preferences.
     */
    private fun formatTimePart(
        zonedDateTime: ZonedDateTime,
        config: app.treelune.core.config.DateTimeConfig,
        locale: Locale
    ): String {
        // Determine 24h format preference
        val use24h = config.use24HourFormat ?: is24HourFormat(locale)

        // Format time
        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        val separator = config.timeSeparator

        return if (use24h) {
            // 24h format: "14:30" or "14h30"
            String.format("%02d%s%02d", hour, separator, minute)
        } else {
            // 12h format: "2:30 PM" or "2h30 PM"
            val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            String.format("%d%s%02d %s", hour12, separator, minute, amPm)
        }
    }

    /**
     * Check if a locale uses 24-hour format by default.
     * Fallback when config.use24HourFormat is null.
     */
    private fun is24HourFormat(locale: Locale): Boolean {
        // Common 24h locales: Most of Europe, Asia, Latin America
        // Common 12h locales: US, UK (partially), Canada (partially)

        // Simple heuristic based on country
        return when (locale.country.uppercase()) {
            "US", "CA", "AU", "NZ", "PH" -> false  // 12h countries
            else -> true  // Default to 24h for most countries
        }
    }
}
