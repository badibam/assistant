package com.assistant.core.utils

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Centralized date utilities for the entire application
 * Ensures consistent date formatting and parsing across all components
 *
 * IMPORTANT: All operations use the timezone configured in AppConfig, NOT the system timezone.
 * This ensures consistency across the application regardless of device timezone changes.
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
     * Uses configured timezone from AppConfig
     */
    fun formatDateForDisplay(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, getConfiguredZone())
        return displayDateFormatter.format(zonedDateTime)
    }

    /**
     * Format timestamp for time display (HH:mm)
     * Uses configured timezone from AppConfig
     */
    fun formatTimeForDisplay(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, getConfiguredZone())
        return displayTimeFormatter.format(zonedDateTime)
    }

    /**
     * Format timestamp for full date-time display (dd/MM/yy HH:mm)
     * Uses configured timezone from AppConfig
     */
    fun formatFullDateTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, getConfiguredZone())
        return fullDateTimeFormatter.format(zonedDateTime)
    }

    /**
     * Parse date string back to timestamp for filtering (dd/MM/yyyy)
     * Uses configured timezone from AppConfig
     */
    fun parseDateForFilter(dateString: String): Long {
        return try {
            val localDate = LocalDate.parse(dateString, displayDateFormatter)
            val zonedDateTime = localDate.atStartOfDay(getConfiguredZone())
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    /**
     * Check if two timestamps are on the same day
     * Uses configured timezone from AppConfig
     */
    fun isOnSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val zone = getConfiguredZone()
        val date1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp1), zone).toLocalDate()
        val date2 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp2), zone).toLocalDate()
        return date1 == date2
    }

    /**
     * Get today's date formatted for display
     * Uses configured timezone from AppConfig
     */
    fun getTodayFormatted(): String {
        return formatDateForDisplay(System.currentTimeMillis())
    }

    /**
     * Get start of day timestamp (00:00:00) for a given date
     * Uses configured timezone from AppConfig
     */
    fun getStartOfDay(timestamp: Long): Long {
        val zone = getConfiguredZone()
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        val startOfDay = zonedDateTime.toLocalDate().atStartOfDay(zone)
        return startOfDay.toInstant().toEpochMilli()
    }

    /**
     * Get end of day timestamp (23:59:59.999) for a given date
     * Uses configured timezone from AppConfig
     */
    fun getEndOfDay(timestamp: Long): Long {
        val zone = getConfiguredZone()
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        val endOfDay = zonedDateTime.toLocalDate().atTime(23, 59, 59, 999_999_999).atZone(zone)
        return endOfDay.toInstant().toEpochMilli()
    }
    
    /**
     * Get current time formatted for display (HH:mm)
     * Uses configured timezone from AppConfig
     */
    fun getCurrentTimeFormatted(): String {
        return formatTimeForDisplay(System.currentTimeMillis())
    }

    /**
     * Parse time string to hour and minute (HH:mm)
     */
    fun parseTime(timeString: String): Pair<Int, Int> {
        return try {
            val parts = timeString.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                Pair(hour, minute)
            } else {
                val zone = getConfiguredZone()
                val now = ZonedDateTime.now(zone)
                Pair(now.hour, now.minute)
            }
        } catch (e: Exception) {
            val zone = getConfiguredZone()
            val now = ZonedDateTime.now(zone)
            Pair(now.hour, now.minute)
        }
    }

    /**
     * Combine date string (dd/MM/yyyy) and time string (HH:mm) into timestamp
     * Uses configured timezone from AppConfig
     */
    fun combineDateTime(dateString: String, timeString: String): Long {
        return try {
            val localDate = LocalDate.parse(dateString, displayDateFormatter)
            val (hour, minute) = parseTime(timeString)
            val localDateTime = localDate.atTime(hour, minute, 0, 0)
            val zonedDateTime = localDateTime.atZone(getConfiguredZone())
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // ================================================================
    // ISO 8601 Conversion Helpers (for custom fields DATE/TIME/DATETIME)
    // ================================================================

    /**
     * Parse ISO 8601 date string to timestamp (YYYY-MM-DD → Long)
     * Used for custom fields DATE type
     * Uses configured timezone from AppConfig
     */
    fun parseIso8601Date(dateStr: String): Long {
        return try {
            val localDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val zonedDateTime = localDate.atStartOfDay(getConfiguredZone())
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Parse ISO 8601 time string to timestamp (HH:MM → Long, today's date)
     * Used for custom fields TIME type
     * Uses configured timezone from AppConfig
     */
    fun parseIso8601Time(timeStr: String): Long {
        return try {
            val localTime = LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME)
            val zone = getConfiguredZone()
            val today = LocalDate.now(zone)
            val zonedDateTime = ZonedDateTime.of(today, localTime, zone)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Parse ISO 8601 datetime string to timestamp (YYYY-MM-DDTHH:MM:SS → Long)
     * Used for custom fields DATETIME type
     * Uses configured timezone from AppConfig
     */
    fun parseIso8601DateTime(dateTimeStr: String): Long {
        return try {
            val localDateTime = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val zonedDateTime = localDateTime.atZone(getConfiguredZone())
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Convert timestamp to ISO 8601 date string (Long → YYYY-MM-DD)
     * Used for custom fields DATE type
     * Uses configured timezone from AppConfig
     */
    fun timestampToIso8601Date(timestamp: Long): String {
        val zone = getConfiguredZone()
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Convert timestamp to ISO 8601 time string (Long → HH:MM)
     * Used for custom fields TIME type
     * Uses configured timezone from AppConfig
     */
    fun timestampToIso8601Time(timestamp: Long): String {
        val zone = getConfiguredZone()
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    /**
     * Convert timestamp to ISO 8601 datetime string (Long → YYYY-MM-DDTHH:MM:SS)
     * Used for custom fields DATETIME type
     * Uses configured timezone from AppConfig
     */
    fun timestampToIso8601DateTime(timestamp: Long): String {
        val zone = getConfiguredZone()
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        return zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}