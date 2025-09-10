package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.schemas.MainScreenSchemas

/**
 * Schema provider for MainScreen UI configuration (future settings)
 * Currently minimal structure - to be implemented later
 */
object MainScreenSchemaProvider : SchemaProvider {
    
    override fun getSchema(schemaType: String): String? {
        return when (schemaType) {
            "config" -> MainScreenSchemas.CONFIG_SCHEMA
            else -> null
        }
    }
    
    fun getConfigSchema(context: Context): String {
        return MainScreenSchemas.CONFIG_SCHEMA
    }
    
    override fun getFormFieldName(fieldName: String, context: android.content.Context?): String {
        if (context == null) throw IllegalArgumentException("Context required for internationalized field names")
        
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        return when(fieldName) {
            // To be implemented when MainScreen gets configuration options
            else -> s.shared("label_field_parameter")
        }
    }
}