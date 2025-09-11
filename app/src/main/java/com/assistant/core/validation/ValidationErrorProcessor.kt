package com.assistant.core.validation

import android.content.Context
import com.assistant.R
import com.networknt.schema.ValidationMessage

/**
 * Processes and formats NetworkNT validation errors
 * Handles error prioritization, filtering, and user-friendly formatting
 */
object ValidationErrorProcessor {
    
    /**
     * Filters and formats validation errors into readable string with error prioritization
     * Filters base schema errors when conditional schemas are present
     * @param context Android context for string resource access
     * @param schemaProvider Optional SchemaProvider for field name translation
     */
    fun filterErrors(errors: Set<ValidationMessage>, schema: String, context: Context, schemaProvider: SchemaProvider? = null): String {
        if (errors.isEmpty()) return ""
        
        // Filter out base schema errors if conditional schemas are present
        val filteredErrors = filterRedundantSchemaErrors(errors, schema)
        
        // Group remaining errors by path and prioritize
        val errorsByPath = filteredErrors.groupBy { it.path }
        
        return errorsByPath.map { (path, pathErrors) ->
            val prioritizedError = selectMostRelevantError(pathErrors)
            val translatedMessage = replaceFieldNameInMessage(prioritizedError, schemaProvider, context)
            translatedMessage
        }.joinToString("\n")
    }
    
    /**
     * Filters out base schema errors when conditional schemas (if/then) are present
     * Analyzes the schema definition itself to detect conditional validation
     */
    private fun filterRedundantSchemaErrors(errors: Set<ValidationMessage>, schema: String): List<ValidationMessage> {
        val errorList = errors.toList()
        
        // Robust detection of conditional validation using JSON parsing
        val hasConditionalValidation = detectConditionalValidation(schema)
        
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
            msg.contains("found") && msg.contains("expected")
        }
        if (typeErrors.isNotEmpty()) {
            return typeErrors.first()
        }
        
        // Priority 2: Required field errors
        val requiredErrors = messages.filter { msg ->
            msg.contains("is missing but it is required") ||
            msg.contains("required property") ||
            msg.contains("is required") ||
            msg.contains("required")
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
     * Replaces $.fieldname references in error messages with translated field names
     * Examples:
     * - "$.name is required but missing" -> "Name is required but missing"
     * - "$.value.quantity: must be a number" -> "Quantity: must be a number"
     * - "$.items[2].default_quantity is invalid" -> "Default quantity is invalid"
     */
    private fun replaceFieldNameInMessage(errorMessage: String, schemaProvider: SchemaProvider?, context: Context): String {
        if (schemaProvider == null) return errorMessage
        
        // Find all $.fieldname patterns in the message
        val fieldPathRegex = Regex("""\$\.([a-zA-Z_][a-zA-Z0-9_]*(?:\[[0-9]+\])?(?:\.[a-zA-Z_][a-zA-Z0-9_]*)*(?:\[[0-9]+\])?)""")
        
        var translatedMessage = errorMessage
        
        fieldPathRegex.findAll(errorMessage).forEach { match ->
            val fullPath = match.value // e.g., "$.value.quantity"
            val fieldPath = match.groupValues[1] // e.g., "value.quantity"
            
            // DEBUG: Log field replacement process
            safeLog("DEBUG FIELD REPLACE: fullPath='$fullPath', fieldPath='$fieldPath'")
            
            // Get the actual field name (last component)
            val fieldName = when {
                fieldPath.contains(".") -> {
                    // Get last part after final dot: "value.quantity" -> "quantity"
                    fieldPath.substringAfterLast(".")
                }
                else -> {
                    // Direct field: "fieldname" -> "fieldname"
                    fieldPath
                }
            }
            
            safeLog("DEBUG FIELD REPLACE: extracted fieldName='$fieldName'")
            
            val translatedName = schemaProvider.getFormFieldName(fieldName, context)
            safeLog("DEBUG FIELD REPLACE: translated to='$translatedName'")
            
            // Replace the full $.path with just the translated name
            translatedMessage = translatedMessage.replace(fullPath, translatedName)
        }
        
        return translatedMessage
    }
    
    /**
     * Detects conditional validation using precise regex patterns
     * Looks for "if": followed by an object AND "then": followed by an object
     */
    private fun detectConditionalValidation(schemaJson: String): Boolean {
        return schemaJson.contains(Regex("\"if\"\\s*:\\s*\\{")) && 
               schemaJson.contains(Regex("\"then\"\\s*:\\s*\\{"))
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