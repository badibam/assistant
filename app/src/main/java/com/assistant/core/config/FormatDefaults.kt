package com.assistant.core.config

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * Single source of truth for all format configuration default values.
 *
 * Philosophy: Detect system defaults at first launch, then store them.
 * This respects user's system preferences while maintaining deterministic behavior.
 *
 * Used by:
 * - AppSettingsDefaults (the format category's first row)
 * - DateTimeConfig (data class defaults)
 *
 * IMPORTANT: All defaults must be defined here and nowhere else.
 */
object FormatDefaults {
    // ===== FIXED DEFAULTS (NOT SYSTEM-DEPENDENT) =====

    const val TIME_SEPARATOR: String = ":"
    const val DAY_START_HOUR: Int = 4
    const val WEEK_START_DAY: String = "monday"  // Lowercase for JSON storage

    const val HOUR_LIMIT: Int = 12
    const val DAY_LIMIT: Int = 7
    const val WEEK_LIMIT: Int = 4
    const val MONTH_LIMIT: Int = 6
    const val YEAR_LIMIT: Int = 3

    // ===== SYSTEM-DETECTED DEFAULTS =====

    /**
     * Detect system 24-hour format preference.
     * Called once at first app launch to initialize default.
     *
     * @return true if system uses 24h format, false for 12h format
     */
    fun getSystemDefault24HourFormat(context: Context): Boolean {
        return android.text.format.DateFormat.is24HourFormat(context)
    }

    /**
     * Detect system date format pattern preference.
     * Called once at first app launch to initialize default.
     *
     * Uses locale to determine most appropriate format:
     * - US/Canada: MM/dd/yyyy
     * - Most of world: dd/MM/yyyy
     * - ISO preference: yyyy-MM-dd
     *
     * @return date format pattern string
     */
    fun getSystemDefaultDatePattern(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        return when (locale.country.uppercase()) {
            "US", "CA" -> "MM/dd/yyyy"  // North America
            "JP", "CN", "KR" -> "yyyy-MM-dd"  // Asia (ISO-like preference)
            else -> "dd/MM/yyyy"  // Europe and rest of world
        }
    }

    /**
     * Get week start day in uppercase for DayOfWeek enum.
     * Used by DateTimeConfig and code that needs DayOfWeek.
     */
    fun getWeekStartDayUppercase(): String = WEEK_START_DAY.uppercase()

    /**
     * Generate default format settings JSON with system-detected values.
     * Used by DefaultFormatSettings in database initialization.
     *
     * @param context Context for detecting system preferences
     * @return JSON string with all default format settings
     */
    fun toJson(context: Context): String {
        val use24Hour = getSystemDefault24HourFormat(context)
        val datePattern = getSystemDefaultDatePattern(context)

        return """
    {
        "week_start_day": "$WEEK_START_DAY",
        "day_start_hour": $DAY_START_HOUR,
        "use_24_hour_format": $use24Hour,
        "date_format_pattern": "$datePattern",
        "time_separator": "$TIME_SEPARATOR",
        "relative_label_limits": {
            "hour_limit": $HOUR_LIMIT,
            "day_limit": $DAY_LIMIT,
            "week_limit": $WEEK_LIMIT,
            "month_limit": $MONTH_LIMIT,
            "year_limit": $YEAR_LIMIT
        }
    }
    """.trimIndent()
    }
}
