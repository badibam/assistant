package com.assistant.tools.tracking.handlers

import org.json.JSONObject
import org.json.JSONArray

/**
 * Handler for counter tracking type
 * Manages increment/decrement button-based tracking data
 */
class CounterTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "counter"
    
    override fun createValueJson(properties: Map<String, Any>): String? {
        val increment = properties["increment"] as? Int ?: return null
        val unit = properties["unit"] as? String ?: ""
        
        return JSONObject().apply {
            put("increment", increment)
            put("unit", unit.trim())
            put("type", "counter")
            put("raw", if (unit.isNotBlank()) {
                if (increment >= 0) "+$increment $unit" else "$increment $unit"
            } else {
                if (increment >= 0) "+$increment" else "$increment"
            })
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        return properties["increment"] is Int
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "counter")
            put("unit", "")
            put("increment_buttons", JSONArray().apply {
                put(JSONObject().apply {
                    put("display", "+1")
                    put("value", 1)
                })
                put(JSONObject().apply {
                    put("display", "+5")
                    put("value", 5)
                })
            })
            put("decrement_buttons", JSONArray().apply {
                put(JSONObject().apply {
                    put("display", "-1")
                    put("value", 1)
                })
            })
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        if (config.optString("type") != "counter") return false
        
        val incrementButtons = config.optJSONArray("increment_buttons") ?: return false
        return incrementButtons.length() > 0
    }
}