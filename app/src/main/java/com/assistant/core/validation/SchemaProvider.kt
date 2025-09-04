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
    
    /**
     * Get user-friendly field name for display in validation errors
     * @param fieldName The technical field name (e.g., "quantity", "name")
     * @return User-friendly field name for display (e.g., "Quantité", "Nom")
     */
    fun getFormFieldName(fieldName: String): String
}