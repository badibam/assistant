package com.assistant.tools.tracking.handlers

import org.json.JSONObject

/**
 * Handler for boolean tracking type
 * Manages true/false state tracking data
 */
class BooleanTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "boolean"
    
    override fun createDataJson(properties: Map<String, Any>): String? {
        val state = properties["state"] as? Boolean ?: return null
        val trueLabel = properties["true_label"] as? String ?: "Oui"
        val falseLabel = properties["false_label"] as? String ?: "Non"
        
        return JSONObject().apply {
            put("state", state)
            put("true_label", trueLabel)
            put("false_label", falseLabel)
            put("type", "boolean")
            put("raw", if (state) trueLabel else falseLabel)
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        return properties["state"] is Boolean
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "boolean")
            put("true_label", "Oui")
            put("false_label", "Non")
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        return config.optString("type") == "boolean"
    }
}