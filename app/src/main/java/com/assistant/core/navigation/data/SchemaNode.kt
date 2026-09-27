package com.assistant.core.navigation.data

/**
 * A node in the schema navigation tree
 */
data class SchemaNode(
    val path: String,           // "zones.health" ou "tools.weight_tracker.value"
    val displayName: String,    // Nom affiché à l'utilisateur
    val type: NodeType,         // ZONE, TOOL, FIELD
    val hasChildren: Boolean,   // Pour UI expand/collapse
    val toolType: String? = null,    // Pour les outils : "tracking", "goal", etc.
    val fieldType: String? = null    // Pour les champs : "string", "number", "boolean", etc.
)

/**
 * The kinds of node the navigation tree holds
 */
enum class NodeType {
    ZONE,   // Zone de l'application
    TOOL,   // Instance d'outil
    FIELD   // Champ de données
}
