package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider

/**
 * Schema provider for App Configuration categories
 * Used for validation of temporal, UI, and data configuration settings
 */
class AppConfigSchemaProvider(private val context: Context) : SchemaProvider {
    
    override fun getSchema(schemaType: String, context: Context): String? {
        return when (schemaType) {
            "temporal" -> AppConfigSchemas.getTemporalSchema(context)
            // Future schema types:
            // "ui" -> AppConfigSchemas.getUiSchema(context)
            // "data" -> AppConfigSchemas.getDataSchema(context)
            else -> null
        }
    }
    
    override fun getFormFieldName(fieldName: String, context: android.content.Context?): String {
        if (context == null) throw IllegalArgumentException("Context required for internationalized field names")
        
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        return when (fieldName) {
            "week_start_day" -> s.shared("app_config_temporal_week_start_day")
            "day_start_hour" -> s.shared("app_config_temporal_day_start_hour")
            else -> fieldName
        }
    }
    
    // Companion object for easy access
    companion object {
        fun create(context: Context): AppConfigSchemaProvider = AppConfigSchemaProvider(context)
    }
}