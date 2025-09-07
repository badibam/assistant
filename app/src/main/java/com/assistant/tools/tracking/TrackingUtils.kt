package com.assistant.tools.tracking

import com.assistant.tools.tracking.handlers.NumericTrackingType
import org.json.JSONObject

/**
 * Utility functions for tracking operations
 * COMPATIBILITY WRAPPER - delegates to new NumericTrackingType handler
 * This class maintains backward compatibility while we migrate to the new architecture
 */
object TrackingUtils {
    
    private val numericHandler = NumericTrackingType()
    
    /**
     * Convert JSON value object to validation format
     * Ensures consistent type conversion for schema validation
     * 
     * @param valueJson The JSON string with user input format
     * @param trackingType The tracking type (numeric, text, etc.)
     * @return Map with proper types for validation
     */
    fun convertToValidationFormat(valueJson: String, trackingType: String): Map<String, Any> {
        return try {
            val jsonObj = JSONObject(valueJson)
            val map = mutableMapOf<String, Any>()
            
            jsonObj.keys().forEach { key ->
                val value = jsonObj.get(key)
                // Convert quantity to number for numeric tracking types to match schema
                if (key == "quantity" && trackingType == "numeric" && value is String) {
                    try {
                        map[key] = value.toDouble()
                    } catch (e: NumberFormatException) {
                        map[key] = value
                    }
                } else {
                    map[key] = value
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Create JSON value for numeric tracking data
     * Returns null if the quantity cannot be parsed as a valid number
     * 
     * @param quantity The numeric quantity as string (from user input)
     * @param unit The unit string (can be empty)
     * @return JSON string or null if parsing fails
     * 
     * @deprecated Use NumericTrackingType directly via TrackingTypeFactory
     */
    @Deprecated("Use NumericTrackingType via TrackingTypeFactory instead")
    fun createNumericValueJson(quantity: String, unit: String): String? {
        return numericHandler.createValueJson(mapOf(
            "quantity" to quantity,
            "unit" to unit
        ))
    }
}