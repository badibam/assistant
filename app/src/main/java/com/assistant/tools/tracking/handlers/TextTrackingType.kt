package com.assistant.tools.tracking.handlers

import org.json.JSONObject

/**
 * Handler for text tracking type
 * Manages free-text tracking data
 */
class TextTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "text"
    
    override fun createDataJson(properties: Map<String, Any>): String? {
        val text = properties["text"] as? String ?: return null
        
        // Basic validation - not empty
        if (text.isBlank()) return null
        
        return JSONObject().apply {
            put("text", text.trim())
            put("type", "text")
            put("raw", text.trim())
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        val text = properties["text"] as? String ?: return false
        return text.isNotBlank()
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "text")
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        return config.optString("type") == "text"
    }
}