package com.assistant.tools.tracking

import org.json.JSONObject

/**
 * Utility functions for tracking operations
 * COMPATIBILITY WRAPPER - delegates to new NumericTrackingType handler
 * This class maintains backward compatibility while we migrate to the new architecture
 */
object TrackingUtils {
    
    
    /**
     * Convert JSON data object to validation format
     * Ensures consistent type conversion for schema validation
     * 
     * @param dataJson The JSON string with user input format
     * @param trackingType The tracking type (numeric, text, etc.)
     * @return Map with proper types for validation
     */
    fun convertToValidationFormat(dataJson: String, trackingType: String): Map<String, Any> {
        return try {
            val dataJsonObj = JSONObject(dataJson)
            val map = mutableMapOf<String, Any>()
            
            dataJsonObj.keys().forEach { key ->
                val value = dataJsonObj.get(key)
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

}