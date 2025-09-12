package com.assistant.tools.tracking.handlers

import android.content.Context
import com.assistant.core.utils.NumberFormatting
import org.json.JSONObject

/**
 * Handler for numeric tracking type
 * Manages quantity + unit tracking data
 */
class NumericTrackingType : TrackingTypeHandler {
    
    override fun getType(): String = "numeric"
    
    override fun createDataJson(properties: Map<String, Any>, context: Context): String? {
        val quantity = properties["quantity"] as? String ?: return null
        val unit = properties["unit"] as? String ?: ""
        
        val numericValue = NumberFormatting.parseUserInput(quantity, context) ?: return null
        
        return JSONObject().apply {
            put("quantity", numericValue)
            put("unit", unit.trim())
            put("type", "numeric")
            put("raw", formatDisplayValue(numericValue, unit.trim()))
        }.toString()
    }
    
    override fun validateInput(properties: Map<String, Any>, context: Context): Boolean {
        val quantity = properties["quantity"] as? String ?: return false
        return NumberFormatting.parseUserInput(quantity, context) != null
    }
    
    override fun getDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("type", "numeric")
        }
    }
    
    override fun validateConfig(config: JSONObject): Boolean {
        return config.optString("type") == "numeric"
    }
    
    /**
     * Format display value for consistent presentation
     */
    private fun formatDisplayValue(value: Double, unit: String): String {
        val formattedValue = if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            value.toString()
        }
        
        return if (unit.isNotBlank()) {
            "$formattedValue\u00A0$unit" // Non-breaking space
        } else {
            formattedValue
        }
    }
}