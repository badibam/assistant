package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Create (✨)
 * Définit la structure pour la création de nouvelles instances d'outils
 */
object CreateEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Création",
        "description": "Permet à l'IA de créer une nouvelle instance d'outil dans une zone",
        "properties": {
            "toolType": {
                "type": "string",
                "description": "Type d'outil à créer",
                "enum": ["tracking", "goal", "chart", "journal", "list", "note", "message", "alert"],
                "required": true
            },
            "zoneName": {
                "type": "string",
                "description": "Nom de la zone où créer l'outil (existante ou nouvelle)",
                "required": true
            },
            "suggestedName": {
                "type": "string",
                "description": "Nom suggéré pour la nouvelle instance (optionnel)",
                "required": false
            }
        },
        "usage": {
            "description": "L'IA peut utiliser cet enrichissement pour:",
            "capabilities": [
                "Créer un nouvel outil dans une zone existante",
                "Créer une nouvelle zone si nécessaire",
                "Configurer automatiquement l'outil selon les besoins utilisateur",
                "Établir des liens avec d'autres outils existants"
            ]
        },
        "workflow": {
            "description": "Processus de création:",
            "steps": [
                "1. Vérifier si la zone existe, sinon proposer création",
                "2. Valider le type d'outil demandé",
                "3. Récupérer le schema de configuration du type d'outil",
                "4. Créer la configuration par défaut ou demander précisions",
                "5. Exécuter la création via tools.create",
                "6. Confirmer succès et proposer prochaines étapes"
            ]
        },
        "commands": {
            "description": "Commandes disponibles:",
            "available": [
                "zones.list - Vérifier zones existantes",
                "zones.create - Créer nouvelle zone si nécessaire",
                "tools.create - Créer l'instance d'outil",
                "metadata.get_schemas - Récupérer schema de config du tool type"
            ]
        },
        "examples": {
            "simple": "Créer outil tracking dans zone santé",
            "complex": "Créer système complet suivi nutrition (tracking + calculs + graphiques)",
            "new_zone": "Créer outil journal dans nouvelle zone 'Développement personnel'"
        }
    }
    """
}