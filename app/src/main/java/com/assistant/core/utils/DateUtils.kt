package com.assistant.core.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Centralized date utilities for the entire application
 * Ensures consistent date formatting and parsing across all components
 */
object DateUtils {
    
    // Date format constants
    private const val DISPLAY_DATE_FORMAT = "dd/MM/yyyy"
    private const val DISPLAY_TIME_FORMAT = "HH:mm"
    private const val FULL_DATE_TIME_FORMAT = "dd/MM/yy HH:mm"
    
    private val displayDateFormat = SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.getDefault())
    private val displayTimeFormat = SimpleDateFormat(DISPLAY_TIME_FORMAT, Locale.getDefault())
    private val fullDateTimeFormat = SimpleDateFormat(FULL_DATE_TIME_FORMAT, Locale.getDefault())
    
    /**
     * Format timestamp for date display (dd/MM/yyyy)
     */
    fun formatDateForDisplay(timestamp: Long): String {
        return displayDateFormat.format(Date(timestamp))
    }
    
    /**
     * Format timestamp for time display (HH:mm)
     */
    fun formatTimeForDisplay(timestamp: Long): String {
        return displayTimeFormat.format(Date(timestamp))
    }
    
    /**
     * Format timestamp for full date-time display (dd/MM/yy HH:mm)
     */
    fun formatFullDateTime(timestamp: Long): String {
        return fullDateTimeFormat.format(Date(timestamp))
    }
    
    /**
     * Parse date string back to timestamp for filtering (dd/MM/yyyy)
     */
    fun parseDateForFilter(dateString: String): Long {
        return try {
            displayDateFormat.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    /**
     * Check if two timestamps are on the same day
     */
    fun isOnSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
    
    
    
    
    /**
     * Get today's date formatted for display
     */
    fun getTodayFormatted(): String {
        return formatDateForDisplay(System.currentTimeMillis())
    }
    
    /**
     * Get start of day timestamp (00:00:00) for a given date
     */
    fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
    
    /**
     * Get end of day timestamp (23:59:59.999) for a given date
     */
    fun getEndOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }
    
    /**
     * Get current time formatted for display (HH:mm)
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
                val cal = Calendar.getInstance()
                Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
        } catch (e: Exception) {
            val cal = Calendar.getInstance()
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
    }
    
    /**
     * Combine date string (dd/MM/yyyy) and time string (HH:mm) into timestamp
     */
    fun combineDateTime(dateString: String, timeString: String): Long {
        return try {
            val dateTimestamp = parseDateForFilter(dateString)
            val (hour, minute) = parseTime(timeString)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = dateTimestamp
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
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
     */
    fun parseIso8601Date(dateStr: String): Long {
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1 // Calendar months are 0-indexed
                val day = parts[2].toInt()

                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Parse ISO 8601 time string to timestamp (HH:MM → Long, today's date)
     * Used for custom fields TIME type
     */
    fun parseIso8601Time(timeStr: String): Long {
        return try {
            val (hour, minute) = parseTime(timeStr)

            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Parse ISO 8601 datetime string to timestamp (YYYY-MM-DDTHH:MM:SS → Long)
     * Used for custom fields DATETIME type
     */
    fun parseIso8601DateTime(dateTimeStr: String): Long {
        return try {
            val parts = dateTimeStr.split("T")
            if (parts.size == 2) {
                val dateStr = parts[0]
                val timeStr = parts[1].substringBefore(":") + ":" + parts[1].split(":").getOrNull(1)

                val dateParts = dateStr.split("-")
                if (dateParts.size == 3) {
                    val year = dateParts[0].toInt()
                    val month = dateParts[1].toInt() - 1
                    val day = dateParts[2].toInt()

                    val timeParts = timeStr.split(":")
                    val hour = timeParts[0].toInt()
                    val minute = timeParts.getOrNull(1)?.toInt() ?: 0

                    Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } else {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Convert timestamp to ISO 8601 date string (Long → YYYY-MM-DD)
     * Used for custom fields DATE type
     */
    fun timestampToIso8601Date(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        return "$year-$month-$day"
    }

    /**
     * Convert timestamp to ISO 8601 time string (Long → HH:MM)
     * Used for custom fields TIME type
     */
    fun timestampToIso8601Time(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", calendar.get(Calendar.MINUTE))
        return "$hour:$minute"
    }

    /**
     * Convert timestamp to ISO 8601 datetime string (Long → YYYY-MM-DDTHH:MM:SS)
     * Used for custom fields DATETIME type
     */
    fun timestampToIso8601DateTime(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        val hour = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", calendar.get(Calendar.MINUTE))
        val second = String.format("%02d", calendar.get(Calendar.SECOND))
        return "$year-$month-${day}T$hour:$minute:$second"
    }
}