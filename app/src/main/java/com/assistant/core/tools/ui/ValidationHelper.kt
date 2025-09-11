package com.assistant.core.tools.ui

import android.content.Context
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.ui.UI
import com.assistant.core.ui.Duration

/**
 * Helper de validation unifiée pour tous les tooltypes.
 * Centralise la logique ToolTypeManager + SchemaValidator + Toast + Logging.
 */
object ValidationHelper {

    /**
     * Valide les données et affiche automatiquement un toast en cas d'erreur.
     * 
     * @param toolTypeName Nom du tooltype (ex: "tracking", "notes")
     * @param configData Données à valider sous forme Map
     * @param context Contexte Android pour le toast
     * @param schemaType Type de schéma à utiliser ("config" ou "data")
     * @param onSuccess Callback appelé si validation réussie avec JSON string
     * @param onError Callback optionnel appelé en cas d'erreur (en plus du toast)
     * @return true si validation réussie, false sinon
     */
    fun validateAndSave(
        toolTypeName: String,
        configData: Map<String, Any>,
        context: Context,
        schemaType: String = "config",
        onSuccess: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val toolType = ToolTypeManager.getToolType(toolTypeName)
        
        if (toolType == null) {
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val errorMsg = s.shared("error_tooltype_not_found").format(toolTypeName)
            android.util.Log.e("ValidationHelper", errorMsg)
            showErrorToast(context, errorMsg)
            onError?.invoke(errorMsg)
            return false
        }
        
        val validation = SchemaValidator.validate(toolType, configData, context, schemaType = schemaType)
        
        if (validation.isValid) {
            // Conversion Map vers JSON string pour compatibilité
            val jsonString = mapToJsonString(configData)
            onSuccess(jsonString)
            return true
        } else {
            val s = com.assistant.core.strings.Strings.`for`(context = context)
            val errorMsg = validation.errorMessage ?: s.shared("message_validation_error_simple")
            android.util.Log.e("ValidationHelper", "Validation failed for $toolTypeName ($schemaType): $errorMsg")
            showErrorToast(context, errorMsg)
            onError?.invoke(errorMsg)
            return false
        }
    }
    
    /**
     * Affiche un toast d'erreur avec durée longue.
     */
    private fun showErrorToast(context: Context, message: String) {
        UI.Toast(context, message, Duration.LONG)
    }
    
    /**
     * Convertit une Map en JSON string pour compatibilité avec l'API existante.
     */
    private fun mapToJsonString(data: Map<String, Any>): String {
        return org.json.JSONObject(data).toString()
    }
}