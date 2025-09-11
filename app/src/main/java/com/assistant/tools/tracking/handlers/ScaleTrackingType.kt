package com.assistant.tools.tracking.handlers

import android.content.Context
import org.json.JSONObject
import com.assistant.core.strings.Strings

/**
 * Handler for scale tracking type
 * Manages numerical scale/rating tracking data
 */
class ScaleTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "scale"
    
    override fun createDataJson(properties: Map<String, Any>): String? {
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
            put("raw", "$rating ($minValue->$maxValue)")
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        val rating = properties["rating"] as? Int ?: return false
        val minValue = properties["min_value"] as? Int ?: 1
        val maxValue = properties["max_value"] as? Int ?: 10
        
        return rating in minValue..maxValue
    }
    
    override fun getDefaultConfig(): JSONObject {
        return getDefaultConfig(null)
    }
    
    fun getDefaultConfig(context: Context?): JSONObject {
        return JSONObject().apply {
            put("type", "scale")
            put("min_value", 1)
            put("max_value", 10)
            if (context != null) {
                val s = Strings.`for`(tool = "tracking", context = context)
                put("min_label", s.tool("scale_min_default"))
                put("max_label", s.tool("scale_max_default"))
            } else {
                // Use empty strings when context is not available
                put("min_label", "")
                put("max_label", "")
            }
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        if (config.optString("type") != "scale") return false
        
        val minValue = config.optInt("min_value", 1)
        val maxValue = config.optInt("max_value", 10)
        
        return maxValue > minValue
    }
}