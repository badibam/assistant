package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Pointer (🔍)
 * Définit la structure et documentation pour référencer des données existantes
 */
object PointerEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Pointer",
        "description": "Permet à l'IA d'accéder aux données existantes avec sélection granulaire",
        "properties": {
            "selectedPath": {
                "type": "string",
                "description": "Chemin complet sélectionné via ZoneScopeSelector",
                "required": true,
                "examples": [
                    "zones/health",
                    "zones/health/tools/weight_tracking",
                    "zones/health/tools/weight_tracking/fields/value"
                ]
            },
            "selectedValues": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Valeurs spécifiques sélectionnées (optionnel)",
                "required": false
            },
            "selectionLevel": {
                "type": "string",
                "description": "Niveau de sélection dans la hiérarchie",
                "enum": ["ZONE", "INSTANCE"],
                "required": true,
                "note": "FIELD level removed - selection limited to ZONE and INSTANCE only"
            },
            "importance": {
                "type": "string",
                "description": "Importance des données pour le contexte IA",
                "enum": ["optionnel", "important", "essentiel"],
                "default": "important"
            },
            "includeData": {
                "type": "boolean",
                "description": "Inclure les données réelles en plus de l'échantillon (pour niveau INSTANCE)",
                "default": false,
                "note": "Si true, ajoute TOOL_DATA aux commands en plus de TOOL_DATA_SAMPLE + TOOL_STATS"
            },
            "période": {
                "type": "string",
                "description": "Filtre temporel optionnel pour limiter les données",
                "required": false,
                "examples": ["last_week", "this_month", "today", "custom_range"]
            },
            "description": {
                "type": "string",
                "description": "Description optionnelle expliquant pourquoi ces données sont référencées",
                "required": false,
                "maxLength": 255
            }
        },
        "usage": {
            "description": "L'IA peut utiliser cet enrichissement pour:",
            "capabilities": [
                "Accéder aux données avec granularité spécifique (zone/outil/champ)",
                "Filtrer temporellement les données selon la période",
                "Adapter l'inclusion contextuelle selon l'importance",
                "Comprendre l'intention via la description utilisateur"
            ]
        },
        "importance_levels": {
            "optionnel": "Données suggérées - IA peut les requêter si nécessaire",
            "important": "Données incluses automatiquement dans le contexte",
            "essentiel": "Données critiques avec priorité haute"
        },
        "commands": {
            "description": "Commandes disponibles suite à cet enrichissement:",
            "available": [
                "tool_data.get - Récupérer données d'un outil spécifique",
                "zones.get - Informations sur la zone",
                "tools.list - Lister outils de la zone",
                "data_navigator.explore - Navigation dans les données"
            ]
        },
        "examples": {
            "zone_level": "Toutes les données de la zone santé cette semaine",
            "tool_level": "Données du suivi poids avec période filtrée",
            "field_level": "Uniquement les valeurs de poids (économie tokens)",
            "with_values": "Entrées spécifiques sélectionnées par timestamp"
        }
    }
    """
}