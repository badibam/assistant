package com.assistant.core.schemas

/**
 * Schémas JSON pour la validation des catégories de configuration de l'application
 * Chaque catégorie possède son propre schéma de validation
 */
object AppConfigSchemas {
    
    /**
     * Schéma pour la catégorie "temporal"
     * Validation des paramètres temporels de l'application
     */
    const val TEMPORAL_SCHEMA = """
    {
        "type": "object",
        "properties": {
            "week_start_day": {
                "type": "string",
                "enum": ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"],
                "description": "Premier jour de la semaine"
            },
            "day_start_hour": {
                "type": "integer",
                "minimum": 0,
                "maximum": 23,
                "description": "Heure de début de journée (0-23h)"
            }
        },
        "required": ["week_start_day", "day_start_hour"],
        "additionalProperties": false
    }
    """
    
    // Future schemas:
    /*
    const val UI_SCHEMA = """
    {
        "type": "object", 
        "properties": {
            "default_display_mode": {
                "type": "string",
                "enum": ["icon", "minimal", "line", "condensed", "extended", "square", "full"]
            },
            "theme": {
                "type": "string", 
                "enum": ["default", "dark", "custom"]
            },
            "grid_columns": {
                "type": "integer",
                "minimum": 2,
                "maximum": 4
            }
        },
        "required": ["default_display_mode", "theme", "grid_columns"],
        "additionalProperties": false
    }
    """
    
    const val DATA_SCHEMA = """
    {
        "type": "object",
        "properties": {
            "default_history_limit": {
                "type": "integer",
                "enum": [10, 25, 100, 250, 1000]
            },
            "backup_frequency": {
                "type": "string",
                "enum": ["daily", "weekly", "manual"]
            },
            "data_retention_days": {
                "type": "integer",
                "minimum": 30
            }
        },
        "required": ["default_history_limit", "backup_frequency"],
        "additionalProperties": false
    }
    """
    */
    
    /**
     * Récupère le schéma pour une catégorie donnée
     */
    fun getSchemaForCategory(category: String): String? {
        return when (category) {
            "temporal" -> TEMPORAL_SCHEMA
            // "ui" -> UI_SCHEMA
            // "data" -> DATA_SCHEMA
            else -> null
        }
    }
    
    /**
     * Liste des catégories supportées
     */
    fun getSupportedCategories(): List<String> {
        return listOf("temporal")
        // return listOf("temporal", "ui", "data")
    }
}