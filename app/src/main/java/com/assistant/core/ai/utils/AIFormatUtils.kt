package com.assistant.core.ai.utils

import java.text.NumberFormat
import java.util.*

/**
 * AI-specific formatting utilities for tokens and costs
 * Used across AI components for consistent formatting
 */
object AIFormatUtils {

    /**
     * Format token count with thousands separator
     * Examples: 45231 → "45,231", 1000000 → "1,000,000"
     *
     * @param count Token count
     * @return Formatted token count with separator
     */
    fun formatTokenCount(count: Int): String {
        val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
        return formatter.format(count)
    }

    /**
     * Format cost in USD
     * Examples: 0.023 → "$0.023", 1.5 → "$1.50"
     *
     * @param cost Cost in USD
     * @return Formatted cost string with dollar sign
     */
    fun formatCost(cost: Double?): String {
        if (cost == null) return "-"
        return String.format(Locale.US, "$%.3f", cost)
    }
}
