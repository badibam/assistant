package com.assistant.tools.journal.utils

import android.content.Context
import com.assistant.core.strings.Strings
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.normalizeTimestampWithConfig
import com.assistant.core.utils.DateUtils

/**
 * Date formatting utilities specific to Journal tooltype
 *
 * Provides smart date formatting that:
 * - Shows "Aujourd'hui à HH:MM" for entries from today
 * - Shows "Le DD/MM/YYYY à HH:MM" for other dates
 *
 * Uses dayStartHour configuration to determine "today" correctly
 */
object DateFormatUtils {

    /**
     * Formats a journal entry timestamp with context-aware display
     *
     * @param timestamp The entry timestamp to format
     * @param context Android context for string resources
     * @return Formatted date string
     *
     * Examples:
     * - Today: "Aujourd'hui à 14:30"
     * - Other day: "Le 15/03/2025 à 09:15"
     */
    fun formatJournalDate(timestamp: Long, context: Context): String {
        val now = System.currentTimeMillis()

        // Normalize both timestamps to compare according to dayStartHour configuration
        // This ensures that if we're at 2am and dayStartHour=4, an entry at 3am
        // is still considered "yesterday"
        val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.DAY)
        val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.DAY)

        val isToday = (normalizedNow == normalizedTimestamp)
        val time = DateUtils.formatTimeForDisplay(timestamp)
        val s = Strings.`for`(tool = "journal", context = context)

        return if (isToday) {
            // Format: "Aujourd'hui à 14:30"
            s.tool("date_today_at").format(time)
        } else {
            // Format: "Le 15/03/2025 à 09:15"
            val date = DateUtils.formatDateForDisplay(timestamp)
            s.tool("date_on_at").format(date, time)
        }
    }
}
