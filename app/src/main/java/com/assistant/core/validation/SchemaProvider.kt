package com.assistant.core.validation

/**
 * Interface for providing JSON schemas for validation
 * Implemented by tool types and other components that need validation
 */
interface SchemaProvider {
    /**
     * Gets the configuration schema for this provider
     * @return JSON Schema string for configuration validation
     */
    fun getConfigSchema(): String
    
    /**
     * Gets the data schema for this provider
     * @return JSON Schema string for data validation, or null if no data schema
     */
    fun getDataSchema(): String?
}