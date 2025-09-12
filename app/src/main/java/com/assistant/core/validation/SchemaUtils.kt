package com.assistant.core.validation

import android.util.Log
import org.json.JSONObject

/**
 * Utilities for JSON schema manipulation and extraction
 */
object SchemaUtils {
    
    /**
     * Extracts "data" or "value" field schema from complete schema
     * Used for data validation during migrations
     * 
     * @param completeSchema Complete JSON schema containing business fields
     * @return JSON schema of data content only, or null if not found
     */
    fun extractDataFieldSchema(completeSchema: String): String? {
        return try {
            val schemaJson = JSONObject(completeSchema)
            val properties = schemaJson.optJSONObject("properties") ?: return null
            
            // Look for required "data" field
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
     * Combines two JSON schemas (merges properties)
     * Used for composing modular schemas
     * 
     * @param baseSchema Base schema
     * @param extensionSchema Extension schema
     * @return Combined schema
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