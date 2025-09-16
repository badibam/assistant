package com.assistant.core.ai.enrichments.schemas

/**
 * Schema pour l'enrichissement Organize (📁)
 * Définit la structure pour organiser zones et outils
 */
object OrganizeEnrichmentSchema {

    const val SCHEMA = """
    {
        "type": "object",
        "title": "Enrichissement Organisation",
        "description": "Permet à l'IA d'organiser, déplacer et structurer zones et outils",
        "properties": {
            "action": {
                "type": "string",
                "description": "Action d'organisation à effectuer",
                "enum": [
                    "move_tool", "move_zone",
                    "create_zone", "delete_zone",
                    "reorder_tools", "reorder_zones",
                    "merge_zones", "split_zone"
                ],
                "required": true
            },
            "elementId": {
                "type": "string",
                "description": "ID de l'élément concerné par l'action",
                "required": true
            },
            "targetId": {
                "type": "string",
                "description": "ID de destination (pour move, merge, etc.)",
                "required": false
            }
        },
        "actions": {
            "move_tool": {
                "description": "Déplacer un outil vers une autre zone",
                "requires": ["elementId (tool)", "targetId (zone)"],
                "example": "Déplacer 'Suivi Poids' vers zone 'Fitness'"
            },
            "move_zone": {
                "description": "Réorganiser l'ordre des zones",
                "requires": ["elementId (zone)", "targetId (position)"],
                "example": "Déplacer zone 'Santé' en première position"
            },
            "create_zone": {
                "description": "Créer une nouvelle zone",
                "requires": ["elementId (nom de la nouvelle zone)"],
                "example": "Créer zone 'Développement Personnel'"
            },
            "delete_zone": {
                "description": "Supprimer une zone (déplace ses outils)",
                "requires": ["elementId (zone)", "targetId (zone destination)"],
                "example": "Supprimer zone 'Test' et déplacer outils vers 'Général'"
            },
            "reorder_tools": {
                "description": "Réorganiser l'ordre des outils dans une zone",
                "requires": ["elementId (zone)", "targetId (nouvel ordre)"],
                "example": "Réorganiser outils zone santé par fréquence d'usage"
            }
        },
        "workflow": {
            "description": "Processus d'organisation:",
            "steps": [
                "1. Vérifier l'existence des éléments concernés",
                "2. Valider les permissions pour l'action demandée",
                "3. Prévoir les impacts (déplacements, liens cassés)",
                "4. Proposer un résumé des changements",
                "5. Exécuter l'action via commandes appropriées",
                "6. Confirmer les modifications effectuées"
            ]
        },
        "commands": {
            "description": "Commandes disponibles selon l'action:",
            "available": [
                "zones.list - Lister zones existantes",
                "zones.create - Créer nouvelle zone",
                "zones.delete - Supprimer zone",
                "tools.move - Déplacer outil entre zones",
                "tools.reorder - Réorganiser ordre des outils"
            ]
        },
        "safety": {
            "description": "Mesures de sécurité:",
            "checks": [
                "Confirmation utilisateur pour suppressions",
                "Sauvegarde avant modifications importantes",
                "Vérification des dépendances entre outils",
                "Préservation des données lors des déplacements"
            ]
        },
        "examples": {
            "simple": "Déplacer outil vers autre zone",
            "complex": "Réorganiser toutes les zones par thématique",
            "maintenance": "Fusionner zones similaires et nettoyer structure"
        }
    }
    """
}