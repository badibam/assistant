package com.assistant.tools.tracking

import com.assistant.core.utils.NumberFormatting
import org.json.JSONObject

/**
 * Utility functions for tracking operations
 */
object TrackingUtils {
    
    /**
     * Create JSON value for numeric tracking data
     * Returns null if the quantity cannot be parsed as a valid number
     * 
     * @param quantity The numeric quantity as string (from user input)
     * @param unit The unit string (can be empty)
     * @return JSON string or null if parsing fails
     */
    fun createNumericValueJson(quantity: String, unit: String): String? {
        val numericValue = NumberFormatting.parseUserInput(quantity) ?: return null
        
        return JSONObject().apply {
            put("quantity", numericValue)
            put("unit", unit.trim())
            put("type", "numeric")
            put("raw", formatDisplayValue(numericValue, unit.trim()))
        }.toString()
    }
    
    /**
     * Format display value for consistent presentation
     */
    private fun formatDisplayValue(value: Double, unit: String): String {
        val formattedValue = if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            value.toString()
        }
        
        return if (unit.isNotBlank()) {
            "$formattedValue\u00A0$unit" // Non-breaking space
        } else {
            formattedValue
        }
    }
}