package com.assistant.tools.tracking.handlers

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import com.assistant.core.strings.Strings

/**
 * Handler for timer tracking type
 * Manages activity timing and duration tracking data
 */
class TimerTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "timer"
    
    override fun createDataJson(properties: Map<String, Any>): String? {
        val activity = properties["activity"] as? String ?: return null
        val durationSeconds = properties["duration_seconds"] as? Int ?: return null
        
        if (activity.isBlank() || durationSeconds <= 0) return null
        
        return JSONObject().apply {
            put("activity", activity.trim())
            put("duration_seconds", durationSeconds)
            put("type", "timer")
            put("raw", formatDuration(activity.trim(), durationSeconds))
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>): Boolean {
        val activity = properties["activity"] as? String ?: return false
        val durationSeconds = properties["duration_seconds"] as? Int ?: return false
        
        return activity.isNotBlank() && durationSeconds > 0
    }
    
    override fun getDefaultConfig(): JSONObject {
        return getDefaultConfig(null)
    }
    
    fun getDefaultConfig(context: Context?): JSONObject {
        return JSONObject().apply {
            put("type", "timer")
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
    private fun formatDuration(activity: String, seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        
        val duration = buildString {
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            if (s > 0 || (h == 0 && m == 0)) append("${s}s")
        }.trim()
        
        return "$activity: $duration"
    }
}