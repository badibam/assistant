package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.schemas.MainScreenSchemas

/**
 * Schema provider for MainScreen UI configuration (future settings)
 * Currently minimal structure - to be implemented later
 */
object MainScreenSchemaProvider : SchemaProvider {
    
    override fun getConfigSchema(): String {
        return MainScreenSchemas.CONFIG_SCHEMA
    }
    
    fun getConfigSchema(context: Context): String {
        return MainScreenSchemas.CONFIG_SCHEMA
    }
    
    override fun getDataSchema(): String? = null
    
    override fun getFormFieldName(fieldName: String): String = when(fieldName) {
        // To be implemented when MainScreen gets configuration options
        else -> "Paramètre"
    }
}