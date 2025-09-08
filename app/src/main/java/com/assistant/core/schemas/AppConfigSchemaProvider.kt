package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider

/**
 * Schema provider for App Configuration categories
 * Used for validation of temporal, UI, and data configuration settings
 */
class AppConfigSchemaProvider(private val context: Context) : SchemaProvider {
    
    override fun getSchema(schemaType: String): String? {
        return when (schemaType) {
            "temporal" -> AppConfigSchemas.TEMPORAL_SCHEMA
            // Future schema types:
            // "ui" -> AppConfigSchemas.UI_SCHEMA
            // "data" -> AppConfigSchemas.DATA_SCHEMA
            else -> null
        }
    }
    
    override fun getFormFieldName(fieldName: String): String {
        return when (fieldName) {
            "week_start_day" -> "Premier jour de la semaine"
            "day_start_hour" -> "Heure de début de journée"
            // Future field translations:
            // "theme" -> "Thème"
            // "default_display_mode" -> "Mode d'affichage par défaut"
            // "default_history_limit" -> "Limite d'historique par défaut"
            else -> fieldName
        }
    }
    
    // Companion object for easy access
    companion object {
        fun create(context: Context): AppConfigSchemaProvider = AppConfigSchemaProvider(context)
    }
}