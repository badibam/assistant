package com.assistant.tools.tracking

import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Utility functions for tracking operations
 * COMPATIBILITY WRAPPER - delegates to new NumericTrackingType handler
 * This class maintains backward compatibility while we migrate to the new architecture
 */
object TrackingUtils {
    
    
    /**
     * Reads the entry's data into the Map form the schema validator takes.
     *
     * Each dialog writes the types its schema declares, so nothing is converted here. A failure
     * means the dialog built something that is not a JSON object, which is a fault in the code
     * rather than in what the user typed, so it is logged rather than passed on silently.
     *
     * @param dataJson The JSON string the entry dialog built
     * @param trackingType The tracking type (numeric, text, etc.)
     * @return The data as a Map, or an empty one if it could not be read
     */
    fun convertToValidationFormat(dataJson: String, trackingType: String): Map<String, Any> {
        return try {
            val dataJsonObj = JSONObject(dataJson)
            dataJsonObj.keys().asSequence().associateWith { key -> dataJsonObj.get(key) }
        } catch (e: Exception) {
            LogManager.tracking(
                "Unreadable $trackingType entry data, validation will see nothing: ${e.message}",
                "ERROR",
                e
            )
            emptyMap()
        }
    }

}