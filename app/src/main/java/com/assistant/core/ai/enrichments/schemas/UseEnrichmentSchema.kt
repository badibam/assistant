package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Use (📝)
 * Définit la structure pour utiliser/ajouter des données dans un outil existant
 */
object UseEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Utilisation",
        "description": "Permet à l'IA d'ajouter/modifier des données dans un outil existant",
        "properties": {
            "toolInstanceId": {
                "type": "string",
                "description": "ID de l'instance d'outil à utiliser",
                "required": true
            },
            "operation": {
                "type": "string",
                "description": "Type d'opération à effectuer",
                "enum": ["create", "update", "delete"],
                "default": "create"
            },
            "timestamp": {
                "type": "integer",
                "description": "Timestamp spécifique pour l'entrée (optionnel - défaut: maintenant)",
                "required": false
            }
        },
        "usage": {
            "description": "L'IA peut utiliser cet enrichissement pour:",
            "capabilities": [
                "Ajouter une nouvelle entrée dans un outil de suivi",
                "Modifier une entrée existante",
                "Supprimer des données selon critères",
                "Effectuer des opérations en lot sur les données"
            ]
        },
        "workflow": {
            "description": "Processus d'utilisation:",
            "steps": [
                "1. Récupérer les métadonnées de l'outil (schema de données)",
                "2. Valider les données utilisateur contre le schema",
                "3. Exécuter l'opération via tool_data.{operation}",
                "4. Confirmer succès et afficher résultat"
            ]
        },
        "commands": {
            "description": "Commandes disponibles:",
            "available": [
                "tools.get - Récupérer métadonnées de l'outil",
                "tool_data.create - Ajouter nouvelle entrée",
                "tool_data.update - Modifier entrée existante",
                "tool_data.delete - Supprimer entrée",
                "tool_data.get - Lire données existantes pour contexte"
            ]
        },
        "validation": {
            "description": "Validation automatique:",
            "checks": [
                "L'outil existe et est accessible",
                "Les données respectent le schema du tool type",
                "Les permissions permettent l'opération demandée",
                "Le timestamp est valide (si spécifié)"
            ]
        },
        "examples": {
            "tracking": "Ajouter 72kg dans suivi poids aujourd'hui",
            "journal": "Créer entrée journal avec réflexion du jour",
            "goal": "Marquer sous-objectif comme complété"
        }
    }
    """
}