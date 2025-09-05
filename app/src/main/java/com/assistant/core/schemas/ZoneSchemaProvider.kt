package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.SchemaLoader

/**
 * Schema provider for Zone configuration (business entity)
 * Used by CreateZoneScreen and other zone-related forms
 * Uses external JSON schema with template variables
 */
class ZoneSchemaProvider(private val context: Context) : SchemaProvider {
    
    override fun getConfigSchema(): String {
        return SchemaLoader.loadSchema(context, "zone_config_schema.json")
    }
    
    override fun getDataSchema(): String? = null
    
    override fun getFormFieldName(fieldName: String): String = when(fieldName) {
        "name" -> "Nom de la zone"
        "description" -> "Description"
        else -> "Champ"
    }
    
    // Companion object for easy access
    companion object {
        fun create(context: Context): ZoneSchemaProvider = ZoneSchemaProvider(context)
    }
}