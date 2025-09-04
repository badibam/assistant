package com.assistant.core.validation

import android.util.Log

/**
 * Utility for extracting sub-schemas from parent JSON schemas
 * Supports nested paths and conditional schema resolution
 */
object SchemaExtractor {
    private const val TAG = "SchemaExtractor"
    
    /**
     * Extracts a sub-schema from a parent schema using a property path
     * @param parentSchema The parent JSON schema string
     * @param path Dot-separated path to the sub-schema (e.g., "properties.items", "properties.nested.properties.sub_items")
     * @param context Context data for resolving conditional schemas
     * @return The extracted sub-schema as JSON string, or null if not found
     */
    fun extractSubSchema(
        parentSchema: String,
        path: String,
        context: Map<String, Any> = emptyMap()
    ): String? {
        safeLog("Extracting sub-schema - path: $path, context: $context")
        
        return try {
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            
            // Resolve conditional schemas first if context is provided
            val resolvedSchema = if (context.isNotEmpty()) {
                SchemaResolver.resolve(parentSchema, context)
            } else {
                parentSchema
            }
            
            val resolvedSchemaNode = objectMapper.readTree(resolvedSchema)
            
            // Navigate the path to find the sub-schema
            val pathParts = path.split(".")
            var currentNode = resolvedSchemaNode
            
            for (part in pathParts) {
                if (currentNode.has(part)) {
                    currentNode = currentNode.get(part)
                } else {
                    safeLog("Path part '$part' not found in schema")
                    return null
                }
            }
            
            val result = objectMapper.writeValueAsString(currentNode)
            safeLog("Successfully extracted sub-schema: ${result.take(100)}...")
            result
            
        } catch (e: Exception) {
            safeLog("Error extracting sub-schema: ${e.message}")
            null
        }
    }
    
    /**
     * Safe logging that works in both Android and unit test environments
     */
    private fun safeLog(message: String) {
        try {
            Log.d(TAG, message)
        } catch (e: RuntimeException) {
            println("$TAG: $message")
        }
    }
}