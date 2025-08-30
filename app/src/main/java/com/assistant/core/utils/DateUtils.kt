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
     * Check if timestamp is yesterday
     */
    fun isYesterday(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        val yesterday = now - 24 * 60 * 60 * 1000L
        return isOnSameDay(timestamp, yesterday)
    }
    
    /**
     * Check if timestamp is today
     */
    fun isToday(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        return isOnSameDay(timestamp, now)
    }
    
    /**
     * Format date with smart relative display (Today, Yesterday, or full date)
     */
    fun formatSmartDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L
        val diff = now - timestamp
        
        return when {
            diff < dayInMs && isToday(timestamp, now) -> "Aujourd'hui ${formatTimeForDisplay(timestamp)}"
            diff < 2 * dayInMs && isYesterday(timestamp, now) -> "Hier ${formatTimeForDisplay(timestamp)}"
            else -> formatFullDateTime(timestamp)
        }
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
}