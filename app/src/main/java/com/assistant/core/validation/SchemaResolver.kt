package com.assistant.core.validation

import com.assistant.core.utils.LogManager

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
        LogManager.schema("Resolving conditional schema with context: $context")
        
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
            LogManager.schema("=== Schema resolved ===")
            LogManager.schema(result)
            LogManager.schema("=== End resolved ===")
            result
            
        } catch (e: Exception) {
            LogManager.schema("Error resolving schema: ${e.message}", "ERROR", e)
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
            LogManager.schema("Processing allOf condition: $condition")
            if (condition.has("if") && condition.has("then")) {
                LogManager.schema("Found if/then condition - checking match...")
                if (matchesCondition(condition.get("if"), context)) {
                    LogManager.schema("Condition matched in allOf")
                    // Merge the "then" part into result
                    mergeSchemas(result, condition.get("then"), objectMapper)
                }
            } else {
                LogManager.schema("Direct schema without condition - merging...")
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
                    LogManager.schema("Condition matched in oneOf")
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
            LogManager.schema("If condition matched")
            schemaNode.get("then")?.let { thenSchema ->
                mergeSchemas(result, thenSchema, objectMapper)
            }
        } else {
            LogManager.schema("If condition not matched")
            schemaNode.get("else")?.let { elseSchema ->
                mergeSchemas(result, elseSchema, objectMapper)
            }
        }
        
        return result
    }
    
    /**
     * Checks if a condition matches the provided context
     * Now handles nested properties like "value": { "properties": { "type": { "const": "numeric" } } }
     */
    private fun matchesCondition(
        condition: com.fasterxml.jackson.databind.JsonNode,
        context: Map<String, Any>
    ): Boolean {
        LogManager.schema("=== Match condition start ===")
        LogManager.schema("Checking condition: $condition")
        LogManager.schema("Against context: $context")
        
        val properties = condition.get("properties")
        if (properties == null) {
            LogManager.schema("No properties in condition - returning false")
            LogManager.schema("=== Match condition end (FALSE) ===")
            return false
        }
        
        // Check each property condition
        properties.fields().forEach { (key, value) ->
            LogManager.schema("Checking key '$key' with condition: $value")
            
            if (!matchesPropertyCondition(key, value, context)) {
                LogManager.schema("Property condition failed for '$key'")
                LogManager.schema("=== Match condition end (FALSE) ===")
                return false
            }
            
            LogManager.schema("Property condition matched for '$key' - continuing...")
        }
        
        LogManager.schema("All conditions matched - returning true")
        LogManager.schema("=== Match condition end (TRUE) ===")
        return true
    }
    
    /**
     * Checks if a single property condition matches
     * Handles both simple properties and nested object properties
     */
    private fun matchesPropertyCondition(
        propertyPath: String,
        condition: com.fasterxml.jackson.databind.JsonNode,
        context: Map<String, Any>
    ): Boolean {
        LogManager.schema("Matching property '$propertyPath' with condition: $condition")
        
        // Get the value from context (could be nested)
        val contextValue = getNestedValue(propertyPath, context)
        LogManager.schema("Context value for '$propertyPath': $contextValue")
        
        if (contextValue == null) {
            LogManager.schema("Property '$propertyPath' not found in context")
            return false
        }
        
        // Handle direct const/enum conditions: "type": { "const": "numeric" }
        if (condition.has("const")) {
            val expectedValue = condition.get("const").asText()
            LogManager.schema("Direct const check: expected='$expectedValue', actual='${contextValue.toString()}'")
            return contextValue.toString() == expectedValue
        }
        
        if (condition.has("enum")) {
            val enumValues = condition.get("enum").map { it.asText() }
            LogManager.schema("Direct enum check: expected=$enumValues, actual='${contextValue.toString()}'")
            return contextValue.toString() in enumValues
        }
        
        // Handle nested object conditions: "value": { "properties": { "type": { "const": "numeric" } } }
        if (condition.has("properties")) {
            LogManager.schema("Nested properties condition detected")
            val nestedProperties = condition.get("properties")
            val nestedContext = contextValue as? Map<*, *>
            
            if (nestedContext == null) {
                LogManager.schema("Expected nested object but got: ${contextValue::class.simpleName}")
                return false
            }
            
            // Convert to proper Map<String, Any>
            val stringKeyContext = nestedContext.mapKeys { it.key.toString() }
                .mapValues { it.value ?: "" }
            
            // Check all nested properties
            nestedProperties.fields().forEach { (nestedKey, nestedCondition) ->
                if (!matchesPropertyCondition(nestedKey, nestedCondition, stringKeyContext)) {
                    LogManager.schema("Nested property '$nestedKey' condition failed")
                    return false
                }
            }
            
            LogManager.schema("All nested properties matched")
            return true
        }
        
        LogManager.schema("No recognizable condition type found")
        return false
    }
    
    /**
     * Gets a value from context, supporting simple property access
     * For now, handles only direct property access (no deep nesting like "a.b.c")
     */
    private fun getNestedValue(propertyPath: String, context: Map<String, Any>): Any? {
        return context[propertyPath]
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
    
}