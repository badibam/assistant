package com.assistant.core.utils

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * Formatting utilities for durations and relative times
 * Generic utilities used across the application for consistent formatting
 */
object FormatUtils {

    /**
     * Format duration in milliseconds to human-readable string
     * Examples: "2m 33s", "1h 15m", "45s", "3d 2h"
     *
     * @param durationMs Duration in milliseconds
     * @return Formatted duration string
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs < 0) return "0s"

        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> {
                val remainingHours = hours % 24
                if (remainingHours > 0) "${days}j ${remainingHours}h"
                else "${days}j"
            }
            hours > 0 -> {
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "${hours}h ${remainingMinutes}m"
                else "${hours}h"
            }
            minutes > 0 -> {
                val remainingSeconds = seconds % 60
                if (remainingSeconds > 0) "${minutes}m ${remainingSeconds}s"
                else "${minutes}m"
            }
            else -> "${seconds}s"
        }
    }

    /**
     * Format relative time for future timestamp
     * Examples: "dans 2h", "dans 15 min", "dans 3 jours", "maintenant"
     *
     * @param timestamp Future timestamp in milliseconds
     * @param context Android context for strings
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: Long, context: Context): String {
        val s = Strings.`for`(context = context)
        val now = System.currentTimeMillis()
        val diffMs = timestamp - now

        // Past or now
        if (diffMs <= 0) {
            return s.shared("time_now")
        }

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> s.shared("time_in_days").format(days)
            hours > 0 -> s.shared("time_in_hours").format(hours)
            minutes > 0 -> s.shared("time_in_minutes").format(minutes)
            else -> s.shared("time_in_seconds").format(seconds)
        }
    }

    /**
     * Format relative time for past timestamp
     * Examples: "il y a 2h", "il y a 15 min", "il y a 3 jours", "à l'instant"
     *
     * @param timestamp Past timestamp in milliseconds
     * @param context Android context for strings
     * @return Formatted relative time string
     */
    fun formatRelativeTimePast(timestamp: Long, context: Context): String {
        val s = Strings.`for`(context = context)
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp

        // Future or now
        if (diffMs <= 0) {
            return s.shared("time_now")
        }

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> s.shared("time_ago_days").format(days)
            hours > 0 -> s.shared("time_ago_hours").format(hours)
            minutes > 0 -> s.shared("time_ago_minutes").format(minutes)
            else -> s.shared("time_ago_seconds").format(seconds)
        }
    }
}
