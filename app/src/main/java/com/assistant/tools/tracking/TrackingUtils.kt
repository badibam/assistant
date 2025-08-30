package com.assistant.tools.tracking

import com.assistant.tools.tracking.handlers.NumericTrackingType

/**
 * Utility functions for tracking operations
 * COMPATIBILITY WRAPPER - delegates to new NumericTrackingType handler
 * This class maintains backward compatibility while we migrate to the new architecture
 */
object TrackingUtils {
    
    private val numericHandler = NumericTrackingType()
    
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