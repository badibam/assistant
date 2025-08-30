package com.assistant.tools.tracking.handlers

import org.json.JSONObject
import org.json.JSONArray

/**
 * Handler for timer tracking type
 * Manages activity timing and duration tracking data
 */
class TimerTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "timer"
    
    override fun createValueJson(properties: Map<String, Any>): String? {
        val activity = properties["activity"] as? String ?: return null
        val durationMinutes = properties["duration_minutes"] as? Int ?: return null
        
        if (activity.isBlank() || durationMinutes <= 0) return null
        
        return JSONObject().apply {
            put("activity", activity.trim())
            put("duration_minutes", durationMinutes)
            put("type", "timer")
            put("raw", formatDuration(activity.trim(), durationMinutes))
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        val activity = properties["activity"] as? String ?: return false
        val durationMinutes = properties["duration_minutes"] as? Int ?: return false
        
        return activity.isNotBlank() && durationMinutes > 0
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "timer")
            put("activities", JSONArray().apply {
                put("Travail")
                put("Pause")
                put("Sport")
                put("Lecture")
            })
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        if (config.optString("type") != "timer") return false
        
        val activities = config.optJSONArray("activities") ?: return false
        return activities.length() > 0
    }
    
    /**
     * Format duration for display
     */
    private fun formatDuration(activity: String, minutes: Int): String {
        return when {
            minutes < 60 -> "$activity: ${minutes}min"
            minutes % 60 == 0 -> "$activity: ${minutes / 60}h"
            else -> "$activity: ${minutes / 60}h${minutes % 60}min"
        }
    }
}