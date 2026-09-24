package com.assistant.core.validation

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleConfigSchema
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
                LogManager.schema("No 'data' field found in schema properties", "WARN")
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
            LogManager.schema("Schema merge failed: ${e.message}", "ERROR", e)
            throw ValidationException("Schema merge failed", e)
        }
    }

    /**
     * Embeds ScheduleConfig schema at placeholder location in target schema template
     *
     * This allows reusing the complete ScheduleConfig validation in other tool schemas
     * without duplication. The ScheduleConfig schema content (type, properties, required)
     * replaces the placeholder in the target template.
     *
     * Example usage:
     * ```
     * val template = """
     * {
     *   "properties": {
     *     "schedule": __SCHEDULE_CONFIG__
     *   }
     * }
     * """
     * val finalSchema = embedScheduleConfig(template, "__SCHEDULE_CONFIG__", context)
     * ```
     *
     * @param templateJson Target schema with placeholder
     * @param placeholder Placeholder string to replace (e.g., "__SCHEDULE_CONFIG__")
     * @param context Android context for string resources
     * @return Complete schema with ScheduleConfig embedded
     */
    fun embedScheduleConfig(templateJson: String, placeholder: String, context: Context): String {
        return try {
            // Get the complete ScheduleConfig schema
            val scheduleSchema = ScheduleConfigSchema.getSchema(context)
            val scheduleJson = JSONObject(scheduleSchema.content)

            // Extract the schema body (everything we want to embed)
            // We want type, properties, required from the root level
            val scheduleBody = JSONObject()
            scheduleJson.keys().forEach { key ->
                // Skip metadata fields, keep validation fields
                if (key !in listOf("\$schema", "\$id", "description")) {
                    scheduleBody.put(key, scheduleJson.get(key))
                }
            }

            // Replace placeholder with the schedule body
            // The placeholder must be unquoted in the template for proper JSON structure
            val result = templateJson.replace("\"$placeholder\"", scheduleBody.toString())

            LogManager.schema("Successfully embedded ScheduleConfig schema at placeholder: $placeholder")
            result

        } catch (e: Exception) {
            LogManager.schema("Failed to embed ScheduleConfig: ${e.message}", "ERROR", e)
            throw ValidationException("Failed to embed ScheduleConfig schema", e)
        }
    }
}