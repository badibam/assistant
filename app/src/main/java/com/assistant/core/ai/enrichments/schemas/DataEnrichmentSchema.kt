package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Data (🔍)
 * Définit la structure et documentation pour l'accès aux données
 */
object DataEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Données",
        "description": "Permet à l'IA d'accéder aux données existantes d'une zone ou d'un outil spécifique",
        "properties": {
            "zoneId": {
                "type": "string",
                "description": "ID de la zone dont récupérer les données",
                "required": true
            },
            "period": {
                "type": "string",
                "description": "Période des données à récupérer",
                "enum": ["today", "yesterday", "this_week", "last_week", "this_month", "last_month", "custom"],
                "default": "this_week"
            },
            "toolInstanceId": {
                "type": "string",
                "description": "ID spécifique d'un outil dans la zone (optionnel - si absent, toute la zone)",
                "required": false
            },
            "detailLevel": {
                "type": "string",
                "description": "Niveau de détail des données retournées",
                "enum": ["basic", "summary", "full", "raw"],
                "default": "summary"
            }
        },
        "usage": {
            "description": "L'IA peut utiliser cet enrichissement pour:",
            "capabilities": [
                "Lire les données d'une zone sur une période donnée",
                "Accéder aux données d'un outil spécifique",
                "Analyser les tendances et patterns",
                "Préparer des recommandations basées sur les données"
            ]
        },
        "commands": {
            "description": "Commandes disponibles suite à cet enrichissement:",
            "available": [
                "tool_data.get - Récupérer données d'un outil",
                "zones.get - Informations sur la zone",
                "tools.list - Lister outils de la zone"
            ]
        },
        "examples": {
            "basic": "Données zone santé cette semaine",
            "specific": "Données outil 'Suivi Poids' dernier mois niveau détaillé",
            "analysis": "Données toutes zones ce mois pour analyse croisée"
        }
    }
    """
}