package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Modify (🔧)
 * Définit la structure pour modifier la configuration d'outils existants
 */
object ModifyEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Modification",
        "description": "Permet à l'IA de modifier la configuration ou les paramètres d'un outil existant",
        "properties": {
            "toolInstanceId": {
                "type": "string",
                "description": "ID de l'instance d'outil à modifier",
                "required": true
            },
            "aspect": {
                "type": "string",
                "description": "Aspect à modifier",
                "enum": ["config", "name", "description", "permissions", "display_settings"],
                "default": "config"
            },
            "description": {
                "type": "string",
                "description": "Description libre des modifications souhaitées",
                "required": false
            }
        },
        "usage": {
            "description": "L'IA peut utiliser cet enrichissement pour:",
            "capabilities": [
                "Modifier la configuration technique d'un outil",
                "Changer le nom ou la description d'un outil",
                "Ajuster les paramètres d'affichage",
                "Modifier les permissions spécifiques de l'outil",
                "Optimiser les réglages selon l'usage"
            ]
        },
        "workflow": {
            "description": "Processus de modification:",
            "steps": [
                "1. Récupérer la configuration actuelle de l'outil",
                "2. Identifier les paramètres modifiables selon l'aspect",
                "3. Proposer les modifications ou demander précisions",
                "4. Valider les nouveaux paramètres contre le schema",
                "5. Exécuter la modification via tools.update",
                "6. Confirmer les changements effectués"
            ]
        },
        "aspects": {
            "config": {
                "description": "Configuration technique de l'outil",
                "examples": ["Unité de mesure", "Fréquence de suivi", "Critères de validation"]
            },
            "name": {
                "description": "Nom affiché de l'outil",
                "examples": ["Renommer 'Poids' en 'Suivi Poids Quotidien'"]
            },
            "description": {
                "description": "Description/but de l'outil",
                "examples": ["Clarifier l'objectif du suivi"]
            },
            "permissions": {
                "description": "Permissions spécifiques à cet outil",
                "examples": ["Autoriser suppression automatique", "Demander validation"]
            },
            "display_settings": {
                "description": "Paramètres d'affichage dans la zone",
                "examples": ["Mode d'affichage", "Position", "Couleur"]
            }
        },
        "commands": {
            "description": "Commandes disponibles:",
            "available": [
                "tools.get - Récupérer configuration actuelle",
                "tools.update - Appliquer les modifications",
                "metadata.get_schemas - Schema de configuration du tool type"
            ]
        },
        "examples": {
            "config": "Modifier l'unité du suivi poids de kg à lbs",
            "display": "Changer l'affichage du graphique en mode condensé",
            "permissions": "Permettre la suppression automatique des anciennes données"
        }
    }
    """
}