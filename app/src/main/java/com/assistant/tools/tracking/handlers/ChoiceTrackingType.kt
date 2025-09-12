package com.assistant.tools.tracking.handlers

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/**
 * Handler for choice tracking type
 * Manages single or multiple choice selection tracking data
 */
class ChoiceTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "choice"
    
    override fun createDataJson(properties: Map<String, Any>, context: Context): String? {
        val selectedOption = properties["selected_option"] as? String
        val availableOptions = properties["available_options"] as? List<*>
        
        // Validate available options
        if (availableOptions.isNullOrEmpty()) return null
        val validOptions = availableOptions.filterIsInstance<String>()
        if (validOptions.isEmpty()) return null
        
        // Single choice only
        if (selectedOption == null || selectedOption !in validOptions) return null
        
        return JSONObject().apply {
            put("selected_option", selectedOption)
            put("available_options", JSONArray(validOptions))
            put("type", "choice")
            put("raw", selectedOption)
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>, context: Context): Boolean {
        val selectedOption = properties["selected_option"] as? String
        val availableOptions = properties["available_options"] as? List<*>
        
        if (availableOptions.isNullOrEmpty()) return false
        val validOptions = availableOptions.filterIsInstance<String>()
        if (validOptions.isEmpty()) return false
        
        return selectedOption != null && selectedOption in validOptions
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "choice")
            put("options", JSONArray(listOf("Option 1", "Option 2", "Option 3")))
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        if (config.optString("type") != "choice") return false
        
        val options = config.optJSONArray("options") ?: return false
        return options.length() > 0
    }
}