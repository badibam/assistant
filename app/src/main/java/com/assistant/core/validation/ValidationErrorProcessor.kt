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
            val friendlyFieldName = extractFieldNameFromMessage(prioritizedError, schemaProvider)
            val translatedMessage = translateErrorMessage(prioritizedError, context)
            "$friendlyFieldName : $translatedMessage"
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
     * Translates technical error messages to user-friendly French messages
     * Uses string resources for consistent translations
     */
    private fun translateErrorMessage(errorMessage: String, context: Context): String {
        val cleanMessage = if (errorMessage.contains(":")) {
            errorMessage.substringAfter(":").trim()
        } else {
            errorMessage
        }
        
        return when {
            // Type mismatch errors
            cleanMessage.contains("string found, number expected") -> 
                context.getString(R.string.validation_type_string_number)
            cleanMessage.contains("number found, string expected") -> 
                context.getString(R.string.validation_type_number_string)
            cleanMessage.contains("boolean found, string expected") -> 
                context.getString(R.string.validation_type_boolean_string)
            cleanMessage.contains("array found, object expected") -> 
                context.getString(R.string.validation_type_array_object)
            cleanMessage.contains("object found, array expected") -> 
                context.getString(R.string.validation_type_object_array)
            cleanMessage.contains("found,") && cleanMessage.contains("expected") -> 
                context.getString(R.string.validation_type_mismatch)
            
            // Required field errors
            cleanMessage.contains("is missing but it is required") -> 
                context.getString(R.string.validation_required_field)
            cleanMessage.contains("required property") -> 
                context.getString(R.string.validation_required_property)
            cleanMessage.contains("is required") -> 
                context.getString(R.string.validation_required_field)
            
            // String length constraints
            cleanMessage.matches(Regex("must be at least (\\d+) characters long")) -> {
                val length = Regex("must be at least (\\d+) characters long").find(cleanMessage)?.groupValues?.get(1)
                if (length == "1") {
                    context.getString(R.string.validation_min_length_1)
                } else {
                    context.getString(R.string.validation_min_length, length ?: "1")
                }
            }
            cleanMessage.matches(Regex("must be at most (\\d+) characters long")) -> {
                val length = Regex("must be at most (\\d+) characters long").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_max_length, length ?: "0")
            }
            
            // Numeric constraints  
            cleanMessage.matches(Regex("must be greater than or equal to ([\\d.]+)")) -> {
                val value = Regex("must be greater than or equal to ([\\d.]+)").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_minimum, value ?: "0")
            }
            cleanMessage.matches(Regex("must be less than or equal to ([\\d.]+)")) -> {
                val value = Regex("must be less than or equal to ([\\d.]+)").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_maximum, value ?: "0")
            }
            cleanMessage.matches(Regex("must be greater than ([\\d.]+)")) -> {
                val value = Regex("must be greater than ([\\d.]+)").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_exclusive_minimum, value ?: "0")
            }
            cleanMessage.matches(Regex("must be less than ([\\d.]+)")) -> {
                val value = Regex("must be less than ([\\d.]+)").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_exclusive_maximum, value ?: "0")
            }
            
            // Array constraints
            cleanMessage.matches(Regex("must have at least (\\d+) items")) -> {
                val count = Regex("must have at least (\\d+) items").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_min_items, count?.toIntOrNull() ?: 1)
            }
            cleanMessage.matches(Regex("must have at most (\\d+) items")) -> {
                val count = Regex("must have at most (\\d+) items").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_max_items, count?.toIntOrNull() ?: 0)
            }
            cleanMessage.contains("items are not unique") -> 
                context.getString(R.string.validation_unique_items)
            
            // Format and pattern errors
            cleanMessage.contains("does not match") -> 
                context.getString(R.string.validation_pattern_mismatch)
            cleanMessage.contains("pattern") -> 
                context.getString(R.string.validation_pattern_mismatch)
            cleanMessage.contains("format") && cleanMessage.contains("email") -> 
                context.getString(R.string.validation_format_email)
            cleanMessage.contains("format") && cleanMessage.contains("date") -> 
                context.getString(R.string.validation_format_date)
            cleanMessage.contains("format") && cleanMessage.contains("time") -> 
                context.getString(R.string.validation_format_time)
            cleanMessage.contains("format") && cleanMessage.contains("uri") -> 
                context.getString(R.string.validation_format_uri)
            cleanMessage.contains("format") -> 
                context.getString(R.string.validation_invalid_format)
            
            // Enum/choice errors
            cleanMessage.contains("enum") -> 
                context.getString(R.string.validation_enum_mismatch)
            cleanMessage.matches(Regex("is not one of \\[(.+)]")) -> {
                val values = Regex("is not one of \\[(.+)]").find(cleanMessage)?.groupValues?.get(1)
                context.getString(R.string.validation_enum_allowed, values ?: "")
            }
            
            // Schema structure errors
            cleanMessage.contains("additional properties") -> 
                context.getString(R.string.validation_additional_properties)
            cleanMessage.contains("is not defined in the schema") -> 
                context.getString(R.string.validation_not_defined_schema)
            
            // Generic fallback
            else -> context.getString(R.string.validation_invalid_format)
        }
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