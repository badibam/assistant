package com.assistant.core.validation

/**
 * Interface for providing JSON schemas for validation
 * Implemented by tool types and other components that need validation
 */
interface SchemaProvider {
    /**
     * Gets the schema for the specified type
     * @param schemaType Schema type: "config", "data" for tool types, or custom types like "temporal"
     * @return JSON Schema string, or null if schema type not supported
     */
    fun getSchema(schemaType: String): String?
    
    /**
     * Get user-friendly field name for display in validation errors
     * @param fieldName The technical field name (e.g., "quantity", "name")
     * @param context Android context for string resource access (optional for backwards compatibility)
     * @return User-friendly field name for display (e.g., "Quantité", "Nom")
     */
    fun getFormFieldName(fieldName: String, context: android.content.Context? = null): String
}