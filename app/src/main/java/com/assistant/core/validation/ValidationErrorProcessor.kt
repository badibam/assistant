package com.assistant.core.validation

import com.networknt.schema.ValidationMessage

/**
 * Processes and formats NetworkNT validation errors
 * Handles error prioritization, filtering, and user-friendly formatting
 */
object ValidationErrorProcessor {
    
    /**
     * Filters and formats validation errors into readable string with error prioritization
     * Filters base schema errors when conditional schemas are present
     * @param schemaProvider Optional SchemaProvider for field name translation
     */
    fun filterErrors(errors: Set<ValidationMessage>, schema: String, schemaProvider: SchemaProvider? = null): String {
        if (errors.isEmpty()) return ""
        
        // Filter out base schema errors if conditional schemas are present
        val filteredErrors = filterRedundantSchemaErrors(errors, schema)
        
        // Group remaining errors by path and prioritize
        val errorsByPath = filteredErrors.groupBy { it.path }
        
        return errorsByPath.map { (path, pathErrors) ->
            val prioritizedError = selectMostRelevantError(pathErrors)
            val friendlyFieldName = extractFieldNameFromMessage(prioritizedError, schemaProvider)
            val cleanMessage = cleanErrorMessage(prioritizedError)
            "$friendlyFieldName : $cleanMessage"
        }.joinToString("; ")
    }
    
    /**
     * Filters out base schema errors when conditional schemas (if/then) are present
     * Analyzes the schema definition itself to detect conditional validation
     */
    private fun filterRedundantSchemaErrors(errors: Set<ValidationMessage>, schema: String): List<ValidationMessage> {
        val errorList = errors.toList()
        
        // Check if schema contains conditional validation by analyzing schema content
        val hasConditionalValidation = schema.contains("\"if\"") && schema.contains("\"then\"")
        
        safeLog("Schema has conditional validation: $hasConditionalValidation")
        
        if (!hasConditionalValidation) {
            // No conditional validation, keep all errors
            return errorList
        }
        
        // Filter out base schema structure errors when conditional validation is present
        val filteredErrors = errorList.filter { error ->
            val isBaseSchemaError = (error.message.contains("not defined in the schema") || 
                                   error.message.contains("additional properties")) &&
                                   !error.schemaPath.contains("/then/") &&
                                   !error.schemaPath.contains("/else/")
            
            if (isBaseSchemaError) {
                safeLog("Filtering out base schema error: ${error.message}")
            }
            
            // Keep error if it's not a base schema error
            !isBaseSchemaError
        }
        
        safeLog("Filtered errors: ${filteredErrors.size} out of ${errorList.size}")
        return filteredErrors
    }
    
    /**
     * Selects the most relevant error message from multiple errors for the same field
     * Prioritizes type errors over schema structure errors
     */
    private fun selectMostRelevantError(errors: List<ValidationMessage>): String {
        val messages = errors.map { it.message }
        
        // Priority 1: Type mismatch errors (most specific)
        val typeErrors = messages.filter { msg ->
            msg.contains("found,") && msg.contains("expected") ||
            msg.contains("string found, number expected") ||
            msg.contains("number found, string expected") ||
            msg.contains("boolean found, string expected") ||
            msg.contains("array found, object expected") ||
            msg.contains("object found, array expected") ||
            msg.contains("trouvé") && msg.contains("attendu")
        }
        if (typeErrors.isNotEmpty()) {
            return typeErrors.first()
        }
        
        // Priority 2: Required field errors
        val requiredErrors = messages.filter { msg ->
            msg.contains("is missing but it is required") ||
            msg.contains("required property") ||
            msg.contains("is required") ||
            msg.contains("obligatoire")
        }
        if (requiredErrors.isNotEmpty()) {
            return requiredErrors.first()
        }
        
        // Priority 3: Format/pattern errors
        val formatErrors = messages.filter { msg ->
            msg.contains("does not match") ||
            msg.contains("pattern") ||
            msg.contains("format")
        }
        if (formatErrors.isNotEmpty()) {
            return formatErrors.first()
        }
        
        // Priority 4: Range/constraint errors
        val constraintErrors = messages.filter { msg ->
            msg.contains("minimum") ||
            msg.contains("maximum") ||
            msg.contains("minLength") ||
            msg.contains("maxLength") ||
            msg.contains("enum")
        }
        if (constraintErrors.isNotEmpty()) {
            return constraintErrors.first()
        }
        
        // Priority 5 (lowest): Schema structure errors
        val schemaErrors = messages.filter { msg ->
            msg.contains("is not defined in the schema") ||
            msg.contains("additional properties") ||
            msg.contains("additionalProperties")
        }
        if (schemaErrors.isNotEmpty() && messages.size == 1) {
            // Only show schema errors if they're the only error
            return schemaErrors.first()
        }
        
        // Fallback: return first error if no pattern matches
        return messages.firstOrNull() ?: "invalid format"
    }
    
    /**
     * Extracts field name from error message and translates it to user-friendly name
     * Examples: 
     * - "$.name: must be at least 1 characters long" -> "name" -> "Nom"
     * - "$.items[2].default_quantity: integer found..." -> "default_quantity" -> "Quantité par défaut"
     */
    private fun extractFieldNameFromMessage(errorMessage: String, schemaProvider: SchemaProvider?): String {
        if (schemaProvider == null) return "Champ"
        
        // Extract field path from error message (before the colon)
        // NetworkNT format: "$.path.to.field: error description"
        val fieldPath = errorMessage.substringBefore(":").trim()
        
        // Get only the last level of the field path
        // Examples: "$.value.quantity" -> "quantity", "$.items[2].default_quantity" -> "default_quantity"
        val fieldName = when {
            fieldPath.contains(".") -> {
                // Get last part after final dot: "$.value.quantity" -> "quantity"
                fieldPath.substringAfterLast(".")
            }
            fieldPath.startsWith("$.") -> {
                // Direct field: "$.fieldname" -> "fieldname"
                fieldPath.substringAfter("$.")
            }
            else -> {
                // Fallback: use as-is
                fieldPath
            }
        }
        
        return schemaProvider.getFormFieldName(fieldName)
    }
    
    /**
     * Removes the technical field path from error message
     * Examples:
     * - "$.name: must be at least 1 characters long" -> "must be at least 1 characters long"
     * - "$.items[2].default_quantity: integer found, string expected" -> "integer found, string expected"
     */
    private fun cleanErrorMessage(errorMessage: String): String {
        return if (errorMessage.contains(":")) {
            errorMessage.substringAfter(":").trim()
        } else {
            errorMessage
        }
    }
    
    /**
     * Safe logging that works in both Android and unit test environments
     */
    private fun safeLog(message: String) {
        try {
            android.util.Log.d("VALDEBUG", message)
        } catch (e: RuntimeException) {
            println("VALDEBUG: $message")
        }
    }
}