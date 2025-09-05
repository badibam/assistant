package com.assistant.core.ui.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.SchemaLoader

/**
 * Schema provider for MainScreen UI configuration (future settings)
 * Currently minimal structure - to be implemented later
 */
object MainScreenSchemaProvider : SchemaProvider {
    
    override fun getConfigSchema(): String {
        throw UnsupportedOperationException("Context required. Use getConfigSchema(context) instead.")
    }
    
    fun getConfigSchema(context: Context): String {
        return SchemaLoader.loadSchema(context, "mainscreen_config_schema.json")
    }
    
    override fun getDataSchema(): String? = null
    
    override fun getFormFieldName(fieldName: String): String = when(fieldName) {
        // To be implemented when MainScreen gets configuration options
        else -> "Paramètre"
    }
}