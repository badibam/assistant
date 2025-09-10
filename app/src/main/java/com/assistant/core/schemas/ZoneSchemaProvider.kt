package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.schemas.ZoneSchemas

/**
 * Schema provider for Zone configuration (business entity)
 * Used by CreateZoneScreen and other zone-related forms
 * Uses external JSON schema with template variables
 */
class ZoneSchemaProvider(private val context: Context) : SchemaProvider {
    
    override fun getSchema(schemaType: String): String? {
        return when (schemaType) {
            "config" -> ZoneSchemas.CONFIG_SCHEMA
            else -> null
        }
    }
    
    override fun getFormFieldName(fieldName: String, context: android.content.Context?): String {
        if (context == null) throw IllegalArgumentException("Context required for internationalized field names")
        
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        return when(fieldName) {
            "name" -> s.shared("label_zone_name")
            "description" -> s.shared("label_description")
            else -> s.shared("label_field_generic")
        }
    }
    
    // Companion object for easy access
    companion object {
        fun create(context: Context): ZoneSchemaProvider = ZoneSchemaProvider(context)
    }
}