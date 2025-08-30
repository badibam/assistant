package com.assistant.tools.tracking.handlers

/**
 * Factory for creating tracking type handlers
 * Centralizes the management of different tracking types
 */
object TrackingTypeFactory {
    
    /**
     * Get a handler for the specified tracking type
     * @param type The tracking type identifier
     * @return Handler instance or null if type is not supported
     */
    fun getHandler(type: String): TrackingTypeHandler? {
        return when (type.lowercase()) {
            "numeric" -> NumericTrackingType()
            // Future types will be added here:
            // "scale" -> ScaleTrackingType()
            // "text" -> TextTrackingType()
            // "choice" -> ChoiceTrackingType()
            // "boolean" -> BooleanTrackingType()
            // "counter" -> CounterTrackingType()
            // "timer" -> TimerTrackingType()
            else -> null
        }
    }
    
    /**
     * Get all supported tracking types
     * @return List of supported type identifiers
     */
    fun getSupportedTypes(): List<String> {
        return listOf(
            "numeric"
            // Future types will be added here
        )
    }
    
    /**
     * Check if a tracking type is supported
     * @param type The tracking type identifier
     * @return True if the type is supported, false otherwise
     */
    fun isTypeSupported(type: String): Boolean {
        return getSupportedTypes().contains(type.lowercase())
    }
}