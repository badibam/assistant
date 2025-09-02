package com.assistant.tools.tracking.handlers

import org.json.JSONObject

/**
 * Handler for scale tracking type
 * Manages numerical scale/rating tracking data
 */
class ScaleTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "scale"
    
    override fun createValueJson(properties: Map<String, Any>): String? {
        val rating = properties["rating"] as? Int ?: return null
        val minValue = properties["min_value"] as? Int ?: 1
        val maxValue = properties["max_value"] as? Int ?: 10
        val minLabel = properties["min_label"] as? String ?: ""
        val maxLabel = properties["max_label"] as? String ?: ""
        
        // Validate range
        if (rating < minValue || rating > maxValue) return null
        
        return JSONObject().apply {
            put("rating", rating)
            put("min_value", minValue)
            put("max_value", maxValue)
            put("min_label", minLabel)
            put("max_label", maxLabel)
            put("type", "scale")
            put("raw", "$rating ($minValue à $maxValue)")
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        val rating = properties["rating"] as? Int ?: return false
        val minValue = properties["min_value"] as? Int ?: 1
        val maxValue = properties["max_value"] as? Int ?: 10
        
        return rating in minValue..maxValue
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "scale")
            put("min_value", 1)
            put("max_value", 10)
            put("min_label", "Faible")
            put("max_label", "Élevé")
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        if (config.optString("type") != "scale") return false
        
        val minValue = config.optInt("min_value", 1)
        val maxValue = config.optInt("max_value", 10)
        
        return maxValue > minValue
    }
}