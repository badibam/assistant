package com.assistant.core.validation

import android.util.Log

/**
 * Utility for resolving conditional JSON schemas
 * Handles if/then/else, allOf, oneOf constructs and returns flattened schemas
 */
object SchemaResolver {
    private const val TAG = "SchemaResolver"
    
    /**
     * Resolves conditional schemas using provided context
     * @param schema The JSON schema with conditional constructs
     * @param context Context data to resolve conditions (e.g., {"type": "numeric"})
     * @return Flattened schema with conditions resolved
     */
    fun resolve(schema: String, context: Map<String, Any>): String {
        safeLog("Resolving conditional schema with context: $context")
        
        return try {
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val schemaNode = objectMapper.readTree(schema)
            
            // Check for conditional constructs
            val resolved = when {
                schemaNode.has("allOf") -> resolveAllOf(schemaNode, context, objectMapper)
                schemaNode.has("oneOf") -> resolveOneOf(schemaNode, context, objectMapper)
                schemaNode.has("if") -> resolveIfThen(schemaNode, context, objectMapper)
                else -> schemaNode // No conditions to resolve
            }
            
            val result = objectMapper.writeValueAsString(resolved)
            safeLog("Schema resolved: ${result.take(100)}...")
            result
            
        } catch (e: Exception) {
            safeLog("Error resolving schema: ${e.message}")
            schema // Return original on error
        }
    }
    
    /**
     * Resolves allOf constructs by merging matching conditions
     */
    private fun resolveAllOf(
        schemaNode: com.fasterxml.jackson.databind.JsonNode,
        context: Map<String, Any>,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ): com.fasterxml.jackson.databind.JsonNode {
        val allOf = schemaNode.get("allOf")
        val result = objectMapper.createObjectNode()
        
        // Copy base properties
        schemaNode.fields().forEach { (key, value) ->
            if (key != "allOf") {
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
        }
        
        // Process each condition in allOf
        for (condition in allOf) {
            if (condition.has("if") && condition.has("then")) {
                if (matchesCondition(condition.get("if"), context)) {
                    safeLog("Condition matched in allOf")
                    // Merge the "then" part into result
                    mergeSchemas(result, condition.get("then"), objectMapper)
                }
            } else {
                // Direct schema without condition - always merge
                mergeSchemas(result, condition, objectMapper)
            }
        }
        
        return result
    }
    
    /**
     * Resolves oneOf constructs by finding the first matching condition
     */
    private fun resolveOneOf(
        schemaNode: com.fasterxml.jackson.databind.JsonNode,
        context: Map<String, Any>,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ): com.fasterxml.jackson.databind.JsonNode {
        val oneOf = schemaNode.get("oneOf")
        val result = objectMapper.createObjectNode()
        
        // Copy base properties
        schemaNode.fields().forEach { (key, value) ->
            if (key != "oneOf") {
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
        }
        
        // Find first matching condition
        for (condition in oneOf) {
            if (condition.has("if")) {
                if (matchesCondition(condition.get("if"), context)) {
                    safeLog("Condition matched in oneOf")
                    mergeSchemas(result, condition, objectMapper)
                    break
                }
            }
        }
        
        return result
    }
    
    /**
     * Resolves if/then/else constructs
     */
    private fun resolveIfThen(
        schemaNode: com.fasterxml.jackson.databind.JsonNode,
        context: Map<String, Any>,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ): com.fasterxml.jackson.databind.JsonNode {
        val result = objectMapper.createObjectNode()
        
        // Copy base properties
        schemaNode.fields().forEach { (key, value) ->
            if (key !in listOf("if", "then", "else")) {
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
        }
        
        // Apply if/then/else logic
        val ifCondition = schemaNode.get("if")
        if (matchesCondition(ifCondition, context)) {
            safeLog("If condition matched")
            schemaNode.get("then")?.let { thenSchema ->
                mergeSchemas(result, thenSchema, objectMapper)
            }
        } else {
            safeLog("If condition not matched")
            schemaNode.get("else")?.let { elseSchema ->
                mergeSchemas(result, elseSchema, objectMapper)
            }
        }
        
        return result
    }
    
    /**
     * Checks if a condition matches the provided context
     */
    private fun matchesCondition(
        condition: com.fasterxml.jackson.databind.JsonNode,
        context: Map<String, Any>
    ): Boolean {
        val properties = condition.get("properties") ?: return false
        
        // Check each property condition
        properties.fields().forEach { (key, value) ->
            val contextValue = context[key] ?: return false
            
            // Handle const condition: "type": { "const": "numeric" }
            if (value.has("const")) {
                val expectedValue = value.get("const").asText()
                if (contextValue.toString() != expectedValue) {
                    return false
                }
            }
            
            // Handle enum condition: "type": { "enum": ["numeric", "text"] }
            if (value.has("enum")) {
                val enumValues = value.get("enum").map { it.asText() }
                if (contextValue.toString() !in enumValues) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Merges two schema nodes together
     */
    private fun mergeSchemas(
        target: com.fasterxml.jackson.databind.node.ObjectNode,
        source: com.fasterxml.jackson.databind.JsonNode,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ) {
        source.fields().forEach { (key, value) ->
            if (target.has(key) && target.get(key).isObject && value.isObject) {
                // Recursive merge for nested objects
                val targetChild = target.get(key) as com.fasterxml.jackson.databind.node.ObjectNode
                mergeSchemas(targetChild, value, objectMapper)
            } else {
                // Direct assignment
                target.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
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