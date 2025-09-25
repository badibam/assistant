package com.assistant.core.tools.ui

import android.content.Context
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.ui.UI
import com.assistant.core.ui.Duration
import com.assistant.core.utils.LogManager

/**
 * Unified validation helper for all tooltypes.
 * Centralizes ToolTypeManager + SchemaValidator + Toast + Logging logic.
 */
object ValidationHelper {

    /**
     * Validates data and automatically displays toast on error.
     *
     * @param toolTypeName Tooltype name (e.g., "tracking", "notes")
     * @param configData Data to validate as Map
     * @param context Android context for toast
     * @param schemaId Full schema ID to use (e.g., "tracking_config_numeric", "notes_data")
     * @param onSuccess Callback called if validation succeeds with JSON string
     * @param onError Optional callback called on error (in addition to toast)
     * @return true if validation succeeded, false otherwise
     */
    fun validateAndSave(
        toolTypeName: String,
        configData: Map<String, Any>,
        context: Context,
        schemaId: String,
        onSuccess: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val toolType = ToolTypeManager.getToolType(toolTypeName)

        if (toolType == null) {
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val errorMsg = s.shared("error_tooltype_not_found").format(toolTypeName)
            LogManager.service(errorMsg, "ERROR")
            showErrorToast(context, errorMsg)
            onError?.invoke(errorMsg)
            return false
        }

        // Extract schema_id from the data
        val extractedSchemaId = configData["schema_id"] as? String
        if (extractedSchemaId.isNullOrEmpty()) {
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val errorMsg = s.shared("error_missing_schema_id")
            LogManager.service("Missing schema_id in data for $toolTypeName", "ERROR")
            showErrorToast(context, errorMsg)
            onError?.invoke(errorMsg)
            return false
        }

        val schema = toolType.getSchema(extractedSchemaId, context)
        val validation = if (schema != null) {
            SchemaValidator.validate(schema, configData, context)
        } else {
            com.assistant.core.validation.ValidationResult.error("Schema not found: $extractedSchemaId")
        }
        
        if (validation.isValid) {
            // Map to JSON string conversion for compatibility
            val jsonString = mapToJsonString(configData)
            onSuccess(jsonString)
            return true
        } else {
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val errorMsg = validation.errorMessage ?: s.shared("message_validation_error_simple")
            LogManager.service("Validation failed for $toolTypeName ($schemaId): $errorMsg", "ERROR")
            showErrorToast(context, errorMsg)
            onError?.invoke(errorMsg)
            return false
        }
    }
    
    /**
     * Shows error toast with long duration.
     */
    private fun showErrorToast(context: Context, message: String) {
        UI.Toast(context, message, Duration.LONG)
    }
    
    /**
     * Converts Map to JSON string for compatibility with existing API.
     */
    private fun mapToJsonString(data: Map<String, Any>): String {
        return org.json.JSONObject(data).toString()
    }
}