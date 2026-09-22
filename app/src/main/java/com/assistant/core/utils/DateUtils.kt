package com.assistant.core.utils

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/**
 * Centralized date utilities for the entire application
 * Ensures consistent date formatting and parsing across all components
 *
 * IMPORTANT: All operations use the timezone configured in AppConfig, NOT the system timezone.
 * This ensures consistency across the application regardless of device timezone changes.
 *
 * Every function takes that timezone as a parameter defaulting to the configured one, so a
 * caller can compute in a stated zone rather than the app's. Production passes nothing and
 * gets the configured zone; the default is read per call, never cached.
 */
object DateUtils {

    // Date format constants
    private const val DISPLAY_DATE_FORMAT = "dd/MM/yyyy"
    private const val DISPLAY_TIME_FORMAT = "HH:mm"
    private const val FULL_DATE_TIME_FORMAT = "dd/MM/yy HH:mm"

    // DateTimeFormatter instances - use AppConfig timezone
    private val displayDateFormatter = DateTimeFormatter.ofPattern(DISPLAY_DATE_FORMAT)
    private val displayTimeFormatter = DateTimeFormatter.ofPattern(DISPLAY_TIME_FORMAT)
    private val fullDateTimeFormatter = DateTimeFormatter.ofPattern(FULL_DATE_TIME_FORMAT)

    /**
     * Get the configured timezone from AppConfig
     */
    private fun getConfiguredZone(): ZoneId {
        return AppConfigManager.getDateTimeConfig().getZoneId()
    }

    /**
     * Format timestamp for date display (dd/MM/yyyy)
     */
    fun formatDateForDisplay(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)
        return displayDateFormatter.format(zonedDateTime)
    }

    /**
     * Format timestamp for time display (HH:mm)
     */
    fun formatTimeForDisplay(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)
        return displayTimeFormatter.format(zonedDateTime)
    }

    /**
     * Format timestamp for full date-time display (dd/MM/yy HH:mm)
     */
    fun formatFullDateTime(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)
        return fullDateTimeFormatter.format(zonedDateTime)
    }

    /**
     * Parse date string back to timestamp for filtering (dd/MM/yyyy)
     *
     * @return the start of that day, or null if the string is not a dd/MM/yyyy date
     */
    fun parseDateForFilter(dateString: String, zone: ZoneId = getConfiguredZone()): Long? {
        return try {
            val localDate = LocalDate.parse(dateString, displayDateFormatter)
            val zonedDateTime = localDate.atStartOfDay(zone)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Check if two timestamps are on the same day
     */
    fun isOnSameDay(timestamp1: Long, timestamp2: Long, zone: ZoneId = getConfiguredZone()): Boolean {
        val date1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp1), zone).toLocalDate()
        val date2 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp2), zone).toLocalDate()
        return date1 == date2
    }

    /**
     * Get today's date formatted for display
     */
    fun getTodayFormatted(zone: ZoneId = getConfiguredZone()): String {
        return formatDateForDisplay(System.currentTimeMillis(), zone)
    }

    /**
     * Get start of day timestamp (00:00:00) for a given date
     */
    fun getStartOfDay(timestamp: Long, zone: ZoneId = getConfiguredZone()): Long {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        val startOfDay = zonedDateTime.toLocalDate().atStartOfDay(zone)
        return startOfDay.toInstant().toEpochMilli()
    }

    /**
     * Get end of day timestamp (23:59:59.999) for a given date
     */
    fun getEndOfDay(timestamp: Long, zone: ZoneId = getConfiguredZone()): Long {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        val endOfDay = zonedDateTime.toLocalDate().atTime(23, 59, 59, 999_999_999).atZone(zone)
        return endOfDay.toInstant().toEpochMilli()
    }

    /**
     * Get current time formatted for display (HH:mm)
     */
    fun getCurrentTimeFormatted(zone: ZoneId = getConfiguredZone()): String {
        return formatTimeForDisplay(System.currentTimeMillis(), zone)
    }

    /**
     * Parse time string to hour and minute (HH:mm)
     *
     * The range is checked here rather than left to whoever uses the pair: an hour of 99
     * parses as an integer and only fails further along, where it is no longer obvious that
     * the time string was the problem.
     *
     * @return the hour and minute, or null if the string is not a valid HH:mm time
     */
    fun parseTime(timeString: String, zone: ZoneId = getConfiguredZone()): Pair<Int, Int>? {
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return Pair(hour, minute)
    }

    /**
     * Combine date string (dd/MM/yyyy) and time string (HH:mm) into timestamp
     *
     * @return that moment, or null if either string cannot be read
     */
    fun combineDateTime(dateString: String, timeString: String, zone: ZoneId = getConfiguredZone()): Long? {
        val localDate = try {
            LocalDate.parse(dateString, displayDateFormatter)
        } catch (e: DateTimeParseException) {
            return null
        }
        val (hour, minute) = parseTime(timeString, zone) ?: return null
        val localDateTime = localDate.atTime(hour, minute, 0, 0)
        return localDateTime.atZone(zone).toInstant().toEpochMilli()
    }

    // ================================================================
    // ISO 8601 Conversion Helpers (for custom fields DATE/TIME/DATETIME)
    // ================================================================

    /**
     * Parse ISO 8601 date string to timestamp (YYYY-MM-DD → Long)
     * Used for custom fields DATE type
     *
     * @return the start of that day, or null if the string is not an ISO 8601 date
     */
    fun parseIso8601Date(dateStr: String, zone: ZoneId = getConfiguredZone()): Long? {
        return try {
            val localDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val zonedDateTime = localDate.atStartOfDay(zone)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Parse ISO 8601 time string to timestamp (HH:MM → Long, today's date)
     * Used for custom fields TIME type
     *
     * @return that time today, or null if the string is not an ISO 8601 time
     */
    fun parseIso8601Time(timeStr: String, zone: ZoneId = getConfiguredZone()): Long? {
        return try {
            val localTime = LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME)
            val today = LocalDate.now(zone)
            val zonedDateTime = ZonedDateTime.of(today, localTime, zone)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Convert timestamp to ISO 8601 date string (Long → YYYY-MM-DD)
     * Used for custom fields DATE type
     */
    fun timestampToIso8601Date(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Convert timestamp to ISO 8601 time string (Long → HH:MM)
     * Used for custom fields TIME type
     */
    fun timestampToIso8601Time(timestamp: Long, zone: ZoneId = getConfiguredZone()): String {
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

}
