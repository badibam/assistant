package com.assistant.tools.tracking.handlers

import org.json.JSONObject

/**
 * Interface for handling different types of tracking data
 * Each tracking type (numeric, scale, text, etc.) implements this interface
 */
interface TrackingTypeHandler {
    
    /**
     * Get the type identifier for this handler
     * @return Type identifier string (e.g., "numeric", "scale", "text")
     */
    fun getType(): String
    
    /**
     * Create JSON data from user input properties
     * @param properties Map of input properties from the UI
     * @return JSON string for storage, or null if validation fails
     */
    fun createDataJson(properties: Map<String, Any>): String?
    
    /**
     * Validate input properties before creating JSON
     * @param properties Map of input properties to validate
     * @return True if the input is valid, false otherwise
     */
    fun validateInput(properties: Map<String, Any>): Boolean
    
    /**
     * Get default configuration for this tracking type
     * @return JSONObject containing default configuration
     */
    fun getDefaultConfig(): JSONObject
    
    /**
     * Validate configuration JSON for this tracking type
     * @param config Configuration JSON to validate
     * @return True if configuration is valid, false otherwise
     */
    fun validateConfig(config: JSONObject): Boolean
}