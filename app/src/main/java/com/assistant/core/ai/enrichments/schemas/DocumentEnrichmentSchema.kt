package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Document (📚)
 * Définit la structure pour documenter et décrire les éléments
 */
object DocumentEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Documentation",
        "description": "Permet à l'IA de créer, modifier ou enrichir la documentation des éléments",
        "properties": {
            "elementType": {
                "type": "string",
                "description": "Type d'élément à documenter",
                "enum": ["zone", "tool", "app", "workflow", "data_pattern"],
                "required": true
            },
            "elementId": {
                "type": "string",
                "description": "ID de l'élément à documenter",
                "required": true
            },
            "docType": {
                "type": "string",
                "description": "Type de documentation à créer/modifier",
                "enum": ["description", "usage_guide", "tips", "troubleshooting", "changelog"],
                "default": "description"
            }
        },
        "documentation_types": {
            "description": {
                "description": "Description générale de l'élément",
                "purpose": "Expliquer le but et l'utilité",
                "example": "Décrire l'objectif de la zone 'Santé'"
            },
            "usage_guide": {
                "description": "Guide d'utilisation détaillé",
                "purpose": "Expliquer comment utiliser efficacement",
                "example": "Guide d'usage optimal du suivi poids"
            },
            "tips": {
                "description": "Conseils et bonnes pratiques",
                "purpose": "Améliorer l'efficacité d'usage",
                "example": "Conseils pour un suivi alimentaire régulier"
            },
            "troubleshooting": {
                "description": "Résolution de problèmes courants",
                "purpose": "Aider à résoudre les difficultés",
                "example": "Que faire si les données semblent incohérentes"
            },
            "changelog": {
                "description": "Historique des modifications",
                "purpose": "Tracer l'évolution des configurations",
                "example": "Changements apportés au calcul nutritionnel"
            }
        },
        "usage": {
            "description": "L'IA peut utiliser cet enrichissement pour:",
            "capabilities": [
                "Créer des descriptions claires et utiles",
                "Générer des guides d'usage personnalisés",
                "Proposer des conseils basés sur les données utilisateur",
                "Documenter les workflows complexes",
                "Maintenir une documentation à jour"
            ]
        },
        "workflow": {
            "description": "Processus de documentation:",
            "steps": [
                "1. Analyser l'élément et son contexte d'usage",
                "2. Identifier le type de documentation le plus utile",
                "3. Récupérer les données pertinentes pour enrichir la doc",
                "4. Générer une documentation personnalisée et actionnable",
                "5. Proposer la documentation à l'utilisateur",
                "6. Sauvegarder dans les métadonnées de l'élément"
            ]
        },
        "commands": {
            "description": "Commandes disponibles selon l'élément:",
            "zone": ["zones.get", "zones.update", "tools.list"],
            "tool": ["tools.get", "tools.update", "tool_data.stats"],
            "app": ["app_config.get", "app_config.update"]
        },
        "personalization": {
            "description": "Personnalisation de la documentation:",
            "factors": [
                "Historique d'usage de l'utilisateur",
                "Patterns dans les données existantes",
                "Fréquence d'utilisation des fonctionnalités",
                "Difficultés rencontrées précédemment"
            ]
        },
        "examples": {
            "zone_desc": "Documenter le but de la zone 'Productivité'",
            "tool_guide": "Créer guide d'usage pour le suivi d'humeur",
            "workflow": "Documenter le processus de suivi nutritionnel complet",
            "tips": "Conseils pour optimiser l'usage des graphiques"
        }
    }
    """
}