package com.assistant.core.validation

import android.util.Log
import org.json.JSONObject

/**
 * Utilitaires pour manipulation et extraction de schémas JSON
 */
object SchemaUtils {
    
    /**
     * Extrait le schéma du champ "data" ou "value" depuis un schéma complet
     * Utilisé pour la validation des données lors des migrations
     * 
     * @param completeSchema Schéma JSON complet contenant les champs métier
     * @return Schéma JSON du contenu données seulement, ou null si pas trouvé
     */
    fun extractDataFieldSchema(completeSchema: String): String? {
        return try {
            val schemaJson = JSONObject(completeSchema)
            val properties = schemaJson.optJSONObject("properties") ?: return null
            
            // Cherche le champ "data" requis (plus de fallback legacy)
            val dataSchema = properties.optJSONObject("data") ?: run {
                Log.w("SchemaUtils", "No 'data' field found in schema properties")
                return null
            }
            
            dataSchema.toString()
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Combine deux schémas JSON (merge des propriétés)
     * Utilisé si on veut composer des schémas modulaires
     * 
     * @param baseSchema Schéma de base
     * @param extensionSchema Schéma d'extension
     * @return Schéma combiné
     */
    fun combineSchemas(baseSchema: String, extensionSchema: String): String {
        return try {
            val base = JSONObject(baseSchema)
            val extension = JSONObject(extensionSchema)
            
            // Merge properties
            val baseProperties = base.optJSONObject("properties") ?: JSONObject()
            val extensionProperties = extension.optJSONObject("properties") ?: JSONObject()
            
            extensionProperties.keys().forEach { key ->
                baseProperties.put(key, extensionProperties.get(key))
            }
            
            base.put("properties", baseProperties)
            
            // Merge required arrays if present
            val baseRequired = base.optJSONArray("required")
            val extensionRequired = extension.optJSONArray("required")
            
            if (baseRequired != null && extensionRequired != null) {
                val combinedRequired = mutableSetOf<String>()
                
                for (i in 0 until baseRequired.length()) {
                    combinedRequired.add(baseRequired.getString(i))
                }
                for (i in 0 until extensionRequired.length()) {
                    combinedRequired.add(extensionRequired.getString(i))
                }
                
                base.put("required", combinedRequired.toList())
            } else if (extensionRequired != null) {
                base.put("required", extensionRequired)
            }
            
            base.toString()
            
        } catch (e: Exception) {
            Log.e("SchemaUtils", "Schema merge failed: ${e.message}")
            throw ValidationException("Schema merge failed", e)
        }
    }
}